package org.fruct.oss.ikm.service;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.location.Location;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.IBinder;
import android.os.Parcelable;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.LocalBroadcastManager;

import com.graphhopper.util.PointList;
import com.graphhopper.util.Unzipper;

import org.fruct.oss.aa.AudioManager;
import org.fruct.oss.aa.AudioPlayer;
import org.fruct.oss.aa.DistanceTracker;
import org.fruct.oss.aa.R;
import org.fruct.oss.ikm.DataService;
import org.fruct.oss.ikm.MainActivity;
import org.fruct.oss.ikm.SettingsActivity;
import org.fruct.oss.ikm.poi.Filter;
import org.fruct.oss.ikm.poi.PointDesc;
import org.fruct.oss.ikm.poi.PointsManager;
import org.fruct.oss.ikm.poi.PointsManager.PointsListener;
import org.fruct.oss.ikm.service.LocationReceiver.Listener;
import org.fruct.oss.ikm.storage.ContentItem;
import org.fruct.oss.ikm.storage.RemoteContentService;
import org.fruct.oss.ikm.utils.Utils;
import org.fruct.oss.ikm.utils.bind.BindHelper;
import org.fruct.oss.ikm.utils.bind.BindSetter;
import org.osmdroid.util.GeoPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

public class DirectionService extends Service implements PointsListener, DistanceTracker.Listener,
		 OnSharedPreferenceChangeListener, Listener, DataService.DataListener {
	private static Logger log = LoggerFactory.getLogger(DirectionService.class);

	// Extras
	public static final String DIRECTIONS_RESULT = "org.fruct.oss.ikm.GET_DIRECTIONS_RESULT";
	public static final String CENTER = "org.fruct.oss.ikm.CENTER";
	public static final String LOCATION = "org.fruct.oss.ikm.LOCATION";
	public static final String MATCHED_LOCATION = "org.fruct.oss.ikm.MATCHED_LOCATION";
	public static final String PATH = "org.fruct.oss.ikm.PATH";

	// Broadcasts
	public static final String DIRECTIONS_READY = "org.fruct.oss.ikm.GET_DIRECTIONS_READY";
	public static final String LOCATION_CHANGED = "org.fruct.oss.ikm.LOCATION_CHANGED";
	public static final String PATH_READY = "org.fruct.oss.ikm.PATH_READY";

	private static final String MOCK_PROVIDER = "mock-provider";

	public static final String PREF_NAVIGATION_DIR = "navigation-dir";

    public static final String BC_ACTION_POINT_IN_RANGE = "BC_ACTION_POINT_IN_RANGE";
    public static final String BC_ACTION_POINT_OUT_RANGE = "BC_ACTION_POINT_IOUTN_RANGE";
    public static final String BC_ACTION_NEW_LOCATION = "BC_ACTION_NEW_LOCATION";

    public static final String ARG_POINT = "ARG_POINT";
    public static final String ARG_LOCATION = "ARG_LOCATION";

    public static final String ACTION_WAKE = "org.fruct.oss.audioguide.TrackingService.ACTION_WAKE";

    public static final String ACTION_START_TRACKING = "org.fruct.oss.audioguide.TrackingService.ACTION_START_TRACKING";
    public static final String ACTION_STOP_TRACKING = "org.fruct.oss.audioguide.TrackingService.ACTION_STOP_TRACKING";

    public static final String ACTION_PLAY = "org.fruct.oss.audioguide.TrackingService.ACTION_PLAY";
    public static final String ACTION_STOP = "org.fruct.oss.audioguide.TrackingService.ACTION_STOP";

    public static final String ACTION_PAUSE = "org.fruct.oss.audioguide.TrackingService.ACTION_PAUSE";
    public static final String ACTION_UNPAUSE = "org.fruct.oss.audioguide.TrackingService.ACTION_UNPAUSE";
    public static final String ACTION_SEEK = "org.fruct.oss.audioguide.TrackingService.ACTION_SEEK";

    public static final String ACTION_BACKGROUND = "org.fruct.oss.audioguide.TrackingService.ACTION_BACKGROUND";
    public static final String ACTION_FOREGROUND = "org.fruct.oss.audioguide.TrackingService.ACTION_FOREGROUND";

    public static final String PREF_IS_TRACKING_MODE = "pref-tracking-service-is-tracking-mode";
    public static final String PREF_IS_BACKGROUND_MODE = "pref-tracking-service-is-background-mode";

	private RemoteContentService remoteContent;

	private final Object dataServiceMutex = new Object();
	private DataService dataService;

	private final Object dirManagerMutex = new Object();
	private DirectionManager dirManager;

	private IBinder binder = new DirectionBinder();

	private LocationReceiver locationReceiver;

	// Last query result
	private ArrayList<Direction> lastResultDirections;
	private GeoPoint lastResultCenter;
	private Location lastResultLocation;

	private Location lastLocation;
	private Location lastMatchedLocation;
	private int lastMatchedNode;

	private GHRouting routing;
	private IMapMatcher mapMatcher;

	private String ghPath;
	private String navigationDir;
	private String currentStoragePath;

	private LocationIndexCache locationIndexCache;

	private SharedPreferences pref;
	private AsyncTask<String, Void, Void> extractTask;
	private int radius;

    private boolean isTrackingMode = false;
    private boolean isBackgroundMode = false;

    private static int NOTIFICATION_ID = 1;

    private DistanceTracker distanceTracker;

    private AudioManager audioManager;

    @Override
    public void pointOutRange(PointDesc point) {

    }

    public class DirectionBinder extends android.os.Binder {
		public DirectionService getService() {
			return DirectionService.this;
		}
	}

	@Override
	public IBinder onBind(Intent intent) {
		return binder;
	}

	@Override
	public void onCreate() {
		super.onCreate();

		File internalDir = getFilesDir();
		assert internalDir != null;

		pref = PreferenceManager.getDefaultSharedPreferences(this);

        
		BindHelper.autoBind(this, this);

		locationReceiver = new LocationReceiver(this);
		locationIndexCache = new LocationIndexCache(this);

        distanceTracker = new DistanceTracker(locationReceiver);
        distanceTracker.addListener(this);



        audioManager = new AudioManager(this);

		PointsManager.getInstance().addListener(this);
		pref.registerOnSharedPreferenceChangeListener(this);

		log.debug("DirectionService created");
	}

	@BindSetter
	public void setDataService(DataService service) {
		synchronized (dataServiceMutex) {
			if (service != null) {
				service.addDataListener(this);
			} else if (dataService != null) {
				dataService.removeDataListener(this);
			}

			dataService = service;

			if (dataService != null && remoteContent != null) {
				updateDirectionsManager();
			}
		}
	}

	@BindSetter
	public void setRemoteContentService(RemoteContentService service) {
		remoteContent = service;

		synchronized (dataServiceMutex) {
			if (dataService != null && remoteContent != null) {
				updateDirectionsManager();
			}
		}
	}

	@Override
	public void dataPathChanged(String newDataPath) {
		updateDirectionsManager();
	}

	@Override
	public int getPriority() {
		return 1;
	}

	@Override
	public void onDestroy() {
		log.debug("DirectionService destroyed");

		if (locationReceiver != null && locationReceiver.isStarted()) {
			locationReceiver.stop();
		}

		pref.unregisterOnSharedPreferenceChangeListener(this);

		PointsManager.getInstance().removeListener(this);

		if (extractTask != null) {
			extractTask.cancel(true);
		}

		new AsyncTask<Void, Void, Void>() {
			@Override
			protected Void doInBackground(Void... params) {
				synchronized (dirManagerMutex) {
					if (dirManager != null) {
						dirManager.closeSync();
						dirManager = null;
						locationIndexCache.close();
					}
				}
				return null;
			}
		}.execute();

		synchronized (dataServiceMutex) {
			if (dataService != null) {
				dataService.removeDataListener(this);
			}
		}

		BindHelper.autoUnbind(this, this);

        audioManager.onDestroy();
        log.trace("DirectionService stopped");
	}

	private GHRouting createRouting() {
		String navigationPath = ghPath + "/" + navigationDir;

		if (new File(navigationPath + "/nodes").exists()) {
			OneToManyRouting routing = new OneToManyRouting(navigationPath, locationIndexCache);

			// Apply encoder from preferences
			routing.setEncoder(pref.getString(SettingsActivity.VEHICLE, "CAR"));

			return routing;
		} else
			return null;
	}

	private void updateDirectionsManager() {
		new AsyncTask<Void, Void, Void>() {
			@Override
			protected Void doInBackground(Void... params) {
				currentStoragePath = null;
				synchronized (dataServiceMutex) {
					if (dataService != null) {
						currentStoragePath = dataService.getDataPath();
					}
				}

				if (currentStoragePath != null) {
					//asyncUpdateDirectionsManager();
				}
				return null;
			}

			@Override
			protected void onPostExecute(Void aVoid) {
				if (dataService != null)
					dataService.dataListenerReady();

				startTracking();
			}
		}.execute();
	}


	@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
	public void fakeLocation(GeoPoint current) {
		if (current == null)
			return;

		if (locationReceiver.isStarted()) {
			float bearing;
			float speed;

			if (lastLocation != null) {
				GeoPoint last = new GeoPoint(lastLocation);
				bearing = (float) last.bearingTo(current);

				speed = (float) last.distanceTo(current) / ((System.currentTimeMillis() - lastLocation.getTime()) / 1000);

				log.debug("fakeLocation last = " + last + ", current = " + current + ", bearing = " + bearing);
			} else {
				bearing = 0;
				speed = 0;
				log.debug("fakeLocation current = " + current + ", bearing = " + bearing);
			}

			Location location = new Location(MOCK_PROVIDER);
			location.setLatitude(current.getLatitudeE6() / 1e6);
			location.setLongitude(current.getLongitudeE6() / 1e6);
			location.setTime(System.currentTimeMillis());
			location.setBearing(bearing);
			location.setAccuracy(1);
			location.setSpeed(speed);

			if (Build.VERSION.SDK_INT > 17) {
				location.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
			}

			locationReceiver.mockLocation(location);
		}
	}

	public void startTracking() {
		if (locationReceiver.isStarted()) {
			if (lastMatchedLocation != null) {
				notifyLocationChanged(lastMatchedLocation, lastMatchedLocation);
			} else {
				notifyLocationChanged(lastLocation, lastLocation);
			}

			if (lastResultDirections != null)
				sendResult(lastResultDirections, lastResultCenter, lastResultLocation);

			return;
		}

		locationReceiver.addListener(this);
		locationReceiver.start();
        distanceTracker.start();
        distanceTracker.updatePoints();
        audioManager.startPlaying();
		locationReceiver.sendLastLocation();
	}

	@Override
	public void newLocation(final Location location) {
		new AsyncTask<Void, Void, Void>() {
			@Override
			protected Void doInBackground(Void... params) {
				asyncNewLocation(location);
				return null;
			}
		}.execute();
	}

	public void asyncNewLocation(Location location) {
		lastLocation = location;

		boolean autoRegion = pref.getBoolean(SettingsActivity.AUTOREGION, true);

		if (routing != null && !routing.isInner(location.getLatitude(), location.getLongitude())) {
			if (autoRegion)
				remoteContent.activateRegionByLocation(location.getLatitude(), location.getLongitude());
			return;
		} else if (mapMatcher != null) {
			mapMatcher.updateLocation(location);

			lastMatchedLocation = mapMatcher.getMatchedLocation();
			lastMatchedNode = mapMatcher.getMatchedNode();
		} else {
			notifyLocationChanged(location, location);
		}

		if (lastMatchedLocation != null) {
			notifyLocationChanged(lastMatchedLocation, lastMatchedLocation);

			synchronized (dirManagerMutex) {
				if (dirManager != null) {
					dirManager.updateLocation(lastMatchedLocation, lastMatchedNode);
					dirManager.calculateForPoints(PointsManager.getInstance().getFilteredPoints());
				}
			}
		} else if (remoteContent != null) {
			if (autoRegion)
				remoteContent.activateRegionByLocation(location.getLatitude(), location.getLongitude());
		}
	}

	/**
	 * Disable network and gps location provider for testing purposes.
	 */
	public void disableRealLocation() {
		if (!locationReceiver.isStarted()) {
			locationReceiver.disableRealLocation();
		} else {
			throw new IllegalStateException("Can't disable real location on running LocationReceiver");
		}
	}

	private void notifyLocationChanged(Location location, Location matchedLocation) {
		if (location == null || matchedLocation == null)
			return;

		Intent intent = new Intent(LOCATION_CHANGED);
		intent.putExtra(LOCATION, location);
		intent.putExtra(MATCHED_LOCATION, matchedLocation);

		LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
	}

	public void findPath(GeoPoint to) {
		if (dirManager != null) {
			dirManager.findPath(to);
		}
	}

	@Override
	public void filterStateChanged(List<PointDesc> newList) {
		if (dirManager != null) {
			dirManager.calculateForPoints(newList);
		}
	}

	private String extractArchive(String path) {
		log.info("Extracting archive {}", path);

		File file = path == null ? null : new File(path);
		if (file == null || !file.exists() || !file.canRead())
			return null;

		try {
			String uuid = UUID.randomUUID().toString();
			String newPath = ghPath + "/ghdata" + uuid;

			new Unzipper().unzip(path, newPath, false);
			log.info("Archive file {} successfully extracted", path);

			return "ghdata" + uuid;
		} catch (IOException e) {
			log.warn("Can not extract archive file {}", path);
			return null;
		}
	}

	private void handleNavigationDataChange(String archiveName) {
		if (archiveName == null)
			return;

		ContentItem contentItem = remoteContent.getContentItem(archiveName);
		String path = remoteContent.getFilePath(contentItem);

		extractTask = new AsyncTask<String, Void, Void>() {
			@Override
			protected Void doInBackground(String... params) {
				String oldNavigationDir = pref.getString(PREF_NAVIGATION_DIR, null);

				String path = params[0];
				String newNavigationDir = extractArchive(path);

				if (newNavigationDir == null) {
					return null;
				}

				navigationDir = newNavigationDir;
				pref.edit().putString(PREF_NAVIGATION_DIR, navigationDir).apply();

				synchronized (dirManagerMutex) {
					//asyncUpdateDirectionsManager();
				}

				if (oldNavigationDir != null) {
					String oldNavigationPath = ghPath + "/" + oldNavigationDir;
					Utils.deleteDir(new File(oldNavigationPath));
				}
				return null;
			}
		};

		extractTask.execute(path);
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences pref,
										  String key) {
		log.debug("DirectionService.onSharedPreferenceChanged");
		if (key.equals(SettingsActivity.NEAREST_POINTS)) {
			List<PointDesc> points = PointsManager.getInstance().getFilteredPoints();
			synchronized (dirManagerMutex) {
				if (dirManager != null) {
					dirManager.calculateForPoints(points);
				}
			}
		} else if (key.equals(SettingsActivity.NAVIGATION_DATA)) {
			handleNavigationDataChange(this.pref.getString(SettingsActivity.NAVIGATION_DATA, null));
		} else if (key.equals(SettingsActivity.VEHICLE)) {
			if (routing != null) {
				routing.setEncoder(pref.getString(key, "CAR"));
				updateMapMatcher();
			}
		} else if (key.equals(SettingsActivity.MAPMATCHING)) {
			updateMapMatcher();
		}
	}

	private void updateMapMatcher() {
		boolean enabled = pref.getBoolean(SettingsActivity.MAPMATCHING, true);
		if (routing != null) {
			if (enabled) {
				mapMatcher = routing.createMapMatcher();
			} else {
				mapMatcher = routing.createSimpleMapMatcher();
			}
		}
	}

	private void sendResult(ArrayList<Direction> directions, GeoPoint center, Location location) {
		Intent intent = new Intent(DIRECTIONS_READY);
		intent.putParcelableArrayListExtra(DIRECTIONS_RESULT, directions);
		intent.putExtra(CENTER, (Parcelable) center);
		intent.putExtra(LOCATION, location);

		LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
	}

	public void setRadius(int dist) {
		this.radius = dist;
		synchronized (dirManagerMutex) {
			if (dirManager != null) {
				dirManager.setRadius(dist);
			}
		}
	}

    private void showNotification(Notification notification) {
        if (!isBackgroundMode)
            return;

        NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        notificationManager.notify(NOTIFICATION_ID, notification);
    }

    private Notification createNotification(String text) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle("Audible Alert")
                .setContentText(text != null ? text : "Audible Alert in background. Click to open")
                .setOngoing(true);

        // Open app action
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0,
                intent, PendingIntent.FLAG_UPDATE_CURRENT);
        builder.setContentIntent(pendingIntent);

        // Stop tracking action
        Intent stopIntent = new Intent(ACTION_STOP_TRACKING, null, this, DirectionService.class);
        PendingIntent stopPendingIntent = PendingIntent.getService(this, 1, stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT);
        //builder.addAction(R.drawable.ic_action_volume_muted, "Stop", stopPendingIntent);
        return builder.build();
    }

    @Override
    public void pointInRange(PointDesc point) {
        log.debug("pointInRange: {}", point.getName());
        if(lastLocation == null)
            return;

        Intent intent = new Intent(BC_ACTION_POINT_IN_RANGE);
        intent.putExtra(ARG_POINT, (Parcelable)point);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);

        GeoPoint last = new GeoPoint(lastLocation);
        double myBearing = lastLocation.getBearing();
        String relDir = "unknown_dir";

        double relativeBearing = last.bearingTo(point.toPoint());

        double bearing =  relativeBearing - myBearing;
        if(bearing < 0)
            bearing += 360;

        if(bearing <= 45 || bearing >= 315)
            relDir = "forward";
        if(bearing <= 135 && bearing > 45)
            relDir = "right";
        if(bearing <= 225 && bearing > 135)
            relDir = "back";
        if(bearing <= 315 && bearing > 225)
            relDir = "left";
        //log.error("bearing = " + bearing);
        if (isTrackingMode) {
            audioManager.queueToPlay(point, relDir);
            audioManager.playNext();
        }else{ // while debugging
            audioManager.queueToPlay(point, relDir);
            audioManager.playNext();
        }

        showNotification(createNotification(point.getName()));
    }

    public int onStartCommand(Intent intent, int flags, int startId) {
        log.error("Started DirectionService");
        return START_STICKY;
    }



}