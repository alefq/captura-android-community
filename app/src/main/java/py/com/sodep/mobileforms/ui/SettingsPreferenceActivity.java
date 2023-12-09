package py.com.sodep.mobileforms.ui;

import io.github.jokoframework.chake.R;
import py.com.sodep.mobileforms.application.BroadcastActions;
import io.github.jokoframework.chake.MFApplication;
import py.com.sodep.mobileforms.settings.AppSettings;
import py.com.sodep.mobileforms.ui.preferences.ActionPreference;
import py.com.sodep.mobileforms.ui.preferences.SimpleTextPreference;

import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import android.view.View;
import android.view.View.OnClickListener;

public class SettingsPreferenceActivity extends PreferenceActivity {

	private static final int LOGGING_OUT_DIALOG = 1;

	private LocalBroadcastManager localBroadcastManager = LocalBroadcastManager.getInstance(this);

	private static volatile SettingsPreferenceActivity currentActivity;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		addPreferencesFromResource(R.xml.settings);

		PreferenceManager manager = getPreferenceManager();
		ActionPreference logoutPreference = (ActionPreference) manager.findPreference("logout");

		logoutPreference.setPositiveButtonOnClickListner(new OnClickListener() {

			@Override
			public void onClick(View v) {
				AppSettings.setLoggedIn(SettingsPreferenceActivity.this, false);
				AppSettings.setPassword(SettingsPreferenceActivity.this, null);

				showDialog(LOGGING_OUT_DIALOG);
				LogoutTask logoutTask = new LogoutTask();
				logoutTask.execute();
			}
		});

		ActionPreference changeAppPreference = (ActionPreference) manager.findPreference("change_app");
		changeAppPreference.setPositiveButtonOnClickListner(new OnClickListener() {

			@Override
			public void onClick(View v) {
				finishMainActivity();

				Intent i = new Intent(SettingsPreferenceActivity.this, AppListActivity.class);
				startActivity(i);
				SettingsPreferenceActivity.this.finish();
			}
		});

		SimpleTextPreference openSodepWebPreference = (SimpleTextPreference) manager.findPreference("web");
		openSodepWebPreference.setSummary("www.sodep.com.py");
	}
	
	

	@Override
	protected void onResume() {
		currentActivity = this;
		super.onResume();
	}

	@Override
	protected void onPause() {
		super.onPause();
	}

	private void finishMainActivity() {
		// Broadcast a message so that the MainActivity finishes
		Intent exitIntent = new Intent();
		exitIntent.setAction(BroadcastActions.ACTION_EXIT);
		currentActivity.localBroadcastManager.sendBroadcast(exitIntent);
	}

	protected void stopNetworkActivity() {
		MFApplication application = (MFApplication) getApplication();
		application.stopSync();
	}

	@Override
	protected Dialog onCreateDialog(int id) {
		switch (id) {
		case LOGGING_OUT_DIALOG:
			return loginOutProgressDialog();
		}
		return null;
	}

	private Dialog loginOutProgressDialog() {
		ProgressDialog dialog = new ProgressDialog(this);
		dialog.setIndeterminate(true);
		dialog.setTitle(R.string.logging_out_title);
		dialog.setMessage(getString(R.string.logging_out_message));
		dialog.setCancelable(false);
		return dialog;
	}

	private static class LogoutTask extends MFAsyncTask<String, Integer, Boolean> {

		@Override
		protected Boolean doWork(String... params) {
			SettingsPreferenceActivity activity = currentActivity;
			activity.stopNetworkActivity();
			return true;
		}

		@Override
		protected void onPostExecute(Boolean result) {
			SettingsPreferenceActivity activity = currentActivity;
			
			try {
				activity.removeDialog(LOGGING_OUT_DIALOG);
			} catch (Exception e) {

			}
			
			if (result != null && result) {
				activity.finishMainActivity();
				// Start the login activity
				// without going through the Splash Screen
				Intent loginActivity = new Intent(activity, LoginActivity.class);
				loginActivity.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
				activity.startActivity(loginActivity);
				activity.finish();
			}
		}
	}
}
