// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.continuosDownload;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Component;
import java.awt.Rectangle;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.LongAdder;
import java.util.jar.Attributes;

import org.awaitility.Awaitility;
import org.awaitility.Durations;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.DataSource;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MapView;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.gui.progress.ProgressMonitor;
import org.openstreetmap.josm.gui.progress.swing.ProgressMonitorExecutor;
import org.openstreetmap.josm.io.OsmApiException;
import org.openstreetmap.josm.io.OsmServerReader;
import org.openstreetmap.josm.io.OsmTransferException;
import org.openstreetmap.josm.plugins.PluginException;
import org.openstreetmap.josm.plugins.PluginInformation;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.testutils.annotations.BasicPreferences;
import org.openstreetmap.josm.testutils.annotations.Main;
import org.openstreetmap.josm.testutils.annotations.Projection;
import org.openstreetmap.josm.tools.ReflectionUtils;

import mockit.Mock;
import mockit.MockUp;

/**
 * Test class for {@link DownloadPlugin}
 * @author Taylor Smock
 */
@BasicPreferences
@Main
@Projection
class DownloadPluginTest {
    @AfterEach
    void cleanUp() {
        // We must clean up the worker thread. First, clean up the queue.
        // Depending upon the number of tests in this class, it might be wise to move this
        // to its own annotation, if it significantly slows down the execution.
        ProgressMonitorExecutor worker = (ProgressMonitorExecutor) MainApplication.worker;
        worker.getQueue().clear();
        // Then interrupt the threads.
        for (Thread thread : Thread.getAllStackTraces().keySet()) {
            if (thread.getName().contains("main-worker-")) {
                thread.interrupt();
            }
        }
    }


    /**
     * Non-regression test for #22363: Too many nodes causes the worker thread to be blocked
     */
    @Timeout(20)
    @Test
    void testNonRegression22363() throws PluginException, InterruptedException, ReflectiveOperationException {
        // This is in ms. Default is 500. For testing, we decrease it to 0, but there is still some time before the timer triggers.
        Config.getPref().putInt("plugin.continuos_download.wait_time", 0);
        OsmDataLayer osmDataLayer = new OsmDataLayer(new DataSet(), "testNonRegression22363", null);
        MainApplication.getLayerManager().addLayer(osmDataLayer);
        // We need a bounds, since otherwise the plugin doesn't download anything.
        osmDataLayer.getDataSet().addDataSource(new DataSource(new Bounds(-1, -1, 0, 0), ""));
        // Register the zoom listener
        DownloadPlugin downloadPlugin = new DownloadPlugin(new PluginInformation(new Attributes(), "ContinuousDownload", null));
        // Ensure that we actually throw the exception
        OsmServerReaderOsmAPIExceptionMock osmServerReaderOsmAPIExceptionMock = new OsmServerReaderOsmAPIExceptionMock();
        // Ensure that we actually get an area for the test to download (otherwise the area is 0)
        new ComponentMock();
        MapView mv = MainApplication.getMap().mapView;
        // The problem doesn't happen reliably. Do it 100 times.
        Field active = DownloadPlugin.class.getDeclaredField("active");
        ReflectionUtils.setObjectsAccessible(active);
        for (int i = 0; i < 100; i++) {
            active.setBoolean(downloadPlugin, true);
            mv.zoomTo(new LatLon(i / 100d, i / 100d));
            assertTrue(mv.getLatLonBounds(mv.getBounds()).getArea() > 0.000001);
            // Give the timer enough time to actually trigger
            synchronized (this) {
                this.wait(10);
            }
        }
        Awaitility.await().pollInterval(Durations.ONE_HUNDRED_MILLISECONDS)
                .atMost(Durations.TEN_SECONDS)
                .untilAdder(osmServerReaderOsmAPIExceptionMock.hit, Matchers.greaterThanOrEqualTo(99L));
        // Give us enough time to call PostDownloadHandler
        Awaitility.await().pollDelay(Durations.ONE_SECOND).until(() -> true);
        AtomicBoolean workerFinished = new AtomicBoolean();
        MainApplication.worker.execute(() -> workerFinished.set(true));
        Awaitility.await().atMost(Durations.ONE_SECOND).untilTrue(workerFinished);
        assertTrue(workerFinished.get());
    }

    private static class OsmServerReaderOsmAPIExceptionMock extends MockUp<OsmServerReader> {
        LongAdder hit = new LongAdder();
        @Mock
        protected InputStream getInputStream(String urlStr, ProgressMonitor progressMonitor) throws OsmTransferException {
            hit.add(1);
            throw new OsmApiException(400, "You requested too many nodes (limit is 50000). Either request a smaller area, or use planet.osm", "", "", null, "xml");
        }
    }

    private static class ComponentMock extends MockUp<Component> {
        @Mock
        public Rectangle getBounds() {
            return new Rectangle(0, 0, 100, 100);
        }
    }
}
