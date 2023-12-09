package py.com.sodep.mobileforms.ui.list;

import java.util.List;

import py.com.sodep.mf.exchange.objects.metadata.DescribableObject;
import io.github.jokoframework.chake.R;
import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

public class ItemAdapter<T extends DescribableObject> extends ArrayAdapter<Item<T>> {

	public ItemAdapter(Context context, int resource, int textViewResourceId,
			List<Item<T>> objects) {
		super(context, resource, textViewResourceId, objects);
	}

	public ItemAdapter(Context context, int resource, int textViewResourceId,
			Item<T>[] objects) {
		super(context, resource, textViewResourceId, objects);
	}

	public ItemAdapter(Context context, int resource, int textViewResourceId) {
		super(context, resource, textViewResourceId);
	}

	public ItemAdapter(Context context, int textViewResourceId,
			List<Item<T>> objects) {
		super(context, textViewResourceId, objects);
	}

	public ItemAdapter(Context context, int textViewResourceId,
			Item<T>[] objects) {
		super(context, textViewResourceId, objects);
	}

	public ItemAdapter(Context context, int textViewResourceId) {
		super(context, textViewResourceId);
	}
	
	public View getView(int position, View convertView, ViewGroup parent) {
		View view = convertView;
		if (view == null) {
			LayoutInflater inflater = (LayoutInflater) super.getContext().getSystemService(
					Context.LAYOUT_INFLATER_SERVICE);
			view = inflater.inflate(R.layout.list_item2, null);
		}

		Item<? extends DescribableObject> item = this.getItem(position);
		DescribableObject object = item.getObject();
		TextView labelTextView = null;
		if (item.isActive()) {
			if (object.getDescription() != null && object.getDescription().trim().length() > 0) {
				labelTextView = (TextView) view.findViewById(R.id.list_item2_textView02);
				TextView descriptionTextView = (TextView) view.findViewById(R.id.list_item2_textView10);
				descriptionTextView.setText(object.getDescription());

				((TextView) view.findViewById(R.id.list_item2_textView01)).setVisibility(View.GONE);
				descriptionTextView.setVisibility(View.VISIBLE);
			} else {
				labelTextView = (TextView) view.findViewById(R.id.list_item2_textView01);
				TextView descriptionTextView = (TextView) view.findViewById(R.id.list_item2_textView10);

				((TextView) view.findViewById(R.id.list_item2_textView02)).setVisibility(View.GONE);
				descriptionTextView.setVisibility(View.GONE);
			}
			labelTextView.setEnabled(true);
			labelTextView.setTextColor(Color.BLACK);
		} else {
			labelTextView = (TextView) view.findViewById(R.id.list_item2_textView02);
			labelTextView.setEnabled(false);
			TextView descriptionTextView = (TextView) view.findViewById(R.id.list_item2_textView10);
			descriptionTextView.setText(item.getInactiveMessage());
			labelTextView.setEnabled(false);

			((TextView) view.findViewById(R.id.list_item2_textView01)).setVisibility(View.GONE);
			descriptionTextView.setVisibility(View.VISIBLE);
			labelTextView.setEnabled(false);
			labelTextView.setTextColor(Color.GRAY);
		}
		labelTextView.setText(object.getLabel());
		labelTextView.setVisibility(View.VISIBLE);
		view.setBackgroundResource(R.drawable.list_row);
//		if (position % 2 == 0) {
//			view.setBackgroundResource(R.drawable.list_row_even);
//		} else {
//			view.setBackgroundResource(R.drawable.list_row_odd);
//		}
		return view;
	}

}
