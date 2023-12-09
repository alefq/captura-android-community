package py.com.sodep.mobileforms.ui.rendering;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import py.com.sodep.mf.form.model.element.MFElement;
import io.github.jokoframework.chake.R;
import py.com.sodep.mobileforms.ui.list.SpinnerLabelFormatter;
import py.com.sodep.mobileforms.ui.rendering.objects.LookupData;
import android.content.Context;
import android.graphics.Color;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

class UIWidgets {

	class Factory {

		private Factory() {

		}

		View newTextView(MFElement element) {
			TextView labelTextView = new TextView(context);

			labelTextView.setText(element.getProto().getLabel());
			labelTextView.setTextAppearance(context, R.style.mf_element_label);
			View labelView;
			if (element.isRequired()) {
				labelView = newRequiredFieldView(labelTextView);
			} else {
				labelView = labelTextView;
			}

			return labelView;
		}

		Button newButton() {
			Button button = new Button(context);
			button.setBackgroundResource(R.drawable.form_button);
			button.setTextColor(context.getResources().getColor(R.color.mf_button_text_color));
			return button;
		}

		private View newRequiredFieldView(TextView labelTextView) {
			TextView asterisk = new TextView(context);
			asterisk.setText("*");
			asterisk.setTextColor(Color.RED);

			LinearLayout horLayout = new LinearLayout(context);
			horLayout.setOrientation(LinearLayout.HORIZONTAL);
			horLayout.addView(labelTextView, new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT,
					LayoutParams.WRAP_CONTENT));
			horLayout.addView(asterisk, new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT,
					LayoutParams.WRAP_CONTENT));
			return horLayout;
		}

        CheckBox newCheckBox() {
            CheckBox checkBox = new CheckBox(context);
            return checkBox;
        }

	}

	class SpinnerLabelFormatterImpl implements SpinnerLabelFormatter {

		private SpinnerLabelFormatterImpl(Context ctx) {
			super();
		}

		@Override
		public String toText(LookupData lookupData) {
			if (lookupData == null || lookupData.getData() == null) {
				return "null";
			}
			if (lookupData.getData() instanceof Date) {
				Date date = (Date) lookupData.getData();
				return DateFormat.getDateFormat(context).format(date);
			} else {
				return lookupData.getData().toString();
			}
		}

	}

	ViewGroup pageLayout;

	ScrollView pageScrollView;

	Button saveButton;

	Button saveAndNewButton;

	Button nextButton;

	Button activeDateButton;

	Button activeTimeButton;

	View saveActionView;

	// Button clearButton;

	/**
	 * The Views in the current page that correspond to an Element
	 */
	List<View> elementViews = new ArrayList<View>();

	private SpinnerLabelFormatter labelFormatter;

	private Context context;

	private LayoutInflater inflater;

	private Factory factory;

	public UIWidgets(Context context) {
		this.context = context;
		this.inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		this.labelFormatter = new SpinnerLabelFormatterImpl(context);
		this.factory = new Factory();
	}

	void addToPage(View... views) {
		View elementContainer = inflater.inflate(R.layout.element_container, null);
		ViewGroup content = (ViewGroup) elementContainer.findViewById(R.id.element_container_content);
		for (View v : views) {
			content.addView(v);
		}
		pageLayout.addView(elementContainer);
	}

	public Factory getFactory() {
		return factory;
	}

	public SpinnerLabelFormatter getLabelFormatter() {
		return labelFormatter;
	}

}
