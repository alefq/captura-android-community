package py.com.sodep.mobileforms.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;

import java.text.DateFormat;
import java.util.Date;

import io.github.jokoframework.chake.R;
import py.com.sodep.mf.exchange.exceptions.HttpResponseException;
import py.com.sodep.mf.exchange.objects.error.ErrorResponse;
import py.com.sodep.mf.exchange.objects.metadata.Form;

public class ActivityUtils {

    private static DateFormat dateFormat = null;

    private static DateFormat timeFormat = null;

	public static Dialog newSimpleDismissableDialog(Activity activity, String title, String message) {
		AlertDialog.Builder builder = new AlertDialog.Builder(activity);

		builder.setTitle(title);
		builder.setIcon(android.R.drawable.ic_dialog_alert);
		builder.setMessage(message);
		builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int id) {
				dialog.dismiss();
			}
		});
		return builder.create();
	}

	public static Dialog uncaughtExceptionDialog(Activity activity, Exception e) {
		return newSimpleDismissableDialog(activity, activity.getString(R.string.error), e.getMessage());
	}

	public static String getMessage(Activity activity, HttpResponseException ex) {
		// default message
		String message = activity.getString(R.string.server_response_not_200, ex.getResponseCode(), ex.getURL());
		ErrorResponse errorResponse = ex.getErrorResponse();
		if (errorResponse != null) {
			String messageInDefaultLanguage = errorResponse.getMessageInDefaultLanguage();
			if (messageInDefaultLanguage != null) {
				message = messageInDefaultLanguage;
			}
		}
		return message;
	}

    public static String getFormStatusAsString(Activity activity, Form form) {
        return getFormStatusAsString(activity, form, null);
    }

    public static String getFormStatusAsString(Activity activity, Form form, Date date) {
        StringBuffer sb = new StringBuffer();
        sb.append(activity.getString(R.string.version) + ": " + form.getVersion());
        if (date != null) {
            sb.append("\n");
            String syncDateStr = formatDate(activity, date);
            sb.append(activity.getString(R.string.document_synced_at, syncDateStr));
        }
        return sb.toString();
    }

    private static String formatDate(Activity activity, Date date) {
        dateFormat = android.text.format.DateFormat.getDateFormat(activity);
        timeFormat = android.text.format.DateFormat.getTimeFormat(activity);
        return dateFormat.format(date) + " " + timeFormat.format(date);
    }
}
