package io.github.jokoframework.chake;


import py.com.sodep.mf.exchange.LoggerFactory;
import py.com.sodep.mf.exchange.net.ConnectionParameters;
import py.com.sodep.mf.exchange.net.ServerConnection;
import py.com.sodep.mobileforms.alarms.AlarmReceiver;
import py.com.sodep.mobileforms.dataservices.sql.SodepSQLiteOpenHelper;
import py.com.sodep.mobileforms.exceptions.NoConnectionInfoException;
import py.com.sodep.mobileforms.log.AndroidLogger;
import py.com.sodep.mobileforms.settings.AppSettings;

import android.app.AlarmManager;
import android.app.Application;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.SystemClock;
import android.util.Log;


public class MFApplication extends Application {

    public static SodepSQLiteOpenHelper sqlHelper;

    static {
        // instruct the LoggerFactory to use the AndroidLogger
        LoggerFactory.initialize(AndroidLogger.class);
    }

    private static final String LOG_TAG = MFApplication.class.getSimpleName();

    // An alarm is triggered to synchronize data with the server
    // Any unsent data will be sent. Also, forms will be downloaded
    private static final int ALARM_REPEAT_MIN = 5;

    private boolean alarmsInitialized = false;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(LOG_TAG, "Application created");
        sqlHelper = new SodepSQLiteOpenHelper(this);
    }

    public synchronized void startSync() {
        if (!alarmsInitialized) {
            Log.d(LOG_TAG, "init sync alarm");
            AlarmManager mgr = (AlarmManager) this.getSystemService(ALARM_SERVICE);
            Intent intent = new Intent(this, AlarmReceiver.class);
            PendingIntent pi = PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_CANCEL_CURRENT);
            mgr.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime(),
                    ALARM_REPEAT_MIN * 60 * 1000, pi);
            alarmsInitialized = true;
        }
    }

    public synchronized void stopSync() {
        Log.d(LOG_TAG, "cancel sync alarm");
        Intent intent = new Intent(this, AlarmReceiver.class);
        PendingIntent sender = PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_CANCEL_CURRENT);
        if (sender != null) {
            AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
            alarmManager.cancel(sender);
        }
        alarmsInitialized = false;
    }

}
