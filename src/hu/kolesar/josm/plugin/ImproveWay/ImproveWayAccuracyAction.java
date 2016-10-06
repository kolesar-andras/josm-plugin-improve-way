// License: GPL. For details, see LICENSE file.
package hu.kolesar.josm.plugin.ImproveWay;

import static org.openstreetmap.josm.tools.I18n.marktr;
import static org.openstreetmap.josm.tools.I18n.tr;
import static org.openstreetmap.josm.tools.I18n.trn;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.GeneralPath;
import java.awt.geom.Line2D;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import javax.swing.JOptionPane;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.actions.ExpertToggleAction;
import org.openstreetmap.josm.actions.ExpertToggleAction.ExpertModeChangeListener;
import org.openstreetmap.josm.actions.mapmode.MapMode;
import org.openstreetmap.josm.command.AddCommand;
import org.openstreetmap.josm.command.ChangeCommand;
import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.command.DeleteCommand;
import org.openstreetmap.josm.command.MoveCommand;
import org.openstreetmap.josm.command.SequenceCommand;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.Preferences.PreferenceChangeEvent;
import org.openstreetmap.josm.data.Preferences.PreferenceChangedListener;
import org.openstreetmap.josm.data.SelectionChangedListener;
import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.data.osm.WaySegment;
import org.openstreetmap.josm.data.osm.visitor.paint.PaintColors;
import org.openstreetmap.josm.data.preferences.ColorProperty;
import org.openstreetmap.josm.gui.MapFrame;
import org.openstreetmap.josm.gui.MapView;
import org.openstreetmap.josm.gui.layer.Layer;
import org.openstreetmap.josm.gui.layer.MapViewPaintable;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.gui.util.GuiHelper;
import org.openstreetmap.josm.gui.util.KeyPressReleaseListener;
import org.openstreetmap.josm.gui.util.ModifierListener;
import org.openstreetmap.josm.tools.Geometry;
import org.openstreetmap.josm.tools.ImageProvider;
import org.openstreetmap.josm.tools.Pair;
import org.openstreetmap.josm.tools.Shortcut;

/**
 * @author Alexander Kachkaev &lt;alexander@kachkaev.ru&gt;, 2011
 */
