package de.gaffa.tools.coreos.mavenplugin.util;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;

public class ServiceFileBuilder {

    public static File build(String serviceName, String dockerImageName, String dockerRunOptions, String xFleetOptions, String dockerHubUser, String dockerHubPass) throws IOException {

        File serviceFile = File.createTempFile(serviceName, ".service");

        PrintWriter writer = new PrintWriter(serviceFile, "UTF-8");
        writer.println("[Unit]");
        writer.println("Description=" + serviceName + " (File generated by coreos-maven-plugin)");
        writer.println("After=docker.service");
        writer.println("Requires=docker.service");
        writer.println("");
        writer.println("[Service]");
        writer.println("TimeoutStartSec=0");
        writer.println("ExecStartPre=-/usr/bin/docker kill " + serviceName);
        writer.println("ExecStartPre=-/usr/bin/docker rm " + serviceName);
        writer.println("ExecStartPre=-/usr/bin/docker login -e coreos@maven.org -u " + dockerHubUser + " -p " + dockerHubPass);
        writer.println("ExecStartPre=/usr/bin/docker pull " + dockerImageName);
        writer.println("ExecStart=/usr/bin/docker run " + dockerRunOptions + " --name=" + serviceName + " " + dockerImageName);
        writer.println("ExecStop=/usr/bin/docker stop " + serviceName);
        writer.println("");
        writer.println("[Install]");
        writer.println("WantedBy=multi-user.target");
        writer.println();
        writer.println("[X-Fleet]");
        writer.println(xFleetOptions);
        writer.close();

        return serviceFile;
    }
}
