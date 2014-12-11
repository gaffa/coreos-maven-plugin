package de.gaffa.tools.coreos.mavenplugin;

import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.SftpException;
import de.gaffa.tools.coreos.mavenplugin.util.ServiceFileBuilder;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
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

        // coreos-node we are operating on
        final CoreOSNode node = new CoreOSNode(nodeAdress, userName, keyFile, log);

        // generate service files
        final List<File> newServiceFiles = ServiceFileBuilder.build(instances, serviceName, dockerImageName, dockerRunOptions, xFleetOptions, dockerHubUser, dockerHubPass);

        String serviceFileFolder = "/home/core/services/" + serviceName + "/";
        try {
            log.info("clearing service folder");
            // make sure the folders exist
            node.execute("mkdir -p " + serviceFileFolder);
            node.execute("rm -rf " + serviceFileFolder + "*");
        } catch (JSchException | IOException e) {
            throw new MojoExecutionException("Exception clearing service trash folder and moving service files", e);
        }

        // copy new service files to node
        try {
            log.info("copying service files to node...");
            for (File serviceFile : newServiceFiles) {
                node.storeFile(serviceFile, serviceFileFolder, serviceFile.getName());
            }
        } catch (SftpException | JSchException | IOException e) {
            throw new MojoExecutionException("Exception while trying to copy service file to CoreOS-Node", e);
        }

        try {
            log.info("pulling docker image...");
            node.execute("docker login -e coreos@maven.org -u " + dockerHubUser + " -p " + dockerHubPass);
            node.execute("docker pull " + dockerImageName);
        } catch (JSchException | IOException e) {
            throw new MojoExecutionException("Exception while trying to pull docker image", e);
        }

        final List<String> oldServices = getOldServiceNames(node);

        int numOldServices = oldServices.size();
        int numNewServices = newServiceFiles.size();

        int maxCountServices = Integer.max(numOldServices, numNewServices);

        for (int i = 0; i < maxCountServices; i++) {

            if (numOldServices >= i) {
                killService(node, oldServices.get(i));
            }

            if (numNewServices >= i) {
                startService(node, serviceFileFolder, newServiceFiles.get(i).getName());
            }
        }

        // FIXME perform smoke test (is there a sane way to do so? how to get the external ips?)
//        {
//            if (executeSmokeTest) {
//                // TODO: should that be per host? is that possible at all?
//                final boolean available = SmokeTester.test("http://" + nodeAdress, log);
//                if (!available) {
//                    throw new MojoExecutionException("Smoke-Test not successful");
//                }
//            }
//        }
    }

    private List<String> getOldServiceNames(CoreOSNode node) throws MojoExecutionException {

        final String listUnitsOuput;
        try {
            listUnitsOuput = node.execute("fleetctl list-units | grep " + serviceName + " | awk '{print $1}''");
        } catch (JSchException | IOException e) {
            throw new MojoExecutionException("Exception listing old units");
        }

        return Arrays.asList(listUnitsOuput.split("\\n"));
    }

    private void startService(CoreOSNode node, String serviceFolder, String serviceName) throws MojoExecutionException {

        log.info("starting service " + serviceName + "...");
        try {
            node.execute("fleetctl start " + serviceFolder + serviceName);
        } catch (JSchException | IOException e) {
            throw new MojoExecutionException("Exception starting service", e);
        }

        // TODO wait until the service is actually started as fleetctl start returns quickly, the webapp takes some time to be available
    }

    private void killService(CoreOSNode node, String serviceName) throws MojoExecutionException {

        log.info("killing service " + serviceName + "...");
        try {
            node.execute("fleetctl stop " + serviceName);
            node.execute("fleetctl destroy " + serviceName);
        } catch (JSchException | IOException e) {
            throw new MojoExecutionException("Exception killing service", e);
        }
    }
}