public class ImproveWayAccuracyAction extends MapMode implements MapViewPaintable,
        SelectionChangedListener, ModifierListener, KeyPressReleaseListener,
        ExpertModeChangeListener, PreferenceChangedListener {

    enum State {
        selecting, improving
    }

    private State state;

    private MapView mv;

    private static final long serialVersionUID = 42L;

    private transient Way targetWay;
    private transient Node candidateNode;
    private transient WaySegment candidateSegment;

    private Point mousePos;
    private boolean dragging;

    private final Cursor cursorSelect;
    private final Cursor cursorSelectHover;
    private final Cursor cursorImprove;
    private final Cursor cursorImproveAdd;
    private final Cursor cursorImproveDelete;
    private final Cursor cursorImproveAddLock;
    private final Cursor cursorImproveLock;

    private Color guideColor;
    private Color turnColor;
    private Color distanceColor;
    private Color arcFillColor;
    private Color arcStrokeColor;
    private Color perpendicularLineColor;
    private Color equalAngleCircleColor;

    private transient Stroke selectTargetWayStroke;
    private transient Stroke moveNodeStroke;
    private transient Stroke moveNodeIntersectingStroke;
    private transient Stroke addNodeStroke;
    private transient Stroke deleteNodeStroke;
    private transient Stroke arcStroke;
    private transient Stroke perpendicularLineStroke;
    private transient Stroke equalAngleCircleStroke;
    private int dotSize;

    private boolean selectionChangedBlocked;

    protected String oldModeHelpText;

    private int arcRadiusPixels;
    private int perpendicularLengthPixels;
    private int turnTextDistance;
    private int distanceTextDistance;
    private int equalAngleCircleRadius;
    private long longKeypressTime;

    private boolean helpersEnabled = false;
    private boolean helpersUseOriginal = false;
    private final transient Shortcut helpersShortcut;
    private long keypressTime = 0;
    private boolean helpersEnabledBeforeKeypressed = false;
    private Timer longKeypressTimer;
    private boolean isExpert = false;

    private boolean mod4 = false; // Windows/Super/Meta key

    /**
     * Constructs a new {@code ImproveWayAccuracyAction}.
     * @param mapFrame Map frame
     */
    public ImproveWayAccuracyAction(MapFrame mapFrame) {
        super(tr("Improve Way"), "improveway",
                tr("Improve Way mode"),
                Shortcut.registerShortcut("mapmode:ImproveWay",
                tr("Mode: {0}", tr("Improve Way")),
                KeyEvent.VK_W, Shortcut.DIRECT), mapFrame, Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));

        helpersShortcut = Shortcut.registerShortcut("mapmode:enablewayaccuracyhelpers",
                tr("Mode: Enable way accuracy helpers"), KeyEvent.CHAR_UNDEFINED, Shortcut.NONE);

        cursorSelect = ImageProvider.getCursor("normal", "mode");
        cursorSelectHover = ImageProvider.getCursor("hand", "mode");
        cursorImprove = ImageProvider.getCursor("crosshair", null);
        cursorImproveAdd = ImageProvider.getCursor("crosshair", "addnode");
        cursorImproveDelete = ImageProvider.getCursor("crosshair", "delete_node");
        cursorImproveAddLock = ImageProvider.getCursor("crosshair",
                "add_node_lock");
        cursorImproveLock = ImageProvider.getCursor("crosshair", "lock");
        ExpertToggleAction.addExpertModeChangeListener(this, true);
        Main.pref.addPreferenceChangeListener(this);
        readPreferences();
    }

    // -------------------------------------------------------------------------
    // Mode methods
    // -------------------------------------------------------------------------
    @Override
    public void enterMode() {
        if (!isEnabled()) {
            return;
        }
        super.enterMode();

        mv = Main.map.mapView;
        mousePos = null;
        oldModeHelpText = "";

        if (getLayerManager().getEditDataSet() == null) {
            return;
        }

        updateStateByCurrentSelection();

        Main.map.keyDetector.addKeyListener(this);
        Main.map.mapView.addMouseListener(this);
        Main.map.mapView.addMouseMotionListener(this);
        Main.map.mapView.addTemporaryLayer(this);
        DataSet.addSelectionListener(this);

        Main.map.keyDetector.addModifierListener(this);

        if (!isExpert) return;
        helpersEnabled = false;
        keypressTime = 0;
        resetTimer();
        longKeypressTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                helpersEnabled = true;
                helpersUseOriginal = true;
                Main.map.mapView.repaint();
            }
        }, longKeypressTime);
    }

    @Override
    protected void readPreferences() {
        guideColor = new ColorProperty(marktr("improve way accuracy helper line"), (Color) null).get();
        if (guideColor == null) guideColor = PaintColors.HIGHLIGHT.get();

        turnColor = new ColorProperty(marktr("improve way accuracy helper turn angle text"), new Color(240, 240, 240, 200)).get();
        distanceColor = new ColorProperty(marktr("improve way accuracy helper distance text"), new Color(240, 240, 240, 120)).get();
        arcFillColor = new ColorProperty(marktr("improve way accuracy helper arc fill"), new Color(200, 200, 200, 50)).get();
        arcStrokeColor = new ColorProperty(marktr("improve way accuracy helper arc stroke"), new Color(240, 240, 240, 150)).get();
        perpendicularLineColor = new ColorProperty(marktr("improve way accuracy helper perpendicular line"), 
                new Color(240, 240, 240, 150)).get();
        equalAngleCircleColor = new ColorProperty(marktr("improve way accuracy helper equal angle circle"), 
                new Color(240, 240, 240, 150)).get();

        selectTargetWayStroke = GuiHelper.getCustomizedStroke(Main.pref.get("improvewayaccuracy.stroke.select-target", "2"));
        moveNodeStroke = GuiHelper.getCustomizedStroke(Main.pref.get("improvewayaccuracy.stroke.move-node", "1 6"));
        moveNodeIntersectingStroke = GuiHelper.getCustomizedStroke(Main.pref.get("improvewayaccuracy.stroke.move-node-intersecting", "1 2 6"));
        addNodeStroke = GuiHelper.getCustomizedStroke(Main.pref.get("improvewayaccuracy.stroke.add-node", "1"));
        deleteNodeStroke = GuiHelper.getCustomizedStroke(Main.pref.get("improvewayaccuracy.stroke.delete-node", "1"));
        arcStroke = GuiHelper.getCustomizedStroke(Main.pref.get("improvewayaccuracy.stroke.helper-arc", "1"));
        perpendicularLineStroke = GuiHelper.getCustomizedStroke(Main.pref.get("improvewayaccuracy.stroke.helper-perpendicular-line", "1 6"));
        equalAngleCircleStroke = GuiHelper.getCustomizedStroke(Main.pref.get("improvewayaccuracy.stroke.helper-eual-angle-circle", "1"));

        dotSize = Main.pref.getInteger("improvewayaccuracy.dot-size", 6);
        arcRadiusPixels = Main.pref.getInteger("improvewayaccuracy.helper-arc-radius", 200);
        perpendicularLengthPixels = Main.pref.getInteger("improvewayaccuracy.helper-perpendicular-line-length", 100);
        turnTextDistance = Main.pref.getInteger("improvewayaccuracy.helper-turn-text-distance", 15);
        distanceTextDistance = Main.pref.getInteger("improvewayaccuracy.helper-distance-text-distance", 15);
        equalAngleCircleRadius = Main.pref.getInteger("improvewayaccuracy.helper-equal-angle-circle-radius", 15);
        longKeypressTime = Main.pref.getInteger("improvewayaccuracy.long-keypress-time", 250);
    }

    @Override
    public void exitMode() {
        super.exitMode();

        Main.map.keyDetector.removeKeyListener(this);
        Main.map.mapView.removeMouseListener(this);
        Main.map.mapView.removeMouseMotionListener(this);
        Main.map.mapView.removeTemporaryLayer(this);
        DataSet.removeSelectionListener(this);

        Main.map.keyDetector.removeModifierListener(this);
        Main.map.mapView.repaint();
    }

    @Override
    protected void updateStatusLine() {
        String newModeHelpText = getModeHelpText();
        if (!newModeHelpText.equals(oldModeHelpText)) {
            oldModeHelpText = newModeHelpText;
            Main.map.statusLine.setHelpText(newModeHelpText);
            Main.map.statusLine.repaint();
        }
    }

    @Override
    public String getModeHelpText() {
        if (state == State.selecting) {
            if (targetWay != null) {
                return tr("Click on the way to start improving its shape.");
            } else {
                return tr("Select a way that you want to make more accurate.");
            }
        } else {
            if (ctrl) {
                return tr("Click to add a new node. Release Ctrl to move existing nodes or hold Alt to delete.");
            } else if (alt) {
                return tr("Click to delete the highlighted node. Release Alt to move existing nodes or hold Ctrl to add new nodes.");
            } else {
                return tr("Click to move the highlighted node. Hold Ctrl to add new nodes, or Alt to delete.");
            }
        }
    }

    @Override
    public boolean layerIsSupported(Layer l) {
        return l instanceof OsmDataLayer;
    }

    @Override
    protected void updateEnabledState() {
        setEnabled(getLayerManager().getEditLayer() != null);
    }

    // -------------------------------------------------------------------------
    // MapViewPaintable methods
    // -------------------------------------------------------------------------
    /**
     * Redraws temporary layer. Highlights targetWay in select mode. Draws
     * preview lines in improve mode and highlights the candidateNode
     */
    @Override
    public void paint(Graphics2D g, MapView mv, Bounds bbox) {

        g.setColor(guideColor);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        if (state == State.selecting && targetWay != null) {
            // Highlighting the targetWay in Selecting state
            // Non-native highlighting is used, because sometimes highlighted
            // segments are covered with others, which is bad.
            g.setStroke(selectTargetWayStroke);

            List<Node> nodes = targetWay.getNodes();

            GeneralPath b = new GeneralPath();
            Point p0 = mv.getPoint(nodes.get(0));
            Point pn;
            b.moveTo(p0.x, p0.y);

            for (Node n : nodes) {
                pn = mv.getPoint(n);
                b.lineTo(pn.x, pn.y);
            }
            if (targetWay.isClosed()) {
                b.lineTo(p0.x, p0.y);
            }

            g.draw(b);

        } else if (state == State.improving) {
            // Drawing preview lines and highlighting the node
            // that is going to be moved.
            // Non-native highlighting is used here as well.

            // Finding endpoints
            Point p1 = null, p2 = null;
            if (ctrl && candidateSegment != null) {
                g.setStroke(addNodeStroke);
                p1 = mv.getPoint(candidateSegment.getFirstNode());
                p2 = mv.getPoint(candidateSegment.getSecondNode());
            } else if (!(alt ^ ctrl) && candidateNode != null) {
                g.setStroke(moveNodeStroke);
                List<Pair<Node, Node>> wpps = targetWay.getNodePairs(false);
                for (Pair<Node, Node> wpp : wpps) {
                    if (wpp.a == candidateNode) {
                        p1 = mv.getPoint(wpp.b);
                    }
                    if (wpp.b == candidateNode) {
                        p2 = mv.getPoint(wpp.a);
                    }
                    if (p1 != null && p2 != null) {
                        break;
                    }
                }
            } else if (alt && !ctrl && candidateNode != null) {
                g.setStroke(deleteNodeStroke);
                List<Node> nodes = targetWay.getNodes();
                int index = nodes.indexOf(candidateNode);

                // Only draw line if node is not first and/or last
                if (index != 0 && index != (nodes.size() - 1)) {
                    p1 = mv.getPoint(nodes.get(index - 1));
                    p2 = mv.getPoint(nodes.get(index + 1));
                }
                // TODO: indicate what part that will be deleted? (for end nodes)
            }

            EastNorth newPointEN = getNewPointEN();
            Point newPoint = mv.getPoint(newPointEN);

            // Drawing preview lines
            GeneralPath b = new GeneralPath();
            if (alt && !ctrl) {
                // In delete mode
                if (p1 != null && p2 != null) {
                    b.moveTo(p1.x, p1.y);
                    b.lineTo(p2.x, p2.y);
                }
            } else if (newPointEN != null && newPoint != null) {
                // In add or move mode
                if (p1 != null) {
                    b.moveTo(newPoint.x, newPoint.y);
                    b.lineTo(p1.x, p1.y);
                }
                if (p2 != null) {
                    b.moveTo(newPoint.x, newPoint.y);
                    b.lineTo(p2.x, p2.y);
                }
            }
            g.draw(b);

            // Highlighting candidateNode
            if (candidateNode != null) {
                Point p = mv.getPoint(candidateNode);
                g.setColor(guideColor);
                g.fillRect(p.x - dotSize/2, p.y - dotSize/2, dotSize, dotSize);
            }

            if (!alt && !ctrl && candidateNode != null) {
                b.reset();
                drawIntersectingWayHelperLines(mv, b, newPoint);
                g.setStroke(moveNodeIntersectingStroke);
                g.draw(b);
            }

            // Painting helpers visualizing turn angles and more
            if (!helpersEnabled) return;

            // Perpendicular line at half distance
            if (!(alt && !ctrl) && p1 != null && p2 != null) {
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

            // Pie with turn angle
            Node node;
            LatLon coor, lastcoor = null;
            Point point, lastpoint = null;
            double distance, lastdistance = 0;
            double heading, lastheading = 0;
            double radius, lastradius = 0;
            double turn;
            Arc2D arc;
            double arcRadius;
            boolean candidateSegmentVisited = false;
            int nodeCounter = 0;
            int nodesCount = targetWay.getNodesCount();
            int endLoop = nodesCount;
            if (targetWay.isClosed()) endLoop++;
            for (int i = 0; i < endLoop; i++) {
                // when way is closed we visit second node again
                // to get turn for start/end node
                node = targetWay.getNode(i == nodesCount ? 1 : i);
                if (!helpersUseOriginal && newPointEN != null &&
                    ctrl &&
                    !candidateSegmentVisited &&
                    candidateSegment != null &&
                    candidateSegment.getSecondNode() == node
                ) {
                    coor = Main.getProjection().eastNorth2latlon(newPointEN);
                    point = newPoint;
                    candidateSegmentVisited = true;
                    i--;
                } else if (!helpersUseOriginal && newPointEN != null && !alt && !ctrl && node == candidateNode) {
                    coor = Main.getProjection().eastNorth2latlon(newPointEN);
                    point = newPoint;
                } else if (!helpersUseOriginal && alt && !ctrl && node == candidateNode) {
                    continue;
                } else {
                    coor = node.getCoor();
                    point = mv.getPoint(coor);
                }
                if (nodeCounter >= 1) {
                    heading = fixHeading(-90-lastcoor.heading(coor)*180/Math.PI);
                    distance = lastcoor.greatCircleDistance(coor);
                    radius = point.distance(lastpoint);
                    if (nodeCounter >= 2) {
                        turn = Math.abs(fixHeading(heading-lastheading));
                        double fixedHeading = fixHeading(heading - lastheading);
                        g.setColor(turnColor);
                        drawDisplacedlabel(
                            lastpoint.x,
                            lastpoint.y,
                            turnTextDistance,
                            (lastheading + fixedHeading/2 + (fixedHeading >= 0 ? 90 : -90))*Math.PI/180,
                            String.format("%1.0f °", turn),
                            g
                        );
                        arcRadius = arcRadiusPixels;
                        arc = new Arc2D.Double(
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
                        drawDisplacedlabel(
                            (lastpoint.x+point.x)/2,
                            (lastpoint.y+point.y)/2,
                            distanceTextDistance,
                            (heading + 90)*Math.PI/180,
                            String.format("%1.0f m", distance),
                            g
                        );
                    }

                    lastradius = radius;
                    lastheading = heading;
                    lastdistance = distance;
                }
                lastcoor = coor;
                lastpoint = point;
                nodeCounter++;
            }

            // Find and display point where turn angle will be same with two neighbours
            EastNorth equalAngleEN = findEqualAngleEN();
            if (equalAngleEN != null) {
                Point equalAnglePoint = mv.getPoint(equalAngleEN);
                Ellipse2D.Double equalAngleCircle = new Ellipse2D.Double(
                    equalAnglePoint.x-equalAngleCircleRadius/2,
                    equalAnglePoint.y-equalAngleCircleRadius/2,
                    equalAngleCircleRadius,
                    equalAngleCircleRadius);
                g.setStroke(equalAngleCircleStroke);
                g.setColor(equalAngleCircleColor);
                g.draw(equalAngleCircle);
            }
        }
    }

    // returns node index for closed ways using possibly under/overflowed index
    // returns -1 if not closed and out of range
    private int fixIndex(int count, boolean closed, int index) {
        if (index >= 0 && index < count) return index;
        if (!closed) return -1;
        while (index < 0) index += count;
        while (index >= count) index -= count;
        return index;
    }

    private double fixHeading(double heading) {
        while (heading < -180) heading += 360;
        while (heading > 180) heading -= 360;
        return heading;
    }

    public static void drawDisplacedlabel(
        int x,
        int y,
        int distance,
        double heading,
        String labelText,
        Graphics2D g
    ) {
        int labelWidth, labelHeight;
        FontMetrics fontMetrics = g.getFontMetrics();
        labelWidth = fontMetrics.stringWidth(labelText);
        labelHeight = fontMetrics.getHeight();
        g.drawString(
           labelText,
            (int) (x+(distance+(labelWidth-labelHeight)/2)*Math.cos(heading)-labelWidth/2),
            (int) (y+distance*Math.sin(heading)+labelHeight/2)
        );
    }

    public EastNorth getNewPointEN() {
        if (mod4) {
            return findEqualAngleEN();
        } else if (mousePos != null) {
            return mv.getEastNorth(mousePos.x, mousePos.y);
        } else {
            return null;
        }
    }

    public EastNorth findEqualAngleEN() {
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

        int i11 = fixIndex(realNodesCount, targetWay.isClosed(), index1-1);
        int i12 = fixIndex(realNodesCount, targetWay.isClosed(), index1);
        int i21 = fixIndex(realNodesCount, targetWay.isClosed(), index2);
        int i22 = fixIndex(realNodesCount, targetWay.isClosed(), index2+1);
        if (i11 < 0 || i12 < 0 || i21 < 0 || i22 < 0) return null;

        EastNorth p11 = targetWay.getNode(i11).getEastNorth();
        EastNorth p12 = targetWay.getNode(i12).getEastNorth();
        EastNorth p21 = targetWay.getNode(i21).getEastNorth();
        EastNorth p22 = targetWay.getNode(i22).getEastNorth();

        double a1 = Geometry.getSegmentAngle(p11, p12);
        double a2 = Geometry.getSegmentAngle(p21, p22);
        double a = fixHeading((a2-a1)*180/Math.PI)*Math.PI/180/3;

        EastNorth p1r = p11.rotate(p12, -a);
        EastNorth p2r = p22.rotate(p21, a);

        return Geometry.getLineLineIntersection(p1r, p12, p21, p2r);
    }

    protected void drawIntersectingWayHelperLines(MapView mv, GeneralPath b, Point newPoint) {
        for (final OsmPrimitive referrer : candidateNode.getReferrers()) {
            if (!(referrer instanceof Way) || targetWay.equals(referrer)) {
                continue;
            }
            final List<Node> nodes = ((Way) referrer).getNodes();
            for (int i = 0; i < nodes.size(); i++) {
                if (!candidateNode.equals(nodes.get(i))) {
                    continue;
                }
                if (i > 0) {
                    final Point p = mv.getPoint(nodes.get(i - 1));
                    b.moveTo(newPoint.x, newPoint.y);
                    b.lineTo(p.x, p.y);
                }
                if (i < nodes.size() - 1) {
                    final Point p = mv.getPoint(nodes.get(i + 1));
                    b.moveTo(newPoint.x, newPoint.y);
                    b.lineTo(p.x, p.y);
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Event handlers
    // -------------------------------------------------------------------------
    @Override
    public void modifiersChanged(int modifiers) {
        if (!Main.isDisplayingMapView() || !Main.map.mapView.isActiveLayerDrawable()) {
            return;
        }
        updateKeyModifiers(modifiers);
        updateCursorDependentObjectsIfNeeded();
        updateCursor();
        updateStatusLine();
        Main.map.mapView.repaint();
    }

    @Override
    public void selectionChanged(Collection<? extends OsmPrimitive> newSelection) {
        if (selectionChangedBlocked) {
            return;
        }
        updateStateByCurrentSelection();
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        dragging = true;
        mouseMoved(e);
    }

    @Override
    public void mouseMoved(MouseEvent e) {
        if (!isEnabled()) {
            return;
        }

        mousePos = e.getPoint();

        updateKeyModifiers(e);
        updateCursorDependentObjectsIfNeeded();
        updateCursor();
        updateStatusLine();
        Main.map.mapView.repaint();
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        dragging = false;
        if (!isEnabled() || e.getButton() != MouseEvent.BUTTON1) {
            return;
        }

        updateKeyModifiers(e);
        mousePos = e.getPoint();
        EastNorth newPointEN = getNewPointEN();

        if (state == State.selecting) {
            if (targetWay != null) {
                getLayerManager().getEditDataSet().setSelected(targetWay.getPrimitiveId());
                updateStateByCurrentSelection();
            }
        } else if (state == State.improving && newPointEN != null) {
            // Checking if the new coordinate is outside of the world
            if (Main.getProjection().eastNorth2latlon(newPointEN).isOutSideWorld()) {
                JOptionPane.showMessageDialog(Main.parent,
                        tr("Cannot add a node outside of the world."),
                        tr("Warning"), JOptionPane.WARNING_MESSAGE);
                return;
            }

            if (ctrl && !alt && candidateSegment != null) {
                // Adding a new node to the highlighted segment
                // Important: If there are other ways containing the same
                // segment, a node must added to all of that ways.
                Collection<Command> virtualCmds = new LinkedList<>();

                // Creating a new node
                Node virtualNode = new Node(
                    Main.getProjection().eastNorth2latlon(newPointEN)
                );
                virtualCmds.add(new AddCommand(virtualNode));

                // Looking for candidateSegment copies in ways that are
                // referenced
                // by candidateSegment nodes
                List<Way> firstNodeWays = OsmPrimitive.getFilteredList(
                        candidateSegment.getFirstNode().getReferrers(),
                        Way.class);
                List<Way> secondNodeWays = OsmPrimitive.getFilteredList(
                        candidateSegment.getFirstNode().getReferrers(),
                        Way.class);

                Collection<WaySegment> virtualSegments = new LinkedList<>();
                for (Way w : firstNodeWays) {
                    List<Pair<Node, Node>> wpps = w.getNodePairs(true);
                    for (Way w2 : secondNodeWays) {
                        if (!w.equals(w2)) {
                            continue;
                        }
                        // A way is referenced in both nodes.
                        // Checking if there is such segment
                        int i = -1;
                        for (Pair<Node, Node> wpp : wpps) {
                            ++i;
                            boolean ab = wpp.a.equals(candidateSegment.getFirstNode())
                                    && wpp.b.equals(candidateSegment.getSecondNode());
                            boolean ba = wpp.b.equals(candidateSegment.getFirstNode())
                                    && wpp.a.equals(candidateSegment.getSecondNode());
                            if (ab || ba) {
                                virtualSegments.add(new WaySegment(w, i));
                            }
                        }
                    }
                }

                // Adding the node to all segments found
                for (WaySegment virtualSegment : virtualSegments) {
                    Way w = virtualSegment.way;
                    Way wnew = new Way(w);
                    wnew.addNode(virtualSegment.lowerIndex + 1, virtualNode);
                    virtualCmds.add(new ChangeCommand(w, wnew));
                }

                // Finishing the sequence command
                String text = trn("Add a new node to way",
                        "Add a new node to {0} ways",
                        virtualSegments.size(), virtualSegments.size());

                Main.main.undoRedo.add(new SequenceCommand(text, virtualCmds));

            } else if (alt && !ctrl && candidateNode != null) {
                // Deleting the highlighted node

                //check to see if node is in use by more than one object
                List<OsmPrimitive> referrers = candidateNode.getReferrers();
                List<Way> ways = OsmPrimitive.getFilteredList(referrers, Way.class);
                if (referrers.size() != 1 || ways.size() != 1) {
                    // detach node from way
                    final Way newWay = new Way(targetWay);
                    final List<Node> nodes = newWay.getNodes();
                    nodes.remove(candidateNode);
                    newWay.setNodes(nodes);
                    Main.main.undoRedo.add(new ChangeCommand(targetWay, newWay));
                } else if (candidateNode.isTagged()) {
                    JOptionPane.showMessageDialog(Main.parent,
                            tr("Cannot delete node that has tags"),
                            tr("Error"), JOptionPane.ERROR_MESSAGE);
                } else {
                    List<Node> nodeList = new ArrayList<>();
                    nodeList.add(candidateNode);
                    Command deleteCmd = DeleteCommand.delete(getLayerManager().getEditLayer(), nodeList, true);
                    if (deleteCmd != null) {
                        Main.main.undoRedo.add(deleteCmd);
                    }
                }


            } else if (candidateNode != null) {
                // Moving the highlighted node
                EastNorth nodeEN = candidateNode.getEastNorth();

                Node saveCandidateNode = candidateNode;
                Main.main.undoRedo.add(new MoveCommand(candidateNode, newPointEN.east() - nodeEN.east(), newPointEN.north()
                        - nodeEN.north()));
                candidateNode = saveCandidateNode;

            }
        }

        updateCursor();
        updateStatusLine();
        Main.map.mapView.repaint();
    }

    @Override
    public void mouseExited(MouseEvent e) {
        if (!isEnabled()) {
            return;
        }

        if (!dragging) {
            mousePos = null;
        }
        Main.map.mapView.repaint();
    }

    // -------------------------------------------------------------------------
    // Custom methods
    // -------------------------------------------------------------------------
    /**
     * Sets new cursor depending on state, mouse position
     */
    private void updateCursor() {
        if (!isEnabled()) {
            mv.setNewCursor(null, this);
            return;
        }

        if (state == State.selecting) {
            mv.setNewCursor(targetWay == null ? cursorSelect
                    : cursorSelectHover, this);
        } else if (state == State.improving) {
            if (alt && !ctrl) {
                mv.setNewCursor(cursorImproveDelete, this);
            } else if (shift || dragging) {
                if (ctrl) {
                    mv.setNewCursor(cursorImproveAddLock, this);
                } else {
                    mv.setNewCursor(cursorImproveLock, this);
                }
            } else if (ctrl && !alt) {
                mv.setNewCursor(cursorImproveAdd, this);
            } else {
                mv.setNewCursor(cursorImprove, this);
            }
        }
    }

    /**
     * Updates these objects under cursor: targetWay, candidateNode,
     * candidateSegment
     */
    public void updateCursorDependentObjectsIfNeeded() {
        if (state == State.improving && (shift || dragging)
                && !(candidateNode == null && candidateSegment == null)) {
            return;
        }

        if (mousePos == null) {
            candidateNode = null;
            candidateSegment = null;
            return;
        }

        if (state == State.selecting) {
            targetWay = ImproveWayAccuracyHelper.findWay(mv, mousePos);
        } else if (state == State.improving) {
            if (ctrl && !alt) {
                candidateSegment = ImproveWayAccuracyHelper.findCandidateSegment(mv,
                        targetWay, mousePos);
                candidateNode = null;
            } else {
                candidateNode = ImproveWayAccuracyHelper.findCandidateNode(mv,
                        targetWay, mousePos);
                candidateSegment = null;
            }
        }
    }

    /**
     * Switches to Selecting state
     */
    public void startSelecting() {
        state = State.selecting;

        targetWay = null;

        mv.repaint();
        updateStatusLine();
    }

    /**
     * Switches to Improving state
     *
     * @param targetWay Way that is going to be improved
     */
    public void startImproving(Way targetWay) {
        state = State.improving;

        Collection<OsmPrimitive> currentSelection = getLayerManager().getEditDataSet().getSelected();
        if (currentSelection.size() != 1
                || !currentSelection.iterator().next().equals(targetWay)) {
            selectionChangedBlocked = true;
            getLayerManager().getEditDataSet().clearSelection();
            getLayerManager().getEditDataSet().setSelected(targetWay.getPrimitiveId());
            selectionChangedBlocked = false;
        }

        this.targetWay = targetWay;
        this.candidateNode = null;
        this.candidateSegment = null;

        mv.repaint();
        updateStatusLine();
    }

    /**
     * Updates the state according to the current selection. Goes to Improve
     * state if a single way or node is selected. Extracts a way by a node in
     * the second case.
     *
     */
    private void updateStateByCurrentSelection() {
        final List<Node> nodeList = new ArrayList<>();
        final List<Way> wayList = new ArrayList<>();
        final Collection<OsmPrimitive> sel = getLayerManager().getEditDataSet().getSelected();

        // Collecting nodes and ways from the selection
        for (OsmPrimitive p : sel) {
            if (p instanceof Way) {
                wayList.add((Way) p);
            }
            if (p instanceof Node) {
                nodeList.add((Node) p);
            }
        }

        if (wayList.size() == 1) {
            // Starting improving the single selected way
            startImproving(wayList.get(0));
            return;
        } else if (nodeList.size() == 1) {
            // Starting improving the only way of the single selected node
            List<OsmPrimitive> r = nodeList.get(0).getReferrers();
            if (r.size() == 1 && (r.get(0) instanceof Way)) {
                startImproving((Way) r.get(0));
                return;
            }
        }

        // Starting selecting by default
        startSelecting();
    }

    private void resetTimer() {
        if (longKeypressTimer != null) {
            try {
                longKeypressTimer.cancel();
                longKeypressTimer.purge();
            } catch (IllegalStateException exception) {
                Main.debug(exception);
            }
        }
        longKeypressTimer = new Timer();
    }

    @Override
    public void doKeyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_WINDOWS) {
            mod4 = true;
            Main.map.mapView.repaint();
            return;
        }
        if (!helpersShortcut.isEvent(e) && !getShortcut().isEvent(e)) return;
        if (!isExpert) return;
        keypressTime = System.currentTimeMillis();
        helpersEnabledBeforeKeypressed = helpersEnabled;
        if (!helpersEnabled) helpersEnabled = true;
        helpersUseOriginal = true;
        Main.map.mapView.repaint();
    }

    @Override
    public void doKeyReleased(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_WINDOWS) {
            mod4 = false;
            Main.map.mapView.repaint();
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
        Main.map.mapView.repaint();
    }

    @Override
    public void expertChanged(boolean isExpert) {
        this.isExpert = isExpert;
        if (!isExpert && helpersEnabled) {
            helpersEnabled = false;
            Main.map.mapView.repaint();
        }
    }

    @Override
    public void preferenceChanged(PreferenceChangeEvent e) {
        if (!isEnabled()) return;
        if (e.getKey().startsWith("improvewayaccuracy") ||
            e.getKey().startsWith("color.improve.way.accuracy")) {
            readPreferences();
            Main.map.mapView.repaint();
        }
    }
}
