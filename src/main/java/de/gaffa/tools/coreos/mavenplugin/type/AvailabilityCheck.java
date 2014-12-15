package de.gaffa.tools.coreos.mavenplugin.type;

public class AvailabilityCheck {

    private boolean enabled;

    private String contextPath;

    private int port;

    private int expectedStatusCode;

    public AvailabilityCheck() {
        this.enabled = true;
        this.contextPath = "/";
        this.port = 80;
        this.expectedStatusCode = 200;
    }

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
