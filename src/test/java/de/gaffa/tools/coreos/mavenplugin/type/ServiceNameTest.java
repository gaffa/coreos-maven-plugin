package de.gaffa.tools.coreos.mavenplugin.type;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ServiceNameTest {

    @Test
    public void testFromServiceFilename() throws Exception {
        ServiceName serviceName = ServiceName.fromFullName("foo.1.service");

        assertEquals("foo", serviceName.getName());
        assertEquals(1, serviceName.getIndex());
    }

    @Test
    public void testFromServiceFilenameWithMultipleDigits() throws Exception {
        ServiceName serviceName = ServiceName.fromFullName("foo.12.service");

        assertEquals("foo", serviceName.getName());
        assertEquals(12, serviceName.getIndex());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testFromServiceFilenameWithNoDigits() throws Exception {
        ServiceName.fromFullName("foo.service");
    }
}