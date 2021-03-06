package org.fruct.oss.ikm.fragment;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.app.ListFragment;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBar.Tab;
import android.support.v7.app.ActionBarActivity;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.fruct.oss.aa.CategoriesManager;
import org.fruct.oss.aa.DistanceTracker;
import org.fruct.oss.ikm.DetailsActivity;
import org.fruct.oss.ikm.PointsActivity;
import org.fruct.oss.aa.R;
import org.fruct.oss.ikm.poi.AllFilter;
import org.fruct.oss.ikm.poi.Filter;
import org.fruct.oss.ikm.poi.PointDesc;
import org.fruct.oss.ikm.poi.PointsManager;
import org.fruct.oss.ikm.utils.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

class PointAdapter extends ArrayAdapter<PointDesc> {
	private static Logger log = LoggerFactory.getLogger(PointAdapter.class);

	private int resource;
	private List<PointDesc> points;

	class Tag {
		TextView textView;
		TextView distanceView;
		ImageView imageView;
	}

	public PointAdapter(Context context, int resource, List<PointDesc> points) {
		super(context, resource, points);

		this.resource = resource;
		this.points = points;
	}
	
	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		LayoutInflater inflater = ((Activity) getContext()).getLayoutInflater();
		View view = null;

		Tag tag = null;

		if (convertView != null && convertView.getTag() != null) {
			tag = (Tag) convertView.getTag();
			if (tag instanceof Tag) {
				view = convertView;
			}
		}

		if (view == null) {
			view = inflater.inflate(resource, parent, false);
			assert view != null;

			tag = new Tag();
			tag.textView = (TextView) view.findViewById(android.R.id.text1);
			tag.distanceView = (TextView) view.findViewById(android.R.id.text2);
			tag.imageView = (ImageView) view.findViewById(android.R.id.icon1);
			view.setTag(tag);
		}

		PointDesc point = points.get(position);

		tag.textView.setText(point.getName());
		if (point.getRelativeDirection() != null) {
			tag.imageView.setImageResource(point.getRelativeDirection().getIconId());
			tag.imageView.setContentDescription(point.getRelativeDirection().getDescription());
			tag.imageView.setVisibility(View.VISIBLE);
			tag.distanceView.setVisibility(View.VISIBLE);
		} else {
			tag.imageView.setVisibility(View.GONE);
		}
		if (point.getDistance() > 0) {
			tag.distanceView.setText(Utils.stringMeters(point.getDistance()));
			tag.distanceView.setVisibility(View.VISIBLE);
		} else {
			tag.distanceView.setVisibility(View.GONE);
		}
		
		return view;
	}
}

public class PointsFragment extends ListFragment implements TextWatcher {
	private static Logger log = LoggerFactory.getLogger(PointsFragment.class);

	private List<PointDesc> poiList;
	private List<PointDesc> shownList = new ArrayList<PointDesc>();
	private Filter currentFilter = null;
	
	private boolean isDualPane;
	private String searchText;

	private PointDesc lastShownPoint;

    private String locale;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

