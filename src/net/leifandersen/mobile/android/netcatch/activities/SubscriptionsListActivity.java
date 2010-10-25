package net.leifandersen.mobile.android.netcatch.activities;

import java.util.ArrayList;

import net.leifandersen.mobile.android.netcatch.R;
import net.leifandersen.mobile.android.netcatch.providers.Episode;
import net.leifandersen.mobile.android.netcatch.providers.Show;
import net.leifandersen.mobile.android.netcatch.providers.ShowsProvider;
import net.leifandersen.mobile.android.netcatch.services.RSSService;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 
 * An activity that allows the user to navigate through
 * all of his subscriptions.
 * 
 * @author Leif Andersen
 *
 */
public class SubscriptionsListActivity extends ListActivity {

	private static final int NEW_FEED = 1;
	
	private View mPlayer;
	
	// Only member variables for access within internal methods.
	private ProgressDialog progressDialog;
	private String newFeed;
	private EditText mEditFeed;
	
	private static class ViewHolder {
		TextView title;
		TextView author;
		ImageView image;
	}
	
	private class ShowAdapter extends ArrayAdapter<Show> {
		
		LayoutInflater mInflater;
		

		
		public ShowAdapter(Context context) {
			super(context, R.layout.subscriptions_list);
			mInflater = getLayoutInflater();
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			ViewHolder holder;
			if(convertView == null) {
				convertView = mInflater.inflate(R.layout.subscription, null);
				holder = new ViewHolder();
				holder.title = (TextView)convertView.findViewById(R.id.sc_title);
				holder.author = (TextView)convertView.findViewById(R.id.sc_author);
				holder.image = (ImageView)convertView.findViewById(R.id.sc_picture);
				convertView.setTag(holder);
			}
			else
				holder = (ViewHolder)convertView.getTag();
			
			Show subscription = getItem(position);
			holder.title.setText(subscription.getTitle());
			holder.author.setText(subscription.getAuthor());
			Drawable d = subscription.getImage();
			if (d != null)
				holder.image.setImageDrawable(d);
			registerForContextMenu(convertView);
			
			convertView.setOnClickListener(new OnClickListener() {	
				@Override
				public void onClick(View v) {
					Intent i = new Intent();
					i.setClass(SubscriptionsListActivity.this, EpisodesListActivity.class);
					i.putExtra(EpisodesListActivity.SHOW_NAME, 
							((TextView)v.findViewById(R.id.sc_title)).getText());
					startActivity(i);
				}
			});
			return convertView;
		}
	}

