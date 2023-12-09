package py.com.sodep.mobileforms.ui;

import java.io.IOException;
import java.io.Serializable;
import java.util.List;

import py.com.sodep.mf.exchange.exceptions.AuthenticationException;
import py.com.sodep.mf.exchange.exceptions.AuthenticationException.FailCause;
import py.com.sodep.mf.exchange.exceptions.HttpResponseException;
import py.com.sodep.mf.exchange.exceptions.SodepException;
import py.com.sodep.mf.exchange.net.ServerConnection;
import py.com.sodep.mf.exchange.objects.metadata.Application;
import io.github.jokoframework.chake.R;
import py.com.sodep.mobileforms.dataservices.ApplicationsDAO;
import py.com.sodep.mobileforms.dataservices.sql.SQLApplicationsDAO;
import py.com.sodep.mobileforms.net.sync.MetadataSynchronizationHelpers;
import py.com.sodep.mobileforms.settings.AppSettings;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import py.com.sodep.ui.common.Eula;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListAdapter;
import android.widget.ListView;

import com.google.firebase.crashlytics.FirebaseCrashlytics;


public class AppListActivity extends AppCompatActivity {

    // private static final String LOG_TAG =
    // AppListActivity.class.getSimpleName();

    private static class DialogID {

        private static final int DOWNLOAD_FAIL = 1;

        private static final int DOWNLOAD_PROGRESS = 2;

        private static final int VERIFYING_DEVICE = 3;

        private static final int VERIFICATION_FAILED = 4;

        private static final int UNCAUGHT_EXCEPTION = 5;

    }

    private boolean skipIfOnlyOne;

    private ApplicationsDAO applicationsDAO = new SQLApplicationsDAO();

    private static volatile AppListActivity currentActivity;

    // FIXME a hack because dialog are restored
    private void removeAndShowDialog(int id, Bundle args) {
        try {
            this.removeDialog(id);
        } catch (Exception e) {

        }
        this.showDialog(id, args);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.app_list);

        if (savedInstanceState != null) {
            restoreMembers(savedInstanceState);
        } else {
            Intent intent = getIntent();
            skipIfOnlyOne = intent.getBooleanExtra("skipIfOnlyOne", false);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    private void restoreMembers(Bundle savedInstanceState) {
        skipIfOnlyOne = savedInstanceState.getBoolean("skipIfOnlyOne");
    }

    @Override
    public void onResume() {
        currentActivity = this;
        super.onResume();
        // downloadApplicationList();
        showAppList();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean("skipIfOnlyOne", skipIfOnlyOne);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.applist_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.item_settings:
                Intent intent = new Intent(this, SettingsPreferenceActivity.class);
                startActivity(intent);
                break;
            case R.id.item_sync:
                downloadApplicationList();
                break;
        }
        return false;
    }

    private void downloadApplicationList() {
        String user = AppSettings.getUser(this);
        String password = AppSettings.getPassword(this);
        String server = AppSettings.getFormServerURI(this);

        DownloadAppListTask task = new DownloadAppListTask();
        task.execute(user, password, server);
    }

    private static class DownloadAppListResult implements Serializable {

        /**
         *
         */
        private static final long serialVersionUID = 1L;

        private List<Application> applications;

        private Result result;

        private Exception exception;

        public DownloadAppListResult() {
            /*Constructor público*/
        }

        public DownloadAppListResult(Result result) {
            this.result = result;
        }

        public enum Result {
            SUCCESS, AUTHENTICATION_EXCEPTION, IO_EXCEPTION, SERVER_UNREACHABLE, EXCEPTION,
            UNEXPECTED_SERVER_RESPONSE, APPLICATIONS_LIST_EMPTY
        }

    }

    private static class DownloadAppListTask extends MFAsyncTask<String, Integer, DownloadAppListResult> {

        @Override
        protected void onPreExecute() {
            currentActivity.showDialog(DialogID.DOWNLOAD_PROGRESS);
        }

