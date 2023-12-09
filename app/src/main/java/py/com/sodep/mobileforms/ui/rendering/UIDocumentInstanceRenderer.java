package py.com.sodep.mobileforms.ui.rendering;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import androidx.appcompat.app.AppCompatActivity;
import android.text.SpannableString;
import android.text.method.PasswordTransformationMethod;
import android.text.style.UnderlineSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import io.github.jokoframework.chake.R;
import py.com.sodep.mf.exchange.objects.metadata.Form;
import py.com.sodep.mf.form.model.MFForm;
import py.com.sodep.mf.form.model.MFPage;
import py.com.sodep.mf.form.model.element.MFElement;
import py.com.sodep.mf.form.model.prototype.MFInput;
import py.com.sodep.mf.form.model.prototype.MFPrototype;
import py.com.sodep.mobileforms.dataservices.documents.Document;
import py.com.sodep.mobileforms.dataservices.documents.DocumentMetadata;
import py.com.sodep.mobileforms.ui.rendering.exceptions.ParseException;
import py.com.sodep.mobileforms.ui.rendering.exceptions.RenderException;
import py.com.sodep.mobileforms.util.Util;

/**
 * Created by rodrigo on 18/03/15.
 */
public class UIDocumentInstanceRenderer {

    private static final String LOG_TAG = UIDocumentInstanceRenderer.class.getSimpleName();

    private Activity activity;

    private Model model;

    private UIWidgets widgets;

    private DateFormat dateFormat = null;

    private DateFormat timeFormat = null;

    private SimpleDateFormat simpleDateFormat = null;

    private static final String DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'";

    private LayoutInflater inflater = null;

    public UIDocumentInstanceRenderer(Activity parent, Form form) throws ParseException {
        this.activity = parent;
        model = new Model(form);
        initUI();
    }

    public UIDocumentInstanceRenderer(Activity activity, Document document) throws ParseException {
        this.activity = activity;
        model = new Model(document);
        initUI();
    }


    public UIDocumentInstanceRenderer(Activity activity, Model model)  {
        this.activity = activity;
        this.model = model;
        initUI();
    }

    private void initUI() {
        widgets = new UIWidgets(this.activity);
        widgets.pageLayout = (ViewGroup) activity.findViewById(R.id.document_instance_layout_container);
        widgets.pageScrollView = (ScrollView) activity.findViewById(R.id.document_instance_scrollview);
        dateFormat = android.text.format.DateFormat.getDateFormat(this.activity);
        timeFormat = android.text.format.DateFormat.getTimeFormat(this.activity);
        simpleDateFormat = new SimpleDateFormat(DATE_FORMAT);
        inflater = (LayoutInflater) this.activity.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }

    public Model getModel() {
        return this.model;
    }


    public void setDocument(Document document) {
        model.document = document;
    }


    public void render() throws ParseException, RenderException {
        try {
            renderTitle();

            MFForm mfForm = this.getModel().getMfform();
            for (MFPage mfPage : mfForm.getPages()) {
                renderPageLabel(mfPage);

                List<MFElement> elements = mfPage.getElements();
                for (MFElement element : elements) {
                    View labelView = this.widgets.getFactory().newTextView(element);
                    View contentView = null;
                    MFPrototype proto = element.getProto();
                    switch (proto.getType()) {
                        case PHOTO: case SIGNATURE:
                            contentView = photoView(element);
                            break;
                        case LOCATION:
                            contentView = locationView(element);
                            break;
                        case CHECKBOX:
                            contentView  = checkboxView(element);
                            break;
                        case INPUT:
                            contentView = inputView(element);
                            break;
                        case HEADLINE:
                            labelView = headlineView(element);
                            break;
                        case SELECT: case BARCODE:
                            contentView = textOnlyView(element);
                            break;
                    }

                    if (contentView != null) {
                        widgets.addToPage(labelView, contentView);
                    } else {
                        widgets.addToPage(labelView);
                    }
                }

            }
        } catch (java.text.ParseException e) {
            throw new ParseException(e);
        } catch (Exception e) {
            throw new RenderException(e);
        } finally {
        }

    }

    private View headlineView(MFElement element) {
        TextView labelTextView = new TextView(this.activity);
        labelTextView.setTextAppearance(this.activity, R.style.HeadLineText);
        labelTextView.setText(element.getProto().getLabel());
        return labelTextView;
    }

    private void renderTitle() {
        Form form = this.getModel().getForm();
        AppCompatActivity ab = (AppCompatActivity) activity;
        ab.setTitle(form.getLabel());
        ab.getSupportActionBar().setSubtitle(getSubtitle());

    }

    // TODO find a better looking way to display the page label
    // The header layout look awkward
    private void renderPageLabel(MFPage mfPage) {
        TextView pageLabel = (TextView) inflater.inflate(R.layout.header, null);
        pageLabel.setText(mfPage.getLabel());
        pageLabel.setTextColor(Color.GRAY);
        this.widgets.pageLayout.addView(pageLabel);
    }

    private String getSubtitle() {
        StringBuffer sb = new StringBuffer();
        String savedAt = this.formatDate(this.model.getDocument().getSavedAt());
        sb.append(this.activity.getString(R.string.saved_at)).append(" ").append(savedAt);

        // TODO rvillalba.
        // I couldn't manage to display the
        // status as a new line in the subtitle. So I left this commented.
        // This should be solved if we want to show the status as well.
        //String status = getDocumentStatusString(this.model.getDocument());
        //sb.append("\n");
        //sb.append(this.activity.getString(R.string.status)).append(" ").append(status);

        return sb.toString();
    }

    private String getDocumentStatusString(Document document) {
        switch (document.getStatus()) {
            case DocumentMetadata.STATUS_FAILED:
                return this.activity.getString(R.string.status_failed);
            case DocumentMetadata.STATUS_IN_PROGRESS:
                return this.activity.getString(R.string.status_in_progress);
            case DocumentMetadata.STATUS_NOT_SYNCED:
                return this.activity.getString(R.string.status_not_synced);
            case DocumentMetadata.STATUS_REJECTED:
                return this.activity.getString(R.string.status_rejected);
            case DocumentMetadata.STATUS_SYNCED:
                return this.activity.getString(R.string.status_synced);
        }
        return "";

    }

    private View textOnlyView(MFElement element) {
        String value = getValue(element);
        TextView view = new TextView(this.activity);
        view.setText(value);
        return view;
    }

    private View inputView(final MFElement element) throws java.text.ParseException {
        MFInput mfInput = (MFInput) element.getProto();
        MFInput.Type inputType = mfInput.getSubtype();
        TextView view = new TextView(this.activity);
        String value = "";
        switch (inputType) {
            case TEXT:
            case INTEGER:
            case DECIMAL:
            case TEXTAREA:
            case EMAIL:
                value = getValue(element);
                break;
            case PASSWORD:
                //FIXME rvillalba. Is this how we should render this?
                view.setTransformationMethod(PasswordTransformationMethod.getInstance());
                break;
            case DATE: {
                String dateStr = getValue(element);
                if (dateStr != null && dateStr.trim().length() > 0) {
                    Date d = simpleDateFormat.parse(dateStr);
                    value = dateFormat.format(d);
                }
                break;
            }
            case TIME: {
                String dateStr = getValue(element);
                if (dateStr != null && dateStr.trim().length() > 0) {
                    Date d = simpleDateFormat.parse(dateStr);
                    value = this.formatDate(d);
                }
                break;
            }
            case EXTERNAL_LINK: {
                value = getValue(element);
                view.setTextColor(activity.getResources().getColor(android.R.color.holo_blue_dark));
                view.setOnClickListener(Util.onURLClickListener(activity, value));
                break;
            }
        }

        view.setText(value);
        return view;
    }

    private View checkboxView(MFElement element) {
        String value = getValue(element);
        TextView content = new TextView(this.activity);
        Boolean booleanValue = Boolean.parseBoolean(value);
        if (booleanValue) {
            content.setText(this.activity.getString(R.string.yes));
        } else {
            content.setText(this.activity.getString(R.string.no));
        }
        return content;
     }


    // FIXME lots of code in this method copy-pasted from UILocationRenderer
    private View locationView(MFElement element) {
        TextView coordinatesTextView = new TextView(this.activity);
        final String value = getValue(element);
        if (value != null) {
            String[] splitted = value.split(",");
            double latitude = Double.parseDouble(splitted[0]);
            double longitude = Double.parseDouble(splitted[1]);
            double altitude = Double.parseDouble(splitted[2]);
            double accuracy = Double.parseDouble(splitted[3]);
            SpannableString string = locationSpannableString(latitude, longitude, altitude, accuracy);
            coordinatesTextView.setText(string);
        }
        //coordinatesTextView.setText(value);

        coordinatesTextView.setTextColor(Color.parseColor("#1122CC"));
        coordinatesTextView.setOnClickListener(new View.OnClickListener() {
            @SuppressLint("HandlerLeak")
            @Override
            public void onClick(View v) {
                if (value != null) {
                    String[] splitted = value.split(",");
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("geo:" + splitted[0] + "," + splitted[1]
                            + "?q=" + splitted[0] + "," + splitted[1] + "(Location+Location)"));
                    activity.startActivity(intent);
                }
            }
        });

        return coordinatesTextView;
    }

    // FIXME copy-pasted from UILocationRenderer
    private SpannableString locationSpannableString(double latitude, double longitude, double altitude, double accuracy) {
        String locationStr = this.activity.getString(R.string.latitude) + " = " + latitude + ", "
                + this.activity.getString(R.string.longitude) + " = " + longitude + "; "
                // + activity.getString(R.string.altitude) + " = " + altitude +
                // "; "
                + this.activity.getString(R.string.accuracy) + " = " + accuracy;
        SpannableString string = new SpannableString(locationStr);
        string.setSpan(new UnderlineSpan(), 0, string.length(), 0);
        return string;
    }

    private View photoView(MFElement element) {
        ImageView imageView = new ImageView(this.activity);
        String path = getValue(element);
        if (path == null) {
            return imageView;
        }
        File imgFile =  new File(path);
        if (imgFile.exists()) {
            Bitmap bmp = BitmapFactory.decodeFile(imgFile.getAbsolutePath());
            imageView.setImageBitmap(bmp);
        }
        return imageView;

    }

    private String getValue(MFElement element) {
        String instanceId = element.getInstanceId();
        String value = this.getModel().getDocument().get(instanceId);
        return value;
    }

    private String formatDate(Date date) {
        return dateFormat.format(date) + " " + timeFormat.format(date);
    }
}
