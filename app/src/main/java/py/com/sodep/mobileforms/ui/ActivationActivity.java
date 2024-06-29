package py.com.sodep.mobileforms.ui;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.content.Intent;

import java.io.Serializable;
import java.util.List;

import io.github.jokoframework.chake.R;
import py.com.sodep.mf.exchange.exceptions.LoginException;
import py.com.sodep.mf.exchange.net.ServerConnection;
import py.com.sodep.mf.exchange.objects.auth.MFAuthenticationResponse;
import py.com.sodep.mf.exchange.objects.metadata.Application;
import py.com.sodep.mobileforms.dataservices.ApplicationsDAO;
import py.com.sodep.mobileforms.dataservices.sql.SQLApplicationsDAO;
import py.com.sodep.mobileforms.net.sync.MetadataSynchronizationHelpers;
import py.com.sodep.mobileforms.settings.AppSettings;
import py.com.sodep.mobileforms.util.PermissionsHelper;

public class ActivationActivity extends Activity {

    private static final String LOG_TAG = ActivationActivity.class.getName();

    private String email = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activation);

        if (savedInstanceState != null) {
            email = savedInstanceState.getString("email");
        }

        TextView versionTextView = findViewById(R.id.versionTextView1);
        if (versionTextView != null) {
            PackageInfo pInfo;
            try {
                pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
                String version = pInfo.versionName;
                versionTextView.setText(version);
            } catch (PackageManager.NameNotFoundException e) {
                Log.e(LOG_TAG, "Error en la activación.", e);
            }
        }

        EditText emailEditText = findViewById(R.id.welcome_01_mailEditText);
        emailEditText.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                emailEditText.setSelection(0);
            }
        });

        Button activateButton = findViewById(R.id.welcome_01_NextButton);
        activateButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (PermissionsHelper.checkAndAskForPermissions(ActivationActivity.this,
                        R.string.permissions_dialog_text,
                        PermissionsHelper.PERMISSIONS)) {
                    email = emailEditText.getText().toString();
                    if (isValidEmail(email)) {
                        activateDevice(email);
                    } else {
                        emailEditText.setError("Ingrese un correo electrónico válido");
                    }
                    forceHideKeyboard(v);
                }
            }
        });

        Button acceptButton = findViewById(R.id.accept_button);
        acceptButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        checkActivationStatus();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    private void forceHideKeyboard(View view) {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    private boolean isValidEmail(CharSequence email) {
        return email != null && android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches();
    }

    private void checkActivationStatus() {
        boolean accountActivated = AppSettings.isAccountActivated(this);
        if(!accountActivated){
            findViewById(R.id.activation_status_layout).setVisibility(View.VISIBLE);
            new CheckActivationTask().execute();
        }else{
            doLogin();
        }

    }

    private void showEmailSentLayout() {
        boolean accountActivated = AppSettings.isAccountActivated(this);
        findViewById(R.id.activation_layout).setVisibility(View.GONE);
        findViewById(R.id.activation_email_sent_layout).setVisibility(View.VISIBLE);
    }

    private class CheckActivationTask extends AsyncTask<Void, Void, Boolean> {
        @Override
        protected Boolean doInBackground(Void... voids) {
            boolean activationStatus = false;
            try {
                ServerConnection serverConnection = ServerConnection.defaultBuilder(ActivationActivity.this);
                activationStatus = serverConnection.getActivationStatus(1L);
            } catch (Exception e) {
                Log.e(LOG_TAG, "Error al comprobar el estado de activación", e);
            }
            return activationStatus;
        }

        @Override
        protected void onPostExecute(Boolean activationStatus) {
            findViewById(R.id.activation_status_layout).setVisibility(View.GONE);
            if (activationStatus) {
                AppSettings.setAccountActivated(ActivationActivity.this, true);
                doLogin();
            } else {
                showActivationScreen();
            }
        }
    }

    private void showActivationScreen() {
        findViewById(R.id.activation_layout).setVisibility(View.VISIBLE);
    }

    private void activateDevice(String email) {
        new ActivateDeviceTask().execute(email);
    }

    private class ActivateDeviceTask extends AsyncTask<String, Void, Boolean> {
        @Override
        protected Boolean doInBackground(String... params) {
            String email = params[0];
            boolean activationSent = false;
            try {
                ServerConnection serverConnection =  ServerConnection.defaultBuilder(ActivationActivity.this);
                activationSent = serverConnection.requestActivateDevice(1L, email);
            } catch (Exception e) {
                Log.e(LOG_TAG, "Error activating device", e);
            }
            return activationSent;
        }

        @Override
        protected void onPostExecute(Boolean activationSent) {
            if (activationSent) {
                Toast.makeText(ActivationActivity.this, "Correo electrónico de activación enviado", Toast.LENGTH_LONG).show();
                showEmailSentLayout();
            } else {
                Toast.makeText(ActivationActivity.this, "No se pudo enviar el correo electrónico de activación", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void launchAppListActivity() {
        Intent i = new Intent(ActivationActivity.this, AppListActivity.class);
        i.putExtra("skipIfOnlyOne", true);
        startActivity(i);
        finish();
    }

    private void doLogin() {
        String user = "chake@feltesq.com";
        String password = "123456";
        String server = AppSettings.DEFAULT_FORM_SERVER_URI;
        new LoginTask().execute(user, password, server);
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

    private class LoginTask extends MFAsyncTask<String, Integer, ActivationActivity.LoginResult> {

        private String user;

        private String password;

        private String server;

        @Override
        protected void onPreExecute() {

        }

        @Override
        protected ActivationActivity.LoginResult doWork(String... params) {
            user = params[0];
            password = params[1];
            server = params[2];

            ServerConnection serverConnection = ServerConnection.builder(ActivationActivity.this, params);
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
                return new ActivationActivity.LoginResult(reachable, null, e);
            }
            // if the server is not reacheable, both will be null
            return new ActivationActivity.LoginResult(reachable, response, null);
        }

        protected void onPostExecute(ActivationActivity.LoginResult result) {
            if (result == null) {
                if (uncaughtException != null) {
                    Bundle b = new Bundle();
                    b.putSerializable("exception", uncaughtException);
                }
                return;
            }

            if (result.getReachable()) {
                MFAuthenticationResponse response = result.getResponse();
                if (response != null) {
                    // the response could be OK or Not Authorized
                    handleServerResponse(ActivationActivity.this, response);
                }
            }

        }

        private void handleServerResponse(ActivationActivity activity, MFAuthenticationResponse response) {
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
                } else {
                    // save list of possible applications
                    ApplicationsDAO applicationsDAO = new SQLApplicationsDAO();
                    applicationsDAO.deleteAllData();
                    MetadataSynchronizationHelpers.saveApps(applicationsDAO, possibleApplications);
                    storeLoginCredentials(user, password, server);
                    launchAppListActivity();
                }
            }
        }
    }

    private void storeLoginCredentials(String user, String password, String server) {
        AppSettings.setUser(ActivationActivity.this, user);
        AppSettings.setPassword(ActivationActivity.this, password);
        AppSettings.setFormServerURI(ActivationActivity.this, server);
        AppSettings.setAppId(ActivationActivity.this, null);
        AppSettings.setLoggedIn(ActivationActivity.this, true);
    }
}
