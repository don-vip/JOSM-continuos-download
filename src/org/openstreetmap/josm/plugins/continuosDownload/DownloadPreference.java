// License: GPL. See LICENSE file for details.
package org.openstreetmap.josm.plugins.continuosDownload;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.util.HashMap;
import java.util.Map;

import javax.swing.Box;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

import org.openstreetmap.josm.gui.preferences.DefaultTabPreferenceSetting;
import org.openstreetmap.josm.gui.preferences.PreferenceTabbedPane;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.tools.Destroyable;
import org.openstreetmap.josm.tools.GBC;

/**
 * Plugin preferences.
 */
public class DownloadPreference extends DefaultTabPreferenceSetting implements Destroyable {

    private final JCheckBox activeDefault = new JCheckBox(tr("Activate continuous downloads at startup."));
    private final JTextField maxThreads = new JTextField(4);
    private final JTextField maxAreas = new JTextField(4);
    private final JTextField waitTime = new JTextField(6);
    private final JTextField extraDownload = new JTextField(4);
    private final JTextField maxArea = new JTextField(4);
    private final JComboBox<String> strategy = new JComboBox<>();
    private final JCheckBox quietDownload = new JCheckBox(tr("Download data in background."));

    private final Map<PreferenceTabbedPane, JPanel> guiPanes = new HashMap<>();
    /**
     * Constructs a new {@code DownloadPreference}.
     */
    public DownloadPreference() {
        super("continuous-download", tr("Download Settings"), tr("Settings for the continuous download."), true);
    }

    @Override
    public void addGui(PreferenceTabbedPane gui) {
        JPanel panel = new JPanel(new GridBagLayout());

        // activeDefault
        activeDefault.setSelected(Config.getPref().getBoolean("plugin.continuos_download.active_default", true));
        activeDefault.setToolTipText(tr("If this plugin is active at startup. This default state will not change when"
                + " you are toggeling the plugin with the menu option."));
        panel.add(activeDefault, GBC.eol().insets(0, 0, 0, 0));

        // maxThreads
        maxThreads.setText(Config.getPref().get("plugin.continuos_download.max_threads", "2"));
        maxThreads.setToolTipText(tr("Maximum number of threads used for downloading, increasing this will cause the"
                + " client to send more concurrent queries to the server.")+" "+tr("Requires restart"));
        panel.add(new JLabel(tr("Max threads")), GBC.std());
        panel.add(maxThreads, GBC.eol().fill(GridBagConstraints.HORIZONTAL).insets(5, 0, 0, 5));

        // maxAreas
        maxAreas.setText(Config.getPref().get("plugin.continuos_download.max_areas", "4"));
        maxAreas.setToolTipText(tr("Maximum number of boxes to download for each pan/zoom."));
        panel.add(new JLabel(tr("Max download boxes")), GBC.std());
        panel.add(maxAreas, GBC.eol().fill(GridBagConstraints.HORIZONTAL).insets(5, 0, 0, 5));

        // waitTime
        waitTime.setText(Config.getPref().get("plugin.continuos_download.wait_time", "500"));
        waitTime.setToolTipText(tr("Time in milliseconds after a pan/zoom before it starts downloading. Additional"
                + " changes in the viewport in the waiting time will reset the timer."));
        panel.add(new JLabel(tr("Wait time (milliseconds)")), GBC.std());
        panel.add(waitTime, GBC.eol().fill(GridBagConstraints.HORIZONTAL).insets(5, 0, 0, 5));

        // extraDownload
        extraDownload.setText(Config.getPref().get("plugin.continuos_download.extra_download", "0.1"));
        extraDownload.setToolTipText(tr("How much extra area around the viewport is it going to download. Setting this"
                + " to 0 will not download any extra data."));
        panel.add(new JLabel(tr("Extra download area")), GBC.std());
        panel.add(extraDownload, GBC.eol().fill(GridBagConstraints.HORIZONTAL).insets(5, 0, 0, 5));

        // maxArea
        maxArea.setText(Config.getPref().get("plugin.continuos_download.max_area", "0.25"));
        maxArea.setToolTipText(tr("Max area to download in degrees^2. Increasing this number will cause the"
                + " plugin to download areas when you are zoomed far out."));
        panel.add(new JLabel(tr("Max download area")), GBC.std());
        panel.add(maxArea, GBC.eol().fill(GridBagConstraints.HORIZONTAL).insets(5, 0, 0, 5));

        // strategy
        for (String strat : DownloadPlugin.getStrategies()) {
            strategy.addItem(strat);
        }
        strategy.setSelectedItem(Config.getPref().get("plugin.continuos_download.strategy", "BoxStrategy"));
        strategy.setToolTipText(tr("The strategy for finding what areas to request from the server."));
        panel.add(new JLabel(tr("Download strategy")), GBC.std());
        panel.add(strategy, GBC.eol().fill(GridBagConstraints.HORIZONTAL).insets(5, 0, 0, 5));

        // quietDownload
        quietDownload.setSelected(Config.getPref().getBoolean("plugin.continuos_download.quiet_download", true));
        quietDownload.setToolTipText(tr("Downloads the data continuously in the background as you pan and zoom around."));
        panel.add(quietDownload, GBC.eol().insets(0, 0, 0, 0));

        panel.add(Box.createVerticalGlue(), GBC.eol().fill(GridBagConstraints.VERTICAL));
        createPreferenceTabWithScrollPane(gui, panel);
        guiPanes.put(gui, panel);
    }

    @Override
    public boolean ok() {
        boolean r = !maxThreads.getText().equals(Config.getPref().get("plugin.continuos_download.max_threads", "2"));

        Config.getPref().putBoolean("plugin.continuos_download.active_default", activeDefault.isSelected());
        Config.getPref().put("plugin.continuos_download.max_threads", maxThreads.getText());
        Config.getPref().put("plugin.continuos_download.max_areas", maxAreas.getText());
        Config.getPref().put("plugin.continuos_download.wait_time", waitTime.getText());
        Config.getPref().put("plugin.continuos_download.extra_download", extraDownload.getText());
        Config.getPref().put("plugin.continuos_download.max_area", maxArea.getText());
        Config.getPref().put("plugin.continuos_download.strategy", (String) strategy.getSelectedItem());
        Config.getPref().putBoolean("plugin.continuos_download.quiet_download", quietDownload.isSelected());
        return r;
    }

    @Override
    public void destroy() {
        guiPanes.forEach((gui, panel) -> gui.remove(panel));
    }
}
