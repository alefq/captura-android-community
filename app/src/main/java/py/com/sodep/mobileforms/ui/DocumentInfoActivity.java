package py.com.sodep.mobileforms.ui;

import java.text.DateFormat;
import java.util.Date;

import py.com.sodep.mf.exchange.objects.metadata.Form;
import io.github.jokoframework.chake.R;
import py.com.sodep.mobileforms.dataservices.documents.DocumentMetadata;
import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

public class DocumentInfoActivity extends Activity {

	private LayoutInflater inflater = null;

	private DateFormat dateFormat = null;

	private DateFormat timeFormat = null;
	
	public static final String EXTRA_DOCUMENT_METADATA = "metadata";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		dateFormat = android.text.format.DateFormat.getDateFormat(this);
		timeFormat = android.text.format.DateFormat.getTimeFormat(this);

		setContentView(R.layout.document_info);

		DocumentMetadata metadata = (DocumentMetadata) getIntent().getSerializableExtra(EXTRA_DOCUMENT_METADATA);
		Form f = metadata.getForm();

		TableLayout definitionTable = (TableLayout) findViewById(R.id.definition_table);

		definitionTable.addView(getNewRow(R.string.form_id, f.getId().toString()));
		definitionTable.addView(getNewRow(R.string.version, f.getVersion().toString()));
		definitionTable.addView(getNewRow(R.string.saved_at, metadata.getSavedAt()));

		TableLayout syncDataTable = (TableLayout) findViewById(R.id.sync_data_table);

		switch (metadata.getStatus()) {
		case DocumentMetadata.STATUS_FAILED:
			syncDataTable.addView(getNewRow(R.string.status, getString(R.string.status_failed)));
			addLastFailureRow(metadata, syncDataTable);
			addRetriesRow(metadata, syncDataTable);
			addResponseCodeRow(metadata, syncDataTable);
			addFailMessage(metadata, syncDataTable);
			break;
		case DocumentMetadata.STATUS_IN_PROGRESS:
			syncDataTable.addView(getNewRow(R.string.status, getString(R.string.status_in_progress)));
			break;
		case DocumentMetadata.STATUS_NOT_SYNCED:
			syncDataTable.addView(getNewRow(R.string.status, getString(R.string.status_not_synced)));
			break;
		case DocumentMetadata.STATUS_REJECTED:
			syncDataTable.addView(getNewRow(R.string.status, getString(R.string.status_rejected)));
			addFailMessage(metadata, syncDataTable);
			break;
		case DocumentMetadata.STATUS_SYNCED:
			syncDataTable.addView(getNewRow(R.string.status, getString(R.string.status_synced)));
			syncDataTable.addView(getNewRow(R.string.synced_at, metadata.getSyncedAt()));
			addRetriesRow(metadata, syncDataTable);
			break;
		}

	}

	private void addFailMessage(DocumentMetadata metadata, TableLayout syncDataTable) {
		String failMessage = metadata.getFailMessage();
		if (failMessage != null && failMessage.trim().length() > 0) {
			syncDataTable.addView(getNewRow(R.string.fail_message_info, failMessage, Gravity.RIGHT));
		}
	}

	private void addResponseCodeRow(DocumentMetadata metadata, TableLayout syncDataTable) {
		int responseCode = metadata.getResponseCode();
		if (responseCode > 0) {
			syncDataTable.addView(getNewRow(R.string.response_code, Integer.toString(responseCode)));
		}
	}

	private void addLastFailureRow(DocumentMetadata metadata, TableLayout syncDataTable) {
		Date failDate = metadata.getLastFailureAt();
		if (failDate != null) {
			syncDataTable.addView(getNewRow(R.string.last_failure, failDate));
		}
	}

	private void addRetriesRow(DocumentMetadata metadata, TableLayout syncDataTable) {
		if (metadata.getUploadAttempts() > 0) {
			int uploadAttempts = metadata.getUploadAttempts();
			syncDataTable.addView(getNewRow(R.string.retries, Integer.toString(uploadAttempts - 1)));
		}
	}

	private TableRow getNewRow(int label, String info) {
		return getNewRow(label, info, Gravity.RIGHT);
	}

	private TableRow getNewRow(int label, Date info) {
		return getNewRow(label, formatDate(info));
	}

	private TableRow getNewRow(int label, String info, int infoGravity) {
		TableRow row = (TableRow) inflater.inflate(R.layout.document_info_table_row, null);
		TextView labelView = (TextView) row.findViewById(R.id.row_label);
		labelView.setText(label);
		TextView infoView = (TextView) row.findViewById(R.id.row_info);
		infoView.setGravity(infoGravity);
		infoView.setText(info);
		return row;
	}

	private String formatDate(Date date) {
		return dateFormat.format(date) + " " + timeFormat.format(date);
	}

}
