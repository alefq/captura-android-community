package py.com.sodep.mobileforms.ui.rendering;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

import py.com.sodep.mf.exchange.IllegalStringFormatException;
import py.com.sodep.mf.exchange.MFDataHelper;
import py.com.sodep.mf.exchange.MFField.FIELD_TYPE;
import py.com.sodep.mf.exchange.objects.metadata.Form;
import py.com.sodep.mf.form.model.MFBaseModel;
import py.com.sodep.mf.form.model.MFForm;
import py.com.sodep.mf.form.model.MFPage;
import py.com.sodep.mf.form.model.element.MFElement;
import py.com.sodep.mf.form.model.element.filter.MFFilter;
import py.com.sodep.mf.form.model.prototype.MFCheckbox;
import py.com.sodep.mf.form.model.prototype.MFInput;
import py.com.sodep.mf.form.model.prototype.MFInput.Type;
import py.com.sodep.mf.form.model.prototype.MFPhoto;
import py.com.sodep.mf.form.model.prototype.MFPrototype;
import py.com.sodep.mf.form.model.prototype.MFSelect;
import io.github.jokoframework.chake.R;
import py.com.sodep.mobileforms.dataservices.documents.Document;
import py.com.sodep.mobileforms.exceptions.LengthException;
import py.com.sodep.mobileforms.exceptions.RequiredValueException;
import py.com.sodep.mobileforms.exceptions.ValidationException;
import py.com.sodep.mobileforms.location.MFLocationManager;
import py.com.sodep.mobileforms.net.sync.services.SyncService;
import py.com.sodep.mobileforms.settings.AppSettings;
import py.com.sodep.mobileforms.ui.FormActivity;
import py.com.sodep.mobileforms.ui.MainActivity;
import py.com.sodep.mobileforms.ui.SignatureActivity;
import py.com.sodep.mobileforms.ui.SimpleScannerActivity;
import py.com.sodep.mobileforms.ui.list.LabelAndValueAdapter;
import py.com.sodep.mobileforms.ui.rendering.exceptions.NoSuchPageException;
import py.com.sodep.mobileforms.ui.rendering.exceptions.ParseException;
import py.com.sodep.mobileforms.ui.rendering.exceptions.RenderException;
import py.com.sodep.mobileforms.ui.rendering.objects.LabelAndValue;
import py.com.sodep.mobileforms.ui.rendering.objects.LookupData;
import py.com.sodep.mobileforms.util.PermissionsHelper;
import py.com.sodep.mobileforms.util.Util;


import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcelable;
import android.provider.MediaStore;
import androidx.core.content.FileProvider;
import android.text.InputType;
import android.text.format.DateFormat;
import android.text.method.DigitsKeyListener;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnFocusChangeListener;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import com.google.firebase.crashlytics.FirebaseCrashlytics;

public class UIRenderer implements OnClickListener, OnItemSelectedListener {

	private static final String LOG_TAG = UIRenderer.class.getSimpleName();

	private FormActivity activity;

	private MFLocationManager mfFormLocationManager;

	private Model model;

	private UIWidgets widgets;

    // Used to know if the user has input data
    // CAP-79
    public Map<String, String> initialData = new HashMap<String, String>();

	public UIRenderer(FormActivity parent, Form form) throws ParseException {
		this.activity = parent;
		model = new Model(form);
		initUI();
	}

	public UIRenderer(FormActivity activity, Document document) throws ParseException {
		this.activity = activity;
		model = new Model(document);
		initUI();
	}


	public UIRenderer(FormActivity activity, Model model)  {
		this.activity = activity;
		this.model = model;
		initUI();
	}

	private void initUI() {
		widgets = new UIWidgets(activity);
		widgets.pageLayout = (ViewGroup) activity.findViewById(R.id.form_layout_container);
		widgets.pageScrollView = (ScrollView) activity.findViewById(R.id.form_scrollview);
		widgets.saveButton = (Button) activity.findViewById(R.id.form_save_button);
		widgets.saveButton.setOnClickListener(this);
		widgets.saveAndNewButton = (Button) activity.findViewById(R.id.form_save_and_new_button);
		widgets.saveAndNewButton.setOnClickListener(this);
		widgets.saveActionView = activity.findViewById(R.id.FormSaveActionsLayout);
		widgets.nextButton = (Button) activity.findViewById(R.id.form_next_button);
		widgets.nextButton.setOnClickListener(this);
	}

	/**
	 * Call this method to dump the data of the current page to the document. In
	 * the state that it is. Without validation.
	 */
	public void copyCurrentPageDataToDocument() {
		try {
			Map<String, String> pageData = parseCurrentPageData(false);
			model.document.putAll(pageData);
		} catch (ValidationException e) {
			// This shouldn't happen because we are
			// calling parseCurrentPageData with validate == false
			throw new RuntimeException(e);
		}

	}

	public void setDocument(Document document) {
		model.document = document;
	}

	public void render() throws RenderException {
		try {
			MFForm mfform = model.getMfform();

			if (mfform.isProvideLocation()) {
				mfFormLocationManager = new MFLocationManager(activity, null);
				mfFormLocationManager.calculate();
			}
			MFPage page = model.getCurrentPage();
			if (page == null) {
				page = model.changeCurrentPageByPosition(0);
			}
			renderPage(page, !model.hasBeenVisited(page));

            copyCurrentPageDataToDocument();
            initialData.clear();
            initialData.putAll(model.document);
		} catch (Exception e) {
			throw new RenderException(e);
		}
	}

    public boolean hasUserInputData() {
        return model.getDocument().getId() != null ||
                !model.areEqual(initialData);
    }

	/**
	 * Renders the page on the screen
	 * 
	 * @param page
	 * @param applyDefaultValues
	 */
	// Remember to call model.changePage(mfPage) before
	private void renderOnLayout(MFPage page, boolean applyDefaultValues) {
		// currentPage = page;
		widgets.elementViews.clear();
		widgets.pageLayout.removeAllViews();
		// No element is initially focused
		widgets.pageLayout.setFocusableInTouchMode(true);
		widgets.pageLayout.setFocusable(true);

		widgets.nextButton.setVisibility(View.GONE);
		widgets.saveActionView.setVisibility(View.GONE);

		activity.setTitle(page.getLabel());
		if (page.isSaveable()) {
			widgets.saveActionView.setVisibility(View.VISIBLE);
		}

        if (page.getElements() != null) {
            List<MFElement> elements = page.getElements();
            for (MFElement element : elements) {
                MFPrototype proto = element.getProto();
                switch (proto.getType()) {
                    case INPUT:
                        renderInput(element, applyDefaultValues);
                        break;
                    case SELECT:
                        renderSelect(element, applyDefaultValues);
                        break;
                    case PHOTO:
                        renderPhoto(element);
                        break;
                    case LOCATION:
                        renderLocation(element);
                        break;
                    case HEADLINE:
                        renderHeadLine(element);
                        break;
                    case CHECKBOX:
                        renderCheckbox(element, applyDefaultValues);
                        break;
                    case BARCODE:
                        renderBarcode(element);
                        break;
                    case SIGNATURE:
                        renderSignature(element);
                        break;
                }
            }
        }


		if (!page.isSaveable() && page.getPosition() < (model.getMfform().getPages().size() - 1)) {
			widgets.nextButton.setVisibility(View.VISIBLE);
		}

		widgets.pageLayout.requestFocus();
		// int scrollY = pageScrollView.getScrollY();
		forceHideKeyboard(widgets.pageLayout.getWindowToken());
		widgets.pageScrollView.scrollTo(0, 0);
	}

