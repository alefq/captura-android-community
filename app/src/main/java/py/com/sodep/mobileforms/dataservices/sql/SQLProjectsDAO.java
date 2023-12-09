package py.com.sodep.mobileforms.dataservices.sql;

import static py.com.sodep.mobileforms.dataservices.sql.SodepSQLiteOpenHelper.PROJECTS_TABLE;

import java.util.ArrayList;
import java.util.List;

import py.com.sodep.mf.exchange.objects.metadata.Project;
import io.github.jokoframework.chake.MFApplication;
import py.com.sodep.mobileforms.dataservices.ProjectsDAO;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

public class SQLProjectsDAO implements ProjectsDAO {

	private static final String LOG_TAG = SQLProjectsDAO.class.getSimpleName();

	private SodepSQLiteOpenHelper sqlHelper;

	public SQLProjectsDAO() {
		sqlHelper = MFApplication.sqlHelper;
	}

	//TODO add application id to the query
	@Override
	public List<Project> listProjects(Long applicationId) {
		SQLiteDatabase db = sqlHelper.getReadableDatabase();
		String[] columns = new String[] { "id", "label", "description" };
		Cursor cursor = db.query(PROJECTS_TABLE, columns, "application=?",
				new String[] { Long.toString(applicationId) }, null, null, "label");
		List<Project> projects = new ArrayList<Project>();
		while (cursor.moveToNext()) {
			Project project = new Project();
			project.setApplicationId(applicationId);
			project.setId(cursor.getLong(0));
			project.setLabel(cursor.getString(1));
			project.setDescription(cursor.getString(2));
			projects.add(project);
		}
		cursor.close();
		return projects;
	}
	
	@Override
	public void saveProject(Project project) {
		SQLiteDatabase db = sqlHelper.getWritableDatabase();
		ContentValues values = new ContentValues();
		// meta-data
		values.put("id", project.getId());
		values.put("label", project.getLabel());
		values.put("description", project.getDescription());
		values.put("application", project.getApplicationId());
		db.insertOrThrow(PROJECTS_TABLE, null, values);
	}

	@Override
	public void deleteProject(Long projectId) {
		SQLiteDatabase db = sqlHelper.getWritableDatabase();
		String[] bindArgs = new String[] { Long.toString(projectId) };
		int affected = db.delete(PROJECTS_TABLE, "id=?", bindArgs);
		Log.d(LOG_TAG, "delete affected " + affected);
	}

	@Override
	public Project getProject(Long projectId) {
		SQLiteDatabase db = sqlHelper.getReadableDatabase();
		String[] columns = new String[] { "id", "application",  "label", "description" };
		Cursor cursor = db.query(PROJECTS_TABLE, columns, "id=?",
				new String[] { Long.toString(projectId) }, null, null, null);
		Project project = null;
		if (cursor.moveToNext()) {
			project = new Project();
			project.setId(cursor.getLong(0));
			project.setApplicationId(cursor.getLong(1));
			project.setLabel(cursor.getString(2));
			project.setDescription(cursor.getString(3));
		}
		cursor.close();
		return project;
	}

	@Override
	public void updateProject(Project project) {
		SQLiteDatabase db = sqlHelper.getWritableDatabase();
		ContentValues values = new ContentValues();
		values.put("label", project.getLabel());
		values.put("description", project.getDescription());
		db.update(PROJECTS_TABLE, values, "id = ?", new String[] { Long.toString(project.getId()) });
	}
}
