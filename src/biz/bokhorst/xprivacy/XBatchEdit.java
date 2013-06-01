package biz.bokhorst.xprivacy;

import biz.bokhorst.xprivacy.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

public class XBatchEdit extends Activity {

	public static final String cExtraPermissionName = "Permission";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		// Set layout
		setContentView(R.layout.xbatchedit);

		// Get permission name
		Bundle extras = getIntent().getExtras();
		final String permissionName = extras.getString(cExtraPermissionName);

		// Display permission name
		TextView tvPermission = (TextView) findViewById(R.id.tvPermission);
		tvPermission.setText(XPermissions.getLocalizedName(getBaseContext(), permissionName));

		// Legend
		TextView tvUsed = (TextView) findViewById(R.id.tvUsed);
		tvUsed.setTypeface(null, Typeface.BOLD_ITALIC);
		TextView tvInternet = (TextView) findViewById(R.id.tvInternet);
		tvInternet.setTextColor(Color.GRAY);

		// Get app list
		PackageManager pm = getBaseContext().getPackageManager();
		final List<XApplicationInfo> listApp = new ArrayList<XApplicationInfo>();
		for (ApplicationInfo appInfo : pm.getInstalledApplications(PackageManager.GET_META_DATA))
			listApp.add(new XApplicationInfo(appInfo, permissionName, pm));
		Collections.sort(listApp);

		// Fill app list view adapter
		final ListView lvApp = (ListView) findViewById(R.id.lvApp);
		AppListAdapter appAdapter = new AppListAdapter(getBaseContext(), R.layout.xappentry, listApp, permissionName);
		lvApp.setAdapter(appAdapter);
	}

	private class XApplicationInfo implements Comparable<XApplicationInfo> {
		private Drawable mDrawable;
		private String mApplicationName;
		private boolean mHasInternet;
		private boolean mIsUsed;
		private int mUid;

		public XApplicationInfo(ApplicationInfo appInfo, String permissionName, PackageManager packageManager) {
			mDrawable = appInfo.loadIcon(packageManager);
			mApplicationName = (String) packageManager.getApplicationLabel(appInfo);
			mHasInternet = XPermissions.hasInternet(getBaseContext(), appInfo.packageName);
			mIsUsed = XPermissions.isUsed(getBaseContext(), appInfo.uid, permissionName);
			mUid = appInfo.uid;
		}

		public Drawable getDrawable() {
			return mDrawable;
		}

		public boolean hasInternet() {
			return mHasInternet;
		}

		public boolean isUsed() {
			return mIsUsed;
		}

		public int getUid() {
			return mUid;
		}

		@Override
		public String toString() {
			return mApplicationName;
		}

		@Override
		public int compareTo(XApplicationInfo other) {
			return toString().compareTo(other.toString());
		}
	}

	private class AppListAdapter extends ArrayAdapter<XApplicationInfo> {
		String mPermissionName;

		public AppListAdapter(Context context, int resource, List<XApplicationInfo> objects, String permissionName) {
			super(context, resource, objects);
			mPermissionName = permissionName;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			View row = inflater.inflate(R.layout.xappentry, parent, false);
			ImageView icon = (ImageView) row.findViewById(R.id.imgAppEntryIcon);
			TextView tvApp = (TextView) row.findViewById(R.id.tvAppEntryName);
			final CheckBox chkPermission = (CheckBox) row.findViewById(R.id.chkAppEntryPermission);

			// Get entry
			final XApplicationInfo appEntry = getItem(position);

			// Set icon
			icon.setImageDrawable(appEntry.getDrawable());

			// Set icon/title
			tvApp.setText(appEntry.toString());

			// Check if internet access
			if (!appEntry.hasInternet())
				tvApp.setTextColor(Color.GRAY);

			// Check if used
			if (appEntry.isUsed())
				tvApp.setTypeface(null, Typeface.BOLD_ITALIC);

			// Set check box
			boolean allowed = XPermissions.getAllowed(null, getBaseContext(), appEntry.getUid(), mPermissionName,
					chkPermission.isChecked());
			chkPermission.setChecked(!allowed);

			// Handle check box click
			chkPermission.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View arg0) {
					XPermissions.setAllowed(null, getBaseContext(), appEntry.getUid(), mPermissionName,
							!chkPermission.isChecked());
				}
			});

			return row;
		}
	}
}