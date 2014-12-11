package de.gaffa.tools.coreos.mavenplugin;

import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.SftpException;
import de.gaffa.tools.coreos.mavenplugin.util.ServiceFileBuilder;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.monitor.logging.DefaultLog;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.codehaus.plexus.logging.console.ConsoleLogger;

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

    public static void main(String[] args) throws MojoFailureException, MojoExecutionException {

        DeployMojo deployMojo = new DeployMojo();
        deployMojo.nodeAdress = "54.194.152.80";
        deployMojo.userName = "core";
        deployMojo.serviceName = "crowdsource";
        deployMojo.keyFile = new File(DeployMojo.class.getResource("/coreos_rsa").getFile());
        deployMojo.log = new DefaultLog(new ConsoleLogger());
        deployMojo.instances = 2;
        deployMojo.dockerHubUser = "asjenkins";
        deployMojo.dockerHubPass = "ideas987";
        deployMojo.dockerImageName = "asideas/crowdsource";
        deployMojo.dockerRunOptions = "-p 80:8080 -e \"SPRING_PROFILES_ACTIVE=prod\"";
        deployMojo.execute();
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {

        log = getLog();

        // coreos-node we are operating on
        final CoreOSNode node = new CoreOSNode(nodeAdress, userName, keyFile, log);

        // generate service files
        final List<File> newServiceFiles = ServiceFileBuilder.build(instances, serviceName, dockerImageName, dockerRunOptions, xFleetOptions, dockerHubUser, dockerHubPass);

        // old service files
        final List<String> oldServiceFileNames;

        // move current service files to "trash" directory and list them
        String serviceFileRoot = "/home/core/services/" + serviceName + "/";
        String oldServiceFileFolder = serviceFileRoot + "trash/";
        String newServiceFileFolder = serviceFileRoot + "current/";

        try {
            log.info("clearing service trash folder and moving current service files");
            // make sure the folders exist
            node.execute("mkdir -p " + oldServiceFileFolder);
            node.execute("mkdir -p " + newServiceFileFolder);
            // move current files to trash
            node.execute("rm -rf " + oldServiceFileFolder + "*");
            if (!StringUtils.isBlank(node.execute("ls " + newServiceFileFolder))) {
                node.execute("mv " + newServiceFileFolder + "* " + oldServiceFileFolder);
            }
            // list current files (to be able to kill them later)
            if (!StringUtils.isBlank(node.execute("ls " + oldServiceFileFolder))) {
                oldServiceFileNames = Arrays.asList(node.execute("ls " + oldServiceFileFolder).split("\n"));
            } else {
                oldServiceFileNames = new ArrayList<>();
            }
        } catch (JSchException | IOException e) {
            throw new MojoExecutionException("Exception clearing service trash folder and moving service files", e);
        }

        // copy new service files to node
        try {
            log.info("copying service files to node...");
            for (File serviceFile : newServiceFiles) {
                node.storeFile(serviceFile, newServiceFileFolder, serviceFile.getName());
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

        int numOldServices = oldServiceFileNames.size();
        int numNewServices = newServiceFiles.size();

        int maxCountServices = Integer.max(numOldServices, numNewServices);

        for (int i = 0; i < maxCountServices; i++) {

            if (numOldServices >= i) {
                killService(node, oldServiceFileNames.get(i));
            }

            if (numNewServices >= i) {
                startService(node, newServiceFileFolder, newServiceFiles.get(i).getName());
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

    private void startService(CoreOSNode node, String serviceFolder, String serviceName) throws MojoExecutionException {

        log.info("starting service " + serviceName + "...");
        try {
            node.execute("fleetctl start " + serviceFolder + serviceName);
        } catch (JSchException | IOException e) {
            throw new MojoExecutionException("Exception starting service", e);
        }
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
