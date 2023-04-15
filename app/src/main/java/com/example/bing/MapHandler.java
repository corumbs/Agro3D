package com.example.bing;

import android.content.Context;
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
        if (pointBMarker != null) {
            pointBMarker.remove();
        }
        LatLng markerPosition = marker.getPosition();
        pointBMarker = map.addMarker(new MarkerOptions().position(markerPosition).title("Point B"));

        drawParallelLines(pointAMarker.getPosition(), pointBMarker.getPosition(), userDefinedWidth);
    }






    public void startTrail() {
        trailPoints = new ArrayList<>();
        PolylineOptions polylineOptions = new PolylineOptions()
                .color(Color.argb(80, 255, 0, 0))
                .width(10);
        trail = map.addPolyline(polylineOptions);
        initMap(); // Initialize the map when starting the trail
    }
    private void drawParallelLines(LatLng pointA, LatLng pointB, float userDefinedWidth) {
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



    private PolylineOptions createParallelLine(PolylineOptions originalLine, double parallelDistance) {
        List<LatLng> originalPoints = originalLine.getPoints();
        LatLng startPoint = originalPoints.get(0);
        LatLng endPoint = originalPoints.get(1);

        double angle = Math.atan2(endPoint.latitude - startPoint.latitude, endPoint.longitude - startPoint.longitude);
        double offsetX = parallelDistance * Math.sin(angle);
        double offsetY = parallelDistance * Math.cos(angle);

        LatLng parallelStart = new LatLng(startPoint.latitude + offsetX, startPoint.longitude - offsetY);
        LatLng parallelEnd = new LatLng(endPoint.latitude + offsetX, endPoint.longitude - offsetY);

        return new PolylineOptions().add(parallelStart).add(parallelEnd).color(originalLine.getColor()).width(originalLine.getWidth());
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
                if (trail != null) {
                    trail.setWidth(polylineWidthInMeters / (float) Math.pow(2, DEFAULT_ZOOM - currentZoom));
                }
            }
        });
        map.setOnCameraIdleListener(new GoogleMap.OnCameraIdleListener() {
            @Override
            public void onCameraIdle() {
                LatLngBounds visibleBounds = map.getProjection().getVisibleRegion().latLngBounds;
                List<LatLng> visiblePoints = trailDatabaseHelper.getPoints(visibleBounds);
                drawTrail(visiblePoints);
            }
        });
    }


}