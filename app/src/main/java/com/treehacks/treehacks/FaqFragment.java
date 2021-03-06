package com.treehacks.treehacks;

import android.app.SearchManager;
import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SearchView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;

import com.parse.DeleteCallback;
import com.parse.FindCallback;
import com.parse.ParseException;
import com.parse.ParseObject;
import com.parse.ParseQuery;
import com.parse.SaveCallback;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Created by eddie_000 on 1/30/2015.
 */
public class FaqFragment extends Fragment {
	RecyclerView faqView;
	FaqAdapter faqAdapter;
	SearchView sv;

	ParseQuery<ParseObject> localQuery;
	ParseQuery<ParseObject> cloudQuery;

	public FaqFragment() {
		faqAdapter = new FaqAdapter();

		localQuery = ParseQuery.getQuery("FAQ");
		localQuery.fromLocalDatastore();
		localQuery.orderByAscending("question");


		cloudQuery = ParseQuery.getQuery("FAQ");
		cloudQuery.orderByDescending("updatedAt");
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setHasOptionsMenu(true);


		// Sync announcements from cloud
		cloudQuery.findInBackground(new FindCallback<ParseObject>() {
			@Override
			public void done(final List<ParseObject> parseCloudFaqs, ParseException e) {
				if (e == null) {
					// Skip if no new updates are found
					// Get newest updatedAt from cloud faqs
					Date newestChange;
					if (!parseCloudFaqs.isEmpty())
						newestChange = parseCloudFaqs.get(0).getUpdatedAt();
					else
						newestChange = new Date(1);
					// Get stored updatedAt
					Date storedChange = new Date(0);
					ParseQuery<ParseObject> getStoredChangeTime = ParseQuery.getQuery("FaqChangeTime");
					getStoredChangeTime.fromLocalDatastore();
					ParseObject storedChangeTime;
					try {
						storedChangeTime = getStoredChangeTime.getFirst();
						if (storedChangeTime.has("time"))
							storedChange = storedChangeTime.getDate("time");
					} catch (ParseException e1) {
						storedChangeTime = new ParseObject("FaqChangeTime");
						try {
							storedChangeTime.pin();
						} catch (ParseException ignored) {
						}
					}
					if (!newestChange.after(storedChange)) {
						Log.d("Parse", "No newer FAQs found in cloud");
						return;
					}
					Log.d("Parse", "Newer FAQs found in cloud, syncing...");
					// Store newer updatedAt
					storedChangeTime.put("time", newestChange);
					// Update local datastore
					ParseObject.unpinAllInBackground("faqs", new DeleteCallback() {
						@Override
						public void done(ParseException e) {
							if (e == null) {
								ParseObject.pinAllInBackground("faqs", parseCloudFaqs, new SaveCallback() {
									@Override
									public void done(ParseException e) {
										if (e == null)
											Log.d("Parse", "cache FAQs from cloud SUCCESS");
										else
											e.printStackTrace();
									}
								});
							}
							else {
								e.printStackTrace();
							}
						}
					});
					if (faqAdapter != null) {
						faqAdapter.changeDataSet(parseCloudFaqs);
					}
				} else {
					e.printStackTrace();
					// We done fucked up
				}
			}
		});
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View rootView = inflater.inflate(R.layout.fragment_faq, container, false);
		faqView = (RecyclerView) rootView.findViewById(R.id.faq_view);
		faqView.setHasFixedSize(true);
		faqView.setLayoutManager(new LinearLayoutManager(getActivity()));
		faqView.setAdapter(faqAdapter);

		// Get faqs from cache
		List<ParseObject> parseObjects;
		try {
			parseObjects = localQuery.find();
		} catch (ParseException e) {
			e.printStackTrace();
			parseObjects = new ArrayList<>();
		}
		faqAdapter.changeDataSet(parseObjects);

		return rootView;
	}

	// Clear searchview on page change
	@Override
	public void onStop() {
		sv.setQuery("", false);
		sv.clearFocus();
		super.onStop();
	}

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		super.onCreateOptionsMenu(menu, inflater);
		inflater.inflate(R.menu.menu_search, menu);

		// Assoc. searchview config to SearchView
		SearchManager sm = (SearchManager) getActivity().getSystemService(Context.SEARCH_SERVICE);
		sv = (SearchView) MenuItemCompat.getActionView(menu.findItem(R.id.action_search));
		sv.setSearchableInfo(sm.getSearchableInfo(getActivity().getComponentName()));
		sv.setQueryHint(getResources().getString(R.string.search_hint));
		sv.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
			@Override
			public boolean onQueryTextSubmit(String s) {
				return false;
			}

			@Override
			public boolean onQueryTextChange(String s) {
				faqAdapter.getFilter().filter(s);
				return false;
			}
		});
	}

}
