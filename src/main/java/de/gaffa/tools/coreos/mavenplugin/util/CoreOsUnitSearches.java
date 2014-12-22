package de.gaffa.tools.coreos.mavenplugin.util;

import de.gaffa.tools.coreos.mavenplugin.type.CoreOsUnit;

import java.util.ArrayList;
import java.util.List;

public class CoreOsUnitSearches {

    public static CoreOsUnit findByFullName(List<CoreOsUnit> coreOsUnits, String serviceFilename) {
        for (CoreOsUnit unit : coreOsUnits) {
            if (serviceFilename.equals(unit.getFullName())) {
                return unit;
            }
        }

        return null;
    }


    public static List<CoreOsUnit> findRunning(List<CoreOsUnit> units) {
        List<CoreOsUnit> runningUnits = new ArrayList<>();

        for (CoreOsUnit unit : units) {
            if (unit.isStateRunning()) {
                runningUnits.add(unit);
            }
        }

        return runningUnits;
    }
}
