package py.com.sodep.mobileforms.settings;

import py.com.sodep.mobileforms.crypto.SimpleCrypto;
import py.com.sodep.mobileforms.crypto.EncodeDecodeAES;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.preference.PreferenceManager;

public class AppSettings {

	private static String PREF_KEY_FORM_SERVER_URI = "FormServerURI";
	private static String PREF_KEY_USER = "User";
	private static String PREF_KEY_PASSWORD = "Password";
	private static String PREF_KEY_APPLICATION_ID = "ApplicationID";
	private static String PREF_LAST_INSTALLED_VERSION_CODE = "LastInstalledVersionCode";

	private static String PREF_KEY_LOGGED_IN = "loggedIn";

	public static final String DEFAULT_FORM_SERVER_URI = "http://dev.sodep.com.py:8080/mf";
	public static final int DEFAULT_PORT = 8080;
	public static final String DEFAULT_CONTEXT = "mf";
	private static final String DEFAULT_LANGUAGE = "en";
	private final static int ANDROID_PIE_9 = 28;

	public static final int RESULT_DO_NOTHING = 2;
	private static int REQUIRED_VERSION_CODE = 1;

	public static final String PROVIDER_EXT = ".provider";

	public static String getFormServerURI(Context context) {
		return PreferenceManager.getDefaultSharedPreferences(context).getString(PREF_KEY_FORM_SERVER_URI,
				null);
	}

	public static String getLanguage(Context context) {
		return DEFAULT_LANGUAGE;
	}

	public static String getUser(Context context) {
		return PreferenceManager.getDefaultSharedPreferences(context).getString(PREF_KEY_USER, null);
	}

	public static String getPassword(Context context) {
		String encryptedPassword = PreferenceManager.getDefaultSharedPreferences(context).getString(PREF_KEY_PASSWORD,
				null);
		if (encryptedPassword != null) {
			try {
				if (android.os.Build.VERSION.SDK_INT >=  ANDROID_PIE_9) {
					return new String(EncodeDecodeAES.decrypt(context, encryptedPassword));
				} else {
					return SimpleCrypto.decrypt(encryptedPassword);
				}
			} catch (Exception e) {
				setPassword(context, null);
				throw new RuntimeException("Could not decrypt the password", e);
			}
		}
		return null;
	}

	public static void setPassword(Context context, String password) {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
		Editor editor = sharedPreferences.edit();

		if (password != null) {
			try {
				String encryptedPassword;
				if (android.os.Build.VERSION.SDK_INT >=  ANDROID_PIE_9) {
					encryptedPassword = EncodeDecodeAES.bytesToHex(EncodeDecodeAES.encrypt(context, password));
				} else {
					encryptedPassword = SimpleCrypto.encrypt(password);
				}
				editor.putString(PREF_KEY_PASSWORD, encryptedPassword);
			} catch (Exception e) {
				throw new RuntimeException("Could not encrypt the password");
			}
		} else {
			editor.remove(PREF_KEY_PASSWORD);
		}
		editor.apply();
	}

	public static void setUser(Context context, String user) {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
		Editor editor = sharedPreferences.edit();
		if (user != null) {
			editor.putString(PREF_KEY_USER, user);
		} else {
			editor.remove(PREF_KEY_USER);
		}
		editor.apply();
	}

	public static void setFormServerURI(Context context, String uri) {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
		Editor editor = sharedPreferences.edit();
		if (uri != null) {
			editor.putString(PREF_KEY_FORM_SERVER_URI, uri);
		} else {
			editor.remove(PREF_KEY_FORM_SERVER_URI);
		}
		editor.apply();
	}

	public static Long getAppId(Context context) {
		Long appId = PreferenceManager.getDefaultSharedPreferences(context).getLong(PREF_KEY_APPLICATION_ID, -1L);
		return appId == -1L ? null : appId;
	}
	
	public static void setAppId(Context context, Long id){
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
		Editor editor = sharedPreferences.edit();
		if (id == null) {
			editor.remove(PREF_KEY_APPLICATION_ID);
		} else {
			editor.putLong(PREF_KEY_APPLICATION_ID, id);
		}
		editor.apply();
	}

	public static boolean isLoggedIn(Context context) {
		SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
		return preferences.getBoolean(PREF_KEY_LOGGED_IN, false);
	}
	
	public static void setLoggedIn(Context context, boolean value){
		SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
		Editor editor = preferences.edit();
		editor.putBoolean(PREF_KEY_LOGGED_IN, value);
		editor.apply();
	}

	public static boolean isFromOldApp(Context context) {

		//Obtiene el ultimo Version Code registrado
		SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
		Editor editor = preferences.edit();
		int previousVersionCode = preferences.getInt(PREF_LAST_INSTALLED_VERSION_CODE, 0);

		//Guarda el Version Code actual
		try {
			PackageInfo packageInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
			if (packageInfo.versionCode != previousVersionCode) {
				editor.putInt(PREF_LAST_INSTALLED_VERSION_CODE, packageInfo.versionCode);
				editor.apply();
			}
		} catch (PackageManager.NameNotFoundException e) {
			return true;
		}

		//Si la version anterior es menor a la requerida
		if (previousVersionCode < REQUIRED_VERSION_CODE) {
			return true;
		}

		return false;
	}
}
