package de.gaffa.tools.coreos.mavenplugin;

import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.SftpException;
import de.gaffa.tools.coreos.mavenplugin.util.ServiceFileBuilder;
import de.gaffa.tools.coreos.mavenplugin.util.SmokeTester;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;
import java.io.IOException;
import java.util.List;

@Mojo(name = "deploy", defaultPhase = LifecyclePhase.DEPLOY)
public class DeployMojo extends AbstractMojo {

    private Log log;

    @Parameter(defaultValue = "core")
    private String userName;

    @Parameter(required = true)
    private String nodeAdress;

    @Parameter(required = true)
    private File keyFile;

    @Parameter(required = true)
    private String serviceName;

    @Parameter(defaultValue = "1")
    private int instances;

    @Parameter(required = true)
    private String dockerImageName;

    @Parameter
    private String dockerRunOptions;

    @Parameter
    private String xFleetOptions;

    @Parameter(defaultValue = "true")
    private Boolean executeSmokeTest;

    @Parameter(required = true)
    private String dockerHubUser;

    @Parameter(required = true)
    private String dockerHubPass;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {

        log = getLog();

        // generate service file
        final List<File> serviceFiles = ServiceFileBuilder.build(instances, serviceName, dockerImageName, dockerRunOptions, xFleetOptions, dockerHubUser, dockerHubPass);

        final CoreOSNode node = new CoreOSNode(nodeAdress, userName, keyFile, log);

        try {
            log.info("copying service files to node...");
            // FIXME what if we decrease the amount of services (instances-param)? do we suffer from leftover service files?
            // before deleting them: think about that you still need to stop/destroy those service
            // maybe we need to work on different folders or something?
            for (File serviceFile : serviceFiles) {
                node.storeFile(serviceFile, "/home/core", serviceFile.getName());
            }
        } catch (SftpException | JSchException | IOException e) {
            throw new MojoExecutionException("Exception while trying to copy service file to CoreOS-Node", e);
        }

        try {
            log.info("pulling docker image...");
            node.execute("docker login -e coreos@maven.org -u " + dockerHubUser + " -p " + dockerHubPass);
            node.execute("docker pull " + dockerImageName);

            // FIXME kill all service instances but the amount that was defined in 'minInstancesDuringUpdate'
            // this is a bug. it takes the configured 'instances' instead of the actual running instances
            for (int i = 0; i < instances; i++) {
                log.info("killing service " + i + "...");
                node.execute("fleetctl stop " + serviceName + "." + i + 1 + ".service");
                node.execute("fleetctl destroy " + serviceName + "." + i + 1 + ".service");
                log.info("starting service " + i + "...");
                node.execute("fleetctl start /home/core/" + serviceName + ".service");
            }

            // TODO: how to make sure service was started successfully? fleetctl always returns successfully...

        } catch (JSchException | IOException e) {
            throw new MojoExecutionException("Exception while trying to update service", e);
        }

        // perform smoke test
        if (executeSmokeTest) {
            final boolean available = SmokeTester.test("http://" + nodeAdress, log);
            if (!available) {
                throw new MojoExecutionException("Smoke-Test not successful");
            }
        }
    }
}
