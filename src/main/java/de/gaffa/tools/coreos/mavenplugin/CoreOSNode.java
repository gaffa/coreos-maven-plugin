package de.gaffa.tools.coreos.mavenplugin;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Properties;

/**
 * Represents a CoreOS-Node and enables consumers to execute commands and transfer files
 */
public class CoreOSNode {

    private final Log log;
    private final String nodeAddress;
    private final String userName;

    private JSch jSch = new JSch();

    public CoreOSNode(String nodeAddress, String userName, File keyFile, Log log) throws MojoExecutionException {

        this.log = log;
        this.nodeAddress = nodeAddress;
        this.userName = userName;

        // add keyfile
        try {
            jSch.addIdentity(keyFile.getPath());
        } catch (JSchException e) {
            throw new MojoExecutionException("Something seems to be wrong with your keyfile", e);
        }
    }

    private Session getSession() throws JSchException {

        // configure ssh
        final Properties config = new Properties();
        // disable host key checking
        config.put("StrictHostKeyChecking", "no");

        // init session
        final Session session = jSch.getSession(userName, nodeAddress);
        session.setConfig(config);
        session.connect();

        return session;
    }

    /**
     * @param command will be executed on the node
     * @throws JSchException
     */
    public void execute(String command) throws JSchException, IOException {

        final Session session = getSession();
        final ChannelExec channel = (ChannelExec) session.openChannel("exec");

        channel.setCommand(command);
        channel.connect();

        while (!channel.isClosed()) {
            // no-op
        }

        channel.disconnect();
        session.disconnect();

        final int exitCode = channel.getExitStatus();
        if (channel.getExitStatus() != 0) {
            final String message = "error executing command: " + command + ". process exit code: " + exitCode + ".";
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

        final Session session = getSession();
        final ChannelSftp channel = (ChannelSftp) session.openChannel("sftp");

        channel.connect();
        channel.cd(path);
        channel.put(new FileInputStream(file), fileName);

        channel.disconnect();
        session.disconnect();
    }
}