        locale = this.getResources().getConfiguration().locale.getLanguage();
        log.error("Locale language = " + locale);
		setHasOptionsMenu(true);
	}

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		super.onCreateOptionsMenu(menu, inflater);

		inflater.inflate(R.menu.points_fragment, menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (item.getItemId() == R.id.action_refresh) {
			PointsManager.getInstance().refresh();
			Toast.makeText(getActivity(), R.string.str_updating_points, Toast.LENGTH_SHORT).show();
		}

		return super.onOptionsItemSelected(item);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		
		return inflater.inflate(R.layout.point_list_layout, container, true);
	}
	
	private void updateList() {
		int index = getListView().getCheckedItemPosition();
		PointDesc pointDesc = null;
		if (index >= 0)
			pointDesc = shownList.get(index);
		
		shownList.clear();
		Utils.select(poiList, shownList, new Utils.Predicate<PointDesc>() {
            @Override
            public boolean apply(PointDesc t) {
                if (currentFilter != null && !currentFilter.accepts(t))
                    return false;
                else if (searchText != null
                        && searchText.length() > 0
                        && !t.getName().toLowerCase(Locale.getDefault()).contains(searchText))
                    return false;
                else
                    return true;
            }
        });

        for(PointDesc pd : shownList){
            pd.setDistance(DistanceTracker.getDistanceTo(pd));
        }

        Comparator<PointDesc> comp = new Comparator<PointDesc>() {
            @Override
            public int compare(PointDesc pointDesc, PointDesc pointDesc2) {
                return pointDesc.getDistance() - pointDesc2.getDistance();
            }

            @Override
            public boolean equals(Object o) {
                return false;
            }
        };

        Collections.sort(shownList, comp);

		PointAdapter adapter = new PointAdapter(
				getActivity(), 
				getListItemLayout(),
				shownList);
		setListAdapter(adapter);
		
		if (pointDesc != null)
			selectIfAvailable(pointDesc, false);
	}
	
	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	public static int getListItemLayout() {
		/*return Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB
				? android.R.layout.simple_list_item_activated_1
				: android.R.layout.simple_list_item_1;*/
	
		return R.layout.point_list_item;
	}
	
	@Override
	public void onListItemClick(ListView l, View v, int position, long id) {
		showDetails(position);
	}
	
	/**
	 * Select item if it in list
	 * 
	 * @param point point of interest
	 * @param alwaysSwitch switch to details window even in one-panel mode
	 */
	public void selectIfAvailable(PointDesc point, boolean alwaysSwitch) {
		int c = -1;
		for (int i = 0; i < shownList.size(); i++) {
			if (shownList.get(i).equals(point)) {
				c = i;
			}
		}
		
		
		if (-1 != c) {
			log.debug("select " + c);
			getListView().setItemChecked(c, true);

			if ((alwaysSwitch || isDualPane)) {
				showDetails(c);
			}
		}
	}
	
	/**
	 * Show point of interest details on details panel (dual-pane layout)
	 * or new activity (one-panel layout)
	 * 
	 * @param index of element
	 */
	public void showDetails(int index) {
		PointDesc pointDesc = shownList.get(index);
		lastShownPoint = pointDesc;

		log.debug("PointsFragment.showDetails isDualPane = " + isDualPane);
		if (isDualPane) {
			getListView().setItemChecked(index, true);
			
			DetailsFragment fragment = new DetailsFragment();
			Bundle args = new Bundle();
			args.putParcelable(DetailsActivity.POINT_ARG, pointDesc);
			fragment.setArguments(args);
			getActivity().getSupportFragmentManager().beginTransaction()
				.replace(R.id.point_details, fragment, "details")
				.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
				.commit();
		} else {
			Intent intent = new Intent(getActivity(), DetailsActivity.class);
			intent.setAction(DetailsActivity.POINT_ACTION);
			intent.putExtra(DetailsActivity.POINT_ARG, (Parcelable) pointDesc);
			startActivity(intent);
		}
	}
	
	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		
		try {			
			Intent intent = getActivity().getIntent();
			List<PointDesc> poiList = intent.getParcelableArrayListExtra(MapFragment.POINTS);

			if (poiList == null) {
                poiList = PointsManager.getInstance().getAllPoints();
            }
			
			this.poiList = poiList;
			updateList();
		} catch (ClassCastException ex) {
			ex.printStackTrace();
		}

		View details = getActivity().findViewById(R.id.point_details);
		if (details != null && details.getVisibility() == View.VISIBLE)
			isDualPane = true;
		else
			isDualPane = false;
		
		// Remove detail fragment when switching from two-panel mode to one-panel mode
		Fragment detailFragment = getActivity().getSupportFragmentManager().findFragmentByTag("details");
		if (detailFragment != null && !isDualPane) {
			getActivity().getSupportFragmentManager().beginTransaction().remove(detailFragment).commit();
		}

		int selectedBar = 0;
		PointDesc storedPointDesc = null;
		if (savedInstanceState != null) {
			selectedBar = savedInstanceState.getInt("selectedBar", 0);
			storedPointDesc = savedInstanceState.getParcelable("lastShownPoint");
		}
		
		if (isDualPane) {
			getListView().setChoiceMode(ListView.CHOICE_MODE_SINGLE);
		}
		
		// If intent has action SHOW_DETAILS, show details activity immediately
		if (PointsActivity.SHOW_DETAILS.equals(getActivity().getIntent().getAction())) {
			Bundle bundle = getActivity().getIntent().getBundleExtra(PointsActivity.DETAILS_INDEX);
			log.debug("PointsFragment receive action SHOW_DETAILS. extras = " + bundle);
			PointDesc point = bundle.getParcelable("pointdesc");
			selectIfAvailable(point, true);
		} else if (isDualPane && storedPointDesc != null) {
			selectIfAvailable(storedPointDesc, false);
		}
		
		setupFilterBar(selectedBar);

		EditText searchBar = (EditText) getActivity().findViewById(R.id.search_field);
		searchBar.addTextChangedListener(this);
	}
	
	private void setupTab(final Filter filter, boolean isSelected) {
		ActionBarActivity activity = (ActionBarActivity) getActivity();
		ActionBar actionBar = activity.getSupportActionBar();
        String name;
		Tab tab = activity.getSupportActionBar().newTab();
        tab.setText(filter.getString());
        /*if(locale.equals("ru")){
            name = CategoriesManager.getName(filter.getString());
            tab.setText(name);
        } */

		
		tab.setTabListener(new ActionBar.TabListener() {
			@Override
			public void onTabUnselected(Tab arg0, FragmentTransaction arg1) {
				
			}
			
			@Override
			public void onTabSelected(Tab arg0, FragmentTransaction arg1) {
				currentFilter = filter;
				updateList();
			}
			
			@Override
			public void onTabReselected(Tab arg0, FragmentTransaction arg1) {
				
			}
		});
		
		actionBar.addTab(tab, isSelected);
	}
	
	private void setupFilterBar(int selectedBar) {
		ActionBar actionBar = getActionBar();
		List<Filter> filters = PointsManager.getInstance().getCategoryFilters();
		
		setupTab(new AllFilter(), false);
		int c = 1;
		for (final Filter filter : filters) {
			if (selectedBar == c) {
				currentFilter = filter;
			}

			setupTab(filter, selectedBar == c);
			c++;
		}
		
		actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
		actionBar.setDisplayShowTitleEnabled(false);
	}
	
	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
	
		int selectedTab = getActionBar().getSelectedNavigationIndex();
		outState.putInt("selectedBar", selectedTab);

		if (lastShownPoint != null)
			outState.putParcelable("lastShownPoint", lastShownPoint);
	}

	private ActionBar getActionBar() {
		ActionBarActivity activity = (ActionBarActivity) getActivity();
		return activity.getSupportActionBar();
	}

	@Override
	public void afterTextChanged(Editable s) {}

	@Override
	public void beforeTextChanged(CharSequence s, int start, int count,
			int after) {}

	@Override
	public void onTextChanged(CharSequence s, int start, int before, int count) {
		log.debug("onTextChanged " + s);
		searchText = s.toString().toLowerCase(Locale.getDefault());
		updateList();
	}
}
