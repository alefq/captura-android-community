package py.com.sodep.ui.common;

import android.app.Activity;
import android.app.AlertDialog;
import android.view.View;
import android.widget.TextView;

import io.github.jokoframework.chake.R;


public class SecurityDialog {

    private SecurityDialog() {
    }

    public static void show(final Activity activity) {
        final AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(activity);
        dialogBuilder.setCancelable(false);
        final View infoView = View.inflate(activity, R.layout.activity_security_dialog, null);

        TextView title = infoView.findViewById(R.id.titleTextView);
        TextView content = infoView.findViewById(R.id.contentTextView);

        title.setText(activity.getString(R.string.security_dialog_title));
        content.setText(activity.getString(R.string.security_dialog_body));

        dialogBuilder.setView(infoView);
        final AlertDialog alert = dialogBuilder.create();
        View acceptButton = infoView.findViewById(R.id.blockout_accept_btn);
        acceptButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                alert.dismiss();
            }
        });
        alert.show();
    }
}