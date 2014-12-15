package de.gaffa.tools.coreos.mavenplugin.type;

import org.apache.maven.plugins.annotations.Parameter;

public class AvailabilityCheck {

    @Parameter(defaultValue = "false")
    private boolean enabled;

    @Parameter(defaultValue = "/")
    private String contextPath;

    @Parameter(defaultValue = "80")
    private int port;

    @Parameter(defaultValue = "200")
    private int expectedStatusCode;

    public boolean isEnabled() {
        return enabled;
    }

    public String getContextPath() {
        return contextPath;
    }

    public int getPort() {
        return port;
    }

    public int getExpectedStatusCode() {
        return expectedStatusCode;
    }
}
