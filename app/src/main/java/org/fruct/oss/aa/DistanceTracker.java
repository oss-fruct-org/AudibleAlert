package org.fruct.oss.aa;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.location.Location;
import android.preference.PreferenceManager;

import org.fruct.oss.ikm.App;
import org.fruct.oss.ikm.poi.PointDesc;
import org.fruct.oss.ikm.poi.PointsManager;
import org.fruct.oss.ikm.service.LocationReceiver;
import org.osmdroid.util.GeoPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DistanceTracker implements LocationReceiver.Listener {
    private final static Logger log = LoggerFactory.getLogger(DistanceTracker.class);

    private Set<PointDesc> pointsInRange = new HashSet<PointDesc>();

    private LocationReceiver locationReceiver;
    private List<Listener> listeners = new ArrayList<Listener>();
    private int radius;

    private final List<PointDesc> points = new ArrayList<PointDesc>();
    private Cursor currentCursor;

    private SharedPreferences pref;
    //test
    private float minDistance = 100000f;
    private int CATEGORY_RADIUS = 30;

    private static Location lastLocation;
    private static GeoPoint lastPoint;
    private Context context;

    public DistanceTracker(LocationReceiver locationReceiver) {
        this.locationReceiver = locationReceiver;

        pref = PreferenceManager.getDefaultSharedPreferences(App.getContext());

    }

    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    public void start() {
        log.debug("DistanceTracker start");

        locationReceiver.addListener(this);
        locationReceiver.start();
    }

    public void stop() {
        log.debug("DistanceTracker stop");

        locationReceiver.stop();

    }

    @Override
    public void newLocation(Location location) {
        if(points.size() == 0)
        updatePoints();
        lastLocation = location;
        ArrayList<PointDesc> outRange = new ArrayList<PointDesc>();
        ArrayList<PointDesc> inRange = new ArrayList<PointDesc>();

        lastPoint = new GeoPoint(location);
        for (PointDesc point : points) {
            boolean isPointInRange = pointsInRange.contains(point);

            float distanceMeters = point.toPoint().distanceTo(lastPoint);

            if(distanceMeters < minDistance)
                minDistance = distanceMeters;

            radius = CategoriesManager.getRadiusForCategory(point.getCategory());
            if (distanceMeters < radius && !isPointInRange) {
                inRange.add(point);
            } else if (distanceMeters >= radius && isPointInRange) {
                outRange.add(point);
            }
        }

        for (PointDesc point : outRange) {
            pointsInRange.remove(point);
            notifyPointOutRange(point);
        }

        for (PointDesc point : inRange) {
            pointsInRange.add(point);
            notifyPointInRange(point);
        }
    }

    private void notifyPointInRange(PointDesc point) {
        for (Listener listener : listeners)
            listener.pointInRange(point);
    }

    private void notifyPointOutRange(PointDesc point) {
        for (Listener listener : listeners)
            listener.pointOutRange(point);
    }

    public void setRadius(int radius) {
        this.radius = radius;
    }

    public List<PointDesc> getPointsInRange() {
        return new ArrayList<PointDesc>(pointsInRange);
    }


    public interface Listener {
        void pointInRange(PointDesc point);
        void pointOutRange(PointDesc point);
    }

    public void updatePoints(){
        List<PointDesc> filteredPoints = PointsManager.getInstance().getFilteredPoints();
        points.clear();

        for (PointDesc point : filteredPoints) {
            points.add(point);
        }
    }

    public static boolean isInRange(PointDesc point, int rad){
        if(lastPoint == null)
            return false;

        int distance = point.toPoint().distanceTo(lastPoint);

        if(distance < rad)
            return true;
        else
            return false;
    }

    public static int getDistanceTo(PointDesc point){
        if(lastPoint == null)
            return 0;

        return point.toPoint().distanceTo(lastPoint);
    }


}
