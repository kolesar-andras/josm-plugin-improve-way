package org.openstreetmap.josm.plugins.improveway;

import java.awt.FontMetrics;
import java.awt.Graphics2D;

public class ImproveWayHelper {
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
            (int) (x + (distance + (labelWidth - labelHeight) / 2) * Math.cos(heading) - labelWidth / 2),
            (int) (y + distance * Math.sin(heading) + labelHeight / 2)
        );
    }

    // returns node index for closed ways using possibly under/overflowed index
    // returns -1 if not closed and out of range
    protected static int fixIndex(int count, boolean closed, int index) {
        if (index >= 0 && index < count) return index;
        if (!closed) return -1;
        while (index < 0) index += count;
        while (index >= count) index -= count;
        return index;
    }

    protected static double fixHeading(double heading) {
        while (heading < -180) heading += 360;
        while (heading > 180) heading -= 360;
        return heading;
    }
}
