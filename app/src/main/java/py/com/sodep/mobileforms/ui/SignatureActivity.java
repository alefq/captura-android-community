package py.com.sodep.mobileforms.ui;

import android.app.Activity;
import android.gesture.GestureOverlayView;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;

import java.io.File;
import java.io.FileOutputStream;

import io.github.jokoframework.chake.R;
import py.com.sodep.mobileforms.settings.AppSettings;

/**
 * Created by jmpr on 04/03/15.
 */
public class SignatureActivity extends Activity {

    public static final String EXTRA_OUTPUT_FILE = "OUTPUT_FILE";

    public boolean gestureStarted = false;

    private File file;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.signature);
        file = (File)getIntent().getSerializableExtra(EXTRA_OUTPUT_FILE);
        final GestureOverlayView gestureView = (GestureOverlayView) findViewById(R.id.signaturePad);
        gestureView.setDrawingCacheEnabled(true);
        gestureView.setAlwaysDrawnWithCacheEnabled(true);
        gestureView.setHapticFeedbackEnabled(false);
        gestureView.cancelLongPress();
        gestureView.cancelClearAnimation();
        gestureView.addOnGestureListener(new GestureOverlayView.OnGestureListener() {

            @Override
            public void onGesture(GestureOverlayView arg0, MotionEvent arg1) {

            }

            @Override
            public void onGestureCancelled(GestureOverlayView arg0, MotionEvent arg1) {

            }

            @Override
            public void onGestureEnded(GestureOverlayView arg0, MotionEvent arg1) {

            }

            @Override
            public void onGestureStarted(GestureOverlayView arg0, MotionEvent arg1) {
                gestureStarted = true;
            }
        });

        Button doneButton = (Button) findViewById(R.id.done_button);
        doneButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                // TODO Auto-generated method stub
                try {
                    if (gestureStarted) {
                        Bitmap bitmap = Bitmap.createBitmap(gestureView.getDrawingCache());
                        FileOutputStream fos = new FileOutputStream(file);
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos);
                        fos.close();

                        setResult(RESULT_OK);
                    } else {
                        file.delete();
                        setResult(RESULT_CANCELED);
                    }
                    finish();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        Button clearButton = (Button) findViewById(R.id.clear_button);
        clearButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View arg0) {
                gestureView.invalidate();
                gestureView.clear(true);
                gestureView.clearAnimation();
                gestureView.cancelClearAnimation();

                gestureStarted = false;
            }
        });
    }

    @Override
    public void onBackPressed() {
        /*
         Volver atras no es lo mismo que guardar una firma vacia,
         se evita que se oculte la imagen de la firma anterior.
        */
        setResult(AppSettings.RESULT_DO_NOTHING);
        finish();
    }
}
