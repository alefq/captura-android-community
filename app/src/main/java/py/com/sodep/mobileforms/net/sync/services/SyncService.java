package py.com.sodep.mobileforms.net.sync.services;

import static py.com.sodep.mobileforms.application.BroadcastActions.ACTION_SYNC_FAILED;
import static py.com.sodep.mobileforms.application.BroadcastActions.ACTION_UPLOAD_FINISHED;
import static py.com.sodep.mobileforms.application.BroadcastActions.ACTION_UPLOAD_STARTED;


import py.com.sodep.mf.exchange.LoggerFactory;
import py.com.sodep.mf.exchange.exceptions.HttpResponseException;
import py.com.sodep.mf.exchange.net.MetadataAndLookupTableSynchronizer;
import py.com.sodep.mf.exchange.net.ServerConnection;
import py.com.sodep.mf.exchange.net.ServerResponse;
import py.com.sodep.mf.exchange.objects.error.ErrorResponse;
import io.github.jokoframework.chake.R;
import py.com.sodep.mobileforms.application.BroadcastActions;
import py.com.sodep.mobileforms.exceptions.NoConnectionInfoException;
import py.com.sodep.mobileforms.log.AndroidLogger;
import py.com.sodep.mobileforms.net.sync.DocumentSyncResult;
import py.com.sodep.mobileforms.net.sync.DocumentsSync;
import py.com.sodep.mobileforms.net.sync.MetadataAndLookupTableSynchronizerImpl;
import py.com.sodep.mobileforms.net.sync.SyncSummary;

import android.annotation.TargetApi;
import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import android.util.Log;


public class SyncService extends IntentService {

	private static final int SYNCYING_DATA_NOTIFICATION = 1;

	private static final int SYNC_OUTCOME_NOTIFICATION = 2;

	private static final int icon = R.drawable.ic_notification;
	private static final String CHANNEL_ID = "Notification";
	private static final int NOTIFICATION_ID = 999;
    private static final String LOG_TAG = SyncService.class.getSimpleName();

    private LocalBroadcastManager localBroadcastManager;
    private NotificationManager notifManager;

	private static final String[] ACTIONS = new String[] { ACTION_UPLOAD_STARTED, ACTION_UPLOAD_FINISHED,
			ACTION_SYNC_FAILED };

