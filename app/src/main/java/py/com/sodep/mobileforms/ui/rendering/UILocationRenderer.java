package py.com.sodep.mobileforms.ui.rendering;

import py.com.sodep.mf.form.model.element.MFElement;
import io.github.jokoframework.chake.R;
import py.com.sodep.mobileforms.location.MFLocationManager;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.SpannableString;
import android.text.style.UnderlineSpan;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

class UILocationRenderer {

	private MFElement element;

	private Context context;

	private UIWidgets widgets;

	private Model model;

	UILocationRenderer(Context context, UIWidgets widgets, Model model, MFElement element) {
		this.context = context;
		this.widgets = widgets;
		this.model = model;
		this.element = element;
	}

	public Button render() {
		View labelView = widgets.getFactory().newTextView(element);

		final TextView resolvingTextView = newLocationResolvingTextView();
		final TextView coordinatesTextView = newLocationCoordinatesTextView();

		final Button locationButton = newLocationButton();
		final Object[] tag = new Object[2];
		tag[0] = element;
		locationButton.setTag(tag);
		final Button stopButton = newLocationStopButton();
		final Button clearButton = newLocationClearButton();

		final Handler tickHandler = new Handler();

		final Runnable tickRunnable = new Runnable() {
			int n = 0;

			@Override
			public void run() {
				String dots = "";
				n++;
				for (int i = 0; i < (n % 4); i++) {
					dots += ".";
				}
				resolvingTextView.setText(context.getString(R.string.resolving) + dots);
				tickHandler.postDelayed(this, 500);
			}
		};

		final Handler locationHandler = new Handler() {
			@Override
			public void handleMessage(Message msg) {
				super.handleMessage(msg);
				switch (msg.what) {
				case MFLocationManager.MF_LOCATION_ACQUIRED:
				case MFLocationManager.MF_LOCATION_ACQUIRED_NOT_ACCURATE:
					Bundle data = msg.getData();

					double latitude = data.getDouble("latitude");
					double longitude = data.getDouble("longitude");
					double altitude = data.getDouble("altitude");
					double accuracy = data.getDouble("accuracy");
					SpannableString string = locationSpannableString(latitude, longitude, altitude, accuracy);
					coordinatesTextView.setText(string);
					tag[1] = latitude + "," + longitude + "," + altitude + "," + accuracy;
					break;
				case MFLocationManager.MF_LOCATION_FINISHED:
					locationButton.setVisibility(View.GONE);
					resolvingTextView.setVisibility(View.GONE);
					stopButton.setVisibility(View.GONE);
					clearButton.setVisibility(View.VISIBLE);
					tickHandler.removeCallbacks(tickRunnable);
					break;
				}
			}
		};

		coordinatesTextView.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				if (tag[1] != null) {
					String str = (String) tag[1];
					String[] splitted = str.split(",");
					Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("geo:" + splitted[0] + "," + splitted[1]
							+ "?q=" + splitted[0] + "," + splitted[1] + "(Location+Location)"));
					context.startActivity(intent);
				}
			}
		});

		clearButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				resolvingTextView.setVisibility(View.GONE);
				coordinatesTextView.setVisibility(View.GONE);
				stopButton.setVisibility(View.GONE);
				clearButton.setVisibility(View.GONE);
				locationButton.setVisibility(View.VISIBLE);
				tag[1] = null;
				model.document.remove(element.getInstanceId());
			}
		});

		final MFLocationManager mgr = new MFLocationManager(context.getApplicationContext(), locationHandler);

		stopButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				mgr.finish();
				resolvingTextView.setVisibility(View.GONE);
				coordinatesTextView.setVisibility(View.VISIBLE);
				stopButton.setVisibility(View.GONE);
				clearButton.setVisibility(View.VISIBLE);
				locationButton.setVisibility(View.GONE);
			}
		});

		locationButton.setOnClickListener(new OnClickListener() {
			@SuppressLint("HandlerLeak")
			@Override
			public void onClick(View v) {
				resolvingTextView.setVisibility(View.VISIBLE);
				coordinatesTextView.setText("");
				coordinatesTextView.setVisibility(View.VISIBLE);
				mgr.calculate();
				stopButton.setVisibility(View.VISIBLE);
				locationButton.setVisibility(View.GONE);
				tickHandler.post(tickRunnable);
			}
		});

		widgets.addToPage(labelView, resolvingTextView, coordinatesTextView, locationButton, stopButton, clearButton);

		SpannableString spannableString = restoreLocation(element, tag);
		if (spannableString != null) {
			resolvingTextView.setVisibility(View.GONE);
			locationButton.setVisibility(View.GONE);
			stopButton.setVisibility(View.GONE);
			coordinatesTextView.setVisibility(View.VISIBLE);
			coordinatesTextView.setText(spannableString);
			clearButton.setVisibility(View.VISIBLE);
		}
		return locationButton;
	}

	private TextView newLocationResolvingTextView() {
		TextView resolvingTextView = new TextView(context);
		resolvingTextView.setText(R.string.resolving);
		resolvingTextView.setVisibility(View.GONE);
		return resolvingTextView;
	}

	private TextView newLocationCoordinatesTextView() {
		TextView coordinatesTextView = new TextView(context);
		coordinatesTextView.setVisibility(View.GONE);
		coordinatesTextView.setTextColor(Color.parseColor("#1122CC"));
		return coordinatesTextView;
	}

	private Button newLocationButton() {
		final Button locationButton = widgets.getFactory().newButton();
		locationButton.setText(R.string.get_current_location);
		return locationButton;
	}

	private Button newLocationClearButton() {
		Button button = widgets.getFactory().newButton();
		button.setText(R.string.clear);
		button.setVisibility(View.GONE);
		return button;
	}

	private Button newLocationStopButton() {
		Button button = widgets.getFactory().newButton();
		button.setText(R.string.stop);
		button.setVisibility(View.GONE);
		return button;
	}

	private SpannableString locationSpannableString(double latitude, double longitude, double altitude, double accuracy) {
		String locationStr = context.getString(R.string.latitude) + " = " + latitude + ", "
				+ context.getString(R.string.longitude) + " = " + longitude + "; "
				// + activity.getString(R.string.altitude) + " = " + altitude +
				// "; "
				+ context.getString(R.string.accuracy) + " = " + accuracy;
		SpannableString string = new SpannableString(locationStr);
		string.setSpan(new UnderlineSpan(), 0, string.length(), 0);
		return string;
	}

	private SpannableString restoreLocation(MFElement element, Object[] tag) {
		String string = model.document.get(element.getInstanceId());
		if (string != null) {
			tag[1] = string;
			String[] splitted = string.split(",");
			double latitude = Double.parseDouble(splitted[0]);
			double longitude = Double.parseDouble(splitted[1]);
			double altitude = Double.parseDouble(splitted[2]);
			double accuracy = Double.parseDouble(splitted[3]);
			return locationSpannableString(latitude, longitude, altitude, accuracy);
		}
		return null;
	}

}