        @Override
        protected DownloadAppListResult doWork(String... params) {
            AppListActivity activity = currentActivity;

            ServerConnection serverConnection = ServerConnection.builder(currentActivity, params);
            DownloadAppListResult response = new DownloadAppListResult();
            try {
                boolean serverReachable = serverConnection.isServerReachable();
                if (serverReachable) {
                    response.applications = serverConnection.getApplications();
                    if (!response.applications.isEmpty()) {
                        MetadataSynchronizationHelpers.saveApps(activity.applicationsDAO, response.applications);
                        response.result = DownloadAppListResult.Result.SUCCESS;
                    } else {
                        response.result = DownloadAppListResult.Result.APPLICATIONS_LIST_EMPTY;
                    }
                } else {
                    response.result = DownloadAppListResult.Result.SERVER_UNREACHABLE;
                }
            } catch (AuthenticationException e) {
                response.result = DownloadAppListResult.Result.AUTHENTICATION_EXCEPTION;
                response.exception = e;
            } catch (HttpResponseException e) {
                response.result = DownloadAppListResult.Result.UNEXPECTED_SERVER_RESPONSE;
                response.exception = e;
            } catch (IOException e) {
                response.result = DownloadAppListResult.Result.IO_EXCEPTION;
                response.exception = e;
            } catch (Exception e) {
                response.result = DownloadAppListResult.Result.EXCEPTION;
                response.exception = e;
            }

            return response;
        }

        protected void onPostExecute(DownloadAppListResult result) {
            AppListActivity activity = currentActivity;

            try {
                activity.removeDialog(DialogID.DOWNLOAD_PROGRESS);
            } catch (Exception e) {

            }

            if (result == null) {
                if (uncaughtException != null) {
                    Bundle b = new Bundle();
                    b.putSerializable("exception", uncaughtException);
                    activity.removeAndShowDialog(DialogID.UNCAUGHT_EXCEPTION, b);
                }
                return;
            }

            switch (result.result) {
                case SERVER_UNREACHABLE:
                case AUTHENTICATION_EXCEPTION:
                case IO_EXCEPTION:
                case EXCEPTION:
                case UNEXPECTED_SERVER_RESPONSE:
                    // show a warning message,
                    // the user should be able to enter previously used apps
                    Bundle bundle = new Bundle();
                    bundle.putSerializable("result", result);

                    activity.removeAndShowDialog(DialogID.DOWNLOAD_FAIL, bundle);
                    break;
                case SUCCESS:
                    // just show the app list
                    activity.showAppList();
                    break;
            }
        }
    }

    /**
     * Start the validation process of a given application and launch it only if
     * the user has enough rights and available license.
     *
     * @param application
     */
    private void launchApplication(Application application) {
        VerificationAndSelectionTask task = new VerificationAndSelectionTask();
        task.execute(application);
    }

    private static class VerificationResponse implements Serializable {

        /**
         *
         */
        private static final long serialVersionUID = 1L;

        private enum Result {
            SUCCESS, AUTHENTICATION_EXCEPTION, IO_EXCEPTION, SERVER_UNREACHABLE, RESPONSE_CODE_NOT_200
        }

        private Result result;

        private Exception exception;

        private Application application;

    }

    private static class VerificationAndSelectionTask extends MFAsyncTask<Application, Integer, VerificationResponse> {

        @Override
        protected void onPreExecute() {
            currentActivity.showDialog(DialogID.VERIFYING_DEVICE);
        }

        @Override
        protected VerificationResponse doWork(Application... params) {
            AppListActivity activity = currentActivity;

            ServerConnection serverConnection = ServerConnection.getInstance(activity);

            VerificationResponse response = new VerificationResponse();
            response.application = params[0];
            Long applicationId = response.application.getId();

            try {
                boolean serverReachable = serverConnection.isServerReachable();
                if (serverReachable) {
                    serverConnection.deviceVerification(applicationId);
                    response.result = VerificationResponse.Result.SUCCESS;
                    activity.applicationsDAO.setDeviceVerified(applicationId, true);
                } else {
                    response.result = VerificationResponse.Result.SERVER_UNREACHABLE;
                }
            } catch (AuthenticationException e) {
                response.result = VerificationResponse.Result.AUTHENTICATION_EXCEPTION;
                response.exception = e;
            } catch (HttpResponseException e) {
                response.result = VerificationResponse.Result.RESPONSE_CODE_NOT_200;
                response.exception = e;
            } catch (IOException e) {
                response.result = VerificationResponse.Result.IO_EXCEPTION;
                response.exception = e;
            }

            return response;
        }

        @Override
        protected void onPostExecute(VerificationResponse result) {
            AppListActivity activity = currentActivity;

            try {
                activity.removeDialog(DialogID.VERIFYING_DEVICE);
            } catch (Exception e) {

            }

            if (result == null) {
                if (uncaughtException != null) {
                    Bundle b = new Bundle();
                    b.putSerializable("exception", uncaughtException);
                    activity.removeAndShowDialog(DialogID.UNCAUGHT_EXCEPTION, b);
                }
                return;
            }

            if (result != null) {
                switch (result.result) {
                    case AUTHENTICATION_EXCEPTION:
                    case IO_EXCEPTION:
                    case SERVER_UNREACHABLE:
                    case RESPONSE_CODE_NOT_200:
                        // show a warning message,
                        // the user should be able to enter previously used apps
                        Bundle bundle = new Bundle();
                        bundle.putSerializable("result", result);
                        activity.removeAndShowDialog(DialogID.VERIFICATION_FAILED, bundle);
                        break;
                    case SUCCESS:
                        // The verification was successful, so continue to the
                        // application
                        activity.setSelectedApplication(result.application);
                        activity.startMainActivity();
                        break;
                }
            }
        }

    }

    private void setSelectedApplication(Application application) {
        Long appId = application.getId();
        AppSettings.setAppId(this, appId);
    }

    private void startMainActivity() {
        Intent i = new Intent(this, MainActivity.class);
        startActivity(i);
        finish();
    }

