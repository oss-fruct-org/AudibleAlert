package org.fruct.oss.ikm;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.fruct.oss.aa.R;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ContentDialog extends DialogFragment implements DialogInterface.OnClickListener, DialogInterface.OnMultiChoiceClickListener {
	private boolean[] active;
	private String[] strings;
	private List<ContentListSubItem> storageItems;

    private static Logger log = LoggerFactory.getLogger(ContentDialog.class);


	interface Listener {
		void downloadsSelected(List<ContentListSubItem> items);
	}

	private Listener listener;

	public ContentDialog() {
	}

	public void setStorageItems(List<ContentListSubItem> storageItems) {
		List<ContentListSubItem> storageItemsCopy = new ArrayList<ContentListSubItem>(storageItems);

		for (Iterator<ContentListSubItem> iterator = storageItemsCopy.iterator(); iterator.hasNext(); ) {
			ContentListSubItem storageItem = iterator.next();

			if (!storageItem.contentItem.isDownloadable()) {
				iterator.remove();
			}
		}

		this.storageItems = storageItemsCopy;
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);

		outState.putBooleanArray("active", active);
		outState.putStringArray("strings", strings);
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setRetainInstance(true);
	}

	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);

		strings = new String[storageItems.size()];
		active = new boolean[storageItems.size()];

		for (int i = 0; i < storageItems.size(); i++) {
			ContentListSubItem sItem = storageItems.get(i);

			String type = sItem.contentItem.getType();
			if (type.equals("mapsforge-map"))
				strings[i] = getString(R.string.offline_map);
			else if (type.equals("graphhopper-map"))
				strings[i] = getString(R.string.navigation_data);

			active[i] = (sItem.state == OnlineContentActivity.LocalContentState.NEEDS_UPDATE
				|| sItem.state == OnlineContentActivity.LocalContentState.NOT_EXISTS);

            if(type.equals("graphhopper-map"))
                active[i] = false;
		}

		try {
			listener = (Listener) activity;
		} catch (ClassCastException ex) {
			throw new ClassCastException(activity.toString() + "must implement " + Listener.class.toString());
		}
	}

	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

		builder.setPositiveButton(R.string.download, this);
		builder.setNegativeButton(android.R.string.cancel, this);

		builder.setTitle(R.string.download);

		if (savedInstanceState != null) {
			strings = savedInstanceState.getStringArray("strings");
			active = savedInstanceState.getBooleanArray("active");
		}

		builder.setMultiChoiceItems(strings, active, this);

		return builder.create();
	}

	@Override
	public void onClick(DialogInterface dialogInterface, int i) {
		if (i == DialogInterface.BUTTON_POSITIVE && listener != null) {
			List<ContentListSubItem> ret = new ArrayList<ContentListSubItem>();

			for (int j = 0; j < active.length; j++) {
				if (active[j])
					ret.add(storageItems.get(j));
			}

			listener.downloadsSelected(ret);
		}
	}

	@Override
	public void onClick(DialogInterface dialogInterface, int i, boolean b) {
		active[i] = b;
	}
}
