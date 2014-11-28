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
        final File serviceFile;
        try {
            serviceFile = ServiceFileBuilder.build(serviceName, dockerImageName, dockerRunOptions, xFleetOptions, dockerHubUser, dockerHubPass);
            log.info("generated service file: " + serviceFile.getPath());
        } catch (IOException e) {
            throw new MojoExecutionException("Exception generating service file");
        }

        final CoreOSNode node = new CoreOSNode(nodeAdress, userName, keyFile, log);

        try {
            log.info("copying service file to node...");
            node.storeFile(serviceFile, "/home/core", serviceName + ".service");
        } catch (SftpException | JSchException | IOException e) {
            throw new MojoExecutionException("Exception while trying to copy service file to CoreOS-Node", e);
        }

        try {
            log.info("pulling docker image...");
            node.execute("docker login -e coreos@maven.org -u " + dockerHubUser + " -p " + dockerHubPass);
            node.execute("docker pull " + dockerImageName);

            log.info("killing service...");
            node.execute("fleetctl stop " + serviceName);
            node.execute("fleetctl destroy " + serviceName);

            log.info("starting service...");
            node.execute("fleetctl start /home/core/" + serviceName + ".service");

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
