package com.example.bing;

import android.graphics.Color;
import android.util.Log;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.GoogleMapOptions;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.maps.android.PolyUtil;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;

import java.util.ArrayList;
import java.util.List;

public class MapHandler implements OnMapReadyCallback {
    private static final float DEFAULT_ZOOM = 20;
    private GoogleMap map;
    private Marker marker;
    private float polylineWidthInMeters = 10; // Default polyline width in meters
    private SupportMapFragment mapFragment;
    private Polyline trail;
    private List<LatLng> trailPoints;
    private boolean hasCameraCentered = false;
    private static final float MIN_ZOOM = 18;
    private static final float MAX_ZOOM = 22;
    private TrailDatabaseHelper trailDatabaseHelper;
    private Marker pointAMarker, pointBMarker;
    private Polyline lineBetweenPoints;
    private List<Polyline> parallelLines = new ArrayList<>();
    private boolean isRecordingTrail = false;
    private Polyline highlightedLine;
    private float userDefinedWidth;
    private static final int NUM_OF_PARALLEL_LINES = 5;
    private int highlightedLineIndex = 0;
    public void setRecordingTrail(boolean isRecordingTrail) {
        this.isRecordingTrail = isRecordingTrail;
    }

    public void placePointA() {
        if (pointAMarker != null) {
            pointAMarker.remove();
        }
        LatLng markerPosition = marker.getPosition();
        pointAMarker = map.addMarker(new MarkerOptions().position(markerPosition).title("Point A"));
    }

    public void placePointB(float userDefinedWidth) {
        this.userDefinedWidth = userDefinedWidth;

        if (pointBMarker != null) {
            pointBMarker.remove();
        }
        LatLng markerPosition = marker.getPosition();
        pointBMarker = map.addMarker(new MarkerOptions().position(markerPosition).title("Point B"));

        LatLngBounds mapBounds = map.getProjection().getVisibleRegion().latLngBounds;
        drawParallelLines(pointAMarker.getPosition(), pointBMarker.getPosition(), userDefinedWidth, mapBounds);
    }


    public void startTrail() {
        trailPoints = new ArrayList<>();
        PolylineOptions polylineOptions = new PolylineOptions()
                .color(Color.argb(80, 255, 0, 0))
                .width(10);
        trail = map.addPolyline(polylineOptions);
        initMap(); // Initialize the map when starting the trail
    }
    private void drawParallelLines(LatLng pointA, LatLng pointB, float userDefinedWidth, LatLngBounds visibleBounds) {
        double distance = calculateDistance(pointA, pointB);
        double dx = (pointB.longitude - pointA.longitude);
        double dy = (pointB.latitude - pointA.latitude);

        double extendDistance = 1000; // The distance (in meters) to extend the lines
        double angle = Math.atan2(dy, dx);
        double extendDx = extendDistance * Math.cos(angle) / 111320; // Convert meters to degrees longitude
        double extendDy = extendDistance * Math.sin(angle) / 111320; // Convert meters to degrees latitude

        // Calculate the perpendicular angle and distance
        double perpendicularAngle = angle + Math.PI / 2;
        double dPerpendicularX = userDefinedWidth * Math.cos(perpendicularAngle) / 111320;
        double dPerpendicularY = userDefinedWidth * Math.sin(perpendicularAngle) / 111320;

        int numOfParallelLines = 3; // Number of parallel lines to create on each side of the main line

        // Remove any previously drawn parallel lines
        for (Polyline line : parallelLines) {
            line.remove();
        }
        parallelLines.clear();
        LatLng extendedStart = new LatLng(visibleBounds.southwest.latitude - extendDy, visibleBounds.southwest.longitude - extendDx);
        LatLng extendedEnd = new LatLng(visibleBounds.northeast.latitude + dy + extendDy, visibleBounds.northeast.longitude + dx + extendDx);
        // Draw the central line
        LatLng centralStart = new LatLng(pointA.latitude - extendDy, pointA.longitude - extendDx);
        LatLng centralEnd = new LatLng(pointA.latitude + dy + extendDy, pointA.longitude + dx + extendDx);
        Polyline centralLine = map.addPolyline(new PolylineOptions()
                .add(centralStart, centralEnd)
                .width(3)
                .color(Color.BLACK));
        parallelLines.add(centralLine);

        // Draw the remaining parallel lines
        for (int j = -numOfParallelLines; j <= numOfParallelLines; j++) {
            if (j == 0) continue; // Skip the central line

            LatLng start = new LatLng(pointA.latitude - extendDy + j * dPerpendicularY,
                    pointA.longitude - extendDx + j * dPerpendicularX);
            LatLng end = new LatLng(pointA.latitude + dy + extendDy + j * dPerpendicularY,
                    pointA.longitude + dx + extendDx + j * dPerpendicularX);

            Polyline line = map.addPolyline(new PolylineOptions()
                    .add(start, end)
                    .width(3) // Width of the line (make it thin)
                    .color(Color.BLACK)); // Set the color to black
            parallelLines.add(line);
        }

        // Remove markers A and B after drawing the lines
        if (pointAMarker != null) {
            pointAMarker.remove();
        }
        if (pointBMarker != null) {
            pointBMarker.remove();
        }
    }







