/*
 * Based on:
 *
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Modified by: alefeltes@sodep.com.py
 *
 * Notes: Original version showed a normal AlertDialog, now a custom activity is used.
 *
 */

package py.com.sodep.ui.common;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.view.View;
import android.webkit.WebView;
import android.widget.CheckBox;

import androidx.annotation.NonNull;
import io.github.jokoframework.chake.R;
import py.com.sodep.utils.ImageUtils;


/**
 * Displays an EULA ("End User License Agreement") that the user has to accept before
 * using the application. Your application should call {@link Eula#show(Activity)}
 * in the onCreate() method of the first activity. If the user accepts the EULA, it will never
 * be shown again. If the user refuses, {@link Activity#finish()} is invoked
 * on your activity.
 */
public class Eula {
    private static final String PREFERENCE_EULA_ACCEPTED = "eula.accepted";
    private static final String PREFERENCES_EULA = "eula";
    private static final String HTML_SCALED_FONT_SIZE = "3";

    private Eula() {
    }

    /**
     * Displays the EULA if necessary. This method should be called from the onCreate()
     * method of your main Activity.
     *
     * @param activity The Activity to finish if the user rejects the EULA.
     * @return Whether the user has agreed already.
     */
    public static boolean show(final Activity activity) {
        final SharedPreferences preferences = activity.getSharedPreferences(PREFERENCES_EULA,
                Activity.MODE_PRIVATE);
        boolean eulaAccepted = isEulaAccepted(activity);
        if (!eulaAccepted) {
            final AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(activity);
            dialogBuilder.setCancelable(false);
            final View eulaView = View.inflate(activity, R.layout.activity_eula, null);

            WebView mWebView = (WebView) eulaView.findViewById(R.id.webviewEula);
            String eulaBodyText = "<html><body>"
                    + "<p align=\"justify\">"
                    + "<font size=\"" +
                    ImageUtils.getHtmlScaledFontSize(activity) +
                    "\">"
                    + activity.getString(R.string.eula_body)
                    + "</p> "
                    + "</body></html>";
            mWebView.loadData(eulaBodyText, "text/html; charset=utf-8", "utf-8");

            View acceptButton = eulaView.findViewById(R.id.eula_accept_btn);
            View refusetButton = eulaView.findViewById(R.id.eula_refuse_btn);
            final CheckBox checkBox = (CheckBox) eulaView.findViewById(R.id.checkBoxAcceptEula);
            dialogBuilder.setView(eulaView);
            final AlertDialog alert = dialogBuilder.create();

            acceptButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    acceptClicked(checkBox, preferences, alert, activity);

                }
            });
            refusetButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    alert.dismiss();
                    refuse(activity);
                }
            });
            alert.show();
            return false;
        }
        return true;
    }

    public static boolean isEulaAccepted(Activity activity) {
        final SharedPreferences preferences = activity.getSharedPreferences(PREFERENCES_EULA,
                Activity.MODE_PRIVATE);
        return preferences.getBoolean(PREFERENCE_EULA_ACCEPTED, false);
    }

    @NonNull
    private static String getHtmlScaledFontSize() {
        return HTML_SCALED_FONT_SIZE;
    }

    private static void acceptClicked(CheckBox checkBox, SharedPreferences preferences, AlertDialog alert, Activity activity) {
        if (checkBox.isChecked()) {
            accept(preferences);
            alert.dismiss();
            SecurityDialog.show(activity);

        } else {

            new AlertDialog.Builder(activity)
                    .setTitle(activity.getString(R.string.eula_warning_title))
                    .setMessage(activity.getString(R.string.eula_warning_body))
                    .setNeutralButton(activity.getString(R.string.standard_back_button),
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog,
                                                    int which) {
                                    /*
                                    No hace nada por el momento
                                     */
                                }
                            }).show();
        }
    }

    private static void accept(SharedPreferences preferences) {
        preferences.edit().putBoolean(PREFERENCE_EULA_ACCEPTED, true).commit();
    }

    private static void refuse(Activity activity) {
        activity.finish();
    }

}
