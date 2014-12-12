package de.gaffa.tools.coreos.mavenplugin.type;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CoreOsUnitTest {

    @Test
    public void testFromServiceFilename() throws Exception {
        CoreOsUnit coreOsUnit = CoreOsUnit.fromFleetListUnitsLine("foo-bar.1.service\t\t\t\tb80dbc28.../172.31.46.89\tactive\trunning");

        assertEquals("foo-bar", coreOsUnit.getName());
        assertEquals(1, coreOsUnit.getIndex());
        assertTrue(coreOsUnit.isStateRunning());
        assertFalse(coreOsUnit.isStateFailed());
    }

    @Test
    public void testFromServiceFilenameWithMultipleDigits() throws Exception {
        CoreOsUnit coreOsUnit = CoreOsUnit.fromFleetListUnitsLine("foo-bar.12.service\t\t\t\tb80dbc28.../172.31.46.89\tactive\trunning");

        assertEquals("foo-bar", coreOsUnit.getName());
        assertEquals(12, coreOsUnit.getIndex());
        assertTrue(coreOsUnit.isStateRunning());
        assertFalse(coreOsUnit.isStateFailed());
    }

    @Test
    public void testFromServiceFilenameAndStateFailed() throws Exception {
        CoreOsUnit coreOsUnit = CoreOsUnit.fromFleetListUnitsLine("foo-bar.1.service\t\t\t\tb80dbc28.../172.31.46.89\tfailed\tfailed");

        assertEquals("foo-bar", coreOsUnit.getName());
        assertEquals(1, coreOsUnit.getIndex());
        assertFalse(coreOsUnit.isStateRunning());
        assertTrue(coreOsUnit.isStateFailed());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testFromServiceFilenameWithNoDigits() throws Exception {
        CoreOsUnit.fromFleetListUnitsLine("foo-bar.service\t\t\t\tb80dbc28.../172.31.46.89\tactive\trunning");
    }
}