package py.com.sodep.mobileforms.util;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.core.app.ActivityCompat;
import androidx.appcompat.app.AlertDialog;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


import io.github.jokoframework.chake.R;

import static android.Manifest.permission.ACCESS_FINE_LOCATION;

/**
 * Created by nelson on 10/23/17.
 */
public class PermissionsHelper {

    private static final String LOG_TAG = PermissionsHelper.class.getSimpleName();
    static int PERMISSION_ALL = 1;
    public static String[] PERMISSIONS = new String[]{ Manifest.permission.READ_PHONE_STATE,
            ACCESS_FINE_LOCATION};

    private static Map<String, String> permissionMessages = new HashMap<String, String>(){{
        put(Manifest.permission.READ_PHONE_STATE, "La aplicación requiere permiso de " +
                "Lectura de Datos del télefono para continuar.");
        put(Manifest.permission.READ_EXTERNAL_STORAGE, "La aplicación requiere permiso de " +
                "Espacio de Almacenamiento para continuar.");
        put(Manifest.permission.WRITE_EXTERNAL_STORAGE, "La aplicación requiere permiso de " +
                "Espacio de Almacenamiento para continuar.");
        put(Manifest.permission.CAMERA, "La aplicación requiere permiso para utilizar la cámara.");
        put(Manifest.permission.VIBRATE, "La aplicación requiere permiso para utilizar la cámara.");
    }};

    private PermissionsHelper(){}

    /**
     * Check if all permissions have been granted
     * @param context
     * @param permissions
     * @return
     */
    public static boolean hasPermissions(Context context, String... permissions) {
       return getMissingPermissions(context, permissions).length>0?false:true;
    }

    public static String[] getMissingPermissions(Context context, String... permissions){

        List<String> toRet = new ArrayList<>();

        if (context != null && permissions != null && permissions.length>0) {
            for (String permission : permissions) {
                if (ActivityCompat.checkSelfPermission(context, permission) !=
                        PackageManager.PERMISSION_GRANTED) {
                    toRet.add(permission);
                }
            }
        }
        return toRet.toArray(new String[toRet.size()]);
    }


    public static boolean checkAndAskForPermissions(Activity activity, int message, String...permissions){
        String[] permissionsRequired = permissions;

        String[] missingPermissions = getMissingPermissions(activity, permissionsRequired);
        if (missingPermissions.length > 0) {
            askForPermission(activity, message, missingPermissions);
            return false;
        }

        return true;
    }

    /**
     * SHows a dialog asking for permissions to the user
     * @param activity
     */
    private static void askForPermission(final Activity activity, final int message, final String...permissions){

        AlertDialog.Builder alertBuilder = new AlertDialog.Builder(activity,
                R.style.Theme_AppCompat_Dialog_Alert);
        alertBuilder.setCancelable(true);
        alertBuilder.setTitle(R.string.permissions_dialog_title);
        alertBuilder.setMessage(message);
        //If press yes request for every permission which has not yet been granted
        alertBuilder.setPositiveButton(R.string.grant_permission,
                new DialogInterface.OnClickListener() {

            @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
            public void onClick(DialogInterface dialog, int which) {
                ActivityCompat.requestPermissions(activity, permissions, PERMISSION_ALL);
            }
        });
        //If press no dissmis the dialog
        alertBuilder.setNegativeButton(R.string.prompt_cancel,
                new DialogInterface.OnClickListener() {

            @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        AlertDialog alert = alertBuilder.create();
        alert.show();

    }
}
