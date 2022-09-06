// License: GPL. See LICENSE file for details.
package org.openstreetmap.josm.plugins.continuosDownload;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Locale;
import java.util.concurrent.Future;

import org.openstreetmap.josm.actions.downloadtasks.AbstractDownloadTask;
import org.openstreetmap.josm.actions.downloadtasks.DownloadGpsTask;
import org.openstreetmap.josm.actions.downloadtasks.DownloadParams;
import org.openstreetmap.josm.actions.downloadtasks.PostDownloadHandler;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.layer.GpxLayer;
import org.openstreetmap.josm.gui.layer.Layer;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.gui.progress.NullProgressMonitor;
import org.openstreetmap.josm.gui.progress.ProgressMonitor;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.tools.Logging;

/**
 * The base download strategy class
 */
public abstract class AbstractDownloadStrategy {

    /**
     * Fetch data
     * @param bbox The bounds to fetch
     */
    public void fetch(Bounds bbox) {
        this.fetch(bbox, OsmDataLayer.class);
        this.fetch(bbox, GpxLayer.class);
    }

    /**
     * Fetch data
     * @param bbox The bounds to fetch
     * @param klass The specific type to download. See {@link #getDownloadTask(Class)} for more information.
     */
    public void fetch(Bounds bbox, Class<?> klass) {
        Collection<Bounds> existing = getExisting(klass);
        if (existing.isEmpty())
            return;
        Bounds extendedBox = extend(bbox, Config.getPref().getDouble("plugin.continuos_download.extra_download", 0.1));
        Collection<Bounds> toFetch = getBoxes(extendedBox, existing,
                Config.getPref().getInt("plugin.continuos_download.max_areas", 4));

        printDebug(extendedBox, toFetch);

        // Try to avoid downloading areas outside the view area unnecessary
        Collection<Bounds> t = toFetch;
        toFetch = new ArrayList<>(t.size());
        for (Bounds box : t) {
            if (box.intersects(bbox)) {
                toFetch.add(box);
            }
        }

        download(toFetch, klass);
    }

    private static void printDebug(Bounds bbox, Collection<Bounds> toFetch) {
        double areaToDownload = 0;
        for (Bounds box : toFetch) {
            areaToDownload += box.getArea();
        }

        double areaDownloaded = 0;
        for (Bounds box : getExisting(OsmDataLayer.class)) {
            if (box.intersects(bbox))
                areaDownloaded += intersection(box, bbox).getArea();
        }

        double downloadP = (areaToDownload * 100) / bbox.getArea();
        double downloadedP = (areaDownloaded * 100) / bbox.getArea();

        Logging.info(String.format(Locale.ENGLISH,
                "Getting %.1f%% of area, already have %.1f%%, overlap %.1f%%%n", downloadP,
                downloadedP,
                downloadP + downloadedP - 100));
    }

    private static Bounds intersection(Bounds box1, Bounds box2) {
        double minX1 = box1.getMin().getX();
        double maxX1 = box1.getMax().getX();
        double minY1 = box1.getMin().getY();
        double maxY1 = box1.getMax().getY();

        double minX2 = box2.getMin().getX();
        double maxX2 = box2.getMax().getX();
        double minY2 = box2.getMin().getY();
        double maxY2 = box2.getMax().getY();

        double minX = Math.max(minX1, minX2);
        double maxX = Math.min(maxX1, maxX2);
        double minY = Math.max(minY1, minY2);
        double maxY = Math.min(maxY1, maxY2);

        return new Bounds(minY, minX, maxY, maxX);
    }

    /**
     * Get existing bounds
     * @param klass The class to get. See {@link #getDownloadTask(Class)} for supported types.
     * @return The collection of bounds that have already been downloaded.
     */
    private static Collection<Bounds> getExisting(Class<?> klass) {
        if (klass.isAssignableFrom(OsmDataLayer.class)) {
            if (!MainApplication.isDisplayingMapView())
                return Collections.emptyList();
            OsmDataLayer layer = MainApplication.getMap().mapView.getLayerManager().getEditLayer();
            if (layer == null) {
                Collection<Layer> layers = MainApplication.getMap().mapView.getLayerManager().getLayers();
                for (Layer layer1 : layers) {
                    if (layer1 instanceof OsmDataLayer)
                        return ((OsmDataLayer) layer1).data.getDataSourceBounds();
                }
                return Collections.emptyList();
            } else {
                return layer.data.getDataSourceBounds();
            }
        } else if (klass.isAssignableFrom(GpxLayer.class)) {
            if (!MainApplication.isDisplayingMapView())
                return Collections.emptyList();
            boolean merge = Config.getPref().getBoolean("download.gps.mergeWithLocal", false);
            Layer active = MainApplication.getMap().mapView.getLayerManager().getActiveLayer();
            if (active instanceof GpxLayer && (merge || ((GpxLayer) active).data.fromServer))
                return ((GpxLayer) active).data.getDataSourceBounds();
            for (GpxLayer l : MainApplication.getMap().mapView.getLayerManager().getLayersOfType(GpxLayer.class)) {
                if (merge || l.data.fromServer)
                    return l.data.getDataSourceBounds();
            }
            return Collections.emptyList();
        } else {
            throw new IllegalArgumentException();
        }
    }

    public abstract Collection<Bounds> getBoxes(Bounds bbox, Collection<Bounds> present, int maxAreas);

    /**
     * Download a collection of bounds
     * @param bboxes The bounds to download
     * @param klass The type to download. See {@link #getDownloadTask(Class)} for more information.
     */
    private static void download(Collection<Bounds> bboxes, Class<?> klass) {
        for (Bounds bbox : bboxes) {
            AbstractDownloadTask<?> task = getDownloadTask(klass);
            
            ProgressMonitor monitor = null;
            if (Config.getPref().getBoolean("plugin.continuos_download.quiet_download", false)) {
                monitor = NullProgressMonitor.INSTANCE;
            }

            Future<?> future = task.download(new DownloadParams(), bbox, monitor);
            DownloadPlugin.worker.execute(new PostDownloadHandler(task, future));
        }
    }

    /**
     * Get the download task for a specified class
     * @param klass The class to get the download class for. Currently supports {@link OsmDataLayer} and {@link GpxLayer}.
     * @return The download task for the class
     */
    private static AbstractDownloadTask<?> getDownloadTask(Class<?> klass) {
        if (klass.isAssignableFrom(OsmDataLayer.class))
            return new DownloadOsmTask2();
        if (klass.isAssignableFrom(GpxLayer.class))
            return new DownloadGpsTask();
        throw new IllegalArgumentException();
    }

    protected static Bounds extend(Bounds bbox, double amount) {
        LatLon min = bbox.getMin();
        LatLon max = bbox.getMax();

        double dLat = Math.abs(max.lat() - min.lat()) * amount;
        double dLon = Math.abs(max.lon() - min.lon()) * amount;

        return new Bounds(min.lat() - dLat, min.lon() - dLon, max.lat() + dLat, max.lon() + dLon);
    }
}
