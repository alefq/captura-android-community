package py.com.sodep.mobileforms.dataservices.sql;

import static py.com.sodep.mobileforms.dataservices.sql.SodepSQLiteOpenHelper.FORMS_TABLE;
import static py.com.sodep.mobileforms.dataservices.sql.SodepSQLiteOpenHelper.FORMS_DATA_FIELDS_TABLE;
import static py.com.sodep.mobileforms.dataservices.sql.SodepSQLiteOpenHelper.PROJECTS_TABLE;

import java.util.ArrayList;
import java.util.List;

import io.github.jokoframework.chake.MFApplication;
import py.com.sodep.mf.exchange.objects.metadata.Form;
import py.com.sodep.mobileforms.dataservices.FormsDAO;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

public class SQLFormsDAO implements FormsDAO {

	// private static final String LOG_TAG = SQLFormsDAO.class.getSimpleName();

	private SodepSQLiteOpenHelper sqlHelper;

	public SQLFormsDAO() {
		sqlHelper = MFApplication.sqlHelper;
	}

	@Override
	public void saveForm(Form form) {
		SQLiteDatabase db = sqlHelper.getWritableDatabase();
		insertForm(db, form);
	}

	private void insertForm(SQLiteDatabase db, Form form) {
		ContentValues values = new ContentValues();
		values.put("id", form.getId());
		values.put("label", form.getLabel());
		values.put("description", form.getDescription());
		values.put("project", form.getProjectId());
		values.put("version", form.getVersion());
		values.put("definition", form.getDefinition());
		db.insert(FORMS_TABLE, null, values);
	}

	@Override
	public String loadDefinition(Form f) {
		SQLiteDatabase db = sqlHelper.getReadableDatabase();
		String[] columns = new String[] { "definition" };
		Cursor cursor = db
				.query(FORMS_TABLE,
						columns,
						"project=? AND id=? AND version=?",
						new String[] { Long.toString(f.getProjectId()), Long.toString(f.getId()),
								Long.toString(f.getVersion()) }, null, null, null);
		try {
			String definition = null;
			if (cursor.moveToNext()) {
				definition = cursor.getString(0);
			}
			f.setDefinition(definition);
			return definition;
		} finally {
			cursor.close();
		}
	}

	@Override
	public List<Form> listForms(Long projectId) {
		SQLiteDatabase db = sqlHelper.getReadableDatabase();
		Cursor cursor = db.rawQuery("SELECT id, label, description, max(version), project, required_lookuptables "
				+ "FROM forms WHERE project=? GROUP BY id", new String[] { Long.toString(projectId) });
		try {
			List<Form> forms = new ArrayList<Form>();
			while (cursor.moveToNext()) {
				Form form = new Form();
				form.setId(cursor.getLong(0));
				form.setLabel(cursor.getString(1));
				form.setDescription(cursor.getString(2));
				form.setVersion(cursor.getLong(3));
				form.setProjectId(cursor.getLong(4));
				form.setRequiredLookupTables(readCommaSeparatedValues(cursor.getString(5)));
				forms.add(form);
			}
			return forms;
		} finally {
			cursor.close();
		}
	}

	@Override
	public void deleteForm(Long formId, Long version) {
		SQLiteDatabase db = sqlHelper.getWritableDatabase();
		String[] bindArgs = new String[] { Long.toString(formId), Long.toString(version) };
		db.delete(FORMS_TABLE, "id=? AND version=?", bindArgs);
	}

	@Override
	public void deleteForm(Long formId) {
		SQLiteDatabase db = sqlHelper.getWritableDatabase();
		String[] bindArgs = new String[] { Long.toString(formId) };
		db.delete(FORMS_TABLE, "id=?", bindArgs);
	}

	private void deleteFormFields(SQLiteDatabase db, Long formId, Long version) {
		String[] bindArgs = new String[] { Long.toString(formId), Long.toString(version) };
		db.delete(FORMS_DATA_FIELDS_TABLE, "form=? AND version=?", bindArgs);
	}

	@Override
	public void updateForm(Form form) {
		SQLiteDatabase db = sqlHelper.getWritableDatabase();
		ContentValues values = new ContentValues();
		values.put("version", form.getVersion());
		values.put("label", form.getLabel());
		values.put("description", form.getDescription());
		if (form.getDefinition() != null) {
			values.put("definition", form.getDefinition());
			deleteFormFields(db, form.getId(), form.getVersion());
		}

		String requiredLookupTables = getCommaSeparatedValues(form.getRequiredLookupTables());
		values.put("required_lookupTables", requiredLookupTables);

		db.update(FORMS_TABLE, values, "id=? and version=?",
				new String[] { Long.toString(form.getId()), Long.toString(form.getVersion()) });
	}

	private String getCommaSeparatedValues(List<Long> list) {
		String str = "";
		if (list != null && !list.isEmpty()) {
			StringBuilder sb = new StringBuilder();
			int lastIndex = list.size() - 1;
			for (int i = 0; i < lastIndex; i++) {
				Long l = list.get(i);
				sb.append(l);
				sb.append(',');
			}

			Long l = list.get(lastIndex);
			sb.append(l);
			str = sb.toString();
		}
		return str;
	}

	private List<Long> readCommaSeparatedValues(String str) {
		List<Long> list = null;
		if (str != null && str.length() > 0) {
			list = new ArrayList<Long>();
			String[] splitted = str.split(",");
			for (String s : splitted) {
				list.add(Long.parseLong(s));
			}
		}
		return list;
	}

	@Override
	public Form getForm(Long formId, Long version) {
		SQLiteDatabase db = sqlHelper.getReadableDatabase();
		Cursor cursor = db.rawQuery("SELECT id, label, description, version, project, required_lookuptables " + "FROM "
				+ FORMS_TABLE + " WHERE id = ? AND version = ? ",
				new String[] { Long.toString(formId), Long.toString(version) });
		try {
			Form form = null;
			if (cursor.moveToNext()) {
				form = new Form();
				form.setId(cursor.getLong(0));
				form.setLabel(cursor.getString(1));
				form.setDescription(cursor.getString(2));
				form.setVersion(cursor.getLong(3));
				form.setProjectId(cursor.getLong(4));
				form.setRequiredLookupTables(readCommaSeparatedValues(cursor.getString(5)));
			}

			return form;
		} finally {
			cursor.close();
		}
	}

	@Override
	public Long getMaxDefinedVersion(Long formId) {
		SQLiteDatabase db = sqlHelper.getReadableDatabase();
		Cursor cursor = db.rawQuery("SELECT max(version) FROM " + FORMS_TABLE
				+ " WHERE id = ? AND definition IS NOT NULL ", new String[] { Long.toString(formId) });
		try {
			Long version = null;
			if (cursor.moveToNext()) {
				if (!cursor.isNull(0)) {
					version = cursor.getLong(0);
				}
			}

			return version;
		} finally {
			cursor.close();
		}
	}

	@Override
	public List<Form> listAllForms(long appId) {
		SQLiteDatabase db = sqlHelper.getReadableDatabase();
		Cursor cursor = db.rawQuery("SELECT f.id, f.label, f.description, f.version, f.project FROM " + FORMS_TABLE
				+ " f JOIN " + PROJECTS_TABLE + " p ON f.project = p.id WHERE p.application=?",
				new String[] { Long.toString(appId) });
		return listForms(cursor);
	}
	
	@Override
	public List<Form> listAllForms() {
		SQLiteDatabase db = sqlHelper.getReadableDatabase();
		Cursor cursor = db.rawQuery("SELECT id, label, description, version, project " + "FROM forms ", null);
		return listForms(cursor);
	}

	private List<Form> listForms(Cursor cursor) {
		try {
			List<Form> forms = new ArrayList<Form>();
			while (cursor.moveToNext()) {
				Form form = new Form();
				form.setId(cursor.getLong(0));
				form.setLabel(cursor.getString(1));
				form.setDescription(cursor.getString(2));
				form.setVersion(cursor.getLong(3));
				form.setProjectId(cursor.getLong(4));
				forms.add(form);
			}

			return forms;
		} finally {
			cursor.close();
		}
	}
}
