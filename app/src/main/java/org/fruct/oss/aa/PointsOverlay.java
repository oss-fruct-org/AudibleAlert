package org.fruct.oss.aa;

import android.content.ClipData;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.Pair;
import android.view.MotionEvent;


import org.fruct.oss.ikm.poi.PointDesc;

import org.mapsforge.core.model.Point;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.Projection;
import org.osmdroid.views.overlay.Overlay;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class PointsOverlay extends Overlay implements Closeable {
	private final Context context;



	private final static Logger log = LoggerFactory.getLogger(PointsOverlay.class);
	private final static int[] markers = {
			R.drawable.marker_4};

	private Map<String, PointsOverlayItem> items = new HashMap<String, PointsOverlayItem>();
	private int itemSize;

	private final Paint itemBackgroundDragPaint;
	private final Paint itemBackgroundPaint;


	private Rect markerPadding;
	private Drawable markerDrawable;
	private Drawable markerDrawable2;

	private Paint linePaint;

    private Drawable defaultMarker;

	private boolean isEditable = false;

	private transient android.graphics.Point point = new android.graphics.Point();
	private transient android.graphics.Point point2 = new android.graphics.Point();
	private transient android.graphics.Point point3 = new android.graphics.Point();

	private transient Rect rect = new Rect();
	private transient Rect rect2 = new Rect();

	private transient HitResult hitResult = new HitResult();

	private final List<Pair<Long, Long>> relations = new ArrayList<Pair<Long, Long>>();

	public PointsOverlay(Context ctx, List<PointDesc> points) {
		super(ctx);

		this.context = ctx;
		itemSize = 20;

		itemBackgroundDragPaint = new Paint();
		itemBackgroundDragPaint.setColor(0xff1143fa);
		itemBackgroundDragPaint.setStyle(Paint.Style.FILL);

        this.defaultMarker = context.getResources().getDrawable(markers[0]);
		linePaint = new Paint();
		linePaint.setColor(0xff1143fa);
		linePaint.setStyle(Paint.Style.STROKE);
		linePaint.setStrokeWidth(2);
		linePaint.setAntiAlias(true);

		itemBackgroundPaint = new Paint();
		itemBackgroundPaint.setColor(0xffffffff);
		itemBackgroundPaint.setStyle(Paint.Style.FILL);
		itemBackgroundPaint.setTextSize(itemSize);
		itemBackgroundPaint.setAntiAlias(true);
		itemBackgroundPaint.setTextAlign(Paint.Align.CENTER);

        int i =0;
        for(PointDesc p : points){
            addPoint(p, CategoriesManager.getIconForCategory(p.getCategory()), i);
            i++;
        }

        setMarkerIndex();
	}

	@Override
	public void close() {

	}

	private int getMeanColor(Drawable drawable) {
		Bitmap bitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);

		drawable.setBounds(0, 0, bitmap.getWidth(), bitmap.getHeight());
		Canvas canvas = new Canvas(bitmap);
		drawable.draw(canvas);

		int r = 0, g = 0, b = 0;
		Random rand = new Random();

		int c = 0;
		for (int i = 0; i < 20; i++) {
			int x = rand.nextInt(bitmap.getWidth());
			int y = rand.nextInt(bitmap.getHeight());

			int pix = bitmap.getPixel(x, y);

			int a = (pix >>> 24) & 0xff;

			if (a > 200) {
				c++;
				r += (pix >>> 16) & 0xff;
				g += (pix >>> 8) & 0xff;
				b += (pix) & 0xff;
			}
		}

		if (c > 0) {
			r /= c;
			g /= c;
			b /= c;
		}

		bitmap.recycle();
		return (r << 16) + (g << 8) + b + 0xff000000;
	}

	public void setMarkerIndex() {
		markerPadding = new Rect();
       // markerPadding.set(6,6,6,10);

		markerDrawable = context.getResources().getDrawable(markers[0]);


		markerDrawable.getPadding(markerPadding);

		linePaint.setColor(getMeanColor(markerDrawable));
	}

	public void setEditable(boolean isEditable) {
		this.isEditable = isEditable;
	}


	@Override
	protected void draw(Canvas canvas, MapView view, boolean shadow) {
		if (shadow) {
           // log.error("Draw in shadow mode, (not)returning...");
           // return;
        }

		//drawPath(canvas, view);
		drawItems(canvas, view);
	}

	private void drawPath(Canvas canvas, MapView view) {
		for (Pair<Long, Long> line : relations) {
			PointsOverlayItem p1 = items.get(line.first);
			PointsOverlayItem p2 = items.get(line.second);

			if (p1 != null && p2 != null)
				drawLine(canvas, view, p1, p2);
		}
	}

	// TODO: projection points can be performed only if map position changes
	private void drawLine(Canvas canvas, MapView view, PointsOverlayItem item, PointsOverlayItem item2) {
		Projection proj = view.getProjection();
		proj.toPixels(item.point.toPoint(), point);
		proj.toPixels(item2.point.toPoint(), point2);

		canvas.drawLine(point.x, point.y, point2.x, point2.y, linePaint);
	}

	private void drawItems(Canvas canvas, MapView view) {
		int i = 0;
        GeoPoint mc = new GeoPoint(view.getMapCenter().getLatitudeE6(), view.getMapCenter().getLongitudeE6());
        Projection proj = view.getProjection();
        proj.toPixels(mc, point);

		for (PointsOverlayItem item : items.values()) {
			drawItem(canvas, view, item, i++);
		}
	}

	private void drawItem(Canvas canvas, MapView view, PointsOverlayItem item, int index) {
		Projection proj = view.getProjection();

		proj.toPixels(item.point.toPoint(), point);

		Drawable marker = defaultMarker;

       // canvas.rotate(view.getMapOrientation(), point.x, point.y);

		marker.setBounds(point.x - itemSize - markerPadding.left,
				point.y - 2 * itemSize - markerPadding.bottom - markerPadding.top,
				point.x + itemSize + markerPadding.right,
				point.y);
        canvas.rotate(-view.getMapOrientation(), point.x, point.y);
		marker.draw(canvas);

		Rect bounds = marker.getBounds();


		if (item.iconBitmap != null) {
			canvas.drawBitmap(item.iconBitmap, bounds.left + markerPadding.left, bounds.top + markerPadding.top, null);
		} else {
			canvas.drawText(String.valueOf(index), point.x, point.y - itemSize + itemSize / 3, itemBackgroundPaint);
		}
        canvas.rotate(view.getMapOrientation(), point.x, point.y);
	}

	public void addPoint(PointDesc point, Bitmap bitmap, int id) {
		PointsOverlayItem item = new PointsOverlayItem(point, bitmap);

		items.put(point.getName() + " " + id, item);
	}

	public boolean testHit(MotionEvent e, MapView mapView, PointsOverlayItem item, HitResult result) {
		final Projection proj = mapView.getProjection();
		final Rect screenRect = proj.getIntrinsicScreenRect();

		final int x = screenRect.left + (int) e.getX();
		final int y = screenRect.top + (int) e.getY();

		proj.toPixels(item.point.toPoint(), point);

		final int ix = point.x - x;
		final int iy = point.y - y;

		if (result != null) {
			result.item = item;
			result.relHookX = ix;
			result.relHookY = iy;
		}

		return ix >= -itemSize && iy >= 0 && ix <= itemSize && iy <= 2 * itemSize;
	}

	public HitResult testHit(MotionEvent e, MapView mapView) {
		for (PointsOverlayItem item : items.values()) {
			if (testHit(e, mapView, item, hitResult))
				return hitResult;
		}

		return null;
	}

    /*
	@Override
	public boolean onTouchEvent(MotionEvent event, MapView mapView) {
		if (event.getAction() == MotionEvent.ACTION_DOWN) {
			HitResult hitResult = testHit(event, mapView);

			if (hitResult != null) {
				draggingItem = hitResult.item;
				dragRelX = hitResult.relHookX;
				dragRelY = hitResult.relHookY;
				dragStartX = (int) event.getX();
				dragStartY = (int) event.getY();
				dragStarted = false;

				if (draggingItem.data.isEditable())
					setupLongPressHandler(draggingItem);

				mapView.invalidate();
				return false;
			} else {
				return false;
			}
		} else if (event.getAction() == MotionEvent.ACTION_UP && draggingItem != null) {
			if (dragStarted) {
				if (listener != null) {
					listener.pointMoved(draggingItem.data, draggingItem.geoPoint);
				}
			} else {
				if (listener != null) {
					listener.pointPressed(draggingItem.data);
				}
			}

			draggingItem = null;
			mapView.invalidate();
			return false;
		} else if (event.getAction() == MotionEvent.ACTION_MOVE
				&& draggingItem != null
				&& draggingItem.data.isEditable()) {
			final int dx = dragStartX - (int) event.getX();
			final int dy = dragStartY - (int) event.getY();

			if (dragStarted || dx * dx + dy * dy > 32 * 32) {
				dragStarted = true;

			}
			return true;
		} else {
			return false;
		}
	} */


	class PointsOverlayItem {
		PointsOverlayItem(PointDesc point, Bitmap bitmap) {
			this.point = point;
			this.iconBitmap = bitmap;
		}

		PointDesc point;
		Bitmap iconBitmap;

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;

			PointsOverlayItem that = (PointsOverlayItem) o;

			if (!point.equals(that.point)) return false;

			return true;
		}

	}

	class HitResult {
		PointsOverlayItem item;
		int relHookX;
		int relHookY;
	}
}
