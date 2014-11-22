package de.gaffa.tools.coreos.mavenplugin;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;
import org.apache.commons.io.IOUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Represents a CoreOS-Node and enables consumers to execute commands and transfer files
 */
public class CoreOSNode {

    private final Log log;

    private final Session session;

    public CoreOSNode(String nodeAdress, String userName, File keyFile, Log log) throws MojoExecutionException {

        this.log = log;

        final JSch jsch = new JSch();

        // add keyfile
        try {
            jsch.addIdentity(keyFile.getPath());
        } catch (JSchException e) {
            throw new MojoExecutionException("Something seems to be wrong with your keyfile", e);
        }

        // configure ssh
        final Properties config = new Properties();
        // disable host key checking
        config.put("StrictHostKeyChecking", "no");

        // init session
        try {
            session = jsch.getSession(userName, nodeAdress);
            session.setConfig(config);
            session.connect();
        } catch (JSchException e) {
            throw new MojoExecutionException("Exception while trying to open ssh session to CoreOS node", e);
        }
    }

    /**
     * @param command will be executed on the node
     * @throws JSchException
     */
    public void execute(String command) throws JSchException, IOException {

        ChannelExec executor = (ChannelExec) session.openChannel("exec");
        executor.setCommand(command);
        final InputStream extInputStream = executor.getExtInputStream();
        executor.connect();
        executor.disconnect();
        log.info(IOUtils.toString(extInputStream));
        if (executor.getExitStatus() != 0) {
            final String message = "error executing command: " + command + ".";
            log.error(message);
            throw new JSchException(message);
        }
    }

    /**
     * @param file     will be stored on the node
     * @param path     path where the file will be stored
     * @param fileName new filename
     * @throws JSchException
     * @throws FileNotFoundException
     * @throws SftpException
     */
    public void storeFile(File file, String path, String fileName) throws JSchException, IOException, SftpException {

        ChannelSftp channel = (ChannelSftp) session.openChannel("sftp");

        final InputStream extInputStream = channel.getExtInputStream();
        channel.connect();
        channel.cd(path);
        channel.put(new FileInputStream(file), fileName);
        channel.disconnect();
        log.info(IOUtils.toString(extInputStream));
        if (channel.getExitStatus() != 0) {
            final String message = "error storing file: " + fileName + ".";
            log.error(message);
            throw new JSchException(message);
        }
    }

    /**
     * Closes the session to the node.
     */
    public void close() {
        session.disconnect();
    }
}
