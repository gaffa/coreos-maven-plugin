package de.gaffa.tools.coreos.mavenplugin.type;

public enum Ensure {

    RUNNING("running"),
    LATEST("latest");

    private final String value;

    private Ensure(String value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return value;
    }
}
