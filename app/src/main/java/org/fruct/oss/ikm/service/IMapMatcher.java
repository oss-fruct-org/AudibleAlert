package org.fruct.oss.ikm.service;

import android.location.Location;

public interface IMapMatcher {
	boolean updateLocation(Location location);
	Location getMatchedLocation();
	int getMatchedNode();
}
