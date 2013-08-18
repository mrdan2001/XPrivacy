package biz.bokhorst.xprivacy;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import android.support.v7.app.ActionBarActivity;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.NavUtils;
import android.support.v4.app.TaskStackBuilder;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Filter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

public class ActivityUsage extends ActionBarActivity {
	private int mThemeId;
	private boolean mAll = true;
	private int mUid;
	private UsageAdapter mUsageAdapter;

	public static final String cUid = "Uid";

	private static ExecutorService mExecutor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors(),
			new PriorityThreadFactory());

	private static class PriorityThreadFactory implements ThreadFactory {
		@Override
		public Thread newThread(Runnable r) {
			Thread t = new Thread(r);
			t.setPriority(Thread.NORM_PRIORITY);
			return t;
		}
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// Set theme
		String themeName = PrivacyManager.getSetting(null, this, PrivacyManager.cSettingTheme, "", false);
		mThemeId = (themeName.equals("Dark") ? R.style.CustomTheme : R.style.CustomTheme_Light);
		setTheme(mThemeId);

		// Set layout
		setContentView(R.layout.usagelist);

		// Get uid
		Bundle extras = getIntent().getExtras();
		mUid = (extras == null ? 0 : extras.getInt(cUid, 0));

		// Start task to get usage data
		UsageTask usageTask = new UsageTask();
		if (Build.VERSION.SDK_INT > Build.VERSION_CODES.GINGERBREAD_MR1) {
			usageTask.executeOnExecutor(mExecutor, (Object) null);
		} else {
			usageTask.execute((Object) null);
		}

		// Listen for clicks
		ListView lvUsage = (ListView) findViewById(R.id.lvUsage);
		lvUsage.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> adapter, View view, int position, long arg) {
				PrivacyManager.UsageData usageData = mUsageAdapter.getItem(position);
				String[] packageName = getPackageManager().getPackagesForUid(usageData.getUid());
				if (packageName != null && packageName.length > 0) {
					Intent intent = new Intent(ActivityUsage.this, ActivityApp.class);
					intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
					intent.putExtra(ActivityApp.cPackageName, packageName[0]);
					intent.putExtra(ActivityApp.cRestrictionName, usageData.getRestrictionName());
					intent.putExtra(ActivityApp.cMethodName, usageData.getMethodName());
					startActivity(intent);
				}
			}
		});

		// Up navigation
		getSupportActionBar().setDisplayHomeAsUpEnabled(true);
	}

	@Override
	protected void onResume() {
		super.onResume();
		if (mUsageAdapter != null)
			mUsageAdapter.notifyDataSetChanged();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.usage, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.home:
		case android.R.id.home:
			Intent upIntent = NavUtils.getParentActivityIntent(this);
			if (upIntent != null)
				if (NavUtils.shouldUpRecreateTask(this, upIntent))
					TaskStackBuilder.create(this).addNextIntentWithParentStack(upIntent).startActivities();
				else
					NavUtils.navigateUpTo(this, upIntent);
			return true;
		case R.id.menu_all:
			mAll = !mAll;
			if (mUsageAdapter != null)
				mUsageAdapter.getFilter().filter(Boolean.toString(mAll));
			return true;
		case R.id.menu_refresh:
			UsageTask usageTask = new UsageTask();
			if (Build.VERSION.SDK_INT > Build.VERSION_CODES.GINGERBREAD_MR1) {
				usageTask.executeOnExecutor(mExecutor, (Object) null);
			} else {
				usageTask.execute((Object) null);
			}
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}

	// Tasks

	private class UsageTask extends AsyncTask<Object, Object, List<PrivacyManager.UsageData>> {
		@Override
		protected List<PrivacyManager.UsageData> doInBackground(Object... arg0) {
			long minTime = new Date().getTime() - 1000 * 60 * 60 * 24;
			List<PrivacyManager.UsageData> listUsageData = new ArrayList<PrivacyManager.UsageData>();
			for (PrivacyManager.UsageData usageData : PrivacyManager.getUsed(ActivityUsage.this, mUid))
				if (usageData.getTimeStamp() > minTime)
					listUsageData.add(usageData);
			return listUsageData;
		}

		@Override
		protected void onPostExecute(List<PrivacyManager.UsageData> listUsageData) {
			super.onPostExecute(listUsageData);

			mUsageAdapter = new UsageAdapter(ActivityUsage.this, R.layout.usageentry, listUsageData);
			ListView lvUsage = (ListView) findViewById(R.id.lvUsage);
			lvUsage.setAdapter(mUsageAdapter);
			mUsageAdapter.getFilter().filter(Boolean.toString(mAll));
		}
	}

	// Adapters

	private class UsageAdapter extends ArrayAdapter<PrivacyManager.UsageData> {
		private List<PrivacyManager.UsageData> mListUsageData;
		private LayoutInflater mInflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);

		public UsageAdapter(Context context, int textViewResourceId, List<PrivacyManager.UsageData> objects) {
			super(context, textViewResourceId, objects);
			mListUsageData = new ArrayList<PrivacyManager.UsageData>();
			mListUsageData.addAll(objects);
		}

		@Override
		public Filter getFilter() {
			return new UsageFilter();
		}

		private class UsageFilter extends Filter {
			public UsageFilter() {
			}

			@Override
			protected FilterResults performFiltering(CharSequence constraint) {
				FilterResults results = new FilterResults();

				// Get argument
				boolean all = Boolean.parseBoolean((String) constraint);

				// Match applications
				List<PrivacyManager.UsageData> lstResult = new ArrayList<PrivacyManager.UsageData>();
				for (PrivacyManager.UsageData usageData : UsageAdapter.this.mListUsageData) {
					if (all ? true : usageData.getRestricted())
						lstResult.add(usageData);
				}

				synchronized (this) {
					results.values = lstResult;
					results.count = lstResult.size();
				}

				return results;
			}

			@Override
			@SuppressWarnings("unchecked")
			protected void publishResults(CharSequence constraint, FilterResults results) {
				clear();
				if (results.values == null)
					notifyDataSetInvalidated();
				else {
					for (PrivacyManager.UsageData usage : (ArrayList<PrivacyManager.UsageData>) results.values) {
						add(usage);
					}
					notifyDataSetChanged();
				}
			}
		}

		private class ViewHolder {
			public TextView tvTime;
			public ImageView imgRestricted;
			public TextView tvApp;
			public TextView tvRestriction;

			public ViewHolder(View row) {
				tvTime = (TextView) row.findViewById(R.id.tvTime);
				imgRestricted = (ImageView) row.findViewById(R.id.imgRestricted);
				tvApp = (TextView) row.findViewById(R.id.tvApp);
				tvRestriction = (TextView) row.findViewById(R.id.tvRestriction);
			}
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			ViewHolder holder;
			if (convertView == null) {
				convertView = mInflater.inflate(R.layout.usageentry, null);
				holder = new ViewHolder(convertView);
				convertView.setTag(holder);
			} else
				holder = (ViewHolder) convertView.getTag();

			// Get data
			PrivacyManager.UsageData usageData = getItem(position);

			// Build entry
			Date date = new Date(usageData.getTimeStamp());
			SimpleDateFormat format = new SimpleDateFormat("HH:mm:ss", Locale.ROOT);
			holder.tvTime.setText(format.format(date));
			holder.imgRestricted.setVisibility(usageData.getRestricted() ? View.VISIBLE : View.INVISIBLE);
			holder.tvApp.setText(Integer.toString(usageData.getUid()));
			holder.tvRestriction.setText(String.format("%s/%s", usageData.getRestrictionName(),
					usageData.getMethodName()));

			return convertView;
		}
	}
}
