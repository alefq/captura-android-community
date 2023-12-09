package py.com.sodep.mobileforms.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnFocusChangeListener;
import android.view.inputmethod.InputMethodManager;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.TextView;

import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.io.Serializable;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.SSLHandshakeException;

import io.github.jokoframework.chake.BuildConfig;
import io.github.jokoframework.chake.MFApplication;
import io.github.jokoframework.chake.R;
import py.com.sodep.mf.exchange.exceptions.AuthenticationException;
import py.com.sodep.mf.exchange.exceptions.AuthenticationException.FailCause;
import py.com.sodep.mf.exchange.exceptions.LoginException;
import py.com.sodep.mf.exchange.net.ServerConnection;
import py.com.sodep.mf.exchange.objects.auth.MFAuthenticationResponse;
import py.com.sodep.mf.exchange.objects.metadata.Application;
import py.com.sodep.mobileforms.dataservices.ApplicationsDAO;
import py.com.sodep.mobileforms.dataservices.sql.SQLApplicationsDAO;
import py.com.sodep.mobileforms.net.sync.MetadataSynchronizationHelpers;
import py.com.sodep.mobileforms.settings.AppSettings;
import py.com.sodep.mobileforms.util.PermissionsHelper;

public class LoginActivity extends Activity implements CompoundButton.OnCheckedChangeListener {

    private static final String LOG_TAG = LoginActivity.class.getName();

    // private static final String LOG_TAG =
    // LoginActivity.class.getSimpleName();

    private int layout = 0;

    private String user = null;

    private String password = null;

    private static volatile LoginActivity currentActivity;

    private ApplicationsDAO applicationsDAO = new SQLApplicationsDAO();


    private static class DialogID {

        private static final int LOGIN_PROGRESS = 1000;

        private static final int SERVER_UNREACHABLE = 1001;

        private static final int INVALID_LOGIN = 1002;

        private static final int DELETE_EXISTING_DATA = 1003;

        private static final int UNSUCCESSFUL_LOGIN_NO_COOKIE = 2001;

        private static final int UNSUCCESSFUL_LOGIN_INVALID_SERVER_RESPONSE = 2002;

        private static final int UNSUCCESSFUL_LOGIN_TOO_MANY_DEVICES = 2003;

        private static final int UNSUCCESSFUL_LOGIN_IOEXCEPTION = 2004;

        private static final int UNSUCCESSFUL_LOGIN_UNCAUGHT_EXCEPTION = 2005;

        private static final int UNSUCCESSFUL_LOGIN_INVALID_CERTIFIATE = 2006;

        private static final int DATABASE_COPIED = 2007;

    }

    //------- Instrumentation to handle number of clicks on default server checkbox --------//
    //Require X clicks in Y seconds to trigger secret action
    final double SECONDS_FOR_CLICKS = 3;
    final int NUM_CLICKS_REQUIRED = 5;

    //List treated circularly to track last NUM_CLICKS_REQUIRED number of clicks
    long[] clickTimestamps = new long[NUM_CLICKS_REQUIRED];
    int oldestIndex = 0;
    int nextIndex = 0;
    //-------------------------------------------------------------------------------------//

    // FIXME a hack because dialog are restored
    // FIXME copy-pasted
    private void removeAndShowDialog(int id, Bundle args) {
        try {
            this.removeDialog(id);
        } catch (Exception e) {

        }
        this.showDialog(id, args);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("currentLayout", layout);
        outState.putString("user", user);
        outState.putSerializable("password", password);
    }

    @Override
    public void onResume() {
        currentActivity = this;
        super.onResume();
		if (StringUtils.isNoneBlank(BuildConfig.CHAKE_USERNAME, BuildConfig.CHAKE_PASSWORD)) {
			user = BuildConfig.CHAKE_USERNAME;
			password = BuildConfig.CHAKE_PASSWORD;
            if (PermissionsHelper.checkAndAskForPermissions(this,
                    R.string.permissions_dialog_text,
                    PermissionsHelper.PERMISSIONS)) {
                doLogin(AppSettings.DEFAULT_FORM_SERVER_URI);
            }
		}
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.login);

        if (savedInstanceState != null) {
            layout = savedInstanceState.getInt("currentLayout");
            if (layout == 1) {
                setupServerLayout();
            }

            user = savedInstanceState.getString("user");
            password = savedInstanceState.getString("password");
        }

        TextView versionTextView = (TextView) findViewById(R.id.versionTextView1);
        TextView versionTextView2 = (TextView) findViewById(R.id.versionTextView2);
        if (versionTextView != null) {
            PackageInfo pInfo;
            try {
                pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
                String version = pInfo.versionName;
                versionTextView.setText(version);
                versionTextView2.setText(version);
            } catch (NameNotFoundException e) {
                Log.e(LOG_TAG, "Error en el login.", e);
            }

        }

        final EditText userEditText = (EditText) findViewById(R.id.welcome_01_mailEditText);
        userEditText.setOnFocusChangeListener(new OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                userEditText.setSelection(0);
            }
        });

        if (user == null) {
            user = AppSettings.getUser(this);
        }

        if (user != null) {
            userEditText.setText(user);
        }

        if (password == null) {
            password = AppSettings.getPassword(this);
        }

        if (password != null) {
            EditText passwordEditText = (EditText) findViewById(R.id.welcome_01_passwordEditText);
            passwordEditText.setText(password);
        }

        String serverURI = AppSettings.getFormServerURI(this);
        boolean checked = serverURI == null || serverURI.trim().length() == 0
                || serverURI.equals(AppSettings.DEFAULT_FORM_SERVER_URI);
        CheckBox defaultServerCheckbox = (CheckBox) findViewById(R.id.welcome_01_defaultServerCheckBox);
        defaultServerCheckbox.setChecked(checked);

        // Easter egg handling, to copy sql lite database
        defaultServerCheckbox.setOnCheckedChangeListener(this);
    }


    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        long timeMillis = (new Date()).getTime();

        // If we have at least the min number of clicks on record
        if (nextIndex == (NUM_CLICKS_REQUIRED - 1) || oldestIndex > 0) {
            // Check that all required clicks were in required time
            int diff = (int) (timeMillis - clickTimestamps[oldestIndex]);
            if (diff < SECONDS_FOR_CLICKS * 1000) {
                doCopySQL();
            }
            oldestIndex++;
        }

        // If not done, record click time, and bump indices
        clickTimestamps[nextIndex] = timeMillis;
        nextIndex++;

        if (nextIndex == NUM_CLICKS_REQUIRED) {
            nextIndex = 0;
        }

        if (oldestIndex == NUM_CLICKS_REQUIRED) {
            oldestIndex = 0;
        }

    }

    private void doCopySQL() {
        try {
            String file = MFApplication.sqlHelper.copyDataBase();
            String message = getString(R.string.database_copied, file);
            Bundle bundle = new Bundle();
            bundle.putString("message", message);
            removeAndShowDialog(DialogID.DATABASE_COPIED, bundle);
        } catch (IOException e) {
            Log.e(LOG_TAG, e.getMessage(), e);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    public void next(View view) {
        if (view.getId() == R.id.welcome_01_NextButton) {
            EditText userEditText = (EditText) findViewById(R.id.welcome_01_mailEditText);
            EditText passwordEditText = (EditText) findViewById(R.id.welcome_01_passwordEditText);
            user = userEditText.getText().toString();
            password = passwordEditText.getText().toString();

            boolean validationError = false;
            if (user.trim().length() == 0) {
                userEditText.setError("User cannot be empty");
                validationError = true;
            }

            if (password.trim().length() == 0) {
                passwordEditText.setError("Password cannot be empty");
                validationError = true;
            }

            if (validationError) {
                return;
            }

            CheckBox defaultServerCheckbox = (CheckBox) findViewById(R.id.welcome_01_defaultServerCheckBox);
            boolean defaultServer = defaultServerCheckbox.isChecked();
            if (!defaultServer) {
                setupServerLayout();
                return;
            } else {
                if (PermissionsHelper.checkAndAskForPermissions(this,
                        R.string.permissions_dialog_text,
                        PermissionsHelper.PERMISSIONS)) {
                    // if the default server was chosen we proceed with that user
                    String server = AppSettings.DEFAULT_FORM_SERVER_URI;
                    doLogin(server);
                    forceHideKeyboard(view);
                }
            }

        } else if (view.getId() == R.id.welcome_02_NextButton) {
            EditText serverEditText = (EditText) findViewById(R.id.welcome_02_serverEditText);
            String server = serverEditText.getText().toString();
            try {
                if (PermissionsHelper.checkAndAskForPermissions(this,
                        R.string.permissions_dialog_text,
                        PermissionsHelper.PERMISSIONS)) {
                    server = servetransform1864(server);
                    doLogin(server);
                    forceHideKeyboard(view);
                }
            } catch (InvalidServerURLException e) {
                serverEditText.setError("Invalid server");
            }

        }
    }

    private static final String URL_REGEX = "^(https?://)?([a-zA-Z0-9\\.\\-]+)(:\\d+)?(/?)([a-zA-Z0-9\\.\\-]+)?$";

    private Pattern serverPattern = Pattern.compile(URL_REGEX);

    private class InvalidServerURLException extends Exception {

        /**
         *
         */
        private static final long serialVersionUID = 1L;

    }

    // 1864 is the ticket number that requested this
    private String servetransform1864(String server) throws InvalidServerURLException {
        Matcher m = serverPattern.matcher(server);
        if (!m.matches()) {
            throw new InvalidServerURLException();
        }

        String protocol = m.group(1);
        if (protocol == null) {
            protocol = "https://";
        }
        String serverName = m.group(2);
        String port = m.group(3);
        if (port == null) {
            port = ":" + AppSettings.DEFAULT_PORT;
        }

        String context = m.group(5);
        if (context == null) {
            context = AppSettings.DEFAULT_CONTEXT;
        }

        return protocol + serverName + port + "/" + context;
    }

    private void doLogin(String server) {
        AppSettings.setAppId(this, null); // to force App Selection
        LoginTask task = new LoginTask();
        task.execute(user, password, server);
    }

    private void forceHideKeyboard(View view) {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    private void setupServerLayout() {
        layout = 1;
        View welcome01 = findViewById(R.id.welcome01_layout);
        welcome01.setVisibility(View.GONE);
        View welcome02 = findViewById(R.id.welcome02_layout);
        welcome02.setVisibility(View.VISIBLE);
        EditText serverEditText = (EditText) findViewById(R.id.welcome_02_serverEditText);
        serverEditText.requestFocus();
        serverEditText.setSelection(0);
        final TextView plainHttpTextView = (TextView) findViewById(R.id.plain_http_warningTextView);
        serverEditText.addTextChangedListener(new TextWatcher() {

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                String str = s.toString();
                Matcher matcher = serverPattern.matcher(str);
                if (matcher.matches() && str.startsWith("http://")) {
                    plainHttpTextView.setVisibility(View.VISIBLE);
                } else {
                    plainHttpTextView.setVisibility(View.GONE);
                }
            }
        });
        String newServerURI = serverEditText.getText().toString();
        String serverURI = AppSettings.getFormServerURI(this);
        serverURI = serverURI == null ? AppSettings.DEFAULT_FORM_SERVER_URI : serverURI;
        if ((newServerURI == null || newServerURI.trim().length() == 0) && serverURI != null
                && serverURI.trim().length() != 0) {
            serverEditText.setText(serverURI);
        }
    }

    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0) {
            View welcome02 = findViewById(R.id.welcome02_layout);
            if (welcome02.getVisibility() == View.VISIBLE) {
                setupUserLayout();
                return true;
            }

            View welcome01 = findViewById(R.id.welcome01_layout);
            if (welcome01.getVisibility() == View.VISIBLE) {
                finish();
                return true;
            }

        }
        return super.onKeyDown(keyCode, event);

    }

    private void setupUserLayout() {
        layout = 0;
        View welcome02 = findViewById(R.id.welcome02_layout);
        welcome02.setVisibility(View.GONE);
        View welcome01 = findViewById(R.id.welcome01_layout);
        welcome01.setVisibility(View.VISIBLE);
        EditText userEditText = (EditText) findViewById(R.id.welcome_01_mailEditText);
        userEditText.requestFocus();
    }

    @Override
    protected Dialog onCreateDialog(int d) {
        return onCreateDialog(d, null);
    }

    @Override
    protected Dialog onCreateDialog(int d, Bundle b) {
        switch (d) {
            case DialogID.LOGIN_PROGRESS:
                return loginProgressDialog();
            case DialogID.DELETE_EXISTING_DATA:
                return deleteExistingDataDialog(b);
            case DialogID.SERVER_UNREACHABLE:
                return newSimpleDismissableDialog(getString(R.string.server_unreachable),
                        getString(R.string.server_unreachable));
            case DialogID.INVALID_LOGIN:
                return newSimpleDismissableDialog(getString(R.string.invalid_login_title),
                        getString(R.string.invalid_login_message));
            case DialogID.UNSUCCESSFUL_LOGIN_IOEXCEPTION:
                return newSimpleDismissableDialog(getString(R.string.unsuccessful_login_ioexception_title),
                        getString(R.string.unsuccessful_login_ioexception_message));
            case DialogID.UNSUCCESSFUL_LOGIN_NO_COOKIE:
                return newSimpleDismissableDialog(getString(R.string.unsuccessful_login_no_cookie_title),
                        getString(R.string.unsuccessful_login_no_cookie_message));
            case DialogID.UNSUCCESSFUL_LOGIN_INVALID_SERVER_RESPONSE:
                return newSimpleDismissableDialog(getString(R.string.unsuccessful_login_response_code_not_200_title),
                        getString(R.string.unsuccessful_login_response_code_not_200_message));
            case DialogID.UNSUCCESSFUL_LOGIN_TOO_MANY_DEVICES:
                return newSimpleDismissableDialog(getString(R.string.unsuccessful_login_too_many_devices_title),
                        getString(R.string.unsuccessful_login_too_many_devices_message));
            case DialogID.UNSUCCESSFUL_LOGIN_UNCAUGHT_EXCEPTION:
                Serializable serializable = b.getSerializable("exception");
                if (serializable != null && serializable instanceof Exception) {
                    Exception e = (Exception) serializable;
                    return ActivityUtils.uncaughtExceptionDialog(this, e);
                }
            case DialogID.UNSUCCESSFUL_LOGIN_INVALID_CERTIFIATE:
                return newSimpleDismissableDialog(getString(R.string.invalid_certificate_title),
                        getString(R.string.invalid_certificate_message));

            case DialogID.DATABASE_COPIED:
                String message = b.getString("message");
                return databaseCopiedDialog(message);
        }

        return null;
    }

    private Dialog databaseCopiedDialog(String message) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        builder.setTitle(R.string.database_copied_title);
        builder.setIcon(android.R.drawable.ic_dialog_info);
        builder.setMessage(message);
        builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                dialog.dismiss();
            }
        });
        return builder.create();
    }

    private Dialog deleteExistingDataDialog(final Bundle bundle) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        builder.setTitle(R.string.existing_data_will_be_deleted_title);
        builder.setIcon(android.R.drawable.ic_dialog_alert);
        builder.setMessage(R.string.existing_data_will_be_deleted_message);
        builder.setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                dialog.dismiss();
            }
        });

        builder.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {

            public void onClick(DialogInterface dialog, int id) {
                ApplicationsDAO applicationsDAO = new SQLApplicationsDAO();
                applicationsDAO.deleteAllData();

                String user = bundle.getString("user");
                String password = bundle.getString("password");
                String server = bundle.getString("server");
                List<Application> possibleApplications = (List<Application>) bundle.getSerializable("possibleApplications");
                MetadataSynchronizationHelpers.saveApps(applicationsDAO, possibleApplications);
                storeLoginCredentials(user, password, server);
                launchAppListActivity();
            }
        });

        return builder.create();
    }

    private Dialog newSimpleDismissableDialog(String title, String message) {
        return ActivityUtils.newSimpleDismissableDialog(this, title, message);
    }

    private Dialog loginProgressDialog() {
        ProgressDialog dialog = new ProgressDialog(this);
        dialog.setIndeterminate(true);
        dialog.setTitle(R.string.logging_in_title);
        dialog.setMessage(getString(R.string.logging_in_message));
        dialog.setCancelable(false);
        return dialog;
    }

    private void launchAppListActivity() {
        Intent i = new Intent(LoginActivity.this, AppListActivity.class);
        i.putExtra("skipIfOnlyOne", true);
        startActivity(i);
        finish();
    }

    private static class LoginResult {

        private Boolean reachable;

        private MFAuthenticationResponse response;

        private LoginException exception;

        public LoginResult(Boolean reachable, MFAuthenticationResponse response, LoginException exception) {
            this.reachable = reachable;
            this.response = response;
            this.exception = exception;
        }

        public Boolean getReachable() {
            return reachable;
        }

        public LoginException getException() {
            return exception;
        }

        public MFAuthenticationResponse getResponse() {
            return response;
        }
    }

    private static class LoginTask extends MFAsyncTask<String, Integer, LoginResult> {

        private String user;

        private String password;

        private String server;

        @Override
        protected void onPreExecute() {
            currentActivity.showDialog(DialogID.LOGIN_PROGRESS);
        }

        @Override
        protected LoginResult doWork(String... params) {
            LoginActivity activity = currentActivity;

            user = params[0];
            password = params[1];
            server = params[2];

            ServerConnection serverConnection = ServerConnection.builder(activity, params);
            MFAuthenticationResponse response = null;
            boolean reachable = serverConnection.isServerReachable();
            try {
                if (reachable) {
                    // if the server is reachable, let's try to login
                    response = serverConnection.login(null);
                    // we can get a response from the server or an exception
                }
            } catch (LoginException e) {
                // if there's an exception, then response from the server is null
                return new LoginResult(reachable, null, e);
            }
            // if the server is not reacheable, both will be null
            return new LoginResult(reachable, response, null);
        }

        protected void onPostExecute(LoginResult result) {
            LoginActivity activity = currentActivity;

            try {
                activity.removeDialog(DialogID.LOGIN_PROGRESS);
            } catch (Exception e) {
            }

            if (result == null) {
                if (uncaughtException != null) {
                    Bundle b = new Bundle();
                    b.putSerializable("exception", uncaughtException);
                    activity.removeAndShowDialog(DialogID.UNSUCCESSFUL_LOGIN_UNCAUGHT_EXCEPTION, b);
                }
                return;
            }

            if (result.getReachable()) {
                MFAuthenticationResponse response = result.getResponse();
                if (response != null) {
                    // the response could be OK or Not Authorized
                    handleServerResponse(activity, response);
                } else {
                    // if the server was reachable and there is no response
                    // an exception was thrown
                    LoginException e = result.getException();
                    if (e != null) {
                        handleLoginException(activity, e);
                    } else {
                        throw new RuntimeException("No server response or login exception");
                    }
                }
            } else {
                activity.showDialog(DialogID.SERVER_UNREACHABLE);
            }

        }

        private void handleLoginException(LoginActivity activity, LoginException e) {
            Throwable throwableCause = e.getCause();
            if (throwableCause instanceof AuthenticationException) {
                AuthenticationException authenticationException = (AuthenticationException) throwableCause;
                FailCause failCause = authenticationException.getFailCause();
                switch (failCause) {
                    case INVALID_USER:
                        // this shouldn't be reached
                        activity.showDialog(DialogID.INVALID_LOGIN);
                        break;
                    case NO_COOKIE:
                        activity.showDialog(DialogID.UNSUCCESSFUL_LOGIN_NO_COOKIE);
                        break;
                    case INVALID_SERVER_RESPONSE:
                        activity.showDialog(DialogID.UNSUCCESSFUL_LOGIN_INVALID_SERVER_RESPONSE);
                        break;
                }
            } else if (throwableCause instanceof SSLHandshakeException) {
                activity.showDialog(DialogID.UNSUCCESSFUL_LOGIN_INVALID_CERTIFIATE);
            } else if (throwableCause instanceof IOException) {
                activity.showDialog(DialogID.UNSUCCESSFUL_LOGIN_IOEXCEPTION);
            }
        }

        private void handleServerResponse(LoginActivity activity, MFAuthenticationResponse response) {
            String savedUser = AppSettings.getUser(activity);
            String savedServer = AppSettings.getFormServerURI(activity);
            if (response.isSuccess()) {
                List<Application> possibleApplications = response.getPossibleApplications();
                if ((savedUser != null && !savedUser.equals(user))
                        || (savedServer != null && !savedServer.equals(server))) {
                    Bundle b = new Bundle();
                    b.putString("user", user);
                    b.putString("password", password);
                    b.putString("server", server);
                    // we don't yet save the list of applications because the user or server has
                    // changed and we first should ask him if he wants to delete the current data
                    b.putSerializable("possibleApplications", (Serializable) possibleApplications);
                    activity.removeAndShowDialog(DialogID.DELETE_EXISTING_DATA, b);
                } else {
                    // save list of possible applications
                    MetadataSynchronizationHelpers.saveApps(activity.applicationsDAO, possibleApplications);
                    activity.storeLoginCredentials(user, password, server);
                    activity.launchAppListActivity();
                }
            } else {
                activity.showDialog(DialogID.INVALID_LOGIN);
            }
        }
    }

    private void storeLoginCredentials(String user, String password, String server) {
        AppSettings.setUser(LoginActivity.this, user);
        AppSettings.setPassword(LoginActivity.this, password);
        AppSettings.setFormServerURI(LoginActivity.this, server);
        AppSettings.setAppId(LoginActivity.this, null);
        AppSettings.setLoggedIn(LoginActivity.this, true);
    }
}
