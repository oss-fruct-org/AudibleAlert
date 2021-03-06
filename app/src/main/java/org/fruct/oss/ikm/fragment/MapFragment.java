package org.fruct.oss.ikm.fragment;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Point;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;
import android.os.Parcelable;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.v4.app.Fragment;
import android.support.v4.content.LocalBroadcastManager;
import android.text.util.Linkify;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.ViewTreeObserver;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.fruct.oss.aa.CategoriesManager;
import org.fruct.oss.aa.PointsOverlay;
import org.fruct.oss.ikm.DataService;
import org.fruct.oss.ikm.HelpTabActivity;
import org.fruct.oss.ikm.MainActivity;
import org.fruct.oss.ikm.OnlineContentActivity;
import org.fruct.oss.ikm.PointsActivity;
import org.fruct.oss.aa.R;
import org.fruct.oss.ikm.SettingsActivity;
import org.fruct.oss.ikm.Smoother;
import org.fruct.oss.ikm.TileProviderManager;
import org.fruct.oss.ikm.poi.PointDesc;
import org.fruct.oss.ikm.poi.PointsManager;
import org.fruct.oss.ikm.service.Direction;
import org.fruct.oss.ikm.service.DirectionService;
import org.fruct.oss.ikm.storage.ContentItem;
import org.fruct.oss.ikm.storage.RemoteContentListenerAdapter;
import org.fruct.oss.ikm.storage.RemoteContentService;
import org.fruct.oss.ikm.utils.Utils;
import org.fruct.oss.ikm.utils.bind.BindHelper;
import org.fruct.oss.ikm.utils.bind.BindSetter;
import org.osmdroid.DefaultResourceProxyImpl;
import org.osmdroid.api.IGeoPoint;
import org.osmdroid.bonuspack.overlays.DefaultInfoWindow;
import org.osmdroid.events.MapListener;
import org.osmdroid.events.ScrollEvent;
import org.osmdroid.events.ZoomEvent;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.util.ResourceProxyImpl;
import org.osmdroid.views.MapView;
import org.osmdroid.views.Projection;
import org.osmdroid.views.overlay.ItemizedIconOverlay;
import org.osmdroid.views.overlay.Overlay;
import org.osmdroid.views.overlay.OverlayItem;
import org.osmdroid.views.overlay.PathOverlay;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;

class MapState implements Parcelable {
	GeoPoint center;
	int zoomLevel;
	List<GeoPoint> currentPath = Collections.emptyList();
	List<Direction> directions = Collections.emptyList();
	boolean isTracking;
	
	boolean warningShown;
	
	@Override
	public int describeContents() {
		return 0;
	}
	
	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeParcelable(center, flags);
		dest.writeInt(zoomLevel);
		dest.writeTypedList(currentPath);
		dest.writeTypedList(directions);
		dest.writeValue(isTracking);
		dest.writeValue(warningShown);
	}
	
	public static final Creator<MapState> CREATOR = new Creator<MapState>() {
		@Override
		public MapState createFromParcel(Parcel source) {
			MapState ret = new MapState();
			ClassLoader loader = ((Object)this).getClass().getClassLoader();
			
			ret.center = source.readParcelable(loader);
			ret.zoomLevel = source.readInt();
			
			ret.currentPath = new ArrayList<GeoPoint>();
			source.readTypedList(ret.currentPath, GeoPoint.CREATOR);
			
			ret.directions = new ArrayList<Direction>();
			source.readTypedList(ret.directions, Direction.CREATOR);
			
			ret.isTracking = (Boolean) source.readValue(loader);
			ret.warningShown = (Boolean) source.readValue(loader);
			
			return ret;
		}
		
		@Override
		public MapState[] newArray(int size) {
			return new MapState[size];
		}
	};
}

