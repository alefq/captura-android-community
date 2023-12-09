package py.com.sodep.mobileforms.ui;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import androidx.fragment.app.DialogFragment;

import io.github.jokoframework.chake.R;

/**
 * Created by jmpr on 26/02/15.
 */
public class RenderErrorDialogFragment extends DialogFragment {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
        setCancelable(false);
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

        builder.setTitle(R.string.error).setMessage(R.string.render_error)
                .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        getActivity().finish();
                    }
                });
        Dialog d = builder.create();
        d.setCancelable(false);
        return d;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

}
