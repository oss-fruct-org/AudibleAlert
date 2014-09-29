package org.fruct.oss.ikm.poi;

import org.fruct.oss.aa.DistanceTracker;

public class DistanceFilter implements Filter {

    private int distance;
    private String name;
    private boolean active = true;

    public DistanceFilter(int distance){
        this.distance = distance;
        this.name = "< " + distance + "m";
    }
    @Override
    public boolean accepts(PointDesc point) {
        return DistanceTracker.isInRange(point,distance);

    }

    @Override
    public String getString() {
        return name;
    }

    @Override
    public boolean isActive() {
        return active;
    }

    @Override
    public void setActive(boolean active) {
        this.active = active;
    }
}