public class MapFragment extends Fragment implements MapListener,
		OnSharedPreferenceChangeListener,
		MyPositionOverlay.OnScrollListener, PointsManager.PointsListener, DataService.DataListener {
	private static Logger log = LoggerFactory.getLogger(MapFragment.class);

	private DefaultInfoWindow infoWindow;

	private boolean networkToastShown;
	private boolean navigationDataToastShown;
	private boolean providersToastShown;

	private Overlay poiOverlay;
    private PointsOverlay po;
    private DefaultResourceProxyImpl mResourceProxy;

    static enum State {
		NO_CREATED(0), CREATED(1), DS_CREATED(2), DS_RECEIVED(3), SIZE(4);
		
		State(int idx) {
			this.idx = idx;
		}
		
		public int getIdx() {
			return idx;
		}
		
		private int idx;
	}

	public static final GeoPoint PTZ = new GeoPoint(61.783333, 34.350000);
	public static final GeoPoint ICELAND = new GeoPoint(64.133038, -21.898337);
	public static final GeoPoint KUOPIO = new GeoPoint(62.892500, 27.678333);
	public static final int DEFAULT_ZOOM = 18;
	
	public static final String POINTS = "org.fruct.oss.ikm.fragment.POI_LIST";
	public static final String MAP_CENTER = "org.fruct.oss.ikm.fragment.MAP_CENTER";
				
	private List<ClickableDirectedLocationOverlay> crossDirections;
	private PathOverlay pathOverlay;
	private MyPositionOverlay myPositionOverlay;
	
	private Menu menu;
	private MapView mapView;
	private TestOverlay panelOverlay;

	private BroadcastReceiver directionsReceiver;
	private BroadcastReceiver locationReceiver;
	private BroadcastReceiver pathReceiver;

	private Location myLocation;
	private Smoother speedAverage = new Smoother(10000);
	
	// Camera follow updates from DirectionService
	private boolean isTracking = false;
	
	private DirectionService directionService;

	private EnumMap<State, List<Runnable>> pendingTasks = new EnumMap<State, List<Runnable>>(
			State.class);
	private EnumSet<State> activeStates = EnumSet.of(State.NO_CREATED);
	
	// Current map state used to restore map view when rotating screen
	private MapState mapState = new MapState();

	private DataService dataService;
	private RemoteContentService remoteContent;
	private boolean localListReady = true;
	private TileProviderManager tileProviderManager;


    Drawable testMarker;
    Drawable hotelMarker;
    Drawable defaultMarker;

    public MapFragment() {
		pendingTasks.put(State.NO_CREATED, new ArrayList<Runnable>());
		pendingTasks.put(State.CREATED, new ArrayList<Runnable>());
		pendingTasks.put(State.DS_CREATED, new ArrayList<Runnable>());
		pendingTasks.put(State.DS_RECEIVED, new ArrayList<Runnable>());
	}

	private ServiceConnection serviceConnection = new ServiceConnection() {
		@Override
		public void onServiceDisconnected(ComponentName name) {
			log.info("MapFragment.onServiceDisconnected");

			clearState(State.DS_CREATED);
			directionService = null;
		}
		
		@Override
		public void onServiceConnected(ComponentName name, IBinder service) {
			log.info("MapFragment.onServiceConnected");
			directionService = ((DirectionService.DirectionBinder) service).getService();
			assert directionService != null;

			setState(State.DS_CREATED);
		}
	};

	@Override
	public void onCreate(Bundle savedInstanceState) {
		log.debug("MapFragment.onCreate");

		super.onCreate(savedInstanceState);
		setHasOptionsMenu(true);



		// Bind DirectionService
		Intent intent = new Intent(getActivity(), DirectionService.class);
        getActivity().startService(intent);
		getActivity().bindService(intent, serviceConnection, getActivity().BIND_AUTO_CREATE);
		
		LocalBroadcastManager.getInstance(getActivity()).registerReceiver(directionsReceiver = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				//log.debug("MapFragment DIRECTIONS_READY");
				//GeoPoint geoPoint = intent.getParcelableExtra(DirectionService.CENTER);
				ArrayList<Direction> directions = intent.getParcelableArrayListExtra(DirectionService.DIRECTIONS_RESULT);
				updateDirectionOverlay(directions);

				setState(State.DS_RECEIVED);
			}
		}, new IntentFilter(DirectionService.DIRECTIONS_READY));
		
		LocalBroadcastManager.getInstance(getActivity()).registerReceiver(locationReceiver = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				log.debug("MapFragment LOCATION_CHANGED");
				Location location = intent.getParcelableExtra(DirectionService.LOCATION);
				Location matchedLocation = intent.getParcelableExtra(DirectionService.MATCHED_LOCATION);

				assert location != null;

				myLocation = location;

				log.debug("New location provider " + location.getProvider());
				log.debug("New location accuracy " + location.getAccuracy());
				log.debug("New location speed " + location.getSpeed());

				PointsManager.getInstance().updatePosition(new GeoPoint(location));

				myPositionOverlay.setLocation(myLocation);
				myPositionOverlay.setMatchedLocation(matchedLocation);

				mapView.invalidate();

				// Auto-zoom and animate to new location if tracking mode enabled
				if (isTracking) {
					assert getActivity() != null;
					// Auto zoom enabled
					if (PreferenceManager.getDefaultSharedPreferences(getActivity()).getBoolean(SettingsActivity.AUTOZOOM, false)) {
						speedAverage.insert(location.getSpeed(), location.getTime());
						float ave = speedAverage.average();

						int newZoomLevel = getZoomBySpeed(ave);
						log.debug("Speed average = " + ave + " zoom = " + newZoomLevel);

						if (newZoomLevel != mapView.getZoomLevel()) {
							mapView.getController().setZoom(newZoomLevel);
						}
					}
					safeAnimateTo(new GeoPoint(myLocation));
					mapView.setMapOrientation(-location.getBearing());
				}
			}
		}, new IntentFilter(DirectionService.LOCATION_CHANGED));

		LocalBroadcastManager.getInstance(getActivity()).registerReceiver(pathReceiver = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				ArrayList<GeoPoint> pathArray = intent.getParcelableArrayListExtra(DirectionService.PATH);
				showPath(pathArray);
				mapView.getController().setCenter(new GeoPoint(myLocation));
			}
		}, new IntentFilter(DirectionService.PATH_READY));

		PreferenceManager.getDefaultSharedPreferences(getActivity()).registerOnSharedPreferenceChangeListener(this);


        hotelMarker = this.getResources().getDrawable(R.drawable.hotel);
        defaultMarker = this.getResources().getDrawable(R.drawable.point_marker_small);


		BindHelper.autoBind(this.getActivity(), this);
	}

	private int getZoomBySpeed(float speed) {
		float speedkmh = speed * 3.6f;
		
		if (speedkmh < 10)
			return 18;
		else if (speedkmh < 30)
			return 17;
		else if (speedkmh < 50)
			return 16;
		else
			return 15;
	}
	
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    private void setHardwareAccelerationOff() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
			mapView.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
    }
    
    private void createMapView(View view) {
    	RelativeLayout layout = (RelativeLayout) view.findViewById(R.id.map_layout);
    	ResourceProxyImpl proxy = new ResourceProxyImpl(this.getActivity().getApplicationContext());
    	
    	tileProviderManager = new TileProviderManager(getActivity());
    	
		mapView = new MapView(getActivity(), 256, proxy, tileProviderManager.getProvider());
    	mapView.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

		log.info("Created MapView using {} tiles", tileProviderManager.isOnline() ? "online" : "offline");

		//mapView.setBuiltInZoomControls(true);
		mapView.setMultiTouchControls(true);
		mapView.setMapListener(this);
		layout.addView(mapView);		
		
		setHardwareAccelerationOff();
    }

	private void setupOverlays() {
		// Setup device position overlay
		Overlay overlay = new Overlay(getActivity()) {
			final Point point = new Point();
			Paint paint = new Paint();
			{
				paint.setColor(Color.GRAY);
				paint.setStrokeWidth(2);
				paint.setStyle(Style.FILL);
				paint.setAntiAlias(true);
			}

			@Override
			protected void draw(Canvas canvas, MapView mapView, boolean shadow) {
				if (shadow)
					return;

				Projection proj = mapView.getProjection();
				Point mapCenter = proj.toPixels(mapView.getMapCenter(), point);
				canvas.drawCircle(mapCenter.x, mapCenter.y, 5, paint);
			}
		};

		myPositionOverlay = new MyPositionOverlay(getActivity(), mapView);
		mapView.getOverlays().add(myPositionOverlay);

		// Apply SHOW_ACCURACY preference
		SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(getActivity());
		myPositionOverlay.setShowAccuracy(pref.getBoolean(SettingsActivity.SHOW_ACCURACY, false));

		mapView.getOverlays().add(overlay);
		//updatePOIOverlay();
        new loadIconsTask().execute();
		// Test lines overlay
		//TestLinesOverlay over = new TestLinesOverlay(getActivity(), mapView);
		//mapView.getOverlays().add(over);

	}
    
	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		log.debug("MapFragment.onActivityCreated instanceState {}", savedInstanceState == null ? "null" : "not null");

		SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(getActivity());

		// Initialize map
		createMapView(getView());
		
		panelOverlay = (TestOverlay) getView().findViewById(R.id.directions_panel);
		panelOverlay.initialize(mapView);

		setupOverlays();

		// Process MAP_CENTER parameter
		Intent intent = getActivity().getIntent();
		GeoPoint center = intent.getParcelableExtra(MAP_CENTER);
		if (center != null && savedInstanceState == null) {
			log.debug("MapFragment.onActivityCreated setCenter = " + center);
			setCenter(center);
		}

		// Process SHOW_PATH action
		if (MainActivity.SHOW_PATH
				.equals(getActivity().getIntent().getAction())
				&& savedInstanceState == null) {
			log.debug("MapFragment.onActivityCreated SHOW_PATH");
			GeoPoint target = getActivity().getIntent().getParcelableExtra(MainActivity.SHOW_PATH_TARGET);
			showPath(target);
		}

		// Listen for new points in PointManager
		PointsManager.getInstance().addListener(this);


		final GeoPoint initialPosition;
		final int initialZoom;

		// Restore saved instance state
		if (savedInstanceState == null) {

            SharedPreferences localPref = getActivity().getSharedPreferences("MapFragment", Context.MODE_PRIVATE);
            int lat = localPref.getInt("last-pos-lat", PTZ.getLatitudeE6());
            int lon = localPref.getInt("last-pos-lon", PTZ.getLongitudeE6());

			initialPosition = new GeoPoint(lat, lon);
			initialZoom = DEFAULT_ZOOM;
		} else {
			log.debug("Restore mapCenter = " + mapState.center);
			
			MapState mapState = savedInstanceState.getParcelable("map-state");
			assert mapState != null;
			initialZoom = mapState.zoomLevel;
			initialPosition = mapState.center;
			providersToastShown = networkToastShown = navigationDataToastShown = mapState.warningShown;

			if (!mapState.directions.isEmpty())
				updateDirectionOverlay(mapState.directions);
			
			if (!mapState.currentPath.isEmpty())
				showPath(mapState.currentPath);
			
			if (mapState.isTracking)
				startTracking();
		}

		setState(State.CREATED);
		
		checkProvidersEnabled();
		checkNetworkAvailable();
		
		// Start tracking if preference set
		if (pref.getBoolean(SettingsActivity.STORE_LOCATION, false)) {
			addPendingTask(new Runnable() {
				@Override
				public void run() {
					startTracking();
				}
			}, State.DS_RECEIVED);
		}

		// Listen for mapView shown
		ViewTreeObserver vto = mapView.getViewTreeObserver();
		assert vto != null;
		vto.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
			@SuppressWarnings("deprecation")
			@Override
			public void onGlobalLayout() {
				log.trace("New size {} {}", mapView.getWidth(), mapView.getHeight());

				mapView.getController().setZoom(initialZoom);
				mapView.getController().setCenter(initialPosition);
				updateRadius();

				ViewTreeObserver obs = mapView.getViewTreeObserver();
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
					obs.removeOnGlobalLayoutListener(this);
				} else {
					obs.removeGlobalOnLayoutListener(this);
				}
			}
		});

        // Set initial position to allow GeTs to load points
       PointsManager.getInstance().updatePosition(initialPosition);
	}


	private void checkNetworkAvailable() {
		boolean networkActive = Utils.checkNetworkAvailability(getActivity());

		if (!networkToastShown && !networkActive && tileProviderManager.isOnline()) {
			SharedPreferences pref = PreferenceManager
					.getDefaultSharedPreferences(getActivity());

			if (!pref.getBoolean(SettingsActivity.WARN_NETWORK_DISABLED, false)) {
				WarnDialog dialog = new WarnDialog(R.string.warn_no_network,
						R.string.configure_use_offline_map,
						R.string.warn_providers_disable,
						SettingsActivity.WARN_NETWORK_DISABLED) {
					@Override
					protected void onAccept() {
						Intent intent = new Intent(getActivity(), OnlineContentActivity.class);
						getActivity().startActivity(intent);
					}
				};
				dialog.show(getFragmentManager(), "network-dialog");
			} else {
				Toast toast = Toast.makeText(getActivity(),
						R.string.warn_no_network, Toast.LENGTH_SHORT);
				toast.show();
			}

			networkToastShown = true;
		}
	}

	private void checkProvidersEnabled() {
		LocationManager locationManager = (LocationManager) getActivity()
				.getSystemService(Context.LOCATION_SERVICE);

		// Check if all providers disabled and show warning
		if (!providersToastShown
				&& !locationManager
						.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
				&& !locationManager
						.isProviderEnabled(LocationManager.GPS_PROVIDER)) {

			SharedPreferences pref = PreferenceManager
					.getDefaultSharedPreferences(getActivity());
			if (!pref.getBoolean(SettingsActivity.WARN_PROVIDERS_DISABLED, false)) {
				WarnDialog dialog = new WarnDialog(R.string.warn_no_providers,
						R.string.configure_providers,
						R.string.warn_providers_disable,
						SettingsActivity.WARN_PROVIDERS_DISABLED) {
					@Override
					protected void onAccept() {
						Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
						startActivity(intent);
					}
				};

				dialog.show(getFragmentManager(), "providers-dialog");
			} else {
				Toast toast = Toast.makeText(getActivity(),
						R.string.warn_no_providers, Toast.LENGTH_SHORT);
				toast.show();
			}
			providersToastShown = true;
		}
	}
	
	@Override
	public void onDestroy() {
		log.debug("MapFragment.onDestroy");

        mapView.getTileProvider().clearTileCache();

		clearState(State.DS_RECEIVED);
		clearState(State.CREATED);
		clearState(State.DS_CREATED);

		LocalBroadcastManager.getInstance(getActivity()).unregisterReceiver(directionsReceiver);
		LocalBroadcastManager.getInstance(getActivity()).unregisterReceiver(locationReceiver);
		LocalBroadcastManager.getInstance(getActivity()).unregisterReceiver(pathReceiver);

		getActivity().unbindService(serviceConnection);
		
		PreferenceManager.getDefaultSharedPreferences(getActivity()).unregisterOnSharedPreferenceChangeListener(this);

        getActivity().getSharedPreferences("MapFragment", Context.MODE_PRIVATE).edit()
                .putInt("last-pos-lat", mapView.getMapCenter().getLatitudeE6())
                .putInt("last-pos-lon", mapView.getMapCenter().getLongitudeE6()).apply();

		PointsManager.getInstance().removeListener(this);

		BindHelper.autoUnbind(this.getActivity(), this);

        if(getActivity().isFinishing()) {
            Intent intent = new Intent(getActivity(), DirectionService.class);
            getActivity().stopService(intent);
            log.trace("Activity is finishing, stopped service");
        }

		super.onDestroy();
	}
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		return inflater.inflate(R.layout.map_fragment, container, false);
	}

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		inflater.inflate(R.menu.main, menu);
		this.menu = menu;
		// mapView.getOverlayManager().onCreateOptionsMenu(menu, 4, mapView);

		super.onCreateOptionsMenu(menu, inflater);
	}
	
	@Override
	public void onPrepareOptionsMenu(Menu menu) {
		this.menu = menu;
		
		menu.findItem(R.id.action_track).setIcon(
				isTracking ? R.drawable.ic_action_location_searching
						: R.drawable.ic_action_location_found);
		super.onPrepareOptionsMenu(menu);
	}


	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.action_search:			
			directionService.fakeLocation(Utils.copyGeoPoint(mapView.getMapCenter()));
            if (isTracking)
                stopTracking();
			break;
			
		case R.id.action_place:
			Intent intent = new Intent(getActivity(), PointsActivity.class);
			startActivity(intent);
			break;
			
		case R.id.action_settings:
			intent = new Intent(getActivity(), SettingsActivity.class);
			startActivity(intent);
			break;
			
		case R.id.action_track:
			if (!isTracking)
				startTracking();
			else
				stopTracking(); 
		
			break;
			
		case R.id.action_filter:
			showFilterDialog();
			break;

		case R.id.action_download_map:
			intent = new Intent(getActivity(), OnlineContentActivity.class);
			startActivity(intent);
			break;

		case R.id.action_about:
			showAboutDialog();
			break;

		case R.id.action_help:
			intent = new Intent(getActivity(), HelpTabActivity.class);
			startActivity(intent);
			break;

		default:
			//mapView.getOverlayManager().onOptionsItemSelected(item, 4, mapView);
			return super.onOptionsItemSelected(item);
		}

		return true;
	}

	private void showAboutDialog() {
		TextView textView = new TextView(new ContextThemeWrapper(getActivity(), Utils.getDialogTheme()));
		textView.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
		textView.setTextSize(16);
		textView.setAutoLinkMask(Linkify.WEB_URLS);
		textView.setText(R.string.about_text);

		final int paddingDP = Utils.getDP(16);
		textView.setPadding(paddingDP, paddingDP, paddingDP, paddingDP);

		AlertDialog.Builder builder = new AlertDialog.Builder(new ContextThemeWrapper(getActivity(), Utils.getDialogTheme()));
		builder.setIcon(R.drawable.ic_launcher);
		builder.setTitle(R.string.app_name);
		builder.setView(textView);

		builder.create();
		builder.show();
	}

	private void showFilterDialog() {
		FilterDialog dialog = new FilterDialog();
		dialog.show(getFragmentManager(), "filter-dialog");
	}

	private void updateDirectionOverlay(final List<Direction> directions) {
		Context context = getActivity();
		if (crossDirections != null) {
			mapView.getOverlays().removeAll(crossDirections);
		}
				
		crossDirections = new ArrayList<ClickableDirectedLocationOverlay>();
		for (Direction direction : directions) {
			final GeoPoint directionPoint = direction.getDirection();
			final GeoPoint centerPoint = direction.getCenter();
			
			final List<PointDesc> points = direction.getPoints();
			
			final double bearing = centerPoint.bearingTo(directionPoint);
			//GeoPoint markerPosition = centerPoint.destinationPoint(50 << (DEFAULT_ZOOM - mapView.getZoomLevel()), (float) bearing);
			final GeoPoint markerPosition = directionPoint;
			
			//markerPosition = directionPoint;
			/*ClickableDirectedLocationOverlay overlay = new ClickableDirectedLocationOverlay(context, mapView, markerPosition, (float) bearing);
			
			overlay.setListener(new ClickableDirectedLocationOverlay.Listener() {
				@Override
				public void onClick() {
					Intent intent = new Intent(getActivity(), PointsActivity.class);
					intent.putParcelableArrayListExtra(POINTS, new ArrayList<PointDesc>(points));
					startActivity(intent);
				}
			});
			
			mapView.getOverlays().add(overlay);
			crossDirections.add(overlay); */
		}
		
		mapState.directions = directions;
		
		// Update panelOverlay after fragment loaded
		addPendingTask(new Runnable() {
			@Override
			public void run() {
				panelOverlay.setDirections(directions, myLocation != null ? myLocation.getBearing() : 0);
			}
		},  State.CREATED);
		
		mapView.invalidate();
	}
	
	private void updatePOIOverlay() {
		log.trace("MapFragment.updatePOIOverlay");
		final Context context = getActivity();

		if (poiOverlay != null)
			mapView.getOverlays().remove(poiOverlay);
        if (po != null)
            mapView.getOverlays().remove(po);

		List<PointDesc> points = PointsManager.getInstance()
				.getFilteredPoints();

        List<PointDesc> withBitmaps = new ArrayList<PointDesc>();
        List<PointDesc> withoutBitmaps = new ArrayList<PointDesc>();

        for(PointDesc p : points){
            if(CategoriesManager.getIconForCategory(p.getCategory()) != null){
                withBitmaps.add(p);
            }else{
                withoutBitmaps.add(p);
            }
        }

        /*
		List<ExtendedOverlayItem> items2 = Utils.map(points, new Utils.Function<ExtendedOverlayItem, PointDesc>() {
            public ExtendedOverlayItem apply(PointDesc point) {
                ExtendedOverlayItem item = new ExtendedOverlayItem(point.getName(), point
                        .getCategory(), point.toPoint());
                item.setRelatedObject(point);
                return item;
            }
        }); */

        po = new PointsOverlay(context,withBitmaps);
        po.setEnabled(true);

        List<OverlayItem> items3 = Utils.map(withoutBitmaps, new Utils.Function<OverlayItem, PointDesc>(){
            public OverlayItem apply(PointDesc point){
                OverlayItem item = new OverlayItem(point.getName(),
                        point.getCategory(), point.toPoint());
                return item;
            }
        });



        mResourceProxy = new DefaultResourceProxyImpl(context);
        ItemizedIconOverlay.OnItemGestureListener<OverlayItem> GListener= new ItemizedIconOverlay.OnItemGestureListener<OverlayItem>() {
            @Override
            public boolean onItemSingleTapUp(int index, OverlayItem item) {
                return false;
            }

            @Override
            public boolean onItemLongPress(int index, OverlayItem item) {
                return false;
            }
        };

		/*infoWindow = new POIInfoWindow(R.layout.bonuspack_bubble, mapView);
		 poiOverlay = new ItemizedOverlayWithBubble<ExtendedOverlayItem>(
				context, items2, mapView, infoWindow) {
			@Override
			public boolean onSingleTapUp(MotionEvent e, MapView mapView) {
				if (infoWindow.isOpen())
					infoWindow.close();
				return super.onSingleTapUp(e, mapView);
			}
		}; */

        poiOverlay = new ItemizedIconOverlay<OverlayItem>(items3, defaultMarker, GListener, mResourceProxy );

        mapView.getOverlayManager().add(po);
		mapView.getOverlays().add(poiOverlay);
		log.trace("MapFragment.updatePOIOverlay EXIT");
	}
	
	public void startTracking() {
		isTracking = true;
		myPositionOverlay.setListener(this);
		//panelOverlay.setVisibility(View.VISIBLE);
		//panelOverlay.setHidden(false);

		if (menu != null)
			menu.findItem(R.id.action_track).setIcon(R.drawable.ic_action_location_searching);
		
		if (activeStates.contains(State.DS_CREATED))
			directionService.startTracking();
	}
	
	public void stopTracking() {
		isTracking = false;
		myPositionOverlay.clearListener();
		//panelOverlay.setVisibility(View.GONE);
		//panelOverlay.setHidden(true);
		
		if (menu != null)
            menu.findItem(R.id.action_track).setIcon(R.drawable.ic_action_location_found);

		mapView.setMapOrientation(0);
	}
	
	@Override
	public void onSaveInstanceState(Bundle outState) {
		mapState.center = Utils.copyGeoPoint(mapView.getMapCenter());
		mapState.zoomLevel = mapView.getZoomLevel();
		mapState.isTracking = isTracking;
		mapState.warningShown = providersToastShown || navigationDataToastShown || networkToastShown;
		//mapState.mapOrientation = mapView.getMapOrientation();
		outState.putParcelable("map-state", mapState);
	}
	
	public void setCenter(IGeoPoint geoPoint) {
		mapView.getController().setZoom(DEFAULT_ZOOM);
		mapView.getController().animateTo(geoPoint);

		if (isTracking)
			stopTracking();
	}

	/**
	 * Animate to point without stopping track mode
	 * @param geoPoint Target point
	 */
	public void safeAnimateTo(IGeoPoint geoPoint) {
		mapView.getController().animateTo(geoPoint);
	}
	
	public void showPath(final GeoPoint target) {
		Runnable task = new Runnable() {
			@Override
			public void run() {
				log.debug("MapFragment.showPath task start");

				// Find path from current location to target location
				directionService.findPath(target);
			}
		};
		
		addPendingTask(task, State.DS_RECEIVED);
	}
	
	private void showPath(List<GeoPoint> path) {
		// Remove existing path overlay
		if (pathOverlay != null) {
			mapView.getOverlays().remove(pathOverlay);
		}
		
		pathOverlay = new PathOverlay(Color.BLUE, 6f, new DefaultResourceProxyImpl(getActivity()));
		pathOverlay.setAlpha(127);

		for (GeoPoint geoPoint : path)
			pathOverlay.addPoint(geoPoint);

		mapView.getOverlays().add(pathOverlay);
		mapView.invalidate();
		
		mapState.currentPath = path;
	}
	
	private void addPendingTask(Runnable runnable, State state) {
		if (activeStates.contains(state))
			runnable.run();
		else
			pendingTasks.get(state).add(runnable);
	}
	
	private void setState(State newState) {
		log.info("MapFragment.setState " + newState.toString());

		activeStates.add(newState);

		List<Runnable> tasks = new ArrayList<Runnable>(pendingTasks.get(newState));
		pendingTasks.get(newState).clear();

		for (Runnable runnable : tasks)
			runnable.run();
	}

	private void clearState(State state) {
		activeStates.remove(state);
	}

	// Scroll from MapListener
	@Override
	public boolean onScroll(ScrollEvent event) {
		return false;
	}

	@Override
	public void onScroll() {
		if (isTracking)
			stopTracking();
	}

	@BindSetter
	public void remoteContentServiceReady(RemoteContentService service) {
		if (service == null) {
			remoteContent.removeListener(remoteContentAdapter);
			remoteContent = null;
			localListReady = false;
		} else {
			remoteContent = service;
			localListReady = !remoteContent.getLocalItems().isEmpty();
			remoteContent.addListener(remoteContentAdapter);
			setupOfflineMap();
		}
	}

	@BindSetter
	public void dataServiceReady(DataService service) {
		if (service == null) {
			dataService.removeDataListener(this);
			dataService = null;
		} else {
			dataService = service;
			dataService.addDataListener(this);
			setupOfflineMap();
		}
	}

	@Override
	public void dataPathChanged(String newDataPath) {
		setupOfflineMap();
	}

	@Override
	public int getPriority() {
		return 2;
	}

	private void setupOfflineMap() {
		if (remoteContent == null || dataService == null || !localListReady)
			return;

		remoteContent.removeListener(remoteContentAdapter);
		SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(getActivity());

		boolean useOfflineMap = pref.getBoolean(SettingsActivity.USE_OFFLINE_MAP, false);
		String offlineMapName = pref.getString(SettingsActivity.OFFLINE_MAP, null);

		if (useOfflineMap && offlineMapName != null) {
			ContentItem contentItem = remoteContent.getContentItem(offlineMapName);
			String offlineMapPath = remoteContent.getFilePath(contentItem);
			if (offlineMapPath != null) {
				tileProviderManager.setFile(offlineMapPath);
				mapView.invalidate();
			}
		} else {
			tileProviderManager.setFile(null);
		}

		dataService.dataListenerReady();
	}

	private void updateRadius() {
		Projection proj = mapView.getProjection();

		GeoPoint p1 = Utils.copyGeoPoint(proj.fromPixels(0, 0));
		IGeoPoint p2 = proj.fromPixels(mapView.getWidth(), 0);
		IGeoPoint p3 = proj.fromPixels(0, mapView.getHeight());

		final int dist = Math.min(p1.distanceTo(p2), p1.distanceTo(p3)) / 2;
		log.trace("Size {} {}", mapView.getWidth(), mapView.getHeight());
		log.trace("Dist {} {}", p1.distanceTo(p2), p1.distanceTo(p3));
		log.trace("Zoom level {}", mapView.getZoomLevel());

		if (dist == 0)
			return;
		
		addPendingTask(new Runnable() {
			@Override
			public void run() {
				assert directionService != null;
				directionService.setRadius(dist);
			}
		}, State.DS_CREATED);
	}
	
	@Override
	public boolean onZoom(ZoomEvent event) {
		log.trace("MapFragment.onZoom");
		updateRadius();
		return false;
	}
	
	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
			String key) {
		log.trace("MapFragment.onSharedPreferenceChanged");

		if (key.equals(SettingsActivity.SHOW_ACCURACY)) {
			if (myPositionOverlay != null)
                myPositionOverlay.setShowAccuracy(sharedPreferences.getBoolean(SettingsActivity.SHOW_ACCURACY, false));

			mapView.invalidate();
		} else if (key.equals(SettingsActivity.OFFLINE_MAP) || key.equals(SettingsActivity.USE_OFFLINE_MAP)) {
			setupOfflineMap();
		} else if (key.equals(SettingsActivity.GETS_ENABLE) || key.equals(SettingsActivity.GETS_SERVER)) {
			PointsManager.getInstance().ensureGetsState();
		} else if (key.equals(SettingsActivity.GETS_RADIUS)) {
			String value = sharedPreferences.getString(key, "200000");
			int radius = Integer.parseInt(value);

			PointsManager.getInstance().updateRadius(radius * 1);
		}
	}

	@Override
	public void filterStateChanged(List<PointDesc> newList) {
		getActivity().runOnUiThread(new Runnable() {
			@Override
			public void run() {
				assert Looper.getMainLooper().getThread() == Thread.currentThread();
				updatePOIOverlay();
			}
		});
	}

	private RemoteContentService.Listener remoteContentAdapter = new RemoteContentListenerAdapter() {
		@Override
		public void localListReady(List<ContentItem> list) {
			if (!localListReady) {
				localListReady = true;
				setupOfflineMap();
			}
		}
	};



    private class loadIconsTask extends AsyncTask {

        @Override
        protected Object doInBackground(Object[] objects) {
            CategoriesManager.init();
            testMarker = CategoriesManager.drawableFromUrlSafe("http://kappa.cs.karelia.ru/~kolomens/bakabaka.png");
            updatePOIOverlay();

            return null;
        }
    }

}