	BroadcastReceiver receiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			if (intent.getAction().equals(ACTION_UPLOAD_STARTED)) {
				syncStartedNotification(icon);
			} else if (intent.getAction().equals(ACTION_UPLOAD_FINISHED)) {
				dismissSyncingProgressNotification();
			} else if (intent.getAction().equals(ACTION_SYNC_FAILED)) {
				dismissSyncingProgressNotification();
			}
		}

		private void dismissSyncingProgressNotification() {
			NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
			notificationManager.cancel(SYNCYING_DATA_NOTIFICATION);
		}
	};

	static {
		// instruct the LoggerFactory to use the AndroidLogger
		LoggerFactory.initialize(AndroidLogger.class);
	}

	public SyncService() {
		super(SyncService.class.getSimpleName());
	}

	@Override
	public void onCreate() {
		super.onCreate();
		localBroadcastManager = LocalBroadcastManager.getInstance(this);
		for (String action : ACTIONS) {
			IntentFilter intent = new IntentFilter(action);
			localBroadcastManager.registerReceiver(receiver, intent);
		}
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            int NOTIFICATION_ID = (int) (System.currentTimeMillis()%10000);
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
					CHANNEL_ID,
                    NotificationManager.IMPORTANCE_DEFAULT);

            ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).createNotificationChannel(channel);

            Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setContentTitle("")
                    .setContentText("").build();

            startForeground(NOTIFICATION_ID, notification);
        }
	}

	@Override
	public void onDestroy() {
		try {
			localBroadcastManager.unregisterReceiver(receiver);
		} catch (IllegalArgumentException e) {

		}
		super.onDestroy();
	}

	/*
	 * IMPORTANT: Only one Intent is processed at a time, but the processing
	 * happens on a worker thread that runs independently from other application
	 * logic. So, if this code takes a long time, it will hold up other requests
	 * to the same IntentService, but it will not hold up anything else
	 * (non-Javadoc)
	 * 
	 * @see android.app.IntentService#onHandleIntent(android.content.Intent)
	 */
	@Override
	protected void onHandleIntent(Intent intent) {
        broadcast(BroadcastActions.ACTION_UPLOAD_STARTED);
		long when = System.currentTimeMillis();
		int notificationFlags = Notification.FLAG_SHOW_LIGHTS | Notification.FLAG_AUTO_CANCEL;

		String contentTitle = getString(R.string.app_name);
        boolean broadcastedUploadFinished = false;
        boolean broadcastedSyncMetadataFinished = false;
        try {
            ServerConnection serverConnection = ServerConnection.getInstance(getBaseContext());
            uploadDocuments(serverConnection, icon, when, notificationFlags, contentTitle);
            broadcast(BroadcastActions.ACTION_UPLOAD_FINISHED);
            broadcastedUploadFinished = true;
            broadcast(BroadcastActions.ACTION_SYNC_METADATA_STARTED);
            downloadMetadataAndLookupTables(serverConnection);
            broadcast(BroadcastActions.ACTION_SYNC_METADATA_FINISHED);
            broadcastedSyncMetadataFinished = true;
        } catch (NoConnectionInfoException e) {
            // Is there a real need to  report this exception?
            Log.e(LOG_TAG, "Error intentando conectar con el servidor.", e);

        } finally {
            if (!broadcastedUploadFinished) {
                broadcast(BroadcastActions.ACTION_UPLOAD_FINISHED);
            } else {
                if (!broadcastedSyncMetadataFinished) {
                    broadcast(BroadcastActions.ACTION_SYNC_METADATA_FINISHED);
                }
            }
        }
    }

    private void downloadMetadataAndLookupTables(ServerConnection serverConnection) {
		MetadataAndLookupTableSynchronizerImpl syncImpl = new MetadataAndLookupTableSynchronizerImpl(this);
		MetadataAndLookupTableSynchronizer metadataAndLookupTableSynchronizer = new MetadataAndLookupTableSynchronizer(
				serverConnection, syncImpl, syncImpl, syncImpl, syncImpl);
		ServerResponse serverResponse = metadataAndLookupTableSynchronizer.syncMetadataAndLookupTables();
		// TODO use the server response to set the synchronization parameters
		// like polling time
	}

	private void syncStartedNotification(int icon) {
		NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(this,
                CHANNEL_ID);
        final String contentTitle = this.getString(R.string.app_name);
		mBuilder.setSmallIcon(icon);
		mBuilder.setOngoing(true);
		mBuilder.setProgress(100, 0, true);
		mBuilder.setTicker(this.getString(R.string.sync_started_ticker));
        mBuilder.setContentTitle(contentTitle);
		mBuilder.setContentInfo(this.getString(R.string.sync_in_progress));
		mBuilder.setContentIntent(getContentIntent());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            configureChannel(contentTitle);
        }
		getNotifManager().notify(SYNCYING_DATA_NOTIFICATION, mBuilder.build());
	}

	private PendingIntent getContentIntent() {
		Intent i = new Intent(this, SyncService.class);
		PendingIntent contentIntent = PendingIntent.getService(this, 0, i, PendingIntent.FLAG_ONE_SHOT);
		return contentIntent;
	}

    private void uploadDocuments(ServerConnection serverConnection, int icon, long when, int notificationFlags, String contentTitle) {
        if (serverConnection.isServerReachable()) {
            DocumentsSync dataSync = new DocumentsSync(this, serverConnection);

            SyncSummary syncSummary = dataSync.syncAll();

            if (syncSummary.wasThereATimeout()) { // OK
                showServerUnreachableNotification(icon, when, notificationFlags, contentTitle);
            }

            if (syncSummary.wasThereAnAuthException()) { // OK
                showInvalidAuthNotification(icon, when, notificationFlags, contentTitle);
            }

            if (syncSummary.wasThereAServerException()) {
                showServerErrorNotification(icon, when, notificationFlags, contentTitle, syncSummary);
            }

            if (syncSummary.wasThereANoHandle()) {
                showNoHandleErrorNotification(icon, when, notificationFlags, contentTitle);
            }

            if (syncSummary.getSuccessCount() > 0 || syncSummary.getRejectCount() > 0) {
                showDataSentNotification(icon, when, notificationFlags, contentTitle, syncSummary);
            }

            if (syncSummary.wasThereAnUnexpectedException()) {
                showUnexpectedExceptionNotification(icon, when, notificationFlags, contentTitle, syncSummary);
            }
        }
    }

	private void showNoHandleErrorNotification(int icon, long when, int notificationFlags, String contentTitle) {
		showNotification(icon, when, notificationFlags, contentTitle,
                getString(R.string.no_handle), contentTitle);
	}

	private void showNotification(int icon, long when, int notificationFlags, String contentTitle,
                                  String contentText, String tickerText) {
		Context context = getApplicationContext();
		PendingIntent contentIntent = getContentIntent();
        final int sdkInt = Build.VERSION.SDK_INT;
        NotificationCompat.Builder mBuilder = getBuilder(icon, contentTitle, contentText,
                tickerText, contentIntent);
        NotificationCompat.Builder builder;

        if (sdkInt >= Build.VERSION_CODES.O) {
            configureChannel(contentTitle);
            getNotifManager().notify(NOTIFICATION_ID, mBuilder.build());
        } else {
            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
            // notificationId is a unique int for each notification that you must define
            notificationManager.notify(NOTIFICATION_ID, mBuilder.build());
        }
	}

	@TargetApi(Build.VERSION_CODES.O)
    private void configureChannel(String contentTitle) {
        CharSequence name = CHANNEL_ID;
        String description = contentTitle;
        int importance = NotificationManager.IMPORTANCE_DEFAULT;
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
        channel.setDescription(description);
        // Register the channel with the system; you can't change the importance
        // or other notification behaviors after this
        getNotifManager().createNotificationChannel(channel);
    }

    private NotificationCompat.Builder getBuilder(int icon, String contentTitle, String contentText, String tickerText, PendingIntent contentIntent) {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                        .setSmallIcon(icon)
                        .setContentTitle(contentTitle)
                        .setContentText(contentText)
                        .setStyle(new NotificationCompat.BigTextStyle()
                                .bigText(contentText))
                        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                        .setTicker(tickerText).setContentIntent(contentIntent);
    }

    private void showUnexpectedExceptionNotification(int icon, long when, int notificationFlags, String contentTitle,
			SyncSummary syncSummary) {
		String exception = getString(R.string.unexpected_error);
		DocumentSyncResult result = syncSummary.getFirstUnexpectedException();
		Exception e = result.getThrownException();
		String contentText = e.getClass().getSimpleName();
		if (e instanceof HttpResponseException) {
			HttpResponseException ex = (HttpResponseException) e;
			ErrorResponse errorResponse = ex.getErrorResponse();
			if (errorResponse != null) {
				String messageInDefaultLanguage = errorResponse.getMessageInDefaultLanguage();
				if (messageInDefaultLanguage != null) {
					contentText = messageInDefaultLanguage;
				}
			}
		}
	    showNotification(icon, when, notificationFlags, contentTitle, contentText, exception);
	}

	private void showInvalidAuthNotification(int icon, long when, int notificationFlags, String contentTitle) {
		showNotification(icon, when, notificationFlags, contentTitle, getString(R.string.invalid_user), contentTitle);
	}

	private void showServerUnreachableNotification(int icon, long when, int notificationFlags, String contentTitle) {
	    String serverUnreachable = getString(R.string.server_unreachable);
        showNotification(icon, when, notificationFlags, contentTitle, serverUnreachable,
                serverUnreachable);
	}

	private void showServerErrorNotification(int icon, long when, int notificationFlags, String contentTitle,
			SyncSummary syncResult) {

		String tickerText = this.getString(R.string.server_error);
		String contentText = this.getString(R.string.server_error);

		DocumentSyncResult result = syncResult.getFirstServerException();

		if (result.getStatusCode() != 0) {
			contentText += "/ " + this.getString(R.string.response_code) + " = " + result.getStatusCode();
		} else {
			Exception e = result.getThrownException();
			if (e != null) {
				String exceptionName = e.getClass().getSimpleName();
				contentText += "/ " + exceptionName + " ";
				String msg = e.getMessage();
				if (msg != null) {
					contentText += " - " + msg;
				}
			}
		}
		showNotification(icon, when, notificationFlags, contentTitle, contentText, tickerText);
	}

	private void showDataSentNotification(int icon, long when, int notificationFlags, String contentTitle,
			SyncSummary syncResult) {
	    boolean saved = syncResult.getSuccessCount() > 0;
		boolean rejected = syncResult.getRejectCount() > 0;
		String tickerText;
		if (!rejected) {
			tickerText = getString(R.string.data_sent_notification);
		} else {
			tickerText = getString(R.string.data_sent_with_error_notification);
		}

		String contentText = "";

		int nSynced = syncResult.getSuccessCount();
		int nRejected = syncResult.getRejectCount();

		if (nSynced == 1) {
			contentText = this.getString(R.string.one_entry_synced);
		} else if (nSynced > 1) {
			contentText = this.getString(R.string.entries_synced, nSynced);
		}

		if (nRejected == 1) {
			contentText += (saved ? " / " : "") + this.getString(R.string.one_entry_rejected);
		} else if (nRejected > 1) {
			contentText += (saved ? " / " : "") + this.getString(R.string.entries_rejected, nRejected);
		}

        showNotification(icon, when, notificationFlags, contentTitle, contentText, tickerText);
	}

	private void notify(Notification notification) {
		NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		notificationManager.cancel(SYNC_OUTCOME_NOTIFICATION);
		notificationManager.notify(SYNC_OUTCOME_NOTIFICATION, notification);
	}

    private void broadcast(String action) {
        Intent i = new Intent();
        i.setAction(action);
        localBroadcastManager.sendBroadcast(i);
    }

    public NotificationManager getNotifManager() {
        if (notifManager == null) {
            notifManager = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
        }
        return notifManager;
    }

    public void setNotifManager(NotificationManager notifManager) {
        this.notifManager = notifManager;
    }
}
