package org.fruct.oss.ikm;

import android.app.Application;
import android.content.Context;
import android.graphics.Bitmap;
import android.preference.PreferenceManager;

import org.fruct.oss.ikm.utils.Utils;
import org.mapsforge.map.android.graphics.AndroidGraphicFactory;
import org.osmdroid.tileprovider.BitmapPool;
import org.osmdroid.tileprovider.constants.OpenStreetMapTileProviderConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.lang.reflect.Field;
import java.util.LinkedList;
import java.util.WeakHashMap;

import org.fruct.oss.aa.R;

public class App extends Application {
	public interface Clearable {
		void clear();
	}

	private static Logger log = LoggerFactory.getLogger(App.class);

	private static Context context;
	private static App app;

	private static WeakHashMap<Clearable, Object> clearables = new WeakHashMap<Clearable, Object>();

	@Override
	public void onCreate() {
		super.onCreate();
	
		App.context = getApplicationContext();
		App.app = this;
		PreferenceManager.setDefaultValues(App.context, R.xml.preferences, false);
		AndroidGraphicFactory.createInstance(this);
		hackOsmdroidCache();
	}

	private void hackOsmdroidCache() {
		// XXX: hack osmdroid cache path. This should must be removed as soon as osmdroid allows customisation
		File newCacheFile = new File(context.getCacheDir(), "osmdroid");
		File tilesFile = new File(newCacheFile, "tiles");

		newCacheFile.mkdirs();
		tilesFile.mkdirs();

		if (newCacheFile.isDirectory() && tilesFile.isDirectory()) {
			try {
				Class<?> cls = OpenStreetMapTileProviderConstants.class;

				Utils.setFinalStatic(cls.getField("OSMDROID_PATH"), newCacheFile);
				Utils.setFinalStatic(cls.getField("TILE_PATH_BASE"), tilesFile);

				//Utils.setFinalStatic(cls.getField("TILE_MAX_CACHE_SIZE_BYTES"), 10 * 1024 * 1024);
				//Utils.setFinalStatic(cls.getField("TILE_TRIM_CACHE_SIZE_BYTES"), 8 * 1024 * 1024);
			} catch (Exception ex) {
				// Let osmdroid write to external storage
				log.error("Can't hack osmdroid", ex);
			}
		}
	}

	public static Context getContext() {
		if (context == null)
			throw new IllegalStateException("Application not initialized yet");
		return context;
	}

	public static App getInstance() {
		if (app == null)
			throw new IllegalStateException("Application not initialized yet");

		return App.app;
	}

	@Override
	public void onLowMemory() {
		super.onLowMemory();
		log.info("onLowMemory called");
		for (Clearable clearable : clearables.keySet()) {
			clearable.clear();
		}
	}

	public static void addClearable(Clearable clearable) {
		clearables.put(clearable, log /* dummy object */);
	}

	public static void clearBitmapPool() {
		// FIXME: use method clearBitmapPool() when it will be available in maven repository
		BitmapPool pool = BitmapPool.getInstance();

		Field[] fields = BitmapPool.class.getDeclaredFields();
		for (Field field : fields) {
			if (field.getName().equals("mPool")) {
				field.setAccessible(true);
				try {
					LinkedList<Bitmap> mPool = (LinkedList) field.get(pool);
					log.debug("Pool size = " + mPool.size());
					synchronized (mPool) {
						while (!mPool.isEmpty()) {
							Bitmap bitmap = mPool.remove();
							bitmap.recycle();
						}
					}
				} catch (IllegalAccessException e) {
					e.printStackTrace();
					return;
				}

				break;
			}
		}
	}
}