    private void renderSignature(MFElement element) {
        View labelView = widgets.getFactory().newTextView(element);

        ImageView imageView = new ImageView(activity);
        imageView.setScaleType(ScaleType.FIT_START);
        imageView.setBackgroundColor(activity.getResources().getColor(R.color.mf_background));
        imageView.setPadding(2, 2, 2, 2);

        LayoutParams params = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        imageView.setLayoutParams(params);
        imageView.setVisibility(View.GONE);
        imageView.setAdjustViewBounds(true);

        final Button button = widgets.getFactory().newButton();
        button.setText(R.string.capture_signature);
        button.setOnClickListener(this);

        button.setFocusableInTouchMode(true);
        button.setFocusable(true);
        button.setOnFocusChangeListener(new OnFocusChangeListener() {

            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    forceHideKeyboard(button.getWindowToken());
                    button.performClick();
                }
            }
        });

        Object[] objects = { element, imageView, null };
        button.setTag(objects);

        widgets.addToPage(labelView, imageView, button);
        String image = restoreImage(imageView, element);
        objects[2] = image;

        widgets.elementViews.add(button);
    }

    private void renderBarcode(final MFElement element) {
        View labelView = widgets.getFactory().newTextView(element);

        final Button button = widgets.getFactory().newButton();
        button.setText(R.string.scan_barcode);
        button.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
				if(PermissionsHelper.hasPermissions(activity, Manifest.permission.CAMERA)){
					Intent i = new Intent(activity, SimpleScannerActivity.class);
					i.putExtra(SimpleScannerActivity.EXTRA_ID, element.getId());
					activity.startActivityForResult(i, FormActivity.BARCODE_SCAN);
				} else {
					PermissionsHelper.checkAndAskForPermissions(activity,
							R.string.permissions_camera_text,
							Manifest.permission.CAMERA,
							Manifest.permission.WRITE_EXTERNAL_STORAGE);
				}
            }
        } );

        EditText editText = new EditText(activity);
		editText.setOnFocusChangeListener(newOnFocusChangeLister(element, editText));
        Object[] objects = { element };
        editText.setTag(objects);

        button.setFocusableInTouchMode(true);
        button.setFocusable(true);
        button.setOnFocusChangeListener(new OnFocusChangeListener() {

            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    forceHideKeyboard(button.getWindowToken());
                    button.performClick();
                }
            }
        });
        restoreBarcode(editText);
        widgets.addToPage(labelView, editText, button);
        widgets.elementViews.add(editText);
    }

    private void restoreBarcode(TextView view) {
        Object[] objects = (Object[]) view.getTag();
        MFElement element = (MFElement) objects[0];
        String instanceId = element.getInstanceId();
        view.setText(model.document.get(instanceId));
    }

    private void renderLocation(MFElement element) {
		UILocationRenderer locationRenderer = new UILocationRenderer(activity, widgets, model, element);
		Button locationButton = locationRenderer.render();
        widgets.elementViews.add(locationButton);
	}

    private void renderCheckbox(MFElement element, boolean applyDefaultValues) {
        CheckBox checkBox = widgets.getFactory().newCheckBox();
        checkBox.setText(renderCheckBoxLabel(element));
        Object[] objects = { element };
        checkBox.setTag(objects);
        restoreCheckbox(checkBox, applyDefaultValues);
        widgets.addToPage(checkBox);
        widgets.elementViews.add(checkBox);
    }

	private String renderCheckBoxLabel(MFElement element) {
		if (element.isRequired()) {
			return activity.getString(
					R.string.required_checkbox_label,
					element.getProto().getLabel());
		} else {
			return element.getProto().getLabel();
		}
	}

	private void restoreCheckbox(CheckBox checkBox, boolean applyDefaultValues) {
        Object[] objects = (Object[]) checkBox.getTag();
        MFElement element = (MFElement) objects[0];
        String instanceId = element.getInstanceId();
        if (applyDefaultValues) {
            MFCheckbox mfCheckbox = (MFCheckbox) element.getProto();
            checkBox.setChecked(mfCheckbox.isChecked());
        } else {
            String value = model.document.get(instanceId);
            checkBox.setChecked(Boolean.parseBoolean(value));
        }
    }

	private void renderPhoto(MFElement element) {
		View labelView = widgets.getFactory().newTextView(element);

		ImageView imageView = new ImageView(activity);
		imageView.setScaleType(ScaleType.FIT_START);
		imageView.setBackgroundColor(activity.getResources().getColor(R.color.mf_background));
		imageView.setPadding(2, 2, 2, 2);

		LayoutParams params = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
		imageView.setLayoutParams(params);
		imageView.setVisibility(View.GONE);
		imageView.setAdjustViewBounds(true);

		final Button button = widgets.getFactory().newButton();
		button.setText(R.string.capture_photo);
		button.setOnClickListener(this);

		button.setFocusableInTouchMode(true);
		button.setFocusable(true);
		button.setOnFocusChangeListener(new OnFocusChangeListener() {

			@Override
			public void onFocusChange(View v, boolean hasFocus) {
				if (hasFocus) {
					forceHideKeyboard(button.getWindowToken());
					button.performClick();
				}
			}
		});

		Object[] objects = { element, imageView, null };
		button.setTag(objects);

		widgets.addToPage(labelView, imageView, button);
		String image = restoreImage(imageView, element);
		objects[2] = image;

		widgets.elementViews.add(button);
	}

	private void forceHideKeyboard(IBinder windowToken) {
		InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
		imm.hideSoftInputFromWindow(windowToken, 0);
	}

	private void renderSelect(MFElement element, boolean applyDefaultValues) {
		final View labelView = widgets.getFactory().newTextView(element);

		final Spinner spinner = new Spinner(activity);
		Object[] objects = { element };
		spinner.setTag(objects);
		spinner.setFocusableInTouchMode(true);
		spinner.setFocusable(true);
		// To list the options correctly we must put all the data in the
		// document
		copyCurrentPageDataToDocument();
		List<LabelAndValue> options = model.listSelectOptions(element);
		LabelAndValue chooseOption = new LabelAndValue(new LookupData(
				activity.getString(R.string.select_choose_option), FIELD_TYPE.STRING), new LookupData("",
				FIELD_TYPE.STRING));
		options.add(0, chooseOption);
		spinner.setPrompt(activity.getString(R.string.select_choose_option));
		setAdapter(spinner, options);

		spinner.setOnFocusChangeListener(new OnFocusChangeListener() {

			@Override
			public void onFocusChange(View v, boolean hasFocus) {
				if (hasFocus) {
					forceHideKeyboard(spinner.getWindowToken());
					spinner.performClick();
				}
			}
		});

		restoreSelected(spinner, element, options, applyDefaultValues);
		spinner.setOnItemSelectedListener(this);
		widgets.addToPage(labelView, spinner);
		widgets.elementViews.add(spinner);
	}

	private void setAdapter(Spinner spinner, List<LabelAndValue> options) {
		LabelAndValueAdapter adapter = new LabelAndValueAdapter(activity, android.R.layout.simple_spinner_item, options);
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		adapter.setLabelFormatter(widgets.getLabelFormatter());
		spinner.setAdapter(adapter);
	}

	private void restoreSelected(Spinner spinner, MFElement element, List<LabelAndValue> options,
			boolean applyDefaultValues) {
		if (options != null) {
			String serializedValue = null;
			if (applyDefaultValues) {
				MFSelect mfSelect = (MFSelect) element.getProto();
				serializedValue = mfSelect.getDefaultValue();
			} else {
				serializedValue = model.document.get(element.getInstanceId());
			}
			setSelected(spinner, options, serializedValue);
		}
	}

	private void setSelected(Spinner spinner, List<LabelAndValue> options, String serializedValue) {
		if (serializedValue != null) {
			int i = 0;
			for (LabelAndValue o : options) {
				try {
					LookupData lookupData = o.getValue();
					Object unserialized = MFDataHelper.unserialize(lookupData.getType(), serializedValue);
					if (unserialized != null && unserialized.equals(lookupData.getData())) {
						spinner.setSelection(i);
						break;
					}
				} catch (Exception e) {

				}
				i++;
			}
		}
	}

	private void renderInput(MFElement element, boolean applyDefaultValues) {
		View labelView = widgets.getFactory().newTextView(element);

		MFInput mfInput = (MFInput) element.getProto();
		Type inputType = mfInput.getSubtype();

		TextView view = null;
		switch (inputType) {
		case TEXT:
			view = renderInputText(element);
			view.setEnabled(!mfInput.isReadOnly());
			break;
		case INTEGER:
			view = renderInputInteger(element);
			view.setEnabled(!mfInput.isReadOnly());
			break;
		case DECIMAL:
			view = renderInputDecimal(element);
			view.setEnabled(!mfInput.isReadOnly());
			break;
		case TEXTAREA:
			view = renderInputTextArea(element);
			view.setEnabled(!mfInput.isReadOnly());
			break;
		case PASSWORD:
			view = renderInputPassword(element);
			view.setEnabled(!mfInput.isReadOnly());
			break;
		case DATE:
			view = renderInputDate(element);
			break;
		case TIME:
			view = renderInputTime(element);
			break;
		case EMAIL:
			view = renderInputEmail(element);
			view.setEnabled(!mfInput.isReadOnly());
			break;
		case EXTERNAL_LINK:
			view = renderInputExternalLink(element);
			break;
		case DATETIME:
			// view = parseInputDatetime(element);
			throw new RuntimeException(activity.getString(R.string.not_yet_impl));
		}

		if (inputType == Type.TEXT || inputType == Type.INTEGER || inputType == Type.DECIMAL
				|| inputType == Type.TEXTAREA || inputType == Type.PASSWORD || inputType == Type.EMAIL) {
			restoreText(view, applyDefaultValues);
		} else if (inputType == Type.DATE || inputType == Type.TIME) {
			restoreDate((Button) view, inputType);
		}

		widgets.addToPage(labelView, view);
		widgets.elementViews.add(view);
	}

	private void restoreText(TextView view, boolean applyDefaultValues) {
		Object[] objects = (Object[]) view.getTag();
		MFElement element = (MFElement) objects[0];
		String instanceId = element.getInstanceId();
		if (applyDefaultValues) {
			MFInput mfinput = (MFInput) element.getProto();
			copyCurrentPageDataToDocument();
			setDynamicDefaultValue(view);
			if (view.getText().length() == 0) {
				String defaultValue = mfinput.getDefaultValue();
				view.setText(defaultValue);
			}
		} else {
			view.setText(model.document.get(instanceId));
		}
	}

	private String restoreImage(ImageView imageView, MFElement element) {
		String path = model.document.get(element.getInstanceId());
		if (path != null) {
			Bitmap bitmap = activity.getSampleBitmap(path);
			imageView.setImageBitmap(bitmap);
			imageView.setVisibility(View.VISIBLE);
			return path;
		}
		return null;
	}

	private void renderHeadLine(MFElement element) {
		TextView labelTextView = new TextView(activity);
		labelTextView.setText(element.getProto().getLabel());
		labelTextView.setTextAppearance(activity, R.style.HeadLineText);
		widgets.addToPage(labelTextView);
	}

	private EditText renderInputText(MFElement element) {
		EditText editText = new EditText(activity);
		editText.setOnFocusChangeListener(newOnFocusChangeLister(element, editText));
		Object[] objects = { element };
		editText.setTag(objects);
		return editText;
	}

	private EditText renderInputTextArea(MFElement element) {
		EditText editText = renderInputText(element);
		editText.setRawInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
		editText.setLines(5);
		editText.setGravity(Gravity.TOP | Gravity.LEFT);
		return editText;
	}

	private EditText renderInputPassword(MFElement element) {
		EditText editText = renderInputText(element);
		editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
		return editText;
	}

	private EditText renderInputInteger(MFElement element) {
		EditText editText = renderInputText(element);
		editText.setInputType(InputType.TYPE_CLASS_NUMBER);
		editText.setKeyListener(new DigitsKeyListener(true, false));
		return editText;
	}

	// http://stackoverflow.com/questions/3821539/decimal-separator-comma-with-numberdecimal-inputtype-in-edittext
	private EditText renderInputDecimal(MFElement element) {
		EditText editText = renderInputText(element);
		editText.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
		editText.setKeyListener(new DigitsKeyListener(true, true));
		return editText;
	}

	public void updateActiveDateDisplay(Calendar cal) {
		Object o = widgets.activeDateButton.getTag();
		Object[] objects = (Object[]) o;
		objects[1] = cal;
		setButtonText(widgets.activeDateButton, cal.getTime(), MFInput.Type.DATE);
	}

	private Button renderInputDate(MFElement element) {
		Button button = widgets.getFactory().newButton();

		Object[] objects = { element, null };
		button.setTag(objects);
		button.setText(R.string.set_date);
		button.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				Object o = v.getTag();
				Object[] objects = (Object[]) o;
				Calendar date = (Calendar) objects[1];
				if (date == null) {
					model.activeDate = Calendar.getInstance();
				} else {
					model.activeDate = date;
				}
				widgets.activeDateButton = (Button) v;
				activity.showDialog(FormActivity.DATE_DIALOG_ID);
			}
		});

		return button;
	}

	private void restoreDate(Button button, MFInput.Type inputType) {
		Object[] objects = (Object[]) button.getTag();
		MFElement element = (MFElement) objects[0];
		String instanceId = element.getInstanceId();
		String data = model.document.get(instanceId);
		if (data != null) {
			Date date = MFDataHelper.unserializeDate(data);
			setButtonText(button, date, inputType);
			storeDate(objects, date);
		}
	}

	private void storeDate(Object[] objects, Date date) {
		Calendar cal = Calendar.getInstance();
		cal.setTime(date);
		objects[1] = cal;
	}

	private void setButtonText(Button button, Date date, MFInput.Type inputType) {
		String text;
		if (inputType == Type.DATE) {
			text = DateFormat.getDateFormat(activity).format(date);
		} else if (inputType == Type.TIME) {
			text = DateFormat.getTimeFormat(activity).format(date);
		} else {
			throw new RuntimeException(activity.getString(R.string.unknown_format_for) + " " + inputType);
		}
		button.setText(text);
	}

	public void updateActiveTimeDisplay(Calendar cal) {
		Object o = widgets.activeTimeButton.getTag();
		Object[] objects = (Object[]) o;
		objects[1] = cal;
		setButtonText(widgets.activeTimeButton, cal.getTime(), MFInput.Type.TIME);
	}

	private Button renderInputTime(MFElement element) {
		Button button = widgets.getFactory().newButton();

		Object[] objects = { element, null };
		button.setTag(objects);
		button.setText(R.string.set_time);
		button.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				Object o = v.getTag();
				Object[] objects = (Object[]) o;
				Calendar time = (Calendar) objects[1];
				if (time == null) {
					model.activeTime = Calendar.getInstance();
				} else {
					model.activeTime = time;
				}
				widgets.activeTimeButton = (Button) v;
				activity.showDialog(FormActivity.TIME_DIALOG_ID);
			}
		});

		return button;
	}

	private EditText renderInputEmail(MFElement element) {
		EditText editText = new EditText(activity);
		editText.setOnFocusChangeListener(newOnFocusChangeLister(element, editText));
		Object[] objects = { element };
		editText.setTag(objects);
		editText.setHint(R.string.email_address_format_hint);
		return editText;
	}

	private TextView renderInputExternalLink(MFElement element) {
		final TextView textView = new TextView(activity);
		textView.setTextColor(activity.getResources().getColor(android.R.color.holo_blue_dark));

		String value = ((MFInput) element.getProto()).getDefaultValue();
		textView.setText(value);

		textView.setOnClickListener(Util.onURLClickListener(activity, value));
		Object[] objects = { element };
		textView.setTag(objects);
		return textView;
	}

	public void renderPage(MFPage page, boolean applyDefaultValues) {
		renderOnLayout(page, applyDefaultValues);
	}

	private void formSaved() {
		// back to home
		Intent mainActivityIntent = new Intent(activity, MainActivity.class);
		activity.startActivity(mainActivityIntent);
		activity.finish();
	}

	private void uploadDocuments() {
		Intent sendDataService = new Intent(activity, SyncService.class);
		activity.startService(sendDataService);
	}

	private void startNewDocument() throws RenderException {
		model.newDocument();
		render();
	}

	private void documentNotSavedMessage(Exception e) {
		Toast.makeText(activity, R.string.save_data_failed, Toast.LENGTH_LONG).show();
		FirebaseCrashlytics crashlytics = FirebaseCrashlytics.getInstance();
		crashlytics.recordException(e);
	}

	@Override
	public void onClick(View v) {
		if (v.getId() == View.NO_ID) {
			Object tag = v.getTag();
			if (tag instanceof Object[]) {
				Object[] objects = (Object[]) tag;
				MFElement e = (MFElement) objects[0];
				if (e.getProto().getType() == MFBaseModel.Type.PHOTO) {
					if(PermissionsHelper.hasPermissions(activity, Manifest.permission.CAMERA)){
						MFPhoto photo = (MFPhoto) e.getProto();
						model.currentPhotoElementId = e.getId();
						captureImage(objects, photo.isCameraOnly());
					} else {
						PermissionsHelper.checkAndAskForPermissions(activity,
								R.string.permissions_camera_text,
								Manifest.permission.CAMERA,
								Manifest.permission.WRITE_EXTERNAL_STORAGE);
					}
				} else if(e.getProto().getType() == MFBaseModel.Type.SIGNATURE){
                    //TODO start Activity to capture signature
                    model.currentPhotoElementId = e.getId();
                    captureSignature(objects);
                }
			}
		} else {
			switch (v.getId()) {
				case R.id.form_next_button:
					doNextButton();
					break;
				case R.id.form_save_button:
					doSaveButton();
					break;
				case R.id.form_save_and_new_button:
					doSaveAndNewButton();
					break;
			}
		}
	}

	private void doNextButton() {
		if (model.getCurrentPage().getFlow() != null) {
			handleFlow();
		} else {
			nextPage();
		}
	}

	private void doSaveButton() {
		try {
			Map<String, String> data = parseCurrentPageData(true);
			model.document.putAll(data);
			try {
				saveDocumentData();
				uploadDocuments();
				formSaved();
				Toast.makeText(activity, R.string.data_saved, Toast.LENGTH_LONG).show();
			} catch (Exception e) {
				documentNotSavedMessage(e);
			}
		} catch (ValidationException e) {
			// Nothing to do, the error messages are shown in
			// parseCurrentPageData
			Log.e(LOG_TAG, e.getMessage(), e);
		}
	}

	private void doSaveAndNewButton() {
		try {
			Map<String, String> data = parseCurrentPageData(true);
			model.document.putAll(data);
			try {
				saveDocumentData();
				uploadDocuments();
				Toast.makeText(activity, R.string.data_saved, Toast.LENGTH_LONG).show();
				startNewDocument();
			} catch (Exception e) {
				documentNotSavedMessage(e);
			}
		} catch (ValidationException e) {
			// Nothing to do, the error messages are shown in
			// parseCurrentPageData
		}
	}

	private void saveDocumentData() {
		if (model.getMfform().isProvideLocation()) {
			String location = computeLocationData();
			model.document.put("location", location);
		}
		// save or update
		model.save();
	}

	private MFPage possibleNextPage;

	private void handleFlow() {
		try {
			Map<String, String> data = parseCurrentPageData(true);
			model.document.putAll(data);
			String target = model.computeTarget();
			if (target != null) {
				MFPage page = model.pageFromInstanceId(target);
				if (page == null) {
					Toast.makeText(activity, activity.getString(R.string.unknown_page), Toast.LENGTH_LONG).show();
				} else {
					Stack<MFPage> pageBackStack = model.getPageBackStack();
					if (pageBackStack.isEmpty()) {
						model.changeCurrentPage(page);
						renderPage(page, !model.hasBeenVisited(page));
					} else {
						MFPage top = pageBackStack.peek();
						if (!top.getId().equals(page.getId())) {
							possibleNextPage = page;
							activity.showDialog(FormActivity.FLOW_CHANGED_DIALOG_ID);
						} else {
							model.changeCurrentPage(page);
							renderPage(page, !model.hasBeenVisited(page));
						}
					}
				}
			} else {
				Toast.makeText(activity, activity.getString(R.string.no_target), Toast.LENGTH_LONG).show();
			}
		} catch (ValidationException e) {
		}
	}

	private void nextPage() {
		try {
			Map<String, String> data = parseCurrentPageData(true);
			model.document.putAll(data);
			int pagePosition = model.getCurrentPage().getPosition();
			try {
				MFPage page = model.changeCurrentPageByPosition(pagePosition + 1);
				renderPage(page, !model.hasBeenVisited(page));
			} catch (NoSuchPageException ex) {
				Toast.makeText(activity, R.string.no_next_page, Toast.LENGTH_LONG).show();
			}
		} catch (ValidationException e) {
		}
	}

    private void captureSignature(Object[] objects){
        try {
            String tempFilePath = (String) objects[2];
            if (tempFilePath == null) {
                model.currentPhotoFile = File.createTempFile("cp-", ".jpg",
                        activity.getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES));
                objects[2] = model.currentPhotoFile.getAbsolutePath();
            } else {
                model.currentPhotoFile = new File(tempFilePath);
            }

            Intent intent = new Intent(activity, SignatureActivity.class);
            intent.putExtra(SignatureActivity.EXTRA_OUTPUT_FILE, model.currentPhotoFile);
            activity.startActivityForResult(intent, FormActivity.SIGNATURE_REQUEST);
        } catch (IOException e) {
            Log.e(LOG_TAG, e.getMessage(), e);
            // Unable to create file, likely because external storage is
            // not currently mounted
            Bundle b = new Bundle();
            b.putString("message", activity.getString(R.string.probably_not_mounted));
            activity.showDialog(FormActivity.ERROR_DIALOG_ID, b);
            objects[2] = null;
        }

    }

	private void captureImage(Object[] objects, boolean onlyCamera) {
		try {
			// FIXME this should not be a accessible to other users, apps, etc.
			String tempFilePath = (String) objects[2];
			if (tempFilePath == null) {
				model.currentPhotoFile = File.createTempFile("cp-", ".jpg",
						activity.getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES));
				objects[2] = model.currentPhotoFile.getAbsolutePath();
			} else {
				model.currentPhotoFile = new File(tempFilePath);
			}

			Uri outputFileUri = FileProvider.getUriForFile(activity,
					activity.getPackageName() + AppSettings.PROVIDER_EXT, model.currentPhotoFile);

            Intent startIntent;
            if(onlyCamera) {
                final Intent captureIntent = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
                captureIntent.putExtra(MediaStore.EXTRA_OUTPUT, outputFileUri);
                startIntent = captureIntent;
            } else {
                final List<Intent> cameraIntents = new ArrayList<Intent>();
                final Intent captureIntent = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
                captureIntent.putExtra(MediaStore.EXTRA_OUTPUT, outputFileUri);
                cameraIntents.add(captureIntent);

                Intent pickImageIntent = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);

                // Chooser of filesystem options.
                final Intent chooserIntent = Intent.createChooser(pickImageIntent, activity.getText(R.string.select_source));

                // Add the camera options.
                chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, cameraIntents.toArray(new Parcelable[]{}));
                startIntent = chooserIntent;
            }
            activity.startActivityForResult(startIntent, FormActivity.CAMERA_PIC_REQUEST);
		} catch (IOException e1) {
			Log.e(LOG_TAG, e1.getMessage(), e1);
			// Unable to create file, likely because external storage is
			// not currently mounted
			Bundle b = new Bundle();
			b.putString("message", activity.getString(R.string.probably_not_mounted));
			activity.showDialog(FormActivity.ERROR_DIALOG_ID, b);
			objects[2] = null;
		}
	}

	public void saveAsDraft() {
		if (model.getMfform().isProvideLocation()) {
			String location = computeLocationData();
			model.document.put("location", location);
		}
		copyCurrentPageDataToDocument();
		model.saveDraft();
	}

	private String computeLocationData() {
		Location bestLocation = mfFormLocationManager.getBestLocation();
		if (bestLocation != null) {
			double latitude = bestLocation.getLatitude();
			double longitude = bestLocation.getLongitude();
			double altitude = bestLocation.getAltitude();
			double accuracy = bestLocation.getAccuracy();
			return latitude + "," + longitude + "," + altitude + "," + accuracy;
		}
		return null;
	}

	/**
	 * Parses the page data. Returns a new Map<String, String> with the page
	 * data.
	 * 
	 * Doesn't modify the document's data
	 * 
	 * @param validate
	 * @return
	 * @throws ValidationException
	 */
	public Map<String, String> parseCurrentPageData(boolean validate) throws ValidationException {
		Map<String, String> data = new HashMap<String, String>();
		boolean validationException = false;
		for (View view : widgets.elementViews) {
			try {
				parseDataFromTag(validate, data, view);
			} catch (ValidationException ex) {
				validationException = true;
			}
		}
		if (validate && validationException) {
			throw new ValidationException();
		}
		return data;
	}

	private void parseDataFromTag(boolean validate, Map<String, String> data, View view) throws ValidationException {
		Object tag = view.getTag();
		if (tag instanceof Object[]) {
			Object[] objects = (Object[]) tag;
			parseDataFromTagArray(validate, data, view, objects);
		}
	}

	private void parseDataFromTagArray(boolean validate, Map<String, String> data, View view, Object[] objects)
			throws ValidationException {
		if (objects.length > 0 && objects[0] instanceof MFElement) {
			MFElement element = (MFElement) objects[0];

            if (element != null) {
                String field = element.getInstanceId();
                String value = null;
                if (view instanceof EditText) {
					EditText editText = (EditText) view;
					// to reset any error messages
					// editText.setError(null);
					// TODO convert according to the type?
					value = editText.getText().toString();
					if (validate) {
						//length applies to all
						lengthControl(editText, element, value);
						if (value.trim().length() > 0) {
							// here should be the validations according to the type
							if (element.getProto() instanceof MFInput) {
								MFInput mfInput = (MFInput) element.getProto();
								Type inputType = mfInput.getSubtype();
								if (Type.isNumericType(inputType.toString())) {
									validateNumericValue(value, editText);
								}
							}
						}
					}
				} else if (view instanceof Spinner) {
					Spinner spinner = (Spinner) view;
					LabelAndValue selected = (LabelAndValue) spinner.getSelectedItem();
					if (selected != null) {
						LookupData lookupData = selected.getValue();
						value = MFDataHelper.serialize(lookupData.getType(), lookupData.getData());
					}
				} else if (view instanceof CheckBox) {
                    CheckBox checkBox = (CheckBox) view;
                    if (element.getProto().getType() == MFBaseModel.Type.CHECKBOX) {
                        value = Boolean.toString(checkBox.isChecked());
                    }
                } else if (view instanceof Button) {
                    switch (element.getProto().getType()) {
                        case PHOTO:
                        case SIGNATURE:
                            value = (String) objects[2];
                            break;
                        case INPUT:
                            // Date, DateTime, Time
                            if (objects[1] != null) {
                                Calendar cal = (Calendar) objects[1];
                                value = MFDataHelper.serialize(cal.getTime());
                            }
                            break;
                        case LOCATION:
                            value = (String) objects[1];
                            break;
                    }
                } else if (view instanceof TextView) {
					if (element.getProto() instanceof MFInput
							&& ((MFInput) element.getProto()).getSubtype() == Type.EXTERNAL_LINK) {
						value = ((MFInput) element.getProto()).getDefaultValue();
					} else {
						TextView textView = (TextView) view;
						value = textView.getText().toString();
					}
				}

				if (validate) {
					requiredControl(view, element, value);
				}

				if (value != null) {
					data.put(field, value);
				}
			}
		}
	}

    private void validateNumericValue(String value, EditText editText) throws ValidationException {
        //TODO check value
        try {
            MFDataHelper.unserialize(FIELD_TYPE.NUMBER, value);
        } catch (IllegalStringFormatException e) {
            String msg = activity.getString(R.string.not_valid_number, value);
            Toast.makeText(activity, msg, Toast.LENGTH_LONG).show();
            editText.setError(msg);
            throw new ValidationException(msg);
        }
    }

    private void lengthControl(EditText e, MFElement element, String value) throws LengthException {
		if (element.getProto().getType() == MFBaseModel.Type.INPUT) {
			MFInput mfinput = (MFInput) element.getProto();

			Integer minLength = mfinput.getMinLength();
			if (minLength != null && value != null) {
				minLengthControl(e, element, value, minLength);
			}

			Integer maxLength = mfinput.getMaxLength();
			if (maxLength != null && value != null) {
				maxLengthControl(e, element, value, maxLength);
			}
		}
	}

	private void maxLengthControl(EditText e, MFElement element, String value, Integer maxLength)
			throws LengthException {
        double length;
        String longMsg, shortMsg;
		MFInput mfinput = (MFInput) element.getProto();
		MFInput.Type inputType = mfinput.getSubtype();
		length = value.trim().length();
		if (length == 0 && !element.isRequired()) {
			return;
		}
		if (inputType.equals(MFInput.Type.INTEGER) || inputType.equals(MFInput.Type.DECIMAL)) {
			try {
				length = Double.parseDouble(value);
			} catch (NumberFormatException ex) {
				length = Double.MAX_VALUE;
			}
			longMsg = activity.getString(R.string.element_max_value_is, element.getProto().getLabel(), maxLength);
			shortMsg = activity.getString(R.string.max_value_is, maxLength);
		} else {
			longMsg = activity.getString(R.string.element_max_length_is, element.getProto().getLabel(), maxLength);
			shortMsg = activity.getString(R.string.max_length_is, maxLength);
		}

		if (length > maxLength) {
			Toast.makeText(activity, longMsg, Toast.LENGTH_LONG).show();
			e.setError(shortMsg);
			throw new LengthException(longMsg);
		}
	}

	private void minLengthControl(EditText e, MFElement element, String value, Integer minLength)
			throws LengthException {
		double length;
		String longMsg, shortMsg;
		MFInput mfinput = (MFInput) element.getProto();
		MFInput.Type inputType = mfinput.getSubtype();
		length = value.trim().length();
		if (length == 0 && !element.isRequired()) {
			return;
		}
		if (inputType.equals(MFInput.Type.INTEGER) || inputType.equals(MFInput.Type.DECIMAL)) {
			try {
				length = Double.parseDouble(value);
			} catch (NumberFormatException ex) {
				length = Double.MIN_VALUE;
			}
			longMsg = activity.getString(R.string.element_min_value_is, element.getProto().getLabel(), minLength);
			shortMsg = activity.getString(R.string.min_value_is, minLength);
		} else {
			longMsg = activity.getString(R.string.element_min_length_is, element.getProto().getLabel(), minLength);
			shortMsg = activity.getString(R.string.min_length_is, minLength);
		}

		if (length < minLength) {
			Toast.makeText(activity, longMsg, Toast.LENGTH_LONG).show();
			e.setError(shortMsg);
			throw new LengthException(longMsg);
		}
	}

	private void requiredControl(View e, MFElement element, String value) throws RequiredValueException {
		final String CHECKBOX_VALUE_FLASE = "false";
		if (value == null) {
			//No dejaba guardar, fallando silenciosamente si una foto no obligatoria
			// no se seleccionaba
			value = "";
			Log.w(LOG_TAG, String.format("Valor null %s", element));
		}

		if (element.isRequired() && value.isEmpty()) {
			Toast.makeText(activity, activity.getString(R.string.is_required, element.getProto().getLabel()),
					Toast.LENGTH_LONG).show();
			if (e instanceof EditText) {
				((EditText) e).setError(activity.getString(R.string.required));
			} else if (e instanceof Button) {
				((Button) e).setError(activity.getString(R.string.required));
			} else if (e instanceof Spinner) {
				// TODO ... one way maybe to implement an adapter
				// see decorating a SpinnerAdapter
				View selected = ((Spinner) e).getSelectedView();
				if (selected instanceof TextView) {
					((TextView) selected).setError(activity.getString(R.string.required));
				}
			}
			throw new RequiredValueException();
		} else if (element.getProto() instanceof MFInput
				&& ((MFInput) element.getProto()).getSubtype() == Type.EMAIL && !value.isEmpty()) {
			if (!android.util.Patterns.EMAIL_ADDRESS.matcher(value).matches()) {
				((EditText) e).setError(activity.getString(R.string.incorrect_email_address_format));
				throw new RequiredValueException();
			} else {
				((EditText) e).setError(null);
			}
		} else if (element.isRequired() && !value.isEmpty()) {
			// BUG FIX #3130
			if (element.getProto().getType() == MFBaseModel.Type.PHOTO) {
				File f = new File(value);
				boolean validFile = f.exists() && f.length() > 0;
				if (!validFile) {
					Toast.makeText(activity, activity.getString(R.string.no_valid_file, element.getProto().getLabel()),
							Toast.LENGTH_LONG).show();
					((Button) e).setError(activity.getString(R.string.required));
					throw new RequiredValueException();
				} else {
					((Button) e).setError(null);
				}
			} else if (element.getProto().getType() == MFBaseModel.Type.SIGNATURE) {
				File f = new File(value);
				boolean validFile = f.exists() && f.length() > 0;
				if (!validFile) {
					((Button) e).setError(activity.getString(R.string.required));
					throw new RequiredValueException();
				} else {
					((Button) e).setError(null);
				}
			} else if (element.getProto().getType() == MFBaseModel.Type.CHECKBOX) {
				if (CHECKBOX_VALUE_FLASE.equals(value)) {
					((CheckBox) e).setError(activity.getString(R.string.required));
					throw new RequiredValueException();
				} else {
					((CheckBox) e).setError(null);
				}
			}
		}
	}

	public ImageView getPhotoHolder(Long photoElementId) {
		Object[] objects = getTag(photoElementId);
		Object obj = objects[1];
		if (obj instanceof ImageView) {
			return (ImageView) obj;
		}
		return null;
	}

	private Object[] getTag(Long elementId) {
		for (View e : widgets.elementViews) {
			Object tag = e.getTag();
			if (tag instanceof Object[]) {
				Object[] objects = (Object[]) tag;
				MFElement element = (MFElement) objects[0];
				if (element.getId().equals(elementId)) {
					return objects;
				}
			}
		}
		return null;
	}

    public View getView(Long elementId) {
        for (View view : widgets.elementViews) {
            Object tag = view.getTag();
            if (tag instanceof Object[]) {
                Object[] objects = (Object[]) tag;
                MFElement element = (MFElement) objects[0];
                if (element.getId().equals(elementId)) {
                    return view;
                }
            }
        }
        return null;
    }

	// #527
	// ----------------------------------------------------------------------------------------------------------------//
	/**
	 * When a text element loses focus we have to check in the current page if
	 * any other element depends on the value of that element.
	 * 
	 * If an element does in fact depend on the value of the element that lost
	 * focus then the value is changed based on a lookup query.
	 * 
	 * @param element
	 * @param editText
	 * @return
	 */
	private OnFocusChangeListener newOnFocusChangeLister(final MFElement element, final EditText editText) {
		return new OnFocusChangeListener() {
			final String id = element.getInstanceId();

			@Override
			public void onFocusChange(View v, boolean hasFocus) {
				if (!hasFocus) {
					for (View e : widgets.elementViews) {
						if (e instanceof EditText && !e.equals(editText)) {
							EditText et = (EditText) e;
							copyCurrentPageDataToDocument();
							setDynamicDefaultValue(id, et);
						} else if (e instanceof Spinner) {
							Spinner spinner = (Spinner) e;
							copyCurrentPageDataToDocument();
							setDynamicOptions(id, spinner);
						}
					}
				}
			}
		};
	}

	private void setDynamicDefaultValue(final String changedElementId, TextView editText) {
		setDynamicDefaultValue(changedElementId, editText, new ArrayList<String>());
	}

	private void setDynamicDefaultValue(final String changedElementId, TextView editText, List<String> path) {
		Object[] tag = (Object[]) editText.getTag();
		MFElement element2 = (MFElement) tag[0];
		List<MFFilter> defaultValueFilters = element2.getDefaultValueFilters();
		path.add(changedElementId);
		if (defaultValueFilters != null && !defaultValueFilters.isEmpty()) {
			for (MFFilter f : defaultValueFilters) {
				if (f.getRightValue().equals(changedElementId) && !path.contains(element2.getInstanceId())) {
					// If the current element has a condition that depends on
					// the changedElementId
					setDynamicDefaultValue(editText);
					// element2 - editText - has changed, so the elements that
					// depend on it should also change
					for (View e : widgets.elementViews) {
						if (e instanceof EditText && !e.equals(editText)) {
							EditText et = (EditText) e;
							copyCurrentPageDataToDocument();
							setDynamicDefaultValue(element2.getInstanceId(), et, path);
						} else if (e instanceof Spinner) {
							Spinner spinner = (Spinner) e;
							copyCurrentPageDataToDocument();
							setDynamicOptions(element2.getInstanceId(), spinner);
						}
					}
				}
			}
		}
		path.remove(changedElementId);
	}

	private void setDynamicDefaultValue(TextView editText) {
		Object[] tag = (Object[]) editText.getTag();
		MFElement element2 = (MFElement) tag[0];
		List<LookupData> values = model.listPossibleValues(element2);
		int size = values == null ? 0 : values.size();
		if (values != null && size == 1) {
			editText.setText(widgets.getLabelFormatter().toText(values.get(0)));
		} else if (values != null && size != 0) {
			editText.setHint("Possible values :" + size); // FIXME i18n
		}
	}

	@Override
	public void onItemSelected(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
		Spinner changedSpinner = (Spinner) arg0;
		Object[] changeSpinnerTag = (Object[]) changedSpinner.getTag();
		MFElement changedSpinnerMeta = (MFElement) changeSpinnerTag[0];
		String instanceId = changedSpinnerMeta.getInstanceId();
		copyCurrentPageDataToDocument();

		for (View e : widgets.elementViews) {
			if (e instanceof Spinner && !e.equals(arg0)) {
				Spinner spinner = (Spinner) e;
				setDynamicOptions(instanceId, spinner);
			} else if (e instanceof EditText) {
				EditText editText = (EditText) e;
				setDynamicDefaultValue(instanceId, editText);
			}
		}

	}

	private void setDynamicOptions(final String changedElementId, Spinner spinner) {
		Object[] tag = (Object[]) spinner.getTag();
		MFElement meta = (MFElement) tag[0];
		List<MFFilter> itemListFilters = meta.getItemListFilters();
		if (itemListFilters != null && !itemListFilters.isEmpty()) {
			for (MFFilter f : itemListFilters) {
				if (f.getRightValue().equals(changedElementId)) {
					// if the modified item affects the list of items in
					// the current spinner
					setDynamicOptions(spinner, meta);
				}
			}
		}
	}

	private void setDynamicOptions(Spinner spinner, MFElement meta) {
		List<LabelAndValue> options = model.listSelectOptions(meta);

        LabelAndValue chooseOption = new LabelAndValue(new LookupData(
                activity.getString(R.string.select_choose_option), FIELD_TYPE.STRING), new LookupData("",
                FIELD_TYPE.STRING));
        options.add(0, chooseOption);

		setAdapter(spinner, options);
		String serializedValue = model.document.get(meta.getInstanceId());
		if (serializedValue != null) {
			setSelected(spinner, options, serializedValue);
		}
	}

	@Override
	public void onNothingSelected(AdapterView<?> arg0) {

	}

	// ----------------------------------------------------------------------------------------------------------------//

	public void onPause() {
		copyCurrentPageDataToDocument();
		widgets.pageLayout.requestFocus();
		if (mfFormLocationManager != null) {
			mfFormLocationManager.finish();
		}
	}

	/**
	 * Return true unless there's no page to go back to
	 * 
	 * @return
	 */
	public boolean back() {
		copyCurrentPageDataToDocument();
		MFPage page = model.back();
		if (page != null) {
			renderPage(page, false);
			return true;
		}
		return false;
	}

	public Model getModel() {
		//FIXME is this safe?
		widgets.pageLayout.requestFocus();
		return model;
	}

	public MFPage getPossibleNextPage() {
		return possibleNextPage;
	}

}
