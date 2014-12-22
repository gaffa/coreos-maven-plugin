package de.gaffa.tools.coreos.mavenplugin;

import de.gaffa.tools.coreos.mavenplugin.type.AvailabilityCheck;
import de.gaffa.tools.coreos.mavenplugin.type.CoreOsUnit;
import de.gaffa.tools.coreos.mavenplugin.type.Ensure;
import de.gaffa.tools.coreos.mavenplugin.util.CoreOsUnitSearches;
import de.gaffa.tools.coreos.mavenplugin.util.ServiceFileBuilder;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;
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

    @Parameter
    private AvailabilityCheck availabilityCheck;

    // maybe make this an object with { path = "/context-path/path", responseStatus = 200 }
    @Parameter(defaultValue = "false")
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

        log.info("listing fleet units...");
        final List<CoreOsUnit> oldServices = node.listUnits(serviceName);
        final List<CoreOsUnit> runningServices = CoreOsUnitSearches.findRunning(oldServices);

        if (ensure == Ensure.RUNNING && instances == runningServices.size()) {
            log.info("The right number of instances is already running. Nothing to do");
            return;
        }

        // generate service files
        final List<File> newServiceFiles = ServiceFileBuilder.build(instances, serviceName, dockerImageName, dockerRunOptions, xFleetOptions, dockerHubUser, dockerHubPass);

        node.clearServiceFilesFolder(serviceName);
        node.copyServiceFilesToNodes(newServiceFiles, serviceName);

        if (ensure == Ensure.RUNNING) {
            ensureRunning(node, serviceName, newServiceFiles, runningServices);
        } else {
            ensureLatest(node, serviceName, newServiceFiles, oldServices);
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

    void ensureRunning(CoreOSNode node, String serviceName, List<File> newServiceFiles, List<CoreOsUnit> runningServices) throws MojoExecutionException {

        int numRunningServices = runningServices.size();
        int numNewServices = newServiceFiles.size();

        // TODO: when starting new services, the existing service indexes must be considered
//        List<Integer> oldServiceIndexes = new ArrayList<>();
//        for (ServiceName oldService : runningServices) {
//            oldServiceIndexes.add(oldService.getIndex());
//        }

        log.info("Ensuring that there will be " + numNewServices + " running (current: " + numRunningServices + ")");

        if (numNewServices > numRunningServices) {

            for (int i = numRunningServices; i < numNewServices; i++) {
                node.startService(serviceName, newServiceFiles.get(i).getName(), availabilityCheck);
            }
        } else if (numNewServices < numRunningServices) {

            // remove the highest index first, and the lowest last
            for (int i = numRunningServices - 1; i >= numNewServices; i--) {
                node.killService(runningServices.get(i));
            }
        }
    }

    private void ensureLatest(CoreOSNode node, String serviceName, List<File> newServiceFiles, List<CoreOsUnit> oldServices) throws MojoExecutionException {

        int numOldServices = oldServices.size();
        int numNewServices = newServiceFiles.size();

        int maxCountServices = Integer.max(numOldServices, numNewServices);

        for (int i = 0; i < maxCountServices; i++) {

            if (numOldServices > i) {
                node.killService(oldServices.get(i));
            }

            if (numNewServices > i) {
                node.startService(serviceName, newServiceFiles.get(i).getName(), availabilityCheck);
            }
        }
    }
}
