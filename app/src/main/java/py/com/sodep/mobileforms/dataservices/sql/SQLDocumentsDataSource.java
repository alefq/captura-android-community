package py.com.sodep.mobileforms.dataservices.sql;

import static py.com.sodep.mobileforms.dataservices.sql.SodepSQLiteOpenHelper.FORMS_DATA_TABLE;
import static py.com.sodep.mobileforms.dataservices.sql.SodepSQLiteOpenHelper.FORMS_TABLE;
import static py.com.sodep.mobileforms.dataservices.sql.SodepSQLiteOpenHelper.PROJECTS_TABLE;

import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import py.com.sodep.mf.exchange.objects.metadata.Form;
import io.github.jokoframework.chake.MFApplication;
import py.com.sodep.mobileforms.dataservices.DocumentsDataSource;
import py.com.sodep.mobileforms.dataservices.FormsDAO;
import py.com.sodep.mobileforms.dataservices.documents.Document;
import py.com.sodep.mobileforms.dataservices.documents.DocumentMetadata;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class SQLDocumentsDataSource implements DocumentsDataSource {

	private static final String LOG_TAG = SQLDocumentsDataSource.class.getSimpleName();

	private SodepSQLiteOpenHelper sqlHelper;

	private ObjectMapper mapper = new ObjectMapper();

	private DateFormat dateFormatISO8601 = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private DateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd");

	public SQLDocumentsDataSource() {
		this.sqlHelper = MFApplication.sqlHelper;
        dateFormatISO8601.setTimeZone(TimeZone.getTimeZone("GMT"));
    }

	private String serialize(Map<String, String> document) {
		try {
			return mapper.writeValueAsString(document);
		} catch (JsonGenerationException e) {
			Log.e(LOG_TAG, e.getMessage(), e);
		} catch (JsonMappingException e) {
			Log.e(LOG_TAG, e.getMessage(), e);
		} catch (IOException e) {
			Log.e(LOG_TAG, e.getMessage(), e);
		}
		return null;
	}

	@SuppressWarnings("unchecked")
	private Map<String, String> unserialize(String serialized) {
		try {
			return mapper.readValue(serialized, Map.class);
		} catch (JsonParseException e) {
			Log.e(LOG_TAG, e.getMessage(), e);
		} catch (JsonMappingException e) {
			Log.e(LOG_TAG, e.getMessage(), e);
		} catch (IOException e) {
			Log.e(LOG_TAG, e.getMessage(), e);
		}
		return null;
	}

	@Override
	public Long save(Form form, Long documentId, final Map<String, String> document) {
		return save(form, documentId, DocumentMetadata.STATUS_NOT_SYNCED, document);
	}

	@Override
	public Long saveDraft(Form form, Long documentId, final Map<String, String> document) {
		return save(form, documentId, DocumentMetadata.STATUS_DRAFT, document);
	}

	private Long save(Form form, Long id, int status, final Map<String, String> document) {
		SQLiteDatabase db = sqlHelper.getWritableDatabase();
		if (document != null) {
			ContentValues values = getSaveContentValues(form, document);
			values.put("status", status);
			if (id == null) {
				return db.insertOrThrow(FORMS_DATA_TABLE, null, values);
			} else {
				boolean updated = db.update(FORMS_DATA_TABLE, values, "_ID=?", new String[] { Long.toString(id) }) > 0;
				if (updated) {
					return id;
				} else {
					return null;
				}
			}
		} else {
			throw new RuntimeException("Null data!");
		}
	}

	private ContentValues getSaveContentValues(Form form, final Map<String, String> document) {
		ContentValues values = new ContentValues();
		String docStr = serialize(document);
		values.put("data", docStr);
		// meta-data
		values.put("version", form.getVersion());
		values.put("form", form.getId());
		values.put("saved_at", dateFormatISO8601.format(new Date()));

        return values;
	}

	@Override
	public List<Map<String, String>> listUnsyncedDocuments(Form f) {
		SQLiteDatabase db = sqlHelper.getReadableDatabase();
		String[] columns = new String[] { "_id", "saved_at", "data" };
		String formId = Long.toString(f.getId());
		String version = Long.toString(f.getVersion());
		Cursor cursor = db.query(FORMS_DATA_TABLE, columns, "status=? AND form=? AND version=?", new String[] { "0",
				formId, version }, null, null, "_id");
		try {
			List<Map<String, String>> result = new ArrayList<Map<String, String>>();
			while (cursor.moveToNext()) {
				Map<String, String> row = new LinkedHashMap<String, String>();
				row.put("form", formId); // FIXME this can be ovewritten by
											// row.putAll(userData);
				row.put("version", version);
				row.put("_id", cursor.getString(0));
				row.put("saved_at", cursor.getString(1));
				Map<String, String> userData = unserialize(cursor.getString(2));
				row.putAll(userData); // FIXME metadata will be ovewritten here
										// if user has fields with the same name
				result.add(row);
			}
			removeEmptyStrings(result);
			return result;
		} finally {
			cursor.close();
		}
	}

	@Override
	public List<Long> listUnsyncedDocuments(Long appId) {
		SQLiteDatabase db = sqlHelper.getReadableDatabase();

		Cursor cursor = db.rawQuery("SELECT d._id FROM " + FORMS_DATA_TABLE + " d JOIN " + FORMS_TABLE
				+ " f ON d.form = f.id AND d.version = f.version JOIN " + PROJECTS_TABLE
				+ " p ON f.project = p.id WHERE p.application = ? AND d.status = ? ORDER BY d.saved_at", new String[] {
				Long.toString(appId), Integer.toString(DocumentMetadata.STATUS_NOT_SYNCED) });
		try {
			if (cursor.getCount() > 0) {
				List<Long> list = new ArrayList<Long>();
				while (cursor.moveToNext()) {
					list.add(cursor.getLong(0));
				}
				return list;
			} else {
				return Collections.emptyList();
			}
		} finally {
			cursor.close();
		}

	}

	@Override
	public List<Long> listUnsyncedDocuments() {
		SQLiteDatabase db = sqlHelper.getReadableDatabase();

		Cursor cursor = db.rawQuery("SELECT d._id FROM " + FORMS_DATA_TABLE + " d JOIN " + FORMS_TABLE
				+ " f ON d.form = f.id AND d.version = f.version JOIN " + PROJECTS_TABLE
				+ " p ON f.project = p.id WHERE d.status = ? ORDER BY d.saved_at",
				new String[] { Integer.toString(DocumentMetadata.STATUS_NOT_SYNCED) });
		try {
			if (cursor.getCount() > 0) {
				List<Long> list = new ArrayList<Long>();
				while (cursor.moveToNext()) {
					list.add(cursor.getLong(0));
				}
				return list;
			} else {
				return Collections.emptyList();
			}
		} finally {
			cursor.close();
		}
	}

	private void setDocumentStatus(int status, long id) {
		SQLiteDatabase db = sqlHelper.getWritableDatabase();
		ContentValues contentValues = new ContentValues();
		contentValues.put("status", status);
		db.update(FORMS_DATA_TABLE, contentValues, "_id=?", new String[] { Long.toString(id) });
	}

	@Override
	public void markDocumentsAsSynced(long id) {
		SQLiteDatabase db = sqlHelper.getWritableDatabase();
 		ContentValues contentValues = new ContentValues();
		contentValues.put("synced_at", dateFormatISO8601.format(new Date()));
		db.update(FORMS_DATA_TABLE, contentValues, "_id=?", new String[] { Long.toString(id) });
        // issue #21 in Fabric.
        // A document set as Synced must have a synced_at set
        // This was not being done atomically
        setDocumentStatus(DocumentMetadata.STATUS_SYNCED, id);
	}

	@Override
	public void markDocumentAsRejected(long id) {
		setDocumentStatus(DocumentMetadata.STATUS_REJECTED, id);
	}

	@Override
	public void markDocumentAsRejected(long id, int responseCode, String message) {
		updateFailOrRejectedDocument(id, DocumentMetadata.STATUS_REJECTED, responseCode, message);
	}

	@Override
	public void markDocumentAsInProgress(long id) {
		setDocumentStatus(DocumentMetadata.STATUS_IN_PROGRESS, id);
	}

	@Override
	public void markDocumentAsFailed(long id) {
		markDocumentAsFailed(id, 0);
	}

	@Override
	public void markDocumentAsFailed(long id, int responseCode) {
		markDocumentAsFailed(id, responseCode, null);
	}

	@Override
	public void markDocumentAsFailed(long id, int responseCode, String message) {
		updateFailOrRejectedDocument(id, DocumentMetadata.STATUS_FAILED, responseCode, message);
	}

	private void updateFailOrRejectedDocument(long id, int status, int responseCode, String message) {
		SQLiteDatabase db = sqlHelper.getWritableDatabase();
		message = message == null ? "" : message;
		db.execSQL("UPDATE " + FORMS_DATA_TABLE
				+ " SET status = ?, response_code = ?, fail_message = ?, upload_attempts = upload_attempts + 1, "
				+ " failed_at = datetime('now') WHERE _id = ?",
				new String[] { Integer.toString(status), Integer.toString(responseCode), message, Long.toString(id) });
	}

    @Override
    public void resetUploadAttempts(long id) {
        SQLiteDatabase db = sqlHelper.getWritableDatabase();
        db.execSQL("UPDATE " + FORMS_DATA_TABLE
                        + " SET upload_attempts = 0 "
                        + " WHERE _id = ?",
                new String[]{Long.toString(id)});
    }

	@Override
	public void deleteSynced() {
		SQLiteDatabase db = sqlHelper.getWritableDatabase();
		db.delete(FORMS_DATA_TABLE, "status=?", new String[] { "1" });
	}

	@Override
	public Form getFormOfDocument(long id) {
		SQLiteDatabase db = sqlHelper.getReadableDatabase();
		String[] columns = new String[] { "form", "version" };
		Cursor cursor = db.query(FORMS_DATA_TABLE, columns, "_id=?", new String[] { Long.toString(id) }, null, null,
				null);
		try {
			if (cursor.moveToFirst()) {
				FormsDAO formsDAO = new SQLFormsDAO();
				long formId = cursor.getLong(0);
				long version = cursor.getLong(1);
				return formsDAO.getForm(formId, version);
			}
			return null;
		} finally {
			cursor.close();
		}
	}

	@Override
	public Map<String, String> getDocumentAsMap(long id) {
		SQLiteDatabase db = sqlHelper.getReadableDatabase();
		String[] columns = new String[] { "_id", "saved_at", "data", "form", "version" };

		Cursor cursor = db.query(FORMS_DATA_TABLE, columns, "_id=?", new String[] { Long.toString(id) }, null, null,
				null);
		try {
			if (cursor.moveToFirst()) {
				Map<String, String> row = new LinkedHashMap<String, String>();
				row.put("_id", cursor.getString(0));
				row.put("saved_at", cursor.getString(1));
				Map<String, String> userData = unserialize(cursor.getString(2));
				row.put("form", Long.toString(cursor.getLong(3)));
				row.put("version", Long.toString(cursor.getLong(4)));
				row.putAll(userData);
				return row;
			}

			return null;
		} finally {
			cursor.close();
		}
	}

	private void removeEmptyStrings(List<Map<String, String>> data) {
		if (data != null) {
			Iterator<Map<String, String>> docIter = data.iterator();
			while (docIter.hasNext()) {
				removeEmtpyStrings(docIter);
			}
		}
	}

	private void removeEmtpyStrings(Iterator<Map<String, String>> docIter) {
		Map<String, String> doc = docIter.next();
		Iterator<String> keyIter = doc.keySet().iterator();
		while (keyIter.hasNext()) {
			String key = keyIter.next();
			String value = doc.get(key);
			if (value == null || (value != null && value.trim().length() == 0)) {
				keyIter.remove();
			}
		}
	}

	@Override
	public LinkedHashMap<Date, List<DocumentMetadata>> listHistory(long appId) {
		SQLiteDatabase db = sqlHelper.getReadableDatabase();
		// http://sqlite.org/lang_datefunc.html
		Cursor cursor = db.rawQuery(
				"SELECT date(d.saved_at) as doc_date, d.saved_at, d._id, d.status, d.form, d.version, d.synced_at, "
						+ "d.failed_at, d.response_code, d.upload_attempts, d.fail_message FROM " + FORMS_DATA_TABLE
						+ " d JOIN " + FORMS_TABLE + " f ON d.form = f.id AND d.version = f.version JOIN "
						+ PROJECTS_TABLE
						+ " p ON f.project = p.id WHERE p.application = ? ORDER BY doc_date DESC, d.saved_at DESC",
				new String[] { Long.toString(appId) });

		LinkedHashMap<Date, List<DocumentMetadata>> list = new LinkedHashMap<Date, List<DocumentMetadata>>();

		try {
			if (cursor.getCount() > 0) {
				FormsDAO formsDAO = new SQLFormsDAO();
				List<Form> forms = formsDAO.listAllForms(appId);

				while (cursor.moveToNext()) {
					String dateStr = cursor.getString(0);
					Date date = dateFormatter.parse(dateStr);
					List<DocumentMetadata> dateList = list.get(date);
					if (dateList == null) {
						dateList = new ArrayList<DocumentMetadata>();
						list.put(date, dateList);
					}

					long formId = cursor.getLong(4);
					long formVersion = cursor.getLong(5);
					Form f = getForm(forms, formId, formVersion);

					DocumentMetadata meta = getDocumentMetadataFromCursor(cursor, f, new DocumentMetadata());
					dateList.add(meta);
				}
			}
			return list;
		} catch (ParseException e) {
			throw new RuntimeException(e);
		} finally {
			cursor.close();
		}
	}

	private <T extends DocumentMetadata> T getDocumentMetadataFromCursor(Cursor cursor, Form f, T inst)
			throws ParseException {
		Date savedAt = dateFormatISO8601.parse(cursor.getString(1));
		long id = cursor.getLong(2);
		int status = cursor.getInt(3);

		String syncedAt = cursor.getString(6);
		String failedAt = cursor.getString(7);
		int responseCode = cursor.getInt(8);
		int uploadAttempts = cursor.getInt(9);
		String failMessage = cursor.getString(10);

		inst.setId(id);
		inst.setSavedAt(savedAt);
		inst.setForm(f);
		inst.setStatus(status);

		if (syncedAt != null) {
			inst.setSyncedAt(dateFormatISO8601.parse(syncedAt));
		}

		if (failedAt != null) {
			inst.setLastFailureAt(dateFormatISO8601.parse(failedAt));
		}

		inst.setResponseCode(responseCode);
		inst.setUploadAttempts(uploadAttempts);
		inst.setFailMessage(failMessage);
		return inst;
	}

	private Form getForm(List<Form> forms, long id, long version) {
		for (Form f : forms) {
			if (f.getId().equals(id) && f.getVersion().equals(version)) {
				return f;
			}
		}
		return null;
	}

	@Override
	public void resetAllFailed(int max) {
		SQLiteDatabase db = sqlHelper.getWritableDatabase();
		db.execSQL("UPDATE " + FORMS_DATA_TABLE + " SET status=? WHERE status=? AND upload_attempts < ?", new String[] {
				Integer.toString(DocumentMetadata.STATUS_NOT_SYNCED), Integer.toString(DocumentMetadata.STATUS_FAILED),
				Integer.toString(max) });
	}

	@Override
	public void resetAllInProgress() {
		SQLiteDatabase db = sqlHelper.getWritableDatabase();
		ContentValues contentValues = new ContentValues();
		contentValues.put("status", DocumentMetadata.STATUS_NOT_SYNCED);
		db.update(FORMS_DATA_TABLE, contentValues, "status=?",
				new String[] { Integer.toString(DocumentMetadata.STATUS_IN_PROGRESS) });
	}

	@Override
	public void deleteAll(int[] status) {
		SQLiteDatabase db = sqlHelper.getReadableDatabase();
		StringBuilder sb = new StringBuilder();
		String[] whereArgs = new String[status.length];
		int lastIndex = status.length - 1;
		for (int i = 0; i < lastIndex; i++) {
			whereArgs[i] = Integer.toString(status[i]);
			sb.append("status = ? OR ");
		}

		whereArgs[lastIndex] = Integer.toString(status[lastIndex]);
		sb.append("status = ?");

		db.delete(FORMS_DATA_TABLE, sb.toString(), whereArgs);

	}

	@Override
	public void setMD5(long id, String md5) {
		SQLiteDatabase db = sqlHelper.getWritableDatabase();
		ContentValues contentValues = new ContentValues();
		contentValues.put("md5", md5);
		db.update(FORMS_DATA_TABLE, contentValues, "_id=?", new String[] { Long.toString(id) });
	}

	@Override
	public String getMD5(long id) {
		SQLiteDatabase db = sqlHelper.getReadableDatabase();
		Cursor cursor = db.query(FORMS_DATA_TABLE, new String[] { "md5" }, "_id=?", new String[] { Long.toString(id) },
				null, null, null);
		if (cursor.moveToFirst()) {
			return cursor.getString(0);
		} else {
			return null;
		}
	}

	@Override
	public void delete(Long id) {
		SQLiteDatabase db = sqlHelper.getWritableDatabase();
		db.delete(FORMS_DATA_TABLE, "_ID=?", new String[] { Long.toString(id) });
	}

	@Override
	public Document getDocument(long id) {
		SQLiteDatabase db = sqlHelper.getReadableDatabase();
		Cursor cursor = db.rawQuery(
				"SELECT date(d.saved_at) as doc_date, d.saved_at, d._id, d.status, d.form, d.version, d.synced_at, "
						+ "d.failed_at, d.response_code, d.upload_attempts, d.fail_message, d.data FROM " + FORMS_DATA_TABLE
						+ " d JOIN " + FORMS_TABLE + " f ON d.form = f.id AND d.version = f.version JOIN "
						+ PROJECTS_TABLE + " p ON f.project = p.id WHERE d._id=?", new String[] { Long.toString(id) });

		FormsDAO formsDAO = new SQLFormsDAO();
		try {
			if (cursor.moveToFirst()) {
				long formId = cursor.getLong(4);
				long formVersion = cursor.getLong(5);
				Form form = formsDAO.getForm(formId, formVersion);
				Document document = getDocumentMetadataFromCursor(cursor, form, new Document());
				Map<String, String> userData = unserialize(cursor.getString(11));
				document.putAll(userData);
				return document;
			}
			return null;
		} catch (ParseException e) {
			throw new RuntimeException(e);
		} finally {
			cursor.close();
		}
	}
}
