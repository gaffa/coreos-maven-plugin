package de.gaffa.tools.coreos.mavenplugin;

import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.SftpException;
import de.gaffa.tools.coreos.mavenplugin.type.AvailabilityCheck;
import de.gaffa.tools.coreos.mavenplugin.type.CoreOsUnit;
import de.gaffa.tools.coreos.mavenplugin.util.CoreOsUnitSearches;
import de.gaffa.tools.coreos.mavenplugin.util.ThreadUtil;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Represents a CoreOS-Node and enables consumers to execute commands and transfer files
 */
public class CoreOSNode {

    private final RemoteHost remoteHost;

    private final Log log;

    public CoreOSNode(String hostAdress, String userName, File keyFile, Log log) throws MojoExecutionException {

        try {
            remoteHost = new RemoteHost(hostAdress, userName, keyFile, log);
        } catch (JSchException e) {
            throw new MojoExecutionException("Error initializing remote host", e);
        }

        this.log = log;
    }

    public void startService(String serviceName, String serviceFilename, AvailabilityCheck availabilityCheck) throws MojoExecutionException {

        log.info("starting service " + serviceFilename + "...");
        try {
            remoteHost.execute("fleetctl start " + serviceFileFolder(serviceName) + serviceFilename);
        } catch (JSchException | IOException e) {
            throw new MojoExecutionException("Exception starting service", e);
        }

        waitForService(serviceName, serviceFilename, availabilityCheck);
    }

    private void waitForService(String serviceName, String serviceFilename, AvailabilityCheck availabilityCheck) throws MojoExecutionException {

        int repetition = 0;
        int repetitions = 120;
        while (repetition < repetitions) {

            log.info("waiting for service to start (" + repetition + "/" + repetitions + ")...");
            final List<CoreOsUnit> coreOsUnits = listUnits(serviceName);

            CoreOsUnit unit = CoreOsUnitSearches.findByFullName(coreOsUnits, serviceFilename);
            if (unit != null && unit.isStateRunning()) {
                log.info("service is running.");
                if (availabilityCheck != null && availabilityCheck.isEnabled()) {
                    checkAvailability(availabilityCheck, unit);
                }
                return;
            }

            ThreadUtil.sleep(1000);
            repetition++;
        }
        log.warn("service did not start successfully.");
        throw new MojoExecutionException("service did not start successfully.");
    }

    public void killService(CoreOsUnit coreOsUnit) throws MojoExecutionException {

        log.info("killing service " + coreOsUnit.getFullName() + "...");
        try {
            remoteHost.execute("fleetctl stop " + coreOsUnit.getFullName());
            remoteHost.execute("fleetctl destroy " + coreOsUnit.getFullName());
        } catch (JSchException | IOException e) {
            throw new MojoExecutionException("Exception killing service", e);
        }
    }

    public List<CoreOsUnit> listUnits(String serviceName) throws MojoExecutionException {

        // egrep produces the status code 0 on match and 1 on no match, both should not lead to an error
        List<Integer> validExitCodes = Arrays.asList(0, 1);

        final String listUnitsOuput;
        try {
            listUnitsOuput = remoteHost.execute("fleetctl list-units | egrep '^" + serviceName + "\\.[0-9]+\\.service'", validExitCodes);
        } catch (JSchException | IOException e) {
            throw new MojoExecutionException("Exception listing old units", e);
        }

        final List<CoreOsUnit> coreOsUnits = new ArrayList<>();
        for (String listUnitsLine : listUnitsOuput.split("\\n")) {

            if (!listUnitsLine.isEmpty()) {
                coreOsUnits.add(CoreOsUnit.fromFleetListUnitsLine(listUnitsLine));
            }
        }

        return coreOsUnits;
    }

    public void clearServiceFilesFolder(String serviceName) throws MojoExecutionException {

        try {
            log.info("clearing service folder");
            // make sure the folders exist
            remoteHost.execute("mkdir -p " + serviceFileFolder(serviceName));
            remoteHost.execute("rm -rf " + serviceFileFolder(serviceName) + "*");
        } catch (JSchException | IOException e) {
            throw new MojoExecutionException("Exception clearing service trash folder and moving service files", e);
        }
    }

    public void copyServiceFilesToNodes(List<File> serviceFiles, String serviceName) throws MojoExecutionException {

        try {
            log.info("copying service files to node...");
            for (File serviceFile : serviceFiles) {
                remoteHost.storeFile(serviceFile, serviceFileFolder(serviceName), serviceFile.getName());
            }
        } catch (SftpException | JSchException | IOException e) {
            throw new MojoExecutionException("Exception while trying to copy service file to CoreOS-Node", e);
        }
    }

    private void checkAvailability(AvailabilityCheck availabilityCheck, CoreOsUnit unit) throws MojoExecutionException {

        log.info("waiting for service availability...");
        int repetition = 0;
        int repetitions = 120;
        while (repetition < repetitions) {
            String url = "http://" + unit.getIp() + ":" + availabilityCheck.getPort() + availabilityCheck.getContextPath();
            String statusCode = getServiceStatus(url);

            log.info("Availability check against " + url + " resulted in HTTP " + statusCode
                    + ", expected: " + availabilityCheck.getExpectedStatusCode() + " (" + repetition + "/" + repetitions + ")");

            String expectedCode = Integer.toString(availabilityCheck.getExpectedStatusCode());
            if (expectedCode.equals(statusCode)) {
                log.info("service availabilty check ok.");
                return;
            }

            ThreadUtil.sleep(1000);
            repetition++;
        }

        throw new MojoExecutionException("service availability check failed.");
    }

    private String getServiceStatus(String url) {
        // curl produces an exit code 0 on success, and 7 on "Failed to connect() to host or proxy"
        // both should not lead to an exception
        List<Integer> validExitCodes = Arrays.asList(0, 7);

        try {
            // TODO: the header-stuff is proprietary for aws and should not be hard coded
            return remoteHost.execute("curl -H 'X-FORWARDED-PROTO: HTTPS' --silent --output /dev/null --write-out \"%{http_code}\" " + url, validExitCodes);
        } catch (JSchException | IOException ignored) {
            return null;
        }
    }

    private String serviceFileFolder(String serviceName) {

        return "/home/core/services/" + serviceName + "/";
    }
}
