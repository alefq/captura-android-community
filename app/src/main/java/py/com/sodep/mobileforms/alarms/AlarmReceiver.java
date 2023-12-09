package py.com.sodep.mobileforms.alarms;

import py.com.sodep.mobileforms.net.sync.services.SyncService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import androidx.core.content.ContextCompat;

/**
 * This receiver is meant for starting the DataSync service at the given
 * intervals
 * 
 * http://informationideas.com/news/2012/03/06/how-to-keep-an-android-service-
 * running/
 * 
 * @author Miguel
 * 
 */
public class AlarmReceiver extends BroadcastReceiver {

	private static final String LOG_TAG = AlarmReceiver.class.getSimpleName();

	@Override
	public void onReceive(Context context, Intent intent) {
		// Log.d(LOG_TAG, "Alarm Received");
		Intent sendDataService = new Intent(context, SyncService.class);
        ContextCompat.startForegroundService(context, sendDataService);
	}

}