    public void setMapTypeNormal() {
        if (map != null) {
            map.setMapType(GoogleMap.MAP_TYPE_NORMAL);
        }
    }

    public void setMapTypeSatellite() {
        if (map != null) {
            map.setMapType(GoogleMap.MAP_TYPE_SATELLITE);
        }
    }

    private double calculateDistance(LatLng pointA, LatLng pointB) {
        double earthRadius = 6371000; // meters
        double dLat = Math.toRadians(pointB.latitude - pointA.latitude);
        double dLng = Math.toRadians(pointB.longitude - pointA.longitude);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(pointA.latitude)) * Math.cos(Math.toRadians(pointB.latitude)) *
                        Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return earthRadius * c;
    }

    public void stopTrail() {
        if (trail != null) {
            trail.remove();
            trail = null;
            trailPoints = null;
        }
    }

    public void clearTrail() {
        if (trail != null) {
            trail.remove();
            if (trailPoints != null) {
                trailPoints.clear();
            }
        }
    }

    public void drawTrail(List<LatLng> points) {
        if (trail != null) {
            trail.remove();
        }

        float zoomLevel = map.getCameraPosition().zoom;
        float metersPerPixel = (float) ((156543.03392 * Math.cos(map.getCameraPosition().target.latitude * Math.PI / 180)) / Math.pow(2, zoomLevel));
        float polylineWidthInPixels = polylineWidthInMeters / metersPerPixel;

        PolylineOptions polylineOptions = new PolylineOptions()
                .color(Color.argb(80, 0, 0, 255))
                .width(polylineWidthInPixels);
        trail = map.addPolyline(polylineOptions);
        trail.setPoints(points);
    }




    public MapHandler(FragmentActivity activity, int mapFragmentId, GoogleMapOptions mapOptions) {
        mapFragment = SupportMapFragment.newInstance(mapOptions);

        // Obtenha a instância SupportMapFragment associada ao ID especificado do FragmentManager da atividade
        mapFragment = SupportMapFragment.newInstance(mapOptions);
        activity.getSupportFragmentManager()
                .beginTransaction()
                .replace(mapFragmentId, mapFragment)
                .commit();


        // Registra o OnMapReadyCallback para ser avisado quando o mapa estiver pronto para ser usado
        mapFragment.getMapAsync(this);
        trailDatabaseHelper = new TrailDatabaseHelper(activity);

    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        // Called when the map is ready to be used, initializes the mMap object
        map = googleMap;
        setupMapListeners();

    }
    private void initMap() {
        map.setMinZoomPreference(MIN_ZOOM);
        map.setMaxZoomPreference(MAX_ZOOM);
        setupPolyline();
    }
    public void updateMarker(double latitude, double longitude) {
        LatLng latLng = new LatLng(latitude, longitude);


        if (map == null) {
            // Log a message or handle the case when the map is not ready yet.
            Log.d("MapHandler", "updateMarker: map is not ready yet");
            return;
        }

        if (marker != null) {
            marker.remove();
        }

        marker = map.addMarker(new MarkerOptions().position(latLng).title("Current Location"));

        if (!hasCameraCentered) {
            centerCameraOnMarker();
            hasCameraCentered = true;
        }

        if (trailPoints != null) {
            trailPoints.add(latLng);
            trail.setPoints(trailPoints);
        }

        trailDatabaseHelper.addPoint(latitude, longitude);
        highlightClosestLine();

    }

    public void centerCameraOnMarker() {
        if (marker != null) {
            LatLng currentPosition = marker.getPosition();
            if (map != null) {
                map.animateCamera(CameraUpdateFactory.newLatLngZoom(currentPosition, DEFAULT_ZOOM));
            }
        }
    }
    private void setupPolyline() {
        PolylineOptions polylineOptions = new PolylineOptions()
                .color(Color.argb(80, 0, 0, 255)) // Set the color to semi-transparent blue
                .width(10);
        Log.d("color", "color changed: " );

        trail = map.addPolyline(polylineOptions);
    }


    public void setPolylineWidthInMeters(float widthInMeters) {
        this.polylineWidthInMeters = widthInMeters;
        if (trail != null ) {
            trail.setWidth(polylineWidthInMeters / (float) Math.pow(2, DEFAULT_ZOOM - map.getCameraPosition().zoom));
        }
    }

    private void setupMapListeners() {
        map.setOnCameraMoveListener(new GoogleMap.OnCameraMoveListener() {
            @Override
            public void onCameraMove() {
                float currentZoom = map.getCameraPosition().zoom;

            }
        });
        map.setOnCameraIdleListener(new GoogleMap.OnCameraIdleListener() {
            @Override
            public void onCameraIdle() {
                LatLngBounds visibleBounds = map.getProjection().getVisibleRegion().latLngBounds;
                List<LatLng> visiblePoints = trailDatabaseHelper.getPoints(visibleBounds);
                drawTrail(visiblePoints);
                if (pointAMarker != null && pointBMarker != null) {
                    drawParallelLines(pointAMarker.getPosition(), pointBMarker.getPosition(), userDefinedWidth, visibleBounds);
                }
            }
        });

    }

    private void highlightClosestLine() {
        if (marker == null || parallelLines.isEmpty()) {
            return;
        }

        Polyline closestLine = null;
        double minDistance = Double.MAX_VALUE;
        LatLng currentPosition = marker.getPosition();
        int closestLineIndex = 0;

        for (int i = 0; i < parallelLines.size(); i++) {
            Polyline line = parallelLines.get(i);
            List<LatLng> points = line.getPoints();
            if (points.size() != 2) {
                continue;
            }
            LatLng startPoint = points.get(0);
            LatLng endPoint = points.get(1);

            double distance = PolyUtil.distanceToLine(currentPosition, startPoint, endPoint);
            if (distance < minDistance) {
                minDistance = distance;
                closestLine = line;
                closestLineIndex = i;
            }
        }

        if (closestLine != null) {
            // Reset the color of the previously highlighted line
            if (highlightedLine != null) {
                highlightedLine.setColor(Color.BLACK);
            }

            // Highlight the closest line
            closestLine.setColor(Color.RED);
            highlightedLine = closestLine;

            if (closestLineIndex == 0 || closestLineIndex == parallelLines.size() - 1) {
                // The marker is on the leftmost or rightmost line, update the lines accordingly
                updateParallelLines(closestLineIndex);
            }
        }

        if (closestLineIndex < parallelLines.size() - 1) {
            // If the current highlighted line is not the last line, update the color of the next line
            Polyline nextLine = parallelLines.get(closestLineIndex + 1);
            nextLine.setColor(Color.YELLOW);
        }
    }
    private void updateParallelLines(int index) {
        if (index == 0 || index == parallelLines.size() - 1) {
            Polyline newLine = null;

            if (index == 0 && parallelLines.size() > 1) {
                Polyline firstLine = parallelLines.get(0);
                List<LatLng> firstLinePoints = firstLine.getPoints();

                double dx = firstLinePoints.get(1).longitude - firstLinePoints.get(0).longitude;
                double dy = firstLinePoints.get(1).latitude - firstLinePoints.get(0).latitude;
                LatLng newStartPoint = new LatLng(firstLinePoints.get(0).latitude - dy, firstLinePoints.get(0).longitude - dx);
                LatLng newEndPoint = new LatLng(firstLinePoints.get(1).latitude - dy, firstLinePoints.get(1).longitude - dx);

                newLine = map.addPolyline(new PolylineOptions()
                        .add(newStartPoint, newEndPoint)
                        .width(3)
                        .color(Color.BLACK));
            } else if (index == parallelLines.size() - 1 && parallelLines.size() > 1) {
                Polyline lastLine = parallelLines.get(parallelLines.size() - 1);
                List<LatLng> lastLinePoints = lastLine.getPoints();

                double dx = lastLinePoints.get(1).longitude - lastLinePoints.get(0).longitude;
                double dy = lastLinePoints.get(1).latitude - lastLinePoints.get(0).latitude;
                LatLng newStartPoint = new LatLng(lastLinePoints.get(0).latitude + dy, lastLinePoints.get(0).longitude + dx);
                LatLng newEndPoint = new LatLng(lastLinePoints.get(1).latitude + dy, lastLinePoints.get(1).longitude + dx);

                newLine = map.addPolyline(new PolylineOptions()
                        .add(newStartPoint, newEndPoint)
                        .width(3)
                        .color(Color.BLACK));
            }

            // Remove the line at the opposite end
            int removeIndex = index == 0 ? parallelLines.size() - 1 : 0;
            Polyline lineToRemove = parallelLines.get(removeIndex);
            lineToRemove.remove();
            parallelLines.remove(removeIndex);

            if (newLine != null) {
                if (index == 0) {
                    parallelLines.add(0, newLine);
                } else {
                    parallelLines.add(newLine);
                }
            }
        }
    }





}