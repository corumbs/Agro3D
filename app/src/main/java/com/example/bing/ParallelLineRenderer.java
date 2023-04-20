package com.example.bing;
import android.graphics.Color;

import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.maps.android.PolyUtil;

import java.util.ArrayList;
import java.util.List;

public class ParallelLineRenderer {

    private static final float LINE_WIDTH = 3;
    private static final int LINE_COLOR = Color.BLACK;

    private float userDefinedWidth;

    public ParallelLineRenderer(float userDefinedWidth) {
        this.userDefinedWidth = userDefinedWidth;
    }

    public List<PolylineOptions> getParallelLines(List<LatLng> points) {
        List<PolylineOptions> parallelLines = new ArrayList<>();

        for (int i = 0; i < points.size() - 1; i++) {
            LatLng pointA = points.get(i);
            LatLng pointB = points.get(i + 1);

            double dx = (pointB.longitude - pointA.longitude);
            double dy = (pointB.latitude - pointA.latitude);
            double angle = Math.atan2(dy, dx);
            double perpendicularAngle = angle + Math.PI / 2;

            double dPerpendicularX = userDefinedWidth * Math.cos(perpendicularAngle) / 111320;
            double dPerpendicularY = userDefinedWidth * Math.sin(perpendicularAngle) / 111320;

            LatLng line1Start = new LatLng(pointA.latitude - dPerpendicularY, pointA.longitude - dPerpendicularX);
            LatLng line1End = new LatLng(pointB.latitude - dPerpendicularY, pointB.longitude - dPerpendicularX);

            LatLng line2Start = new LatLng(pointA.latitude + dPerpendicularY, pointA.longitude + dPerpendicularX);
            LatLng line2End = new LatLng(pointB.latitude + dPerpendicularY, pointB.longitude + dPerpendicularX);

            parallelLines.add(new PolylineOptions()
                    .add(line1Start, line1End)
                    .width(LINE_WIDTH)
                    .color(LINE_COLOR));

            parallelLines.add(new PolylineOptions()
                    .add(line2Start, line2End)
                    .width(LINE_WIDTH)
                    .color(LINE_COLOR));
        }

        return parallelLines;
    }

    public PolylineOptions getHighlightedLine(List<LatLng> points, LatLng currentPosition) {
        double minDistance = Double.MAX_VALUE;
        LatLng startPoint = null;
        LatLng endPoint = null;

        for (int i = 0; i < points.size() - 1; i++) {
            LatLng pointA = points.get(i);
            LatLng pointB = points.get(i + 1);

            double distance = PolyUtil.distanceToLine(currentPosition, pointA, pointB);
            if (distance < minDistance) {
                minDistance = distance;
                startPoint = pointA;
                endPoint = pointB;
            }
        }

        if (startPoint != null && endPoint != null) {
            return new PolylineOptions()
                    .add(startPoint, endPoint)
                    .width(LINE_WIDTH * 2)
                    .color(Color.RED);
        } else {
            return null;
        }
    }
}
