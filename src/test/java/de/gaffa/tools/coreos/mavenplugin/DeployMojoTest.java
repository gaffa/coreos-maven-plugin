package de.gaffa.tools.coreos.mavenplugin;

import de.gaffa.tools.coreos.mavenplugin.type.AvailabilityCheck;
import de.gaffa.tools.coreos.mavenplugin.type.CoreOsUnit;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;


@RunWith(MockitoJUnitRunner.class)
public class DeployMojoTest {

    @Mock
    private CoreOSNode node;

    private DeployMojo deployMojo = new DeployMojo();

    private AvailabilityCheck availabilityCheck = new AvailabilityCheck();

    @Before
    public void initMocks() throws Exception {
        ReflectionTestUtils.setField(deployMojo, "log", new SystemStreamLog());
        ReflectionTestUtils.setField(deployMojo, "availabilityCheck", availabilityCheck);
    }

    @Test
    public void testEnsureRunningTwoMoreRunningThanRequested() throws Exception {

        deployMojo.ensureRunning(node, "some/folder", fileList(1), serviceList(3));
        verify(node, times(2)).killService(any(CoreOsUnit.class));
        verify(node, times(0)).startService(anyString(), anyString(), eq(availabilityCheck));
    }

    @Test
    public void testEnsureRunningTwoLessRunningThanRequested() throws Exception {

        deployMojo.ensureRunning(node, "some/folder", fileList(3), serviceList(1));
        verify(node, times(0)).killService(any(CoreOsUnit.class));
        verify(node, times(2)).startService(anyString(), anyString(), eq(availabilityCheck));
    }

    @Test
    public void testEnsureSameNumberRunningAsRequested() throws Exception {

        deployMojo.ensureRunning(node, "some/folder", fileList(2), serviceList(2));
        verify(node, times(0)).killService(any(CoreOsUnit.class));
        verify(node, times(0)).startService(anyString(), anyString(), eq(availabilityCheck));
    }

    @Test
    public void testEnsureRunningNoneCurrentlyRunning() throws Exception {

        deployMojo.ensureRunning(node, "some/folder", fileList(2), serviceList(0));
        verify(node, times(0)).killService(any(CoreOsUnit.class));
        verify(node, times(2)).startService(anyString(), anyString(), eq(availabilityCheck));
    }

    @Test
    public void testEnsureRunningNoneShouldBeRunning() throws Exception {

        deployMojo.ensureRunning(node, "some/folder", fileList(0), serviceList(5));
        verify(node, times(5)).killService(any(CoreOsUnit.class));
        verify(node, times(0)).startService(anyString(), anyString(), eq(availabilityCheck));
    }

    private List<CoreOsUnit> serviceList(int num) {

        List<CoreOsUnit> services = new ArrayList<>();
        for (int i = 0; i < num; i++) {
            services.add(new CoreOsUnit("lalala", i + 1));
        }
        return services;
    }

    List<File> fileList(int num) {

        List<File> files = new ArrayList<>();
        for (int i = 0; i < num; i++) {
            files.add(new File("lalala"));
        }
        return files;
    }
}