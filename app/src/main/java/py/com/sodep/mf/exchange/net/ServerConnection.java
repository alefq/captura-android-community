package py.com.sodep.mf.exchange.net;

import android.content.Context;
import android.net.ConnectivityManager;
import androidx.annotation.NonNull;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Map;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import io.github.jokoframework.chake.BuildConfig;
import py.com.sodep.mf.exchange.LoggerFactory;
import py.com.sodep.mf.exchange.MFLogger;
import py.com.sodep.mf.exchange.MFLoookupTableDefinition;
import py.com.sodep.mf.exchange.TXInfo;
import py.com.sodep.mf.exchange.activation.ActivationRequest;
import py.com.sodep.mf.exchange.activation.ActivationResponse;
import py.com.sodep.mf.exchange.activation.ActivationStatusResponse;
import py.com.sodep.mf.exchange.device.info.DeviceInfo;
import py.com.sodep.mf.exchange.exceptions.AuthenticationException;
import py.com.sodep.mf.exchange.exceptions.AuthenticationException.FailCause;
import py.com.sodep.mf.exchange.exceptions.HttpResponseException;
import py.com.sodep.mf.exchange.exceptions.LoginException;
import py.com.sodep.mf.exchange.objects.auth.MFAuthenticationRequest;
import py.com.sodep.mf.exchange.objects.auth.MFAuthenticationResponse;
import py.com.sodep.mf.exchange.objects.device.MFDevice;
import py.com.sodep.mf.exchange.objects.error.ErrorResponse;
import py.com.sodep.mf.exchange.objects.lookup.MFDMLTransportMultiple;
import py.com.sodep.mf.exchange.objects.metadata.Application;
import py.com.sodep.mf.exchange.objects.metadata.Form;
import py.com.sodep.mf.exchange.objects.upload.UploadHandle;
import py.com.sodep.mf.exchange.objects.upload.UploadProgress;
import py.com.sodep.mobileforms.exceptions.NoConnectionInfoException;
import py.com.sodep.mobileforms.settings.AppSettings;

import java.util.List;

public class ServerConnection {

	private static final MFLogger logger = LoggerFactory.getLogger(ServerConnection.class);

	private static final String JSON_CONTENT_TYPE = "application/json; charset=utf-8";

	private static final String POST = "POST";

    private static final String GET = "GET";

	private static final String encoding = "UTF-8";

	private final ObjectMapper objectMapper = new ObjectMapper();

	private final Context context;

	private final ConnectionParameters params;

	private static final Object LOGIN_LOCK = new Object();

	private static String J_SESSSION_ID = null;

	private static ServerConnection _INSTANCE;

	public ServerConnection(Context context, ConnectionParameters params) {
		this.context = context;
		this.params = new ConnectionParameters(params.getBaseURL(), params.getUser(), params.getPassword(),
				params.getApplicationId());
	}

	// always verify the host - don't check for certificate
	final static HostnameVerifier DO_NOT_VERIFY = new HostnameVerifier() {
		public boolean verify(String hostname, SSLSession session) {
			return true;
		}
	};

	/**
	 * Trust every server - don't check for any certificate
	 */
	private static void trustAllHosts() {
		// Create a trust manager that does not validate certificate chains
		TrustManager[] trustAllCerts = new TrustManager[] { new X509TrustManager() {
			public java.security.cert.X509Certificate[] getAcceptedIssuers() {
				return new java.security.cert.X509Certificate[] {};
			}

			public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
			}

			public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
			}
		} };

		// Install the all-trusting trust manager

		SSLContext sc;
		try {
			sc = SSLContext.getInstance("TLS");
			sc.init(null, trustAllCerts, new java.security.SecureRandom());
			HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
		} catch (KeyManagementException e) {
			throw new RuntimeException(e);
		}

	}

	protected HttpURLConnection openConnection(String path) throws IOException {
		return openConnection(path, GET);
	}

	/**
	 * A new connection is open every time by calling this method. If the cookie
	 * {@link #J_SESSSION_ID} is not null, it will be submitted. The request
	 * method is POST.
	 * 
	 * @param path
	 * @return
	 * @throws IOException
	 */
	protected HttpURLConnection openConnection(String path, String method) throws IOException {
		return openConnection(path, method, false);
	}

    protected HttpURLConnection openConnection(String pathOrUrl, String method, boolean isAbosolute) throws IOException {
        HttpURLConnection c;
        if(isAbosolute){
            c = openAbsoluteURLIgnoringCertificates(pathOrUrl);
        } else {
            c = openPathIgnoringCertificates(pathOrUrl);
        }

        synchronized (LOGIN_LOCK) {
            if (J_SESSSION_ID != null) {
                c.setRequestProperty("cookie", J_SESSSION_ID);
            }
        }
        c.setRequestMethod(method);
        if (!method.equals(GET)) {
            c.setDoOutput(true);
        }
        c.setReadTimeout(ConnectionSettings.READ_TIMEOUT);
        c.setConnectTimeout(ConnectionSettings.CONNECT_TIMEOUT);
        return c;
    }

	private HttpURLConnection openPathIgnoringCertificates(String path) throws MalformedURLException, IOException {
		return newConnection(this.params.getBaseURL() + path);
	}

    private HttpURLConnection openAbsoluteURLIgnoringCertificates(String url) throws MalformedURLException, IOException {
        return newConnection(url);
    }

    private static HttpURLConnection newConnection(String absoluteURL) throws MalformedURLException, IOException {
        URL u = new URL(absoluteURL);
        HttpURLConnection c = null;
        if (u.getProtocol().toLowerCase().equals("https")) {
            boolean trustAllHosts = ConnectionSettings.TRUST_ALL_HOSTS;
            if (trustAllHosts) {
                trustAllHosts();
            }
            HttpsURLConnection https = (HttpsURLConnection) u.openConnection();
            if (trustAllHosts) {
                https.setHostnameVerifier(DO_NOT_VERIFY);
            }
            c = https;
        } else {
            c = (HttpURLConnection) u.openConnection();
        }
        return c;
    }

    MFAuthenticationResponse doLogin() throws IOException, AuthenticationException {
        synchronized (LOGIN_LOCK) {
            HttpURLConnection c = openConnection(ServerPaths.login(), POST);
            try {
                MFAuthenticationResponse response = requestLogin(c);
                if(response.isSuccess()) {
                    storeCookie(c);
                }
                return response;
            } catch (HttpResponseException e) {
                if (e.getResponseCode() == HttpURLConnection.HTTP_UNAUTHORIZED) {
                    MFAuthenticationResponse response = new MFAuthenticationResponse();
                    response.setSuccess(false);
                    return response;
                }
                AuthenticationException exception = new AuthenticationException("Server response was " + e.getResponseCode());
                exception.setResponseCode(e.getResponseCode());
                exception.setFailCause(FailCause.INVALID_SERVER_RESPONSE);
                throw exception;
            }
        }
    }

    public List<Form> getForms() throws  IOException, HttpResponseException {
        HttpURLConnection connection = getLoggedConnection(ServerPaths.formListPath());
        checkResponseCode(connection);
        InputStream in = connection.getInputStream();
        try {
            List<Form> list = objectMapper.readValue(in, new TypeReference<List<Form>>() {
            });
            return list;
        } finally {
            close(in);
        }
    }

	public MFAuthenticationResponse login(Long applicationId) throws LoginException {
		try {
            params.setApplicationId(applicationId);
            return doLogin();
        } catch (AuthenticationException e) {
			throw new LoginException(e.getMessage(), e);
		} catch (IOException e) {
			throw new LoginException(e.getMessage(), e);
		}
	}

    private void storeCookie(HttpURLConnection c) throws AuthenticationException {
        String cookie = getCookie(c);
        if (cookie == null && J_SESSSION_ID == null) {
            AuthenticationException authenticationException = new AuthenticationException("No cookie sent by the server");
            authenticationException.setFailCause(FailCause.NO_COOKIE);
            throw authenticationException;
        } else if (cookie != null) {
            // A new cookie
            String[] cookieValues = cookie.split(";");
            J_SESSSION_ID = cookieValues[0];
        }
    }

	private MFAuthenticationResponse requestLogin(HttpURLConnection c) throws IOException, UnsupportedEncodingException, HttpResponseException {
        c.setRequestProperty("Content-Type", JSON_CONTENT_TYPE);
        OutputStream outputStream = c.getOutputStream();
		try {
            MFAuthenticationRequest request = new MFAuthenticationRequest();
            request.setUser(this.params.getUser());
            request.setPassword(this.params.getPassword());
            request.setApplicationId(this.params.getApplicationId());
            objectMapper.writeValue(outputStream, request);
            checkResponseCode(c);
            InputStream in = c.getInputStream();
            try {
                MFAuthenticationResponse response = objectMapper.readValue(in, MFAuthenticationResponse.class);
                return response;
            } finally {
                close(in);
            }
		} finally {
			close(outputStream);
		}
	}

	public List<Application> getApplications() throws  IOException, HttpResponseException {
		String path = ServerPaths.applicationListPath();
		HttpURLConnection connection = getLoggedConnection(path);
		checkResponseCode(connection);
		InputStream in = connection.getInputStream();
		try {
			List<Application> list = objectMapper.readValue(in, new TypeReference<List<Application>>() {
			});
			return list;
		} finally {
			close(in);
		}
	}

	public void deviceVerification(Long appId) throws IOException, HttpResponseException {
		String path = ServerPaths.deviceVerificationPath();
		HttpURLConnection connection = openConnection(path, POST);
		connection.setRequestProperty("Content-Type", JSON_CONTENT_TYPE);
		MFDevice device = getMFDevice(appId);
		if (device.getDeviceInfo().getIdentifier() == null) {
			// if we couldn't get an identifier, then it is better to stop
			throw new RuntimeException("Unsupported device. Couldn't detect the identifier of this device.");
		}
		OutputStream outputStream = connection.getOutputStream();
		try {
			objectMapper.writeValue(outputStream, device);
			checkResponseCode(connection);
		} finally {
			close(outputStream);
		}
	}

	public boolean getActivationStatus(Long appId) throws IOException, HttpResponseException {
		String path = ServerPaths.getActivationStatus();
		HttpURLConnection connection = openConnection(path, POST);
		connection.setRequestProperty("Content-Type", JSON_CONTENT_TYPE);

		MFDevice device = getMFDevice(appId);
		if (device.getDeviceInfo().getIdentifier() == null) {
			// if we couldn't get an identifier, then it is better to stop
			throw new RuntimeException("Unsupported device. Couldn't detect the identifier of this device.");
		}

		OutputStream outputStream = connection.getOutputStream();
		try {
			objectMapper.writeValue(outputStream, device);
			checkResponseCode(connection);

			InputStream in = connection.getInputStream();
			try {
				ActivationStatusResponse response = objectMapper.readValue(in, ActivationStatusResponse.class);
				return response.isActive();
			} finally {
				close(in);
			}
		} finally {
			close(outputStream);
		}
	}

	public boolean requestActivateDevice(Long appId, String email) throws IOException, HttpResponseException {
		String path = ServerPaths.getAccountActivate();
		HttpURLConnection connection = openConnection(path, POST);
		connection.setRequestProperty("Content-Type", JSON_CONTENT_TYPE);

		MFDevice device = getMFDevice(appId);
		if (device.getDeviceInfo().getIdentifier() == null) {
			// if we couldn't get an identifier, then it is better to stop
			throw new RuntimeException("Unsupported device. Couldn't detect the identifier of this device.");
		}

		OutputStream outputStream = connection.getOutputStream();
		try {
			ActivationRequest request = new ActivationRequest(device, email);
			objectMapper.writeValue(outputStream, request);
			checkResponseCode(connection);

			InputStream in = connection.getInputStream();
			try {
				ActivationResponse response = objectMapper.readValue(in, ActivationResponse.class);
				return response.isSentEmail();
			} finally {
				close(in);
			}
		} finally {
			close(outputStream);
		}
	}

	private void close(Closeable closeable) {
		try {
			if (closeable != null) {
				closeable.close();
			}
		} catch (IOException e) {
		}
	}

	private void flush(Writer w) {
		try {
			w.flush();
		} catch (IOException e) {
		}
	}

	private MFDevice getMFDevice(Long appId) {
		DeviceInfo deviceInfo = new DeviceInfo(context);
		MFDevice device = new MFDevice();
		device.setApplicationId(appId);
		device.setDeviceInfo(deviceInfo.getMFDeviceInfo());
		return device;
	}

	private HttpURLConnection getLoggedConnection(String path) throws IOException, AuthenticationException {
		return getLoggedConnection(path, GET);
	}

    private HttpURLConnection getLoggedConnection(String path, String method) throws IOException,
            AuthenticationException {
        HttpURLConnection connection = openConnection(path, method);
		/*
		 * But GET requests are supposed to have no content... by writing to the
		 * connections output stream you are changing the nature of the request
		 * to a POST.
		 */
        if (connection.getResponseCode() == HttpURLConnection.HTTP_UNAUTHORIZED) {
            doLogin();
            connection = openConnection(path, method);
        }
        return connection;
    }

	public Form getFormDefinition(long formId, long version) throws  IOException,
			HttpResponseException {
		String url =  ServerPaths.formDefinitionPath(formId, version);

		HttpURLConnection connection = getLoggedConnection(url);
		checkResponseCode(connection);
		InputStream in = connection.getInputStream();
		try {
			Form form = objectMapper.readValue(in, Form.class);
			return form;
		} finally {
			close(in);
		}
	}

	public MFLoookupTableDefinition getLookupTableDefinition(Long lookupTable) throws HttpResponseException,
			IOException {
		String url = ServerPaths.lookupDefinition(lookupTable);
		HttpURLConnection connection = getLoggedConnection(url);
		checkResponseCode(connection);
		InputStream in = connection.getInputStream();
		try {
			MFLoookupTableDefinition lookup = objectMapper.readValue(in, MFLoookupTableDefinition.class);
			return lookup;
		} finally {
			close(in);
		}
	}

	public MFDMLTransportMultiple getDataFast(Long lookupTableId, TXInfo txInfo) throws
             IOException,  HttpResponseException {
		logger.debug("Checking if lookupTable #" + lookupTableId + " is synced. Data will be downloaded if necessary");
		String url = ServerPaths.lookupData();
		// No need to call getLoggedConnection. There's no way to
		// reach this point without proper login.
		// calling getLoggedConnection will give an Exception
		HttpURLConnection connection = openConnection(url, POST);
		sendJSON(connection, txInfo);
		return parseResponse(connection, MFDMLTransportMultiple.class);
	}

	private <T> T parseResponse(HttpURLConnection connection, Class<T> expectedClass) throws IOException,
			HttpResponseException, JsonMappingException {
		checkResponseCode(connection);
		InputStream in = connection.getInputStream();
		try {
			connection.getResponseCode();
			T dmlTransportMultiple = objectMapper.readValue(in, expectedClass);
			return dmlTransportMultiple;
		} finally {
			close(in);
		}
	}

	private void checkResponseCode(HttpURLConnection connection) throws IOException, HttpResponseException {
		int responseCode = connection.getResponseCode();
		if (responseCode != HttpURLConnection.HTTP_OK) {
			HttpResponseException httpResponseException = new HttpResponseException();
			try {
				InputStream errorStream = connection.getErrorStream();
				ErrorResponse errorResponse = objectMapper.readValue(errorStream, ErrorResponse.class);
				httpResponseException.setErrorResponse(errorResponse);
			} catch (Exception e) {
				logger.error(e.getMessage(), e);
			}
			httpResponseException.setResponseCode(responseCode);
			httpResponseException.setURL(connection.getURL().toExternalForm());
			throw httpResponseException;
		}
	}

    private void sendJSON(HttpURLConnection connection, Object object) throws IOException {
        connection.setRequestProperty("Content-Type", JSON_CONTENT_TYPE);
        String txInfoJSON = objectMapper.writeValueAsString(object);
        OutputStream outputStream = connection.getOutputStream();
        try {
            OutputStreamWriter writer = new OutputStreamWriter(outputStream);
            writer.write(txInfoJSON);
            flush(writer);
        } finally {
            close(outputStream);
        }
    }

	private String getCookie(HttpURLConnection c) {
		String cookie = c.getHeaderField("Set-Cookie");
		if (cookie == null) {
			cookie = c.getHeaderField("set-cookie");
		}
		return cookie;
	}

    public UploadHandle requestUploadHandle(String deviceId, Long formId, String documentId, long fileSize)
            throws AuthenticationException, IOException, HttpResponseException {
        HttpURLConnection connection = getLoggedConnection(
                ServerPaths.uploadHandle(formId, documentId, deviceId, fileSize), POST);
        InputStream in = null;
        try {
            checkResponseCode(connection);
            in = connection.getInputStream();
            UploadHandle handle = objectMapper.readValue(in, UploadHandle.class);
            return handle;
        } finally {
            close(in);
        }
    }

	public UploadProgress getProgress(String handle) throws AuthenticationException, IOException {
		HttpURLConnection connection = getLoggedConnection(ServerPaths.uploadStatus(handle));
		int responseCode = connection.getResponseCode();
		if (responseCode != HttpURLConnection.HTTP_OK) {
			return null;
		} else {
			InputStream in = null;
			try {
				in = connection.getInputStream();
				return objectMapper.readValue(in, UploadProgress.class);
			} finally {
				close(in);
			}
		}
	}

	public HttpURLConnection uploadConnection(UploadHandle uploadHandle) throws IOException {
        final String method = "PUT";
        String handle = uploadHandle.getHandle();
        String location = uploadHandle.getLocation();
        HttpURLConnection c;
        if (location == null) {
            c = openConnection(ServerPaths.uploadFile(handle), method, false);
        } else {
            c = openConnection(location, method, true);
        }
        return c;
    }

	/**
	 * This method tests the connection to the server and gets the default
	 * connection settings from the server
	 * 
	 * It's more or less like a ping in which also default settings are returned
	 * by the server
	 * 
	 * @return
	 */
	public ServerResponse testConnectionAndGetSettings() {
		ConnectivityManager conMgr = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
		ServerResponse response = new ServerResponse();
		if (conMgr.getActiveNetworkInfo() == null || !conMgr.getActiveNetworkInfo().isAvailable()
				|| !conMgr.getActiveNetworkInfo().isConnected()) {
			response.setCause("No active network connection"); // TODO i18n
		}

		try {
			HttpURLConnection c = openPathIgnoringCertificates(ServerPaths.settingsPath());
			c.setConnectTimeout(ConnectionSettings.PING_CONNECT_TIMEOUT);
			c.setReadTimeout(ConnectionSettings.PING_READ_TIMEOUT);
			int responseCode = c.getResponseCode();
			if (responseCode == HttpURLConnection.HTTP_OK) {
				response.setReachable(true);
				InputStream in = c.getInputStream();
				try {
					Map<String, String> settings = objectMapper.readValue(in, new TypeReference<Map<String, String>>() {
					});
					response.setSettings(settings);
				} finally {
					in.close();
				}
			} else if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
				// backwards compatibility, older versions of the server don't
				// implement settings
				boolean reachable = isServerReachable();
				response.setReachable(reachable);
			}
		} catch (Exception e) {
			logger.debug(e.getMessage(), e);
			response.setCause(e.getMessage());
		}

		return response;
	}

	public boolean isServerReachable() {
		ConnectivityManager conMgr = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

		if (conMgr == null || conMgr.getActiveNetworkInfo() == null || !conMgr.getActiveNetworkInfo().isAvailable()
                || !conMgr.getActiveNetworkInfo().isConnected()) {
            return false;
        }

		try {
			HttpURLConnection c = openPathIgnoringCertificates(ServerPaths.pingPath());
			c.setConnectTimeout(ConnectionSettings.PING_CONNECT_TIMEOUT);
			c.setReadTimeout(ConnectionSettings.PING_READ_TIMEOUT);
			int response = c.getResponseCode();
			return response == HttpURLConnection.HTTP_OK;
		} catch (SSLHandshakeException e) {
			return true; // invalid certificate but reachable
		} catch (Exception e) {
			logger.debug(e.getMessage(), e);
		}

		return false;
	}

	public static ServerConnection builder(Context context) {
		ConnectionParameters connectionParameters = getConnectionParameters(context);
		if (connectionParameters != null) {
			return getOrInitServerConnection(context, connectionParameters);
		}
		throw new NoConnectionInfoException("Null or invalid connection parameters");
	}

	public static ServerConnection builder(Context context, String[] params) {
		ConnectionParameters connectionParameters = getConnectionParameters(context, params);
		if (connectionParameters != null) {
			return getOrInitServerConnection(context, connectionParameters);
		}
		throw new NoConnectionInfoException("Null or invalid connection parameters");
	}

	public static ServerConnection defaultBuilder(Context context) {
		String[] params = new String[3];
		params[0] = BuildConfig.CHAKE_USERNAME;
		params[1] = BuildConfig.CHAKE_PASSWORD;
		params[2] = AppSettings.DEFAULT_FORM_SERVER_URI;

		ConnectionParameters connectionParameters = getConnectionParameters(context, params);
		if (connectionParameters != null) {
			return getOrInitServerConnection(context, connectionParameters);
		}
		throw new NoConnectionInfoException("Null or invalid connection parameters");
	}

	@NonNull
	private static ServerConnection getOrInitServerConnection(Context context,
															  ConnectionParameters connectionParameters) {
		_INSTANCE = new ServerConnection(context, connectionParameters);
        return _INSTANCE;
	}

	private static ConnectionParameters getConnectionParameters(Context context) {
		String user = AppSettings.getUser(context);
		String password = AppSettings.getPassword(context);
		String serverURL = AppSettings.getFormServerURI(context);
		Long applicationId = AppSettings.getAppId(context);
		if (serverURL != null && user != null && password != null) {
			return new ConnectionParameters(serverURL, user, password, applicationId);
		}
		return null;
	}

	private static ConnectionParameters getConnectionParameters(Context context, String[] params) {
		String user = params[0];
		String password = params[1];
		String serverURL = params[2];
		Long applicationId = AppSettings.getAppId(context);
		if (serverURL != null && user != null && password != null) {
			return new ConnectionParameters(serverURL, user, password, applicationId);
		}
		return null;
	}

	public static ServerConnection getInstance(Context context) {
		return builder(context);
	}
}
