package py.com.sodep.mobileforms.ui.list;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import io.github.jokoframework.chake.R;
import py.com.sodep.mobileforms.dataservices.documents.DocumentMetadata;
import android.content.Context;
import android.graphics.drawable.AnimationDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

public class DocumentsAdapter extends BaseAdapter {

	private List<DocumentMetadata> documents = new ArrayList<DocumentMetadata>();

	private LayoutInflater inflater;

	private SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());

	private Context context;

	public DocumentsAdapter(Context context) {
		inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		this.context = context;
	}

	@Override
	public int getCount() {
		return documents.size();
	}

	public void clear() {
		documents.clear();
		notifyDataSetChanged();
	}

	public void add(DocumentMetadata documentMetadata) {
		this.documents.add(documentMetadata);
		notifyDataSetChanged();
	}

	public void addAll(List<DocumentMetadata> list) {
		this.documents.addAll(list);
		notifyDataSetChanged();
	}

	@Override
	public Object getItem(int position) {
		return documents.get(position);
	}

	@Override
	public long getItemId(int position) {
		return documents.get(position).getId();
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		View vi = convertView;
		if (convertView == null) {
			vi = inflater.inflate(R.layout.document_row, null);
		}

		DocumentMetadata meta = documents.get(position);
		int status = meta.getStatus();

		int statusImage = 0;
		String statusText = "";
		switch (status) {
		case DocumentMetadata.STATUS_NOT_SYNCED:
			statusImage = R.drawable.not_synced;
			statusText = context.getString(R.string.document_status_not_synced);
			break;
		case DocumentMetadata.STATUS_FAILED:
			statusImage = R.drawable.failed;
			statusText = context.getString(R.string.document_status_sync_failed); 
			break;
		case DocumentMetadata.STATUS_IN_PROGRESS:
			statusImage = R.drawable.in_progress_animation;
			statusText = context.getString(R.string.document_status_sync_in_progress) + " " + meta.getPercentage() + "%";
			break;
		case DocumentMetadata.STATUS_REJECTED:
			statusImage = R.drawable.rejected;
			statusText = context.getString(R.string.document_status_rejected);
			break;
		case DocumentMetadata.STATUS_SYNCED:
			statusImage = R.drawable.synced;
			statusText = context.getString(R.string.document_status_synced);
			break;
		case DocumentMetadata.STATUS_DRAFT:
			statusImage = R.drawable.draft;
			statusText = context.getString(R.string.document_status_draft);
			break;
		}

		TextView title = (TextView) vi.findViewById(R.id.document_row_title);
		TextView description = (TextView) vi.findViewById(R.id.document_row_description);
		TextView savedAt = (TextView) vi.findViewById(R.id.document_saved_at);
		TextView syncedAt = (TextView) vi.findViewById(R.id.document_synced_at);
		ImageView image = (ImageView) vi.findViewById(R.id.list_image);

		title.setText(meta.getForm().getLabel());
		description.setText(statusText);
		savedAt.setText(context.getString(R.string.document_saved_at, sdf.format(meta.getSavedAt())));
		if (status == DocumentMetadata.STATUS_SYNCED) {
			syncedAt.setText(context.getString(R.string.document_synced_at, sdf.format(meta.getSyncedAt())));
		} else {
			if (meta.getUploadAttempts() > 0) {
				syncedAt.setText(context.getString(R.string.sync_attempts, meta.getUploadAttempts()));
			} else {
				syncedAt.setText("");
			}
		}

		image.setImageResource(statusImage);
		if (status == DocumentMetadata.STATUS_IN_PROGRESS) {
			ProgressBar progressBar = (ProgressBar) vi.findViewById(R.id.progressBar1);
			progressBar.setVisibility(View.VISIBLE);
			progressBar.setProgress(meta.getPercentage());
			
			AnimationDrawable animation = (AnimationDrawable) image.getDrawable();
			animation.start();
		}

		vi.setBackgroundResource(R.drawable.list_row);
//		if (position % 2 == 0) {
//			vi.setBackgroundResource(R.drawable.list_row_even);
//		} else {
//			vi.setBackgroundResource(R.drawable.list_row_odd);
//		}

		return vi;

	}

}
