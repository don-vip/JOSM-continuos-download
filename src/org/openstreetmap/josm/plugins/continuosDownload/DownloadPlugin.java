// License: GPL. See LICENSE file for details.
package org.openstreetmap.josm.plugins.continuosDownload;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import javax.swing.ButtonModel;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JOptionPane;

import org.openstreetmap.josm.actions.JosmAction;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.preferences.IntegerProperty;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MainMenu;
import org.openstreetmap.josm.gui.MapView;
import org.openstreetmap.josm.gui.NavigatableComponent;
import org.openstreetmap.josm.gui.NavigatableComponent.ZoomChangeListener;
import org.openstreetmap.josm.gui.Notification;
import org.openstreetmap.josm.gui.PleaseWaitRunnable;
import org.openstreetmap.josm.gui.preferences.PreferenceSetting;
import org.openstreetmap.josm.gui.util.GuiHelper;
import org.openstreetmap.josm.io.OsmApiException;
import org.openstreetmap.josm.plugins.Plugin;
import org.openstreetmap.josm.plugins.PluginInformation;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.tools.Destroyable;
import org.openstreetmap.josm.tools.Logging;
import org.openstreetmap.josm.tools.Shortcut;

/**
 * The POJO class for Continuous Download
 */
public class DownloadPlugin extends Plugin implements ZoomChangeListener, Destroyable {
    private static final IntegerProperty maxThreads = new IntegerProperty("plugin.continuos_download.max_threads", 2);

    private static final List<Consumer<Exception>> exceptionConsumers = new ArrayList<>();

    /**
     * The worker that runs all our downloads, it has more threads than
     * {@link MainApplication#worker}.
     */
    public static final ExecutorService worker = new ThreadPoolExecutor(
            /*
             * maximumPoolSize only matters when the queue is full. Which should never happen (Integer.MAX_VALUE).
             * We will set core size to maxThreads and allow them to time out
             */
            maxThreads.get(), maxThreads.get(), 1, TimeUnit.SECONDS, new LinkedBlockingQueue<>(),
            Executors.defaultThreadFactory());
    private static final HashMap<String, AbstractDownloadStrategy> strats = new HashMap<>();
    static {
        registerStrat(new SimpleStrategy());
        registerStrat(new BoxStrategy());
        // This ensures that threads will be destroyed when not used.
        ((ThreadPoolExecutor) worker).allowCoreThreadTimeOut(true);
    }
    private Timer timer;
    private TimerTask task;
    private Bounds lastBbox;
    private boolean active;

    private DownloadPreference preference;
    private final JCheckBoxMenuItem menuItem;
    private Double zoomDisabled;

    /**
     * Constructs a new {@code DownloadPlugin}.
     * @param info plugin info
     */
    public DownloadPlugin(PluginInformation info) {
        super(info);
        active = Config.getPref().getBoolean("plugin.continuos_download.active_default", true);

        timer = new Timer();
        NavigatableComponent.addZoomChangeListener(this);

        ToggleAction toggle = new ToggleAction();
        menuItem = MainMenu.addWithCheckbox(MainApplication.getMenu().fileMenu, toggle,
                MainMenu.WINDOW_MENU_GROUP.ALWAYS);
        menuItem.setState(active);
        // If the user re-enables continuousDownload, reset the zoom disabled level.
        menuItem.addChangeListener(l -> {
            if (menuItem.isSelected()) {
                this.zoomDisabled = null;
            }
        });
        toggle.addButtonModel(menuItem.getModel());
        exceptionConsumers.add(this::handleException);
    }

    @Override
    public PreferenceSetting getPreferenceSetting() {
        if (preference == null)
            preference = new DownloadPreference();
        return preference;
    }

    @Override
    public void zoomChanged() {
        if (MainApplication.getMap() == null)
            return;
        MapView mv = MainApplication.getMap().mapView;
        Bounds bbox = mv.getLatLonBounds(mv.getBounds());
        // Re-enable if the user has zoomed in
        if (this.zoomDisabled != null && this.zoomDisabled > mv.getScale()) {
            this.zoomDisabled = null;
            this.active = true;
            this.menuItem.setSelected(true);
        }

        // Have the user changed view since last time
        if (active && (lastBbox == null || !lastBbox.equals(bbox))) {
            if (task != null) {
                task.cancel();
            }

            // wait 500ms before downloading in case the user is in the middle of a pan/zoom
            int delay = Config.getPref().getInt("plugin.continuos_download.wait_time", 500);
            task = new Task(bbox);
            try {
                timer.schedule(task, delay);
            } catch (IllegalStateException e) {
                // #8836: "Timer already cancelled" error received even if we don't cancel it
                Logging.debug(e);
                timer = new Timer();
                timer.schedule(task, delay);
            }
            lastBbox = bbox;
        }
    }

