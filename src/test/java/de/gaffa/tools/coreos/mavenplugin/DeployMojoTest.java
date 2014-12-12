package de.gaffa.tools.coreos.mavenplugin;

import de.gaffa.tools.coreos.mavenplugin.type.CoreOsUnit;
import junit.framework.TestCase;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.runners.MockitoJUnitRunner;

import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;


@RunWith(MockitoJUnitRunner.class)
public class DeployMojoTest extends TestCase {

    @Mock
    private CoreOSNode node;

    @Mock
    private Log log;

    @Spy
    private DeployMojo deployMojo;

    @Before
    public void initMocks() throws Exception {
        Field logField = DeployMojo.class.getDeclaredField("log");
        logField.setAccessible(true);
        logField.set(deployMojo, new SystemStreamLog());
        doNothing().when(deployMojo).killService(any(CoreOSNode.class), any(CoreOsUnit.class));
        doNothing().when(deployMojo).startService(any(CoreOSNode.class), anyString(), anyString());
    }

    @Test
    public void testEnsureRunningTwoMoreRunningThanRequested() throws Exception {

        deployMojo.ensureRunning(node, "some/folder", fileList(1), serviceList(3));
        verify(deployMojo, times(2)).killService(any(CoreOSNode.class), any(CoreOsUnit.class));
        verify(deployMojo, times(0)).startService(any(CoreOSNode.class), anyString(), anyString());
    }

    @Test
    public void testEnsureRunningTwoLessRunningThanRequested() throws Exception {

        deployMojo.ensureRunning(node, "some/folder", fileList(3), serviceList(1));
        verify(deployMojo, times(0)).killService(any(CoreOSNode.class), any(CoreOsUnit.class));
        verify(deployMojo, times(2)).startService(any(CoreOSNode.class), anyString(), anyString());
    }

    @Test
    public void testEnsureSameNumberRunningAsRequested() throws Exception {

        deployMojo.ensureRunning(node, "some/folder", fileList(2), serviceList(2));
        verify(deployMojo, times(0)).killService(any(CoreOSNode.class), any(CoreOsUnit.class));
        verify(deployMojo, times(0)).startService(any(CoreOSNode.class), anyString(), anyString());
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