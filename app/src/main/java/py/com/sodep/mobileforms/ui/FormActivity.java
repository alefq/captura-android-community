package py.com.sodep.mobileforms.ui;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.app.TimePickerDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.graphics.BitmapFactory.Options;
import android.graphics.Matrix;
import android.graphics.Point;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import androidx.fragment.app.DialogFragment;
import androidx.appcompat.app.AppCompatActivity;
import android.util.Log;
import android.view.Display;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TimePicker;
import android.widget.Toast;



import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Calendar;

import io.github.jokoframework.chake.R;
import py.com.sodep.mf.exchange.objects.metadata.Form;
import py.com.sodep.mf.form.model.MFPage;
import py.com.sodep.mobileforms.dataservices.DocumentsDataSource;
import py.com.sodep.mobileforms.dataservices.FormsDAO;
import py.com.sodep.mobileforms.dataservices.documents.Document;
import py.com.sodep.mobileforms.dataservices.documents.DocumentMetadata;
import py.com.sodep.mobileforms.dataservices.sql.SQLDocumentsDataSource;
import py.com.sodep.mobileforms.dataservices.sql.SQLFormsDAO;
import py.com.sodep.mobileforms.ui.rendering.Model;
import py.com.sodep.mobileforms.ui.rendering.UIRenderer;
import py.com.sodep.mobileforms.ui.rendering.exceptions.ParseException;
import py.com.sodep.mobileforms.ui.rendering.exceptions.RenderException;

/**
 * Activity that contains the Form. Uses {@link UIRenderer} to
 * render the form definition on the screen
 *
 * @author Miguel
 */
public class FormActivity extends AppCompatActivity {

    private static final String LOG_TAG = FormActivity.class.getSimpleName();

    public static final String EXTRA_FORM = "form";

    public static final String EXTRA_DOCUMENT_METADATA = "metadata";

    private static final String MODEL = "model";

    public static final int DATE_DIALOG_ID = 0;

    public static final int TIME_DIALOG_ID = 1;

    public static final int ERROR_DIALOG_ID = 2;

    private static final int DISCARD_CHANGES_DIALOG_ID = 3;

    public static final int FLOW_CHANGED_DIALOG_ID = 4;

    private UIRenderer renderer;

    private DocumentsDataSource ds = new SQLDocumentsDataSource();

