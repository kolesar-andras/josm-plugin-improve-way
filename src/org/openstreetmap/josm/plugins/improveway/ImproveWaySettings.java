package org.openstreetmap.josm.plugins.improveway;

import org.openstreetmap.josm.actions.ExpertToggleAction;
import org.openstreetmap.josm.actions.ExpertToggleAction.ExpertModeChangeListener;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.util.KeyPressReleaseListener;
import org.openstreetmap.josm.tools.Logging;
import org.openstreetmap.josm.tools.Shortcut;

import java.awt.event.KeyEvent;
import java.util.Timer;
import java.util.TimerTask;

public class ImproveWaySettings implements KeyPressReleaseListener, ExpertModeChangeListener {

    protected boolean helpersEnabled;
    protected boolean helpersUseOriginal;
    protected boolean helpersEnabledBeforeKeypressed;
    protected long keypressTime;
    protected long longKeypressTime;
    protected Timer longKeypressTimer;
    protected boolean isExpert;
    protected boolean meta; // Windows/Super/Meta key

    protected final transient ImproveWayAction improveWayAction;
    protected final transient Shortcut helpersShortcut;

    public ImproveWaySettings(ImproveWayAction improveWayAction, Shortcut helpersShortcut) {
        this.improveWayAction = improveWayAction;
        this.helpersShortcut = helpersShortcut;
        ExpertToggleAction.addExpertModeChangeListener(this, true);
    }

    void onEnterMode() {
        MainApplication.getMap().keyDetector.addKeyListener(this);
        if (!isExpert) return;
        meta = false;
        helpersEnabled = false;
        keypressTime = 0;
        resetTimer();
        longKeypressTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                helpersEnabled = true;
                helpersUseOriginal = true;
                MainApplication.getLayerManager().invalidateEditLayer();
            }
        }, longKeypressTime);
    }

    void onExitMode() {
        MainApplication.getMap().keyDetector.removeKeyListener(this);
    }

    @Override
    public void doKeyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_WINDOWS) {
            meta = true;
            improveWayAction.setMouseToEqualAnglePoint();
            MainApplication.getLayerManager().invalidateEditLayer();
            return;
        }
        if (!helpersShortcut.isEvent(e)) return;
        if (!isExpert) return;
        keypressTime = System.currentTimeMillis();
        helpersEnabledBeforeKeypressed = helpersEnabled;
        if (!helpersEnabled) helpersEnabled = true;
        helpersUseOriginal = true;
        MainApplication.getLayerManager().invalidateEditLayer();
    }

    @Override
    public void doKeyReleased(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_WINDOWS) {
            meta = false;
            MainApplication.getLayerManager().invalidateEditLayer();
            return;
        }
        if (!helpersShortcut.isEvent(e)) return;
        if (!isExpert) return;
        resetTimer();
        long keyupTime = System.currentTimeMillis();
        if (keypressTime == 0) { // comes from enterMode
            helpersEnabled = false;
        } else if (keyupTime - keypressTime > longKeypressTime) {
            helpersEnabled = helpersEnabledBeforeKeypressed;
        } else {
            helpersEnabled = !helpersEnabledBeforeKeypressed;
        }
        helpersUseOriginal = false;
        MainApplication.getLayerManager().invalidateEditLayer();
    }

    protected void resetTimer() {
        if (longKeypressTimer != null) {
            try {
                longKeypressTimer.cancel();
                longKeypressTimer.purge();
            } catch (IllegalStateException exception) {
                Logging.debug(exception);
            }
        }
        longKeypressTimer = new Timer();
    }

    @Override
    public void expertChanged(boolean isExpert) {
        this.isExpert = isExpert;
        if (!isExpert && helpersEnabled) {
            helpersEnabled = false;
            MainApplication.getLayerManager().invalidateEditLayer();
        }
    }

}
