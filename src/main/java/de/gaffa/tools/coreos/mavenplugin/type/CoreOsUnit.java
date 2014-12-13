package de.gaffa.tools.coreos.mavenplugin.type;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CoreOsUnit {

    private static final Pattern UNIT_NAME_PATTERN = Pattern.compile("(.+)\\.([0-9]+)\\.service$");

    private final String name;
    private final int index;
    private final String subState;
    private final String ip;

    public CoreOsUnit(String name, int index) {
        this(name, index, null, null);
    }

    public CoreOsUnit(String name, int index, String subState, String ip) {
        this.name = name;
        this.index = index;
        this.subState = subState;
        this.ip = ip;
    }

    public static CoreOsUnit fromFleetListUnitsLine(String listUnitsLine) {

        String[] listUnitsColumns = listUnitsLine.split("\\s+");
        if (listUnitsColumns.length < 4) {
            throw new IllegalArgumentException("Expected an output with at least 4 columns but was [" + listUnitsLine + "]");
        }

        String fullName = listUnitsColumns[0];
        String machine = listUnitsColumns[1];
        String ip = machine.substring(machine.indexOf("/") + 1);
        String subState = listUnitsColumns[3];

        Matcher matcher = UNIT_NAME_PATTERN.matcher(fullName);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("[" + fullName + "] does not match with the expected pattern");
        }

        String serviceName = matcher.group(1);
        int index = Integer.parseInt(matcher.group(2));

        return new CoreOsUnit(serviceName, index, subState, ip);
    }

    public String getName() {
        return name;
    }

    public int getIndex() {
        return index;
    }

    public String getIp() {
        return ip;
    }

    public String getFullName() {
        return name + "." + index + ".service";
    }

    public boolean isStateFailed() {
        return "failed".equals(subState);
    }

    public boolean isStateRunning() {
        return "running".equals(subState);
    }
}