    public AbstractDownloadStrategy getStrat() {
        AbstractDownloadStrategy r = strats.get(Config.getPref().get("plugin.continuos_download.strategy", "BoxStrategy"));

        if (r == null) {
            r = strats.get("SimpleStrategy");
        }

        return r;
    }

    /**
     * Handle download exceptions.
     * @param exception the exception to handle
     */
    private void handleException(final Exception exception) {
        if (exception instanceof OsmApiException && ((OsmApiException) exception).getErrorHeader().contains("requested too many")) {
            this.active = false;
            GuiHelper.runInEDT(() -> this.menuItem.setSelected(false));
            final ThreadPoolExecutor executor = (ThreadPoolExecutor) worker;
            // Remove anything that is currently in the queue. There are going to be a lot of PostDownloadHandler objects, which
            // does not have cancel functionality. Unfortunately.
            new ArrayList<>(executor.getQueue()).forEach(runnable -> {
                executor.remove(runnable);
                // DownloadTask is a subclass of PleaseWaitRunnable
                if (runnable instanceof PleaseWaitRunnable) {
                    ((PleaseWaitRunnable) runnable).operationCanceled();
                }
                // See #22363: Worker thread is blocked
                if (runnable instanceof Future) {
                    ((Future<?>) runnable).cancel(true);
                }
            });
            this.zoomDisabled = Optional.ofNullable(MainApplication.getMap()).map(map -> map.mapView)
                    .map(NavigatableComponent::getScale).orElse(null);
            GuiHelper.runInEDT(() -> {
                final Notification notification = new Notification(tr("Disabling continuous download until you zoom in"));
                notification.setIcon(JOptionPane.WARNING_MESSAGE);
                notification.show();
            });
        }
    }

    /**
     * Get a list of handlers for exceptions from downloading data
     * @return the exception handlers -- they take action based off of the exceptions passed in.
     */
    public static List<Consumer<Exception>> getDownloadExceptionConsumers() {
        return Collections.unmodifiableList(exceptionConsumers);
    }

    /**
     * Register download strategies
     * @param strat The strategy to register
     */
    public static void registerStrat(AbstractDownloadStrategy strat) {
        strats.put(strat.getClass().getSimpleName(), strat);
    }

    private class Task extends TimerTask {
        private final Bounds bbox;

        public Task(Bounds bbox) {
            this.bbox = bbox;
        }

        @Override
        public void run() {
            if (!active)
                return;
            
            // Do not try to download an area if the user have zoomed far out
            if (bbox.getArea() < Config.getPref().getDouble("plugin.continuos_download.max_area", 0.25))
                getStrat().fetch(bbox);
        }
    }

    private class ToggleAction extends JosmAction {

        private final transient Collection<ButtonModel> buttonModels;

        public ToggleAction() {
            super(tr("Download OSM data continuously"), "continuous-download",
                    tr("Download map data continuously when paning and zooming."), Shortcut.registerShortcut(
                            "continuosdownload:activate", tr("Toggle the continuous download on/off"), KeyEvent.VK_D,
                            Shortcut.ALT_SHIFT), true, "continuosdownload/activate", true);
            buttonModels = new ArrayList<>();
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            active = !active;
            notifySelectedState();
            zoomChanged(); // Trigger a new download
        }

        /**
         * Add a button model
         * @param model The model to add. This action will toggle the selected state.
         */
        public void addButtonModel(ButtonModel model) {
            if (model != null && !buttonModels.contains(model)) {
                buttonModels.add(model);
            }
        }

        protected void notifySelectedState() {
            for (ButtonModel model : buttonModels) {
                if (model.isSelected() != active) {
                    model.setSelected(active);
                }
            }
        }
    }

    public static List<String> getStrategies() {
        return new ArrayList<>(strats.keySet());
    }

    @Override
    public void destroy() {
        NavigatableComponent.removeZoomChangeListener(this);
        worker.shutdown();
        MainApplication.getMenu().fileMenu.remove(menuItem);
        if (preference != null)
            preference.destroy();
        exceptionConsumers.clear();
    }
}
