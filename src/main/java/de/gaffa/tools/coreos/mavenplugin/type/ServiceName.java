package de.gaffa.tools.coreos.mavenplugin.type;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ServiceName {

    private static final Pattern PATTERN = Pattern.compile("(.+)\\.([0-9]+)\\.service$");

    private final String name;
    private final int index;

    public ServiceName(String name, int index) {
        this.name = name;
        this.index = index;
    }

    public static ServiceName fromFullName(String fullName) {
        Matcher matcher = PATTERN.matcher(fullName);
        if (!matcher.matches()) {
            throw new IllegalArgumentException(fullName + " does not match with the expected pattern");
        }

        String serviceName = matcher.group(1);
        int index = Integer.parseInt(matcher.group(2));

        return new ServiceName(serviceName, index);
    }

    public String getName() {
        return name;
    }

    public int getIndex() {
        return index;
    }

    public String getFullName() {
        return name + "." + index + ".service";
    }
}
