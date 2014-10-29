package org.fruct.oss.ikm.poi;

import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.os.AsyncTask;
import android.preference.PreferenceManager;

import org.fruct.oss.ikm.App;
import org.fruct.oss.ikm.SettingsActivity;
import org.fruct.oss.ikm.utils.Utils;
import org.osmdroid.util.GeoPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PointsManager {
	private static Logger log = LoggerFactory.getLogger(PointsManager.class);

	public interface PointsListener {
		void filterStateChanged(List<PointDesc> newList);
	}

	private List<PointLoader> loaders = new ArrayList<PointLoader>();
	private ExecutorService executor = Executors.newSingleThreadExecutor();

	/**
	 * All points fetched from loaders
	 */
	private final List<PointDesc> points = new ArrayList<PointDesc>();

	/**
	 * Points that accepted by at least one filter
	 */
	private List<PointDesc> filteredPoints = new ArrayList<PointDesc>();

	private List<Filter> filters = new ArrayList<Filter>();

    private List<Filter> distanceFilters = new ArrayList<Filter>();

    private List<Filter> setFilters = new ArrayList<Filter>();

    private boolean disabilitiesLoaded = false;

    List<String[]> disabs = new ArrayList<String[]>();

    private List<PointsListener> listeners = new ArrayList<PointsListener>();

    int[] distances =  { 10, 15, 30, 50, 75, 100, 200, 300, 500};

    String [][] sets = new String[][]{
           new String [] {"Inns", "hotels" , "hostels"},
           new String [] {"Sightseeing","museums", "sights", "monasteries" , "monuments"},
           new String [] {"Vacation", "natural monuments", "Sanatoriums"}
    };


	private GetsPointLoader getsPointsLoader;
	private PointsStorage storage;

	public PointsManager() {
		storage = new PointsStorage(App.getContext());
       new disablititiesLoadAsyncTask().execute();
	}

	public void addPointLoader(final PointLoader pointLoader) {
		loaders.add(pointLoader);

		// Schedule loader
		executor.execute(new Runnable() {
			@Override
			public void run() {
				updatePointLoader(pointLoader);
			}
		});
	}

	public void refresh() {
		executor.execute(new Runnable() {
			@Override
			public void run() {
				for (PointLoader loader : loaders) {
					try {
						List<PointDesc> currentPoints = loader.getPoints();
						loader.loadPoints();
						if (loader.getPoints() != currentPoints) {
							storage.insertPoints(loader.getPoints(), loader.getName());
						}
					} catch (Exception ex) {
						log.error("Can't load points from loader " + loader.getClass().getName(), ex);
					}
				}

				recreatePointList();
			}
		});
	}

	private void removePointLoader(final PointLoader pointLoader) {
		loaders.remove(pointLoader);
	}

	private void updatePointLoader(PointLoader pointLoader) {
		try {
			pointLoader.loadFromStorage(storage);
			recreatePointList();

			List<PointDesc> currentPoints = pointLoader.getPoints();

			if (pointLoader.needUpdate()) {
				pointLoader.loadPoints();
				if (pointLoader.getPoints() != currentPoints) {
					storage.insertPoints(pointLoader.getPoints(), pointLoader.getName());

					recreatePointList();
				}
			}
		} catch (Exception ex) {
			log.error("Can't load points from loader " + pointLoader.getClass().getName(), ex);
		}
	}

	public void updatePosition(final GeoPoint position) {
		executor.execute(new Runnable() {
			@Override
			public void run() {
				for (PointLoader loader : loaders) {
					loader.updatePosition(position);
					if (loader.needUpdate()) {
						updatePointLoader(loader);
					}
				}
			}
		});
	}

	public void updateRadius(final int radiusM) {
		executor.execute(new Runnable() {
			@Override
			public void run() {
				if (getsPointsLoader != null) {
					getsPointsLoader.setRadius(radiusM);
				}
			}
		});
	}

	// Ensure that GeTS loader state matches GETS_ENABLE preference
	public void ensureGetsState() {
		SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(App.getContext());

		if (!pref.getBoolean(SettingsActivity.GETS_ENABLE, true)) {
			// If GETS_ENABLE is off and and GETS loaded, unload it
			if (getsPointsLoader != null) {
				removePointLoader(getsPointsLoader);
				getsPointsLoader = null;
			}
			return;
		}

		String getsServer = pref.getString(SettingsActivity.GETS_SERVER, SettingsActivity.GETS_SERVER_DEFAULT);
		if (getsServer == null || getsServer.isEmpty()) {
			log.trace("GETS_SERVER argument is empty");
			getsServer = SettingsActivity.GETS_SERVER_DEFAULT;
		}

		String radiusString = pref.getString(SettingsActivity.GETS_RADIUS, "200000");
		int radius = Integer.parseInt(radiusString);

		getsPointsLoader = new GetsPointLoader(getsServer);
		getsPointsLoader.setRadius(radius);
		addPointLoader(getsPointsLoader);
	}

	private void recreatePointList() {
		synchronized (points) {
			points.clear();

			for (PointLoader pointLoader : loaders) {
				points.addAll(pointLoader.getPoints());

			}
           // getCategoriesFromGets();

            createFilters();


		}
	}

    private void createFilters(){
        createDistanceFilters();
        createCategoryFilters();
        if(disabilitiesLoaded)
            createSetsFilters();

        notifyFiltersUpdated();
    }


	private void createCategoryFilters() {
        log.trace("Recreating filters");

        HashMap<String, String> names = new HashMap<String, String>();
		Set<String> oldDiabledFilters = new HashSet<String>();

		for (Filter filter : filters) {
			if (!filter.isActive()) {
				oldDiabledFilters.add(filter.getString());
			}
		}

        filters.clear();
		for (PointDesc point : points)
			names.put(point.getCategory(), point.getCat_id());

        //removeUnusedCategories(names);

        Iterator it = names.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry pairs = (Map.Entry) it.next();
            // log.trace("Filter for category {}", str);
            CategoryFilter filter = new CategoryFilter(pairs.getKey()+"", pairs.getKey()+"", pairs.getValue()+"");
            filters.add(filter);
            if (oldDiabledFilters.contains(filter.getString())) {
                filter.setActive(false);
            }
        }
	}

    /**
     * Create distance or category filters
     *
     */
    private void createDistanceFilters(){
        distanceFilters.clear();
        int count = distances.length;

        for(int i = 0; i < count; i++){
            DistanceFilter filter = new DistanceFilter(distances[i]);
            distanceFilters.add(filter);
        }

        distanceFilters.add(new DistanceFilter(10000));
    }

    /**
     * Create set of SetFilters that contain multiple category-filters
     * which are defined in sets[][] array
     */
    private void createSetFilters(){
        setFilters.clear();
        Set<Filter> groupedFilters = new HashSet<Filter>();
        Set<Filter> rest = new HashSet<Filter>();
        for(Filter flt : filters)
            rest.add(flt);
        for(int i =0; i < sets.length; i++){
            groupedFilters.clear();
            if(sets[i].length < 2)
                continue;
            for(int j = 1; j < sets[i].length; j++){
                for(Filter flt : filters){
                    if(flt.getString().equalsIgnoreCase(sets[i][j])) {
                        groupedFilters.add(flt);
                        rest.remove(flt);
                        continue;
                    }
                }
            }
            setFilters.add(new SetFilter(sets[i][0],groupedFilters));

        }

        setFilters.add(new SetFilter("...", rest));
    }

    public void createSetsFilters(){
        Set <String>oldDisabledFilters = new HashSet<String>();
        for(Filter flt : setFilters){
            if(!flt.isActive()){
                oldDisabledFilters.add(flt.getName());
            }
        }
        setFilters.clear();
        Set<Filter> groupedFilters = new HashSet<Filter>();
        Set<Filter> rest = new HashSet<Filter>();
        for(Filter flt : filters)
            rest.add(flt);
        for(int i =0; i < disabs.size() - 1; i++){
            groupedFilters.clear();
            if(disabs.get(i).length < 2)
                continue;
            for(int j = 1; j < disabs.get(i).length; j++){
                for(Filter flt : filters){
                    if(((CategoryFilter)flt).getId().equalsIgnoreCase(disabs.get(i)[j])) {
                        groupedFilters.add(flt);
                        rest.remove(flt);
                        continue;
                    }
                }
            }
            setFilters.add(new SetFilter(disabs.get(i)[0],groupedFilters));
        }
        for(Filter flt : setFilters){
            if(oldDisabledFilters.contains(flt.getName()))
                flt.setActive(false);
        }
        setFilters.add(new SetFilter("...", rest));
    }


	public void addListener(PointsListener listener) {
		listeners.add(listener);
	}

	public void removeListener(PointsListener listener) {
		listeners.remove(listener);
	}

	public List<PointDesc> getFilteredPoints() {
		synchronized (points) {
			return filteredPoints;
		}
	}

	public List<PointDesc> getAllPoints() {
		synchronized (points) {
			return points;
		}
	}


	private void filterPoints(List<PointDesc> in, List<PointDesc> out) {
		out.clear();
		Utils.select(in, out, new Utils.Predicate<PointDesc>() {
            public boolean apply(PointDesc point) {
                for (Filter filter : setFilters) {
                    if (filter.isActive() && filter.accepts(point))
                        return true;
                }
                return false;
            }
        });
	}

	public List<Filter> getFilters() {
		return Collections.unmodifiableList(setFilters);
	}

    public List<Filter> getDistanceFilters() {
        return Collections.unmodifiableList(distanceFilters);
    }

    public List<Filter> getCategoryFilters(){
        return Collections.unmodifiableList(filters);
    }

	public void notifyFiltersUpdated() {
		List<PointDesc> filteredPoints = new ArrayList<PointDesc>();
		filterPoints(points, filteredPoints);

		this.filteredPoints = Collections.unmodifiableList(filteredPoints);

		for (PointsListener listener : listeners) {
			listener.filterStateChanged(filteredPoints);
		}
	}

	public synchronized static PointsManager getInstance() {
		if (instance == null) {
			instance = new PointsManager();

			if (true) {
				//instance.addPointLoader(new StubPointLoader());
				instance.ensureGetsState();
				return instance;
			}

			log.debug("Loading points from assets");

			String[] jsonFiles;
			try {
				AssetManager am = App.getContext().getAssets();
				jsonFiles = am.list("karelia-poi");
			} catch (IOException e) {
				e.printStackTrace();
				log.warn("Can not open list of .json files");
				instance.addPointLoader(new StubPointLoader());
				return instance;
			}

			for (String filename : jsonFiles) {
				JSONPointLoader jsonLoader;
				try {
					jsonLoader = JSONPointLoader.createForAsset("karelia-poi/" + filename);
				} catch (IOException ex) {
					ex.printStackTrace();
					log.warn("Can not load POI from file {}", "karelia-poi/" + filename);
					continue;
				}
				instance.addPointLoader(jsonLoader);
			}
		}
		return instance;
	}

	/**
	 * For unit testing
	 */
	public static void resetInstance() {
		instance = null;
	}

	private static volatile PointsManager instance;

    public void loadDisabilities(){
        DisabilitiesLoader dl = new DisabilitiesLoader();
    }

    public class disablititiesLoadAsyncTask extends AsyncTask {

        @Override
        protected Object doInBackground(Object[] objects) {
            loadDisabilities();
            disabilitiesLoaded = true;
            createSetFromDisabilities();
            createFilters();


            return null;
        }
    }

    public void createSetFromDisabilities(){
        disabs.clear();
        if(DisabilitiesLoader.disabilities == null)
            return;
        List<Disability> ds = DisabilitiesLoader.disabilities.getDisabilities();
        disabs = new ArrayList<String[]>();

        for(Disability d : ds){
            String [] da = new String[d.getCategories().size() + 1];
            da[0] = d.getName();
            int i = 1;
            for(String cat : d.getCategories()){
                da[i++] = cat;
            }
            disabs.add(da);
        }
    }

}
