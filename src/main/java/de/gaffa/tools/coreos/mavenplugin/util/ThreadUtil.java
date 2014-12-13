package de.gaffa.tools.coreos.mavenplugin.util;

public class ThreadUtil {

    public static void sleep(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
