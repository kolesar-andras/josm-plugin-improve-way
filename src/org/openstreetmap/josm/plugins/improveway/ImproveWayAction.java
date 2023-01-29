// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.improveway;

import org.openstreetmap.josm.actions.ExpertToggleAction;
import org.openstreetmap.josm.actions.ExpertToggleAction.ExpertModeChangeListener;
import org.openstreetmap.josm.actions.mapmode.ImproveWayAccuracyAction;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.preferences.NamedColorProperty;
import org.openstreetmap.josm.data.projection.ProjectionRegistry;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MapView;
import org.openstreetmap.josm.gui.util.GuiHelper;
import org.openstreetmap.josm.gui.util.KeyPressReleaseListener;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.spi.preferences.PreferenceChangeEvent;
import org.openstreetmap.josm.spi.preferences.PreferenceChangedListener;
import org.openstreetmap.josm.tools.*;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.util.Timer;
import java.util.*;

import static org.openstreetmap.josm.tools.I18n.*;

/**
 * @author Alexander Kachkaev &lt;alexander@kachkaev.ru&gt;, 2011
 */
public class ImproveWayAction
    extends ImproveWayAccuracyAction
    implements KeyPressReleaseListener, ExpertModeChangeListener, PreferenceChangedListener
{
    protected Color turnColor;
    protected Color distanceColor;
    protected Color arcFillColor;
    protected Color arcStrokeColor;
    protected Color perpendicularLineColor;
    protected Color equalAngleCircleColor;

    protected transient Stroke arcStroke;
    protected transient Stroke perpendicularLineStroke;
    protected transient Stroke equalAngleCircleStroke;

    protected int arcRadiusPixels;
    protected int perpendicularLengthPixels;
    protected int turnTextDistance;
    protected int distanceTextDistance;
    protected int equalAngleCircleRadius;
    protected long longKeypressTime;

    protected boolean helpersEnabled = false;
    protected boolean helpersUseOriginal = false;
    protected final transient Shortcut helpersShortcut;
    protected long keypressTime = 0;
    protected boolean helpersEnabledBeforeKeypressed = false;
    protected Timer longKeypressTimer;
    protected boolean isExpert = false;

    protected boolean meta = false; // Windows/Super/Meta key

    /**
     * Constructs a new {@code ImproveWayAccuracyAction}.
     */
    public ImproveWayAction() {
        super(tr("Improve Way"), "improveway",
            tr("Improve Way mode"),
            Shortcut.registerShortcut("mapmode:ImproveWay",
                tr("Mode: {0}", tr("Improve Way")),
                KeyEvent.VK_W, Shortcut.DIRECT), Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));

        helpersShortcut = Shortcut.registerShortcut("mapmode:enablewayaccuracyhelpers",
                tr("Mode: Enable way accuracy helpers"), KeyEvent.CHAR_UNDEFINED, Shortcut.NONE);

        ExpertToggleAction.addExpertModeChangeListener(this, true);
    }

    // -------------------------------------------------------------------------
    // Mode methods
    // -------------------------------------------------------------------------
    @Override
    public void enterMode() {
        super.enterMode();
        meta = false;
        MainApplication.getMap().keyDetector.addKeyListener(this);
        if (!isExpert) return;
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

    @Override
    public void exitMode() {
        super.exitMode();
        MainApplication.getMap().keyDetector.removeKeyListener(this);
    }

    @Override
    protected void readPreferences() {
        super.readPreferences();
        turnColor = new NamedColorProperty(marktr("improve way accuracy helper turn angle text"), new Color(240, 240, 240, 200)).get();
        distanceColor = new NamedColorProperty(marktr("improve way accuracy helper distance text"), new Color(240, 240, 240, 120)).get();
        arcFillColor = new NamedColorProperty(marktr("improve way accuracy helper arc fill"), new Color(200, 200, 200, 50)).get();
        arcStrokeColor = new NamedColorProperty(marktr("improve way accuracy helper arc stroke"), new Color(240, 240, 240, 150)).get();
        perpendicularLineColor = new NamedColorProperty(marktr("improve way accuracy helper perpendicular line"), 
                new Color(240, 240, 240, 150)).get();
        equalAngleCircleColor = new NamedColorProperty(marktr("improve way accuracy helper equal angle circle"), 
                new Color(240, 240, 240, 150)).get();

        arcStroke = GuiHelper.getCustomizedStroke(Config.getPref().get("improvewayaccuracy.stroke.helper-arc", "1"));
        perpendicularLineStroke = GuiHelper.getCustomizedStroke(Config.getPref().get("improvewayaccuracy.stroke.helper-perpendicular-line", "1 6"));
        equalAngleCircleStroke = GuiHelper.getCustomizedStroke(Config.getPref().get("improvewayaccuracy.stroke.helper-eual-angle-circle", "1"));

        arcRadiusPixels = Config.getPref().getInt("improvewayaccuracy.helper-arc-radius", 200);
        perpendicularLengthPixels = Config.getPref().getInt("improvewayaccuracy.helper-perpendicular-line-length", 100);
        turnTextDistance = Config.getPref().getInt("improvewayaccuracy.helper-turn-text-distance", 15);
        distanceTextDistance = Config.getPref().getInt("improvewayaccuracy.helper-distance-text-distance", 15);
        equalAngleCircleRadius = Config.getPref().getInt("improvewayaccuracy.helper-equal-angle-circle-radius", 15);
        longKeypressTime = Config.getPref().getInt("improvewayaccuracy.long-keypress-time", 250);
    }

    @Override
    public void paint(Graphics2D g, MapView mv, Bounds bbox) {
        super.paint(g, mv, bbox);

        if (state == State.IMPROVING) {
            // Painting helpers visualizing turn angles and more
            if (!helpersEnabled) return;
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            drawHalfDistanceLine(g, mv);
            drawTurnAnglePie(g, mv);
            drawEqualAnglePoint(g, mv);
        }
    }

    /**
     * Draw a perpendicular line at half distance between two endpoints
     */
    protected void drawHalfDistanceLine(Graphics2D g, MapView mv) {
        if (!(alt && !ctrl) && endpoint1 != null && endpoint2 != null) {
            Point p1 = mv.getPoint(endpoint1);
            Point p2 = mv.getPoint(endpoint2);
            Point half = new Point(
                (p1.x + p2.x)/2,
                (p1.y + p2.y)/2
            );
            double heading = Math.atan2(
                p2.y-p1.y,
                p2.x-p1.x
            ) + Math.PI/2;
            g.setStroke(perpendicularLineStroke);
            g.setColor(perpendicularLineColor);
            g.draw(new Line2D.Double(
                half.x + perpendicularLengthPixels * Math.cos(heading),
                half.y + perpendicularLengthPixels * Math.sin(heading),
                half.x - perpendicularLengthPixels * Math.cos(heading),
                half.y - perpendicularLengthPixels * Math.sin(heading)
            ));
        }
    }

    /**
     * Draw a pie (part of a circle) representing turn angle at each node
     */
    protected void drawTurnAnglePie(Graphics2D g, MapView mv) {
        LatLon lastcoor = null;
        Point lastpoint = null;
        double lastheading = 0d;
        boolean candidateSegmentVisited = false;
        int nodeCounter = 0;
        int nodesCount = targetWay.getNodesCount();
        int endLoop = nodesCount;
        if (targetWay.isClosed()) endLoop++;

        LatLon newLatLon = getNewLatLon();
        Point newPoint = mv.getPoint(newLatLon);

        for (int i = 0; i < endLoop; i++) {
            // when way is closed we visit second node again
            // to get turn for start/end node
            Node node = targetWay.getNode(i == nodesCount ? 1 : i);
            LatLon coor;
            Point point;
            if (!helpersUseOriginal && newLatLon != null &&
                ctrl &&
                !candidateSegmentVisited &&
                candidateSegment != null &&
                candidateSegment.getSecondNode() == node
            ) {
                coor = newLatLon;
                point = newPoint;
                candidateSegmentVisited = true;
                i--;
            } else if (!helpersUseOriginal && newLatLon != null && !alt && !ctrl && node == candidateNode) {
                coor = newLatLon;
                point = newPoint;
            } else if (!helpersUseOriginal && alt && !ctrl && node == candidateNode) {
                continue;
            } else {
                coor = node.getCoor();
                point = mv.getPoint(coor);
            }
            if (nodeCounter >= 1 && lastcoor != null && lastpoint != null) {
                double heading = ImproveWayHelper.fixHeading(-90+lastcoor.bearing(coor)*180/Math.PI);
                double distance = lastcoor.greatCircleDistance(coor);
                if (nodeCounter >= 2) {
                    double turn = Math.abs(ImproveWayHelper.fixHeading(heading-lastheading));
                    double fixedHeading = ImproveWayHelper.fixHeading(heading - lastheading);
                    g.setColor(turnColor);
                    ImproveWayHelper.drawDisplacedlabel(
                        lastpoint.x,
                        lastpoint.y,
                        turnTextDistance,
                        (lastheading + fixedHeading/2 + (fixedHeading >= 0 ? 90 : -90))*Math.PI/180,
                        String.format("%1.0f °", turn),
                        g
                    );
                    double arcRadius = arcRadiusPixels;
                    Arc2D arc = new Arc2D.Double(
                        lastpoint.x-arcRadius,
                        lastpoint.y-arcRadius,
                        arcRadius*2,
                        arcRadius*2,
                        -heading + (fixedHeading >= 0 ? 90 : -90),
                        fixedHeading,
                        Arc2D.PIE
                    );
                    g.setStroke(arcStroke);
                    g.setColor(arcFillColor);
                    g.fill(arc);
                    g.setColor(arcStrokeColor);
                    g.draw(arc);
                }

                // Display segment length
                // avoid doubling first segment on closed ways
                if (i != nodesCount) {
                    g.setColor(distanceColor);
                    ImproveWayHelper.drawDisplacedlabel(
                        (lastpoint.x+point.x)/2,
                        (lastpoint.y+point.y)/2,
                        distanceTextDistance,
                        (heading + 90)*Math.PI/180,
                        String.format("%1.0f m", distance),
                        g
                    );
                }

                lastheading = heading;
            }
            lastcoor = coor;
            lastpoint = point;
            nodeCounter++;
        }
    }

    /**
     * Draw a point where turn angle will be same with two neighbours
     */
    protected void drawEqualAnglePoint(Graphics2D g, MapView mv) {
        LatLon equalAngleLatLon = findEqualAngleLatLon();
        if (equalAngleLatLon != null) {
            Point equalAnglePoint = mv.getPoint(equalAngleLatLon);
            Ellipse2D.Double equalAngleCircle = new Ellipse2D.Double(
                equalAnglePoint.x-equalAngleCircleRadius/2d,
                equalAnglePoint.y-equalAngleCircleRadius/2d,
                equalAngleCircleRadius,
                equalAngleCircleRadius);
            g.setStroke(equalAngleCircleStroke);
            g.setColor(equalAngleCircleColor);
            g.draw(equalAngleCircle);
        }
    }

    public LatLon getNewLatLon() {
        if (meta) {
            return findEqualAngleLatLon();
        } else if (mousePos != null) {
            return mv.getLatLon(mousePos.x, mousePos.y);
        } else {
            return null;
        }
    }

    public LatLon findEqualAngleLatLon() {
        int index1 = -1;
        int index2 = -1;
        int realNodesCount = targetWay.getRealNodesCount();

        for (int i = 0; i < realNodesCount; i++) {
            Node node = targetWay.getNode(i);
            if (node == candidateNode) {
                index1 = i-1;
                index2 = i+1;
            }
            if (candidateSegment != null) {
                if (node == candidateSegment.getFirstNode()) index1 = i;
                if (node == candidateSegment.getSecondNode()) index2 = i;
            }
        }

        int i11 = ImproveWayHelper.fixIndex(realNodesCount, targetWay.isClosed(), index1-1);
        int i12 = ImproveWayHelper.fixIndex(realNodesCount, targetWay.isClosed(), index1);
        int i21 = ImproveWayHelper.fixIndex(realNodesCount, targetWay.isClosed(), index2);
        int i22 = ImproveWayHelper.fixIndex(realNodesCount, targetWay.isClosed(), index2+1);
        if (i11 < 0 || i12 < 0 || i21 < 0 || i22 < 0) return null;

        EastNorth p11 = targetWay.getNode(i11).getEastNorth();
        EastNorth p12 = targetWay.getNode(i12).getEastNorth();
        EastNorth p21 = targetWay.getNode(i21).getEastNorth();
        EastNorth p22 = targetWay.getNode(i22).getEastNorth();

        double a1 = Geometry.getSegmentAngle(p11, p12);
        double a2 = Geometry.getSegmentAngle(p21, p22);
        double a = ImproveWayHelper.fixHeading((a2-a1)*180/Math.PI)*Math.PI/180/3;

        EastNorth p1r = p11.rotate(p12, -a);
        EastNorth p2r = p22.rotate(p21, a);

        EastNorth intersection = Geometry.getLineLineIntersection(p1r, p12, p21, p2r);
        return ProjectionRegistry.getProjection().eastNorth2latlon(intersection);
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
    protected void updateMousePosition(MouseEvent e) {
        if (meta) {
            mousePos = mv.getPoint(findEqualAngleLatLon());
        } else {
            super.updateMousePosition(e);
        }
    }

    @Override
    public void doKeyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_WINDOWS) {
            meta = true;
            mousePos = mv.getPoint(findEqualAngleLatLon());
            MainApplication.getLayerManager().invalidateEditLayer();
            return;
        }
        if (!helpersShortcut.isEvent(e) && !getShortcut().isEvent(e)) return;
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
        if (!helpersShortcut.isEvent(e) && !getShortcut().isEvent(e)) return;
        if (!isExpert) return;
        resetTimer();
        long keyupTime = System.currentTimeMillis();
        if (keypressTime == 0) { // comes from enterMode
            helpersEnabled = false;
        } else if (keyupTime-keypressTime > longKeypressTime) {
            helpersEnabled = helpersEnabledBeforeKeypressed;
        } else {
            helpersEnabled = !helpersEnabledBeforeKeypressed;
        }
        helpersUseOriginal = false;
        MainApplication.getLayerManager().invalidateEditLayer();
    }

    @Override
    public void expertChanged(boolean isExpert) {
        this.isExpert = isExpert;
        if (!isExpert && helpersEnabled) {
            helpersEnabled = false;
            MainApplication.getLayerManager().invalidateEditLayer();
        }
    }

    @Override
    public void preferenceChanged(PreferenceChangeEvent e) {
        super.preferenceChanged(e);
        if (isEnabled() && (e.getKey().startsWith("improvewayaccuracy") || e.getKey().startsWith("color.improve.way.accuracy"))) {
            MainApplication.getLayerManager().invalidateEditLayer();
        }
    }
}
