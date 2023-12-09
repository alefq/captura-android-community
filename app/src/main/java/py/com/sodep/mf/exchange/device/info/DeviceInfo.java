package py.com.sodep.mf.exchange.device.info;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.provider.Settings.Secure;
import androidx.core.app.ActivityCompat;
import android.telephony.TelephonyManager;
import android.util.Log;


import py.com.sodep.mf.exchange.objects.device.MFDeviceInfo;

public class DeviceInfo {

    private Context context;

    private MFDeviceInfo mfDeviceInfo = null;

    public DeviceInfo(Context context) {
        this.context = context;
    }

    public String getBrand() {
        return android.os.Build.BRAND;
    }

    public String getManufacturer() {
        return android.os.Build.MANUFACTURER;
    }

    public String getModel() {
        return android.os.Build.MODEL;
    }

    /**
     * This method will try to return the unique phone id (the IMEI for GSM and
     * the MEID or ESN for CDMA phones). If its not possible it will return the
     * serial id that is only available from 2.3 and above. If it still not
     * possible, then it will return the ANDROID_ID.
     *
     * @return an id or null if it couldn't get one
     */
    public String getUniqueIdentifier() {
        return getUniqueIdentifier(context);
    }

    public String getProduct() {
        return android.os.Build.PRODUCT;
    }

    public int getVersionNumber() {
        return android.os.Build.VERSION.SDK_INT;
    }

    public String getRelease() {
        return android.os.Build.VERSION.RELEASE;
    }

    public MFDeviceInfo getMFDeviceInfo() {
        if (mfDeviceInfo == null) {
            mfDeviceInfo = new MFDeviceInfo();
            mfDeviceInfo.setOs("ANDROID");
            mfDeviceInfo.setBrand(getBrand());
            mfDeviceInfo.setIdentifier(getUniqueIdentifier());
            mfDeviceInfo.setModel(getModel());
            mfDeviceInfo.setVersionNumber(Integer.toString(getVersionNumber()));
            mfDeviceInfo.setManufacturer(getManufacturer());
            mfDeviceInfo.setProduct(getProduct());
            mfDeviceInfo.setRelease(getRelease());
        }
        return mfDeviceInfo;
    }

    public static String getUniqueIdentifier(Context context) {

        String id = null;
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE)
                == PackageManager.PERMISSION_GRANTED) {
            TelephonyManager telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            if (telephonyManager != null) {

                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                try {
                    id = Secure.getString(context.getContentResolver(), Secure.ANDROID_ID);
                } catch (SecurityException e) {
                    Log.e("getUniqueIdentifier", e.getMessage(), e);
                }
            }

        }
        // if we couldn't get the IMEI then try with the SERIAL_ID
        if (id == null) {
            if (android.os.Build.VERSION.SDK_INT >= 9 /*Build.VERSION_CODES.GINGERBREAD*/) {
                // It's not a good idea to use Build.VERSION_CODES.GINGERBREAD because, Froyo and earlier devices won't
                // have this property
                id = android.os.Build.SERIAL;
            }
        }
        // if we are still not able, then go to the latest option
        if (id == null || id.equals("unknown")) {
            id = Secure.getString(context.getContentResolver(), Secure.ANDROID_ID);
        }
        return id;

    }


    public Context getContext() {
        return context;
    }

    public void setContext(Activity context) {
        this.context = context;
    }

}