    public FormActivity() {
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.forms_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.save_as_draft:
                renderer.saveAsDraft();
                Toast.makeText(this, R.string.data_saved, Toast.LENGTH_LONG).show();
                break;
            // Respond to the action bar's Up/Home button
            case android.R.id.home:
               // NavUtils.navigateUpFromSameTask(this);
                renderer.copyCurrentPageDataToDocument();
                if (unsavedChanges()) {
                    showDialog(DISCARD_CHANGES_DIALOG_ID);
                } else {
                    startMainActivity();
                }
                return true;
        }
        return false;
    }


    private boolean unsavedChanges() {
        if (renderer.hasUserInputData()) {
            Model model = renderer.getModel();
            Long documentId = model.getDocument().getId();
            if (documentId != null) {
                Document savedDocument = ds.getDocument(documentId);
                if (savedDocument == null) {
                    throw new RuntimeException("The document doesn't exist");
                }
                return !model.areEqual(savedDocument);
            } else {
                return true;
            }
        } else {
            return false;
        }
    }

    @Override
    protected void onPause() {
        if (renderer != null) {
            renderer.onPause();
        }
        super.onPause();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        Log.d(LOG_TAG, "onSaveInstanceState");
        Model model = renderer.getModel();
        outState.putSerializable(MODEL, model);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onResume() {
        //FIXME shouldn't there be an onResume in the renderer to continue getting the location?
        super.onResume();
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            setContentView(R.layout.form);
            setTheme(R.style.FormTheme);

            if (savedInstanceState == null) {
                DocumentMetadata documentMetadata = (DocumentMetadata) getIntent().getSerializableExtra(
                        EXTRA_DOCUMENT_METADATA);
                if (documentMetadata == null) {
                    Form form = (Form) getIntent().getSerializableExtra(EXTRA_FORM);
                    renderer = new UIRenderer(this, form);
                } else {
                    DocumentsDataSource documentsDAO = new SQLDocumentsDataSource();
                    Document document = documentsDAO.getDocument(documentMetadata.getId());
                    Form form = document.getForm();
                    if (form.getDefinition() == null) {
                        FormsDAO dao = new SQLFormsDAO();
                        form.setDefinition(dao.loadDefinition(form));
                    }
                    renderer = new UIRenderer(this, document);
                }

            } else {
                Model model = (Model) savedInstanceState.getSerializable(MODEL);
                renderer = new UIRenderer(this, model);
            }
            renderer.render();
        } catch (ParseException e) {
            DialogFragment dialog = new RenderErrorDialogFragment();
            Log.e(LOG_TAG, e.getMessage(), e);
            dialog.show(getSupportFragmentManager(), "ParseException");

        } catch (RenderException e) {
            DialogFragment dialog = new RenderErrorDialogFragment();
            Log.e(LOG_TAG, e.getMessage(), e);
            dialog.show(getSupportFragmentManager(), "RenderException");

        }
    }

    public static final int CAMERA_PIC_REQUEST = 1337;
    
    public static final int BARCODE_SCAN = 1338;

    public static final int SIGNATURE_REQUEST = 1339;

    private final byte[] buffer = new byte[2048];

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (resultCode == RESULT_OK && (requestCode == CAMERA_PIC_REQUEST || requestCode == SIGNATURE_REQUEST)) {
            processCameraPicRequestResult(intent);
        } else if (resultCode == RESULT_OK && requestCode == BARCODE_SCAN) {
            processBarCodeScanResult(intent);
        } else if (resultCode == RESULT_CANCELED && requestCode == SIGNATURE_REQUEST) {
            processNoSignature();
        }
    }

    private void processNoSignature() {
        Model model = renderer.getModel();
        Long id = model.getCurrentPhotoElementId();

        ImageView photoHolder = renderer.getPhotoHolder(id);
        photoHolder.setVisibility(View.GONE);
    }

    private void processBarCodeScanResult(Intent intent) {
        Long id = intent.getLongExtra(SimpleScannerActivity.EXTRA_ID, -1);
        if (id != -1) {
            EditText editText = (EditText) renderer.getView(id);
            editText.setText(intent.getStringExtra(SimpleScannerActivity.RESULT_TEXT));
            editText.requestFocus();
        }
    }

    private void processCameraPicRequestResult(Intent intent) {
        String url = null;
        boolean isFromGoogleContent = false;
        if(intent != null && intent.getData() != null) {
            //when comes from camera intent is null
            url = intent.getData().toString();
            isFromGoogleContent = url.startsWith("content://com.google.android.apps.photos.content");
        }

        boolean isCamera = isCamera(intent);
        Model model = renderer.getModel();
        String path = model.getCurrentPhotoFilePath();

        if (!isCamera) {
            if (intent == null) {
                return;
            }
            copyFile(intent, path);

        }

        File f = new File(path);
        if (!f.exists()) {
            // TODO use a default image to show that there was no image
            return;
        }
        Long id = model.getCurrentPhotoElementId();

        Bitmap sample = null;
        InputStream is = null;
        if (isFromGoogleContent) {
            Log.d(LOG_TAG, "Image is coming from google drive/plus/picasa");
            try {
                is = getContentResolver().openInputStream(Uri.parse(url));
                InputStream input = getContentResolver().openInputStream(Uri.parse(url));
                //Needed for the file that goes to the server as part of the form
                writeImageToFile(input, path);
                input.close();
            } catch (FileNotFoundException e) {
                Log.e(LOG_TAG, "Unexpected FileNotFound exception", e);
            } catch (IOException e) {
                Log.e(LOG_TAG, "Unexpected IO exception", e);
            }
            sample = BitmapFactory.decodeStream(is);
            // why isn't there no resizeAndRotate here?
        } else {
            Bitmap image = resizeAndRotate(path);
            if (image == null) {
                Dialog dialog = errorMessageDialog(getString(R.string.invalid_image_selection));
                dialog.show();
                return;
            }

            FileOutputStream fos = null;
            try {
                fos = new FileOutputStream(path);
                image.compress(CompressFormat.JPEG, 100, fos);
                fos.flush();
                image.recycle();
                sample = getSampleBitmap(path);
            } catch (FileNotFoundException e) {
                // FIXME DO SOMETHING!
                Log.e(LOG_TAG, "FileNotFoundException while processing image", e);
            } catch (IOException e) {
                Log.e(LOG_TAG, "IOException while processing image", e);
            } finally {
                if (fos != null) {
                    try {
                        fos.close();
                    } catch (IOException e) {
                        Log.e(LOG_TAG, "IOException while processing image", e);
                    }
                }
            }
        }

        if (sample != null) {
            ImageView photoHolder = renderer.getPhotoHolder(id);
            photoHolder.setVisibility(View.VISIBLE);
            photoHolder.setImageBitmap(sample);
        } else {
            Log.e(LOG_TAG, "No image found to load into photoHolder!");
        }
    }

    private void copyFile(Intent data, String path) {
        String picturePath = getPicturePath(data);
        if (picturePath == null) {
            return;
        }
        try {
            FileInputStream fis = new FileInputStream(picturePath);
            writeImageToFile(fis, path);
            fis.close();
        } catch (FileNotFoundException e) {
            Log.e(LOG_TAG, "File Not FOUND!!!", e);
        } catch (IOException e) {
            Log.d(LOG_TAG, "IOException while reading file", e);
        }
    }

    private void writeImageToFile(InputStream input, String path) throws IOException {
        FileOutputStream fos = new FileOutputStream(path);
        int read;
        Log.d(LOG_TAG, "Writing file to: " + path);
        while ((read = input.read(buffer)) != -1) {
            fos.write(buffer, 0, read);
            Log.d(LOG_TAG, "Written: " + read + " bytes");
        }
        fos.close();
    }

    private String getPicturePath(Intent data) {
        String picturePath = null;
        Cursor cursor = null;
        try {
            Uri selectedImage = data.getData();
            String[] filePathColumn = {MediaStore.Images.Media.DATA};
            cursor = getContentResolver().query(selectedImage, filePathColumn, null, null, null);
            cursor.moveToFirst();
            int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
            picturePath = cursor.getString(columnIndex);
            cursor.close();
        } catch (Exception e) {
            Log.e(LOG_TAG, "Exception while trying to get the path of the image", e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return picturePath;
    }

    private boolean isCamera(Intent data) {
        if (data == null) {
            return true;
        } else {
            final String action = data.getAction();
            if (action == null) {
                return false;
            } else {
                return action.equals(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
            }
        }
    }

    private enum AspectRatio {
        R4_3(1024, 0.75), R16_9(1280, 0.5625);

        int maxLength;

        double ratio;

        AspectRatio(int maxLength, double ratio) {
            this.maxLength = maxLength;
            this.ratio = ratio;
        }

        public int getMaxLength() {
            return maxLength;
        }

        public double getFactor() {
            return ratio;
        }

    }

    private static final int DEFAULT_LONGER_EDGE_SIZE = 1280;

    private enum Orientation {
        Horizontal, Vertical
    }

    private Bitmap resizeAndRotate(String path) {
        Options originalImageOptions = getOriginalImageOptions(path);
        int width = originalImageOptions.outWidth;
        int height = originalImageOptions.outHeight;
        Log.d(LOG_TAG, "original image width " + width);
        Log.d(LOG_TAG, "original image height " + height);
        if (width < 0 || height < 0) {
            //See Bug #4061
            return null;
        }

        double r;
        Orientation orientation;
        if (width >= height) {
            orientation = Orientation.Horizontal;
            r = height / (double) width;
        } else {
            orientation = Orientation.Vertical;
            r = width / (double) height;
        }

        AspectRatio ratio = null;

        if (r == AspectRatio.R4_3.getFactor()) {
            ratio = AspectRatio.R4_3;
        } else if (r == AspectRatio.R16_9.getFactor()) {
            ratio = AspectRatio.R16_9;
        }

        int maxLength;
        if (ratio != null) {
            maxLength = ratio.getMaxLength();
        } else {
            maxLength = DEFAULT_LONGER_EDGE_SIZE;
        }

        System.gc();

        Bitmap bmp = decodeFile(path, width, height, orientation, maxLength);
        if (orientation == Orientation.Horizontal && bmp.getWidth() > maxLength) {
            Bitmap bmp2 = Bitmap.createScaledBitmap(bmp, maxLength, (int) (maxLength * r), false);
            bmp.recycle();
            bmp = bmp2;
        } else if (orientation == Orientation.Vertical && bmp.getHeight() > maxLength) {
            Bitmap bmp2 = Bitmap.createScaledBitmap(bmp, (int) (maxLength * r), maxLength, false);
            bmp.recycle();
            bmp = bmp2;
        }
        System.gc();
        Matrix matrix = getRotationMatrix(path);
        if (!matrix.isIdentity()) {
            Bitmap bmp2 = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), matrix, false);
            bmp.recycle();
            bmp = bmp2;

        }
        System.gc();
        return bmp;
    }

    private Bitmap decodeFile(String path, int width, int height, Orientation orientation, int maxLength) {
        Options options = new Options();
        if (orientation == Orientation.Horizontal && width > maxLength) {
            options.inSampleSize = (int) Math.round(width / (double) maxLength);
        } else if (orientation == Orientation.Vertical && height > maxLength) {
            options.inSampleSize = (int) Math.round(height / (double) maxLength);
        }

        Bitmap bmp = BitmapFactory.decodeFile(path, options);
        return bmp;
    }

    public Bitmap getSampleBitmap(String path) {
        System.gc();
        Options originalImageOptions = getOriginalImageOptions(path);
        // Load the sample bitmap to show
        Bitmap sampleBitmap = BitmapFactory.decodeFile(path, getSampleImageOptions(originalImageOptions, path));
        System.gc();
        return sampleBitmap;
    }

    private BitmapFactory.Options getOriginalImageOptions(String path) {
        BitmapFactory.Options options = new Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, options);
        return options;
    }

    public BitmapFactory.Options getSampleImageOptions(BitmapFactory.Options options, String path) {
        Point size = getScreenDimensions();
        int width = size.x;

        BitmapFactory.Options sampleOptions = new Options();
        /*
         * If set to a value > 1, requests the decoder to subsample the original
		 * image, returning a smaller image to save memory. The sample size is
		 * the number of pixels in either dimension that correspond to a single
		 * pixel in the decoded bitmap. For example, inSampleSize == 4 returns
		 * an image that is 1/4 the width/height of the original, and 1/16 the
		 * number of pixels. Any value <= 1 is treated the same as 1. Note: the
		 * decoder will try to fulfill this request, but the resulting bitmap
		 * may have different dimensions that precisely what has been requested.
		 * Also, powers of 2 are often faster/easier for the decoder to honor.
		 */

        sampleOptions.inSampleSize = (int) Math.round(options.outWidth / (double) width);
        return sampleOptions;
    }

    private Matrix getRotationMatrix(String path) {
        Matrix matrix = new Matrix();
        try {
            ExifInterface exif = new ExifInterface(path);
            String orientation = exif.getAttribute(ExifInterface.TAG_ORIENTATION);

            if (orientation.equals(Integer.toString(ExifInterface.ORIENTATION_NORMAL))) {
                // Do nothing. The original image is fine.
            } else if (orientation.equals(Integer.toString(ExifInterface.ORIENTATION_ROTATE_90))) {
                matrix.postRotate(90);
            } else if (orientation.equals(Integer.toString(ExifInterface.ORIENTATION_ROTATE_180))) {
                matrix.postRotate(180);
            } else if (orientation.equals(Integer.toString(ExifInterface.ORIENTATION_ROTATE_270))) {
                matrix.postRotate(270);
            }

        } catch (IOException e) {
            Log.e(LOG_TAG, "Could not get matrix rotation", e);
        }
        return matrix;
    }

    private DatePickerDialog.OnDateSetListener dateSetListener = new DatePickerDialog.OnDateSetListener() {

        public void onDateSet(DatePicker view, int year, int monthOfYear, int dayOfMonth) {
            Calendar date = Calendar.getInstance();
            date.set(Calendar.YEAR, year);
            date.set(Calendar.MONTH, monthOfYear);
            date.set(Calendar.DAY_OF_MONTH, dayOfMonth);
            renderer.updateActiveDateDisplay(date);
        }
    };

    private TimePickerDialog.OnTimeSetListener timeSetListener = new TimePickerDialog.OnTimeSetListener() {

        public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
            Calendar date = Calendar.getInstance();
            date.set(Calendar.HOUR_OF_DAY, hourOfDay);
            date.set(Calendar.MINUTE, minute);
            renderer.updateActiveTimeDisplay(date);
        }
    };

    @Override
    protected Dialog onCreateDialog(int id) {
        return onCreateDialog(id, null);
    }

    @Override
    protected Dialog onCreateDialog(int id, Bundle b) {
        Model model = renderer.getModel();
        switch (id) {
            case DATE_DIALOG_ID:
                return new DatePickerDialog(this, dateSetListener, model.getActiveDate().get(Calendar.YEAR), model
                        .getActiveDate().get(Calendar.MONTH), model.getActiveDate().get(Calendar.DAY_OF_MONTH));
            case TIME_DIALOG_ID:
                return new TimePickerDialog(this, timeSetListener, model.getActiveTime().get(Calendar.HOUR_OF_DAY), model
                        .getActiveTime().get(Calendar.MINUTE), false);
            case ERROR_DIALOG_ID:
                String message = b.getString("message");
                return errorMessageDialog(message);
            case DISCARD_CHANGES_DIALOG_ID:
                return discardChangesDialog();
            case FLOW_CHANGED_DIALOG_ID:
                return flowChangedDialog();
        }
        return null;
    }

    private Dialog flowChangedDialog() {
        AlertDialog.Builder alertbox = new AlertDialog.Builder(this);
        alertbox.setTitle(R.string.flow_changed_title);
        alertbox.setMessage(R.string.flow_changed_message);
        alertbox.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                // clean all data from the further pages
                Model model = renderer.getModel();
                model.cleanBackStackAndRelatedData();
                MFPage page = renderer.getPossibleNextPage();
                model.changeCurrentPage(page);
                renderer.renderPage(page, true);
                dialog.dismiss();
            }
        });

        alertbox.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                dialog.dismiss();
            }
        });

        return alertbox.create();
    }

    private void startMainActivity() {
        Intent i = new Intent(this, MainActivity.class);
        startActivity(i);
        finish();
    }

    private Dialog discardChangesDialog() {
        AlertDialog.Builder alertbox = new AlertDialog.Builder(this);
        alertbox.setTitle(R.string.discard_form);
        alertbox.setMessage(R.string.discard_form_question);

        alertbox.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface arg0, int arg1) {
                startMainActivity();
            }
        });

        alertbox.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface arg0, int arg1) {

            }
        });

        return alertbox.create();
    }

    @Override
    protected void onPrepareDialog(int id, Dialog dialog) {
        super.onPrepareDialog(id, dialog);
        Model model = renderer.getModel();
        switch (id) {
            case DATE_DIALOG_ID:
                ((DatePickerDialog) dialog).updateDate(model.getActiveDate().get(Calendar.YEAR),
                        model.getActiveDate().get(Calendar.MONTH), model.getActiveDate().get(Calendar.DAY_OF_MONTH));
                break;
            case TIME_DIALOG_ID:
                ((TimePickerDialog) dialog).updateTime(model.getActiveTime().get(Calendar.HOUR_OF_DAY), model
                        .getActiveTime().get(Calendar.MINUTE));
                break;
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0) {
            if (!renderer.back()) {
                if(unsavedChanges()) {
                    showDialog(DISCARD_CHANGES_DIALOG_ID);
                } else {
                    startMainActivity();
                }
            }
            return true;
        } else {
            return super.onKeyDown(keyCode, event);
        }
    }

    @Override
    protected void onDestroy() {
        Log.d(LOG_TAG, "onDestroy");
        super.onDestroy();
    }

    private Point getScreenDimensions() {
        Display display = getWindowManager().getDefaultDisplay();
        int width = display.getWidth();
        int height = display.getHeight();
        return new Point(width, height);
    }

    private Dialog errorMessageDialog(String message) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.error);
        builder.setMessage(message);
        builder.setCancelable(false);
        builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                dialog.dismiss();
            }
        });

        return builder.create();
    }

}
