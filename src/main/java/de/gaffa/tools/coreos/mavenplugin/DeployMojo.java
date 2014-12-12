package de.gaffa.tools.coreos.mavenplugin;

import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.SftpException;
import de.gaffa.tools.coreos.mavenplugin.type.CoreOsUnit;
import de.gaffa.tools.coreos.mavenplugin.type.Ensure;
import de.gaffa.tools.coreos.mavenplugin.util.ServiceFileBuilder;
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

    @Parameter
    private String dockerHubUser;

    @Parameter
    private String dockerHubPass;

    @Parameter(defaultValue = "LATEST")
    private Ensure ensure;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {

        log = getLog();

        // coreos-node we are operating on
        final CoreOSNode node = new CoreOSNode(nodeAdress, userName, keyFile, log);

        final List<CoreOsUnit> oldServices = listUnits(node);

        if (ensure == Ensure.RUNNING && instances == oldServices.size()) {
            log.info("The right number of instances is already running. Nothing to do");
            return;
        }

        // generate service files
        final List<File> newServiceFiles = ServiceFileBuilder.build(instances, serviceName, dockerImageName, dockerRunOptions, xFleetOptions, dockerHubUser, dockerHubPass);

        String serviceFileFolder = "/home/core/services/" + serviceName + "/";

        clearServiceFilesFolder(node, serviceFileFolder);
        copyServiceFilesToNodes(node, newServiceFiles, serviceFileFolder);

        if (ensure == Ensure.RUNNING) {
            ensureRunning(node, serviceFileFolder, newServiceFiles, oldServices);
        } else {
            ensureLatest(node, serviceFileFolder, newServiceFiles, oldServices);
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

    void ensureRunning(CoreOSNode node, String serviceFileFolder, List<File> newServiceFiles, List<CoreOsUnit> oldServices) throws MojoExecutionException {

        int numOldServices = oldServices.size();
        int numNewServices = newServiceFiles.size();

        // TODO: when starting new services, the existing service indexes must be considered
//        List<Integer> oldServiceIndexes = new ArrayList<>();
//        for (ServiceName oldService : oldServices) {
//            oldServiceIndexes.add(oldService.getIndex());
//        }

        log.info("Ensuring that there will be " + numNewServices + " running (current: " + numOldServices + ")");

        if (numNewServices > numOldServices) {

            int diff = numNewServices - numOldServices;

            for (int i = 0; i < diff; i++) {
                startService(node, serviceFileFolder, newServiceFiles.get(i).getName());
            }
        } else if (numNewServices < numOldServices) {

            int diff = numOldServices - numNewServices;

            // remove the highest index first, and the lowest last
            for (int i = numOldServices - 1; i >= diff; i--) {
                killService(node, oldServices.get(i));
            }
        }
    }

    private void ensureLatest(CoreOSNode node, String serviceFileFolder, List<File> newServiceFiles, List<CoreOsUnit> oldServices) throws MojoExecutionException {

        int numOldServices = oldServices.size();
        int numNewServices = newServiceFiles.size();

        int maxCountServices = Integer.max(numOldServices, numNewServices);

        for (int i = 0; i < maxCountServices; i++) {

            if (numOldServices > i) {
                killService(node, oldServices.get(i));
            }

            if (numNewServices > i) {
                startService(node, serviceFileFolder, newServiceFiles.get(i).getName());
            }
        }
    }

    private void clearServiceFilesFolder(CoreOSNode node, String serviceFileFolder) throws MojoExecutionException {

        try {
            log.info("clearing service folder");
            // make sure the folders exist
            node.execute("mkdir -p " + serviceFileFolder);
            node.execute("rm -rf " + serviceFileFolder + "*");
        } catch (JSchException | IOException e) {
            throw new MojoExecutionException("Exception clearing service trash folder and moving service files", e);
        }
    }

    private void copyServiceFilesToNodes(CoreOSNode node, List<File> newServiceFiles, String serviceFileFolder) throws MojoExecutionException {

        try {
            log.info("copying service files to node...");
            for (File serviceFile : newServiceFiles) {
                node.storeFile(serviceFile, serviceFileFolder, serviceFile.getName());
            }
        } catch (SftpException | JSchException | IOException e) {
            throw new MojoExecutionException("Exception while trying to copy service file to CoreOS-Node", e);
        }
    }

    private List<CoreOsUnit> listUnits(CoreOSNode node) throws MojoExecutionException {

        final String listUnitsOuput;
        try {
            log.info("listing fleet units...");
            // TODO: This lists all units, even if they are not running
            listUnitsOuput = node.execute("fleetctl list-units | egrep '^" + serviceName + "\\.[0-9]+\\.service'");
        } catch (JSchException | IOException e) {
            throw new MojoExecutionException("Exception listing old units");
        }

        final List<CoreOsUnit> coreOsUnits = new ArrayList<>();
        for (String listUnitsLine : listUnitsOuput.split("\\n")) {

            if (!listUnitsLine.isEmpty()) {
                coreOsUnits.add(CoreOsUnit.fromFleetListUnitsLine(listUnitsLine));
            }
        }

        return coreOsUnits;
    }

    void startService(CoreOSNode node, String serviceFolder, String serviceFilename) throws MojoExecutionException {

        log.info("starting service " + serviceFilename + "...");
        try {
            node.execute("fleetctl start " + serviceFolder + serviceFilename);
        } catch (JSchException | IOException e) {
            throw new MojoExecutionException("Exception starting service", e);
        }

        // TODO wait until the service is actually started as fleetctl start returns quickly, the webapp takes some time to be available
    }

    void killService(CoreOSNode node, CoreOsUnit coreOsUnit) throws MojoExecutionException {

        log.info("killing service " + coreOsUnit.getFullName() + "...");
        try {
            node.execute("fleetctl stop " + coreOsUnit.getFullName());
            node.execute("fleetctl destroy " + coreOsUnit.getFullName());
        } catch (JSchException | IOException e) {
            throw new MojoExecutionException("Exception killing service", e);
        }
    }
}
