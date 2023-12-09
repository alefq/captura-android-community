package py.com.sodep.mobileforms.dataservices.sql;

import static py.com.sodep.mobileforms.dataservices.sql.SodepSQLiteOpenHelper.APPLICATIONS_TABLE;

import java.util.ArrayList;
import java.util.List;

import py.com.sodep.mf.exchange.objects.metadata.Application;
import io.github.jokoframework.chake.MFApplication;
import py.com.sodep.mobileforms.dataservices.ApplicationsDAO;
import py.com.sodep.mobileforms.dataservices.lookup.LookupDataSource;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

public class SQLApplicationsDAO implements ApplicationsDAO {

	private static final String LOG_TAG = SQLProjectsDAO.class.getSimpleName();

	private SodepSQLiteOpenHelper sqlHelper;

	public SQLApplicationsDAO() {
		this.sqlHelper = MFApplication.sqlHelper;
	}

	@Override
	public List<Application> listApplications() {
		SQLiteDatabase db = sqlHelper.getReadableDatabase();
		String[] columns = new String[] { "id", "label", "description" };
		Cursor cursor = db.query(APPLICATIONS_TABLE, columns, null, null, null, null, "label");
		try {
			List<Application> applications = new ArrayList<Application>();
			while (cursor.moveToNext()) {
				Application app = new Application();
				app.setId(cursor.getLong(0));
				app.setLabel(cursor.getString(1));
				app.setDescription(cursor.getString(2));
				applications.add(app);
			}

			return applications;
		} finally {
			cursor.close();
		}
	}

	@Override
	public void saveApplication(Application application) {
		SQLiteDatabase db = sqlHelper.getWritableDatabase();
		ContentValues values = new ContentValues();
		// meta-data
		values.put("id", application.getId());
		values.put("label", application.getLabel());
		values.put("description", application.getDescription());

		db.insert(APPLICATIONS_TABLE, null, values);
	}

	@Override
	public void deleteApplication(Long applicationId) {
		SQLiteDatabase db = sqlHelper.getWritableDatabase();
		String[] bindArgs = new String[] { Long.toString(applicationId) };
		int affected = db.delete(APPLICATIONS_TABLE, "id=?", bindArgs);
		Log.d(LOG_TAG, "delete affected " + affected);
	}
	
	@Override
	public void setDeviceVerified(Long applicationId, boolean verified){
		SQLiteDatabase db = sqlHelper.getWritableDatabase();
		String[] whereArgs = new String[] { Long.toString(applicationId) };
		ContentValues values = new ContentValues();
		values.put("device_verified", verified);
		db.update(APPLICATIONS_TABLE, values, "id=?", whereArgs);
	}
	
	@Override
	public boolean isDeviceVerified(Long applicationId){
		SQLiteDatabase db = sqlHelper.getReadableDatabase();
		String[] columns = new String[] { "device_verified" };
		String[] whereArgs = new String[] { Long.toString(applicationId) };
		Cursor cursor = db.query(APPLICATIONS_TABLE, columns, "id=?", whereArgs, null, null, null);
		try {
			if (cursor.moveToFirst()) {
				int deviceVerified = cursor.getInt(0);
				return deviceVerified != 0;
			}
			throw new RuntimeException("Invalid appzlication id");
		} finally {
			cursor.close();
		}
	}

	@Override
	public Application getApplication(Long appId) {
		SQLiteDatabase db = sqlHelper.getReadableDatabase();
		String[] columns = new String[] { "label", "description" };
		Cursor cursor = db.query(APPLICATIONS_TABLE, columns, "id=?", new String[] { Long.toString(appId) }, null,
				null, null);
		try {
			Application app = null;
			if (cursor.moveToNext()) {
				app = new Application();
				app.setId(appId);
				app.setLabel(cursor.getString(0));
				app.setDescription(cursor.getString(1));
			}

			return app;
		} finally {
			cursor.close();
		}
	}

	@Override
	public void updateApplication(Application application) {
		SQLiteDatabase db = sqlHelper.getWritableDatabase();
		ContentValues values = new ContentValues();
		values.put("label", application.getLabel());
		values.put("description", application.getDescription());

		db.update(APPLICATIONS_TABLE, values, "id = ?", new String[] { Long.toString(application.getId()) });
		// db.close();
	}

	@Override
	public void deleteAllData() {
		List<Application> applications = listApplications();
		for (Application app : applications) {
			deleteApplication(app.getId());
		}

		LookupDataSource lookupDS = new SQLLookupDataSource();
		lookupDS.deleteLookupData();
	}

}
