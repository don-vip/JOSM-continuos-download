// License: GPL. See LICENSE file for details.
package org.openstreetmap.josm.plugins.continuosDownload;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import java.util.function.Consumer;

import org.openstreetmap.josm.actions.downloadtasks.DownloadOsmTask;
import org.openstreetmap.josm.actions.downloadtasks.DownloadParams;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.gui.progress.ProgressMonitor;
import org.openstreetmap.josm.io.OsmServerReader;
import org.openstreetmap.josm.io.OsmTransferException;
import org.xml.sax.SAXException;

/**
 * This is a copy of the DownloadOsmTask that does not change the view after the area is downloaded.
 * It still displays modal windows and ugly dialog boxes :(
 */
public class DownloadOsmTask2 extends DownloadOsmTask {
    /**
     * Constructs a new {@code DownloadOsmTask2}.
     */
    public DownloadOsmTask2() {
        warnAboutEmptyArea = false;
    }

    @Override
    public Future<?> download(OsmServerReader reader, DownloadParams settings, Bounds downloadArea,
            ProgressMonitor progressMonitor) {
        return download(new DownloadTask2(settings, reader, progressMonitor), downloadArea);
    }

    @Override
    protected Future<?> download(DownloadTask downloadTask, Bounds downloadArea) {
        // This method needs to be overridden to avoid using JOSM's MainApplication.worker for downloads
        this.downloadTask = downloadTask;
        this.currentBounds = new Bounds(downloadArea);
        // We need submit instead of execute so we can wait for it to finish and get the error
        // message if necessary. If no one calls getErrorMessage() it just behaves like execute.
        return DownloadPlugin.worker.submit(downloadTask);
    }

    protected class DownloadTask2 extends DownloadTask {
        public DownloadTask2(DownloadParams settings, OsmServerReader reader,
                ProgressMonitor progressMonitor) {
            super(settings, reader, progressMonitor, false);
        }

        @Override
        public void realRun() throws OsmTransferException, IOException, SAXException {
            // Get the current error messages
            final List<Object> oldErrors = new ArrayList<>(DownloadOsmTask2.this.getErrorObjects());
            // Do the actual run
            super.realRun();
            // Get the new error messages
            final List<Object> newErrors = new ArrayList<>(DownloadOsmTask2.this.getErrorObjects());
            // But we have to remove the old error messages first
            newErrors.removeIf(oldErrors::contains);

            final List<Consumer<Exception>> handlers = DownloadPlugin.getDownloadExceptionConsumers();
            newErrors.stream().filter(Exception.class::isInstance).map(Exception.class::cast)
                    .forEach(exception -> handlers.forEach(handler -> handler.accept(exception)));
        }
    }
}