	ShowAdapter mAdapter;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.subscriptions_list);

		// Set up the list
		refreshList();
		
		// Start the widget
		mPlayer = ((ViewStub)findViewById(R.id.sl_small_player_stub)).inflate();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflator = getMenuInflater();
		inflator.inflate(R.layout.subscriptions_menu, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.sm_new_show:
			showDialog(NEW_FEED);
			return true;
		}
		return false;
	}

	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
		super.onListItemClick(l, v, position, id);
		Intent i = new Intent();
		i.setClass(SubscriptionsListActivity.this, EpisodesListActivity.class);
		i.putExtra(EpisodesListActivity.SHOW_NAME, 
				((TextView)v.findViewById(R.id.sc_title)).getText());
		startActivity(i);
	}
	
	@Override
	protected Dialog onCreateDialog(int id, Bundle args) {
		Dialog dialog = null;
		switch(id) {
		case NEW_FEED:
			AlertDialog.Builder builder = new AlertDialog.Builder(this);

			// Set up the layout
			LayoutInflater inflater = 
				(LayoutInflater)getSystemService(LAYOUT_INFLATER_SERVICE);
			View layout = inflater.inflate(R.layout.subscription_feed_dialog, null);
			mEditFeed = (EditText)layout.findViewById(R.id.sfd_editText);
			builder.setView(layout);
			builder.setCancelable(false);
			builder.setPositiveButton(getString(android.R.string.ok), new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					// Get the RSS feed the user entered
					newFeed = mEditFeed.getText().toString();

					// Get the feed's data
					// Set the broadcast reciever
					BroadcastReceiver finishedReceiver = new BroadcastReceiver() {
						@Override
						public void onReceive(Context context, Intent intent) {							
							// Get the data
							// Get the show
							Bundle showBundle = intent.getBundleExtra(RSSService.SHOW);
							Show show = (Show)showBundle.get(RSSService.SHOW);
							
							// Add the show
							ContentValues values = new ContentValues();
							values.put(ShowsProvider.TITLE, show.getTitle());
							values.put(ShowsProvider.AUTHOR, show.getAuthor());
							values.put(ShowsProvider.FEED, show.getFeed());
							values.put(ShowsProvider.IMAGE, show.getImagePath());
							getContentResolver().insert(ShowsProvider.SUBSCRIPCTIONS_CONTENT_URI, values);
							
							// Get the episodes, add to database.
							// Database doesn't need to be cleared, as it shouldn't have existed.
							Bundle episodeBundle = intent.getBundleExtra(RSSService.EPISODES);
							ArrayList<String> titles = episodeBundle.getStringArrayList(RSSService.EPISODE_TITLES);
							for (String title : titles) {								
								Episode episode = (Episode)episodeBundle.get(title);
								values = new ContentValues();
								values.put(ShowsProvider.TITLE, episode.getTitle());
								values.put(ShowsProvider.AUTHOR, episode.getAuthor());
								values.put(ShowsProvider.DATE, episode.getDate());
								values.put(ShowsProvider.PLAYED, episode.isPlayed());
								values.put(ShowsProvider.DESCRIPTION, episode.getDescription());
								getContentResolver().insert(Uri.parse("content://" + ShowsProvider.PROVIDER_NAME
										+ "/"+ show.getTitle()), values);
							}

							progressDialog.cancel();

							// Refresh and return
							refreshList();
						}
					};
					registerReceiver(finishedReceiver, new IntentFilter(RSSService.RSSFINISH + newFeed));

					// Set up the failed dialog
					BroadcastReceiver failedReciever = new BroadcastReceiver() {
						@Override
						public void onReceive(Context context, Intent intent) {
							progressDialog.cancel();
							Toast.makeText(SubscriptionsListActivity.this, "Failed to fetch feed", Toast.LENGTH_LONG);
						}
					};
					registerReceiver(failedReciever, new IntentFilter(RSSService.RSSFAILED + newFeed));
					
					// Show a waiting dialog (that can be canceled)
					progressDialog =
						ProgressDialog.show(SubscriptionsListActivity.this,
								"", getString(R.string.getting_show_details));
					progressDialog.setCancelable(true);
					progressDialog.show();
					
					// Start the service
					Intent service = new Intent();
					service.putExtra(RSSService.FEED, newFeed);
					service.setClass(SubscriptionsListActivity.this, RSSService.class);
					startService(service);
				}
			});
			builder.setNegativeButton(getString(android.R.string.cancel), new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					dialog.cancel();
				}
			});
			dialog = builder.create();
			break;
		default:
			dialog = null;
		}
		return dialog;
	}

	private void refreshList() {
		// Reset the view, 
		mAdapter = new ShowAdapter(this);
		setListAdapter(mAdapter);

		// Get all of the shows
		Cursor shows = managedQuery(ShowsProvider.SUBSCRIPCTIONS_CONTENT_URI, null, null, null, null);

		// Populate the view
		if(shows.moveToFirst())
			do {
				Show s = new Show();
				s.setTitle(shows.getString(shows.getColumnIndex(ShowsProvider.TITLE)));
				s.setAuthor(shows.getString(shows.getColumnIndex(ShowsProvider.AUTHOR)));
				String imagePath = shows.getString(shows.getColumnIndex(ShowsProvider.IMAGE));
				if (imagePath != "")
					s.setImage(Drawable.createFromPath(imagePath));
				mAdapter.add(s);
			} while (shows.moveToNext());
	}

}
