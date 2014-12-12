package de.gaffa.tools.coreos.mavenplugin;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;
import org.apache.commons.io.IOUtils;
import org.apache.maven.plugin.logging.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class RemoteHost {

    private final String nodeAddress;
    private final String userName;
    private final Log log;

    private JSch jSch = new JSch();

    public RemoteHost(String hostAdress, String userName, File keyFile, Log log) throws JSchException {

        this.log = log;
        this.nodeAddress = hostAdress;
        this.userName = userName;

        // add keyfile
        jSch.addIdentity(keyFile.getPath());
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
    public String execute(String command) throws JSchException, IOException {

        final Session session = getSession();
        final ChannelExec channel = (ChannelExec) session.openChannel("exec");

        channel.setCommand(command);
        final InputStream inputStream = channel.getInputStream();
        final InputStream errorStream = channel.getErrStream();
        channel.connect();

        while (!channel.isClosed()) {
            // no-op
        }
        final String output = IOUtils.toString(inputStream);

        channel.disconnect();
        session.disconnect();

        final int exitCode = channel.getExitStatus();
        if (channel.getExitStatus() != 0) {
            final String message = "error executing command: " + command + ". process exit code: " + exitCode + ". error stream: " + IOUtils.toString(errorStream);
            log.error(message);
            throw new JSchException(message);
        }

        return output;
    }

    /**
     * @param file     will be stored on the node
     * @param path     path where the file will be stored
     * @param fileName new filename
     * @throws JSchException
     * @throws java.io.FileNotFoundException
     * @throws com.jcraft.jsch.SftpException
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