    private void showAppList() {
        List<Application> list = applicationsDAO.listApplications();

        final ListView listView = findViewById(R.id.appListView);
        ListAdapter adapter = new ArrayAdapter<>(this, R.layout.list_item, R.id.list_item_textView, list);
        listView.setAdapter(adapter);
        listView.setDivider(new ColorDrawable(getColor(R.color.mf_actionbar_background_color1)));
        listView.setDividerHeight(1);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Application application = (Application) listView.getItemAtPosition(position);
                launchApplication(application);
            }
        });

        if (skipIfOnlyOne && list.size() == 1) {
            Application application = list.get(0);
            launchApplication(application);
        } else if (list.size() == 0) {
            Bundle b = new Bundle();
            b.putSerializable("result", new DownloadAppListResult(DownloadAppListResult.Result.APPLICATIONS_LIST_EMPTY));
            currentActivity.removeAndShowDialog(DialogID.DOWNLOAD_FAIL, b);
        }
    }

    @Override
    protected Dialog onCreateDialog(int id) {
        return onCreateDialog(id, null);
    }

    @Override
    protected Dialog onCreateDialog(int id, Bundle bundle) {
        Dialog dialog = null;
        switch (id) {
            case DialogID.DOWNLOAD_PROGRESS:
                ProgressDialog progressDialog = new ProgressDialog(this);
                progressDialog.setTitle(getString(R.string.updating_application_list_title));
                progressDialog.setMessage(getString(R.string.updating_application_list_message));
                progressDialog.setIndeterminate(true);
                progressDialog.setCancelable(false);
                dialog = progressDialog;
                break;
            case DialogID.DOWNLOAD_FAIL:
                DownloadAppListResult downloadAppListResponse = (DownloadAppListResult) bundle
                        .getSerializable("result");
                dialog = downloadAppListFailedDialog(downloadAppListResponse);
                break;
            case DialogID.VERIFYING_DEVICE:
                progressDialog = new ProgressDialog(this);
                progressDialog.setTitle(getString(R.string.verifying_device_title));
                progressDialog.setMessage(getString(R.string.verifying_device_message));
                progressDialog.setIndeterminate(true);
                progressDialog.setCancelable(false);
                dialog = progressDialog;
                break;
            case DialogID.VERIFICATION_FAILED:
                VerificationResponse verificationResponse = (VerificationResponse) bundle.getSerializable("result");
                dialog = verificationFailedDialog(verificationResponse);
                break;
            case DialogID.UNCAUGHT_EXCEPTION:
                Serializable serializable = bundle.getSerializable("exception");
                if (serializable != null && serializable instanceof Exception) {
                    Exception e = (Exception) serializable;
                    dialog = ActivityUtils.uncaughtExceptionDialog(this, e);
                }
                break;
        }
        return dialog;
    }

    private Dialog verificationFailedDialog(VerificationResponse response) {
        FirebaseCrashlytics crashlytics = FirebaseCrashlytics.getInstance();
        boolean tryAgain = false;
        String message = getString(R.string.verification_failed);

        switch (response.result) {
            case AUTHENTICATION_EXCEPTION:
                AuthenticationException exception = (AuthenticationException) response.exception;
                FailCause failCause = exception.getFailCause();
                switch (failCause) {
                    case INVALID_USER:
                        tryAgain = false;
                        message = getString(R.string.sync_failed_authentication_error);
                        break;
                    case NO_COOKIE:
                        tryAgain = true;
                        message = getString(R.string.sync_failed_session_error_try_again);
                        crashlytics.recordException(exception);
                        break;
                    case INVALID_SERVER_RESPONSE:
                        tryAgain = true;
                        message = getString(R.string.sync_failed_http_error_try_again);
                        crashlytics.recordException(exception);
                        break;
                }

                break;
            case IO_EXCEPTION:
                message = getString(R.string.connection_failed);
                crashlytics.recordException(new SodepException(message));
                break;
            case SERVER_UNREACHABLE:
                tryAgain = true;
                message = getString(R.string.server_unreachable_try_again);
                crashlytics.recordException(new SodepException(message));
                break;
            case RESPONSE_CODE_NOT_200:
                message = ActivityUtils.getMessage(this, (HttpResponseException) response.exception);
                crashlytics.recordException(new SodepException(message));
                break;
            case SUCCESS:
                throw new RuntimeException("The verification was successful, #verificationFailedDialog shouldn't be called");
        }

        return verificationFailedDialog(tryAgain, message, response.application);
    }

    private Dialog verificationFailedDialog(boolean tryAgain, String message, final Application application) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.sync_error);
        builder.setMessage(message);
        builder.setCancelable(false);
        int negativeButtonTextid = R.string.no;
        if (tryAgain) {
            builder.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    dialog.dismiss();
                    launchApplication(application);
                }
            });
        } else {
            negativeButtonTextid = R.string.ok;
        }
        builder.setNegativeButton(negativeButtonTextid, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                dialog.dismiss();
            }
        });

        return builder.create();
    }

    private Dialog downloadAppListFailedDialog(DownloadAppListResult response) {
        FirebaseCrashlytics crashlytics = FirebaseCrashlytics.getInstance();
        boolean tryAgain = false;
        String message = getString(R.string.sync_failed);

        switch (response.result) {
            case AUTHENTICATION_EXCEPTION:
                AuthenticationException exception = (AuthenticationException) response.exception;
                FailCause failCause = exception.getFailCause();
                switch (failCause) {
                    case INVALID_USER:
                        tryAgain = false;
                        message = getString(R.string.sync_failed_authentication_error);
                        break;
                    case NO_COOKIE:
                        tryAgain = true;
                        message = getString(R.string.sync_failed_session_error_try_again);
                        crashlytics.recordException(exception);
                        break;
                    case INVALID_SERVER_RESPONSE:
                        tryAgain = true;
                        message = getString(R.string.sync_failed_http_error_try_again);
                        crashlytics.recordException(exception);
                        break;
                }

                break;
            case IO_EXCEPTION:
                message = getString(R.string.connection_failed);
                crashlytics.recordException(new SodepException(message));
                break;
            case SERVER_UNREACHABLE:
                tryAgain = true;
                message = getString(R.string.server_unreachable_try_again);
                break;
            case SUCCESS:
                break;
            case UNEXPECTED_SERVER_RESPONSE:
                HttpResponseException ex = (HttpResponseException) response.exception;
                message = getString(R.string.server_response_not_200, ex.getResponseCode(), ex.getURL());
                crashlytics.recordException(new SodepException(message));
                break;
            case APPLICATIONS_LIST_EMPTY:
                tryAgain = false;
                message = getString(R.string.sync_applications_not_active);
            case EXCEPTION:
                break;
        }

        return downloadAppListFailedDialog(tryAgain, message);
    }

    private Dialog downloadAppListFailedDialog(boolean tryAgain, String message) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.sync_error);
        builder.setMessage(message);
        builder.setCancelable(false);
        int negativeButtonTextid = R.string.no;
        if (tryAgain) {
            builder.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    dialog.dismiss();
                    downloadApplicationList();
                }
            });
        } else {
            negativeButtonTextid = R.string.ok;
        }
        builder.setNegativeButton(negativeButtonTextid, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                dialog.dismiss();
                AppSettings.setLoggedIn(currentActivity, false);
                Intent i = new Intent(currentActivity, LoginActivity.class);
                i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                currentActivity.startActivity(i);
            }
        });

        return builder.create();
    }

}
