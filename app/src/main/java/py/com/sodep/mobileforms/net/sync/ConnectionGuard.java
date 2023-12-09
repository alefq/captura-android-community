package py.com.sodep.mobileforms.net.sync;

import java.io.IOException;
import java.io.Serializable;
import java.net.HttpURLConnection;

import py.com.sodep.mf.exchange.exceptions.AuthenticationException;
import py.com.sodep.mf.exchange.net.ServerConnection;
import py.com.sodep.mf.exchange.objects.upload.UploadProgress;
import py.com.sodep.mobileforms.application.BroadcastActions;
import android.content.Intent;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import android.util.Log;

class ConnectionGuard {

	private static final String LOG_TAG = ConnectionGuard.class.getSimpleName();

	private HttpURLConnection c;

	private volatile boolean keepAlive = true;

	private ServerConnection serverConnection;

	public static final int PING_INTERVAL = 3000;

	private static final int MAX_FAILURES = 3;

	private static final int MAX_RECEIVED_COUNT_REPETITION = 20;

	private volatile int failures = 0;

	private volatile int receivedCountRepetition = 0;

	private Object threadLock = new Object();

	private String handle;

	private Object uploadProcessLock = new Object();

	private UploadProgress lastProgress = null;
	
	private LocalBroadcastManager localBroadcastManger;

	ConnectionGuard(ServerConnection serverConnection, LocalBroadcastManager localBroadcastManger) {
		this.serverConnection = serverConnection;
		this.localBroadcastManger = localBroadcastManger;
	}

	private Thread thread;

	private Runnable runnable = new Runnable() {
		public void run() {
			while (keepAlive) {

				try {
					UploadProgress progress = serverConnection.getProgress(handle);
					if (progress != null) {
						switch (progress.getStatus()) {
						case INVALID:
						case REJECTED:
						case SAVED:
						case FAIL:
							gracefullyDisconnect();
							break;
						case COMPLETED:
						case SAVING:
						case PROGRESS:
							if (lastProgress != null && lastProgress.getStatus() == progress.getStatus()
									&& lastProgress.getReceivedBytes() == progress.getReceivedBytes()) {
								receivedCountRepetition++;
							} else {
								receivedCountRepetition = 0;
							}
						}
						Intent intent = new Intent(BroadcastActions.ACTION_UPLOAD_PROGRESS);
						intent.putExtra(UploadProgress.class.getSimpleName(), (Serializable) progress);
						localBroadcastManger.sendBroadcast(intent);
						
						setLastProgress(progress);
						failures = 0;
					} else {
						failures++;
					}
				} catch (AuthenticationException e) {
					forceDisconnect();
				} catch (IOException e) {
					forceDisconnect();
				}

				if (failures == MAX_FAILURES) {
					forceDisconnect();
				}

				if (receivedCountRepetition == MAX_RECEIVED_COUNT_REPETITION) {
					// This means that if the document received bytes didn't
					// change after 5 iteration we are going to declared that the
					// connection is stalled
					forceDisconnect();
				}

				try {
					if (keepAlive) {// FIXME move this block to the end
						Log.d(LOG_TAG, "Guard Thread is going to sleep");
						Thread.sleep(PING_INTERVAL);
					}
				} catch (InterruptedException e) {
				}

			}
			Log.d(LOG_TAG, "Guard Thread is going down");
		}
	};

	public void join() throws InterruptedException {
		Thread t = null;
		synchronized (threadLock) {
			t = thread;
		}
		if (t != null) {
			thread.join();
		}

	}

	public UploadProgress getLastProgress() {
		synchronized (uploadProcessLock) {
			return lastProgress;
		}
	}

	public void setLastProgress(UploadProgress lastProgress) {
		synchronized (uploadProcessLock) {
			this.lastProgress = lastProgress;
		}

	}

	private void gracefullyDisconnect() {
		Log.d(LOG_TAG, "Gracefully disconnect connection");
		keepAlive = false;
	}

	private void forceDisconnect() {
		Log.d(LOG_TAG, "Force disconnect connection");
		c.disconnect();
		keepAlive = false;
	}

	public boolean hasFailed() {
		return failures == MAX_FAILURES;
	}

	void guardConnection(HttpURLConnection c, String handle) {
		synchronized (threadLock) {
			if (this.thread != null && thread.isAlive()) {
				releaseGuard();
			}
			Log.d(LOG_TAG, "Guard connection " + c);
			this.keepAlive = true;
			this.c = c;
			this.handle = handle;
			this.receivedCountRepetition = 0;
			this.failures = 0;
			thread = new Thread(runnable);
			thread.start();
		}
	}

	void releaseGuard() {
		synchronized (threadLock) {
			Log.d(LOG_TAG, "Releasing guard thread");
			this.keepAlive = false;
			if (thread != null) {
				thread.interrupt();
			}
		}
		try {
			join();
		} catch (InterruptedException e) {
		}
	}
}
