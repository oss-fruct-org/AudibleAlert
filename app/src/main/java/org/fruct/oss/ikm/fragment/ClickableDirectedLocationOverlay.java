package org.fruct.oss.ikm.fragment;

import android.content.Context;
import android.graphics.Point;
import android.view.MotionEvent;

import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.Projection;
import org.osmdroid.views.overlay.DirectedLocationOverlay;

public class ClickableDirectedLocationOverlay extends DirectedLocationOverlay {
	public static interface Listener {
		void onClick();
	}
	
	private Point screenPoint;
	
	public ClickableDirectedLocationOverlay(Context ctx, MapView mapView, GeoPoint location, float bearing) {
		super(ctx);
		setLocation(location);
		setBearing(bearing);
	} 
	
	@Override
	public boolean onSingleTapUp(MotionEvent e, MapView mapView) {
		Projection proj = mapView.getProjection();
		screenPoint = proj.toPixels(mLocation, screenPoint);

		float mx = e.getX() + proj.getIntrinsicScreenRect().left;
		float my = e.getY() + proj.getIntrinsicScreenRect().top;

		float dx = screenPoint.x - mx;
		float dy = screenPoint.y - my;
		
		float d2 = dx * dx + dy * dy;
		
		if (d2 < 32 * 32) {
			if (listener != null)
				listener.onClick();
			
			return true;
		} else
			return super.onSingleTapUp(e, mapView);
	}

	private Listener listener;
	
	public void setListener(Listener listener) {
		this.listener = listener;
	}
}
