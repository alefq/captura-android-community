package py.com.sodep.mobileforms.net.sync;

import android.content.Context;
import android.content.Intent;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import android.util.Log;



import com.google.firebase.crashlytics.FirebaseCrashlytics;

import java.util.List;

import py.com.sodep.mf.exchange.MFDataSetDefinition;
import py.com.sodep.mf.exchange.TXInfo;
import py.com.sodep.mf.exchange.listeners.LookupDataListener;
import py.com.sodep.mf.exchange.listeners.MetadataListener;
import py.com.sodep.mf.exchange.listeners.SynchronizationListener;
import py.com.sodep.mf.exchange.listeners.SynchronizationStatusProvider;
import py.com.sodep.mf.exchange.objects.lookup.MFDMLTransport;
import py.com.sodep.mf.exchange.objects.metadata.Application;
import py.com.sodep.mf.exchange.objects.metadata.Form;
import py.com.sodep.mf.exchange.objects.metadata.SynchronizationError;
import py.com.sodep.mobileforms.application.BroadcastActions;
import py.com.sodep.mobileforms.dataservices.ApplicationsDAO;
import py.com.sodep.mobileforms.dataservices.FormsDAO;
import py.com.sodep.mobileforms.dataservices.ProjectsDAO;
import py.com.sodep.mobileforms.dataservices.lookup.LookupDataSource;
import py.com.sodep.mobileforms.dataservices.sql.SQLApplicationsDAO;
import py.com.sodep.mobileforms.dataservices.sql.SQLFormsDAO;
import py.com.sodep.mobileforms.dataservices.sql.SQLLookupDataSource;
import py.com.sodep.mobileforms.dataservices.sql.SQLProjectsDAO;

public class MetadataAndLookupTableSynchronizerImpl implements MetadataListener, SynchronizationListener, SynchronizationStatusProvider,
		LookupDataListener {

	private final static String LOG_TAG = MetadataAndLookupTableSynchronizerImpl.class.getSimpleName();

	private LocalBroadcastManager localBroadcastManager;

	private void broadcast(String action) {
		Intent i = new Intent();
		i.setAction(action);
		localBroadcastManager.sendBroadcast(i);
	}

	public MetadataAndLookupTableSynchronizerImpl(Context context) {
		localBroadcastManager = LocalBroadcastManager.getInstance(context);
	}

	private void log(String msg) {
		Log.d(LOG_TAG, msg);
	}

	@Override
	public void syncOfMetadataStarted() {
		log("download of forms started");
	}

	@Override
	public void syncOfMetadataFinished() {
		log("download of forms ended");
	}

	@Override
	public void synchronizationFailed(SynchronizationError error) {
		Intent i = new Intent();
		i.setAction(BroadcastActions.ACTION_SYNC_FAILED);
		i.putExtra("synchronizationError", error);
		localBroadcastManager.sendBroadcast(i);
	}

	@Override
	public void unexpectedError(Throwable throwable) {
		broadcast(BroadcastActions.ACTION_SYNC_UNEXPECTED_ERROR);
		FirebaseCrashlytics crashlytics = FirebaseCrashlytics.getInstance();
		crashlytics.recordException(throwable);
	}

	private FormsDAO formsDAO;

	private FormsDAO getFormsDAO() {
		if (formsDAO == null) {
			formsDAO = new SQLFormsDAO();
		}
		return formsDAO;
	}

	@Override
	public void fullUpdate(List<Form> forms) {
		MetadataSynchronizationHelpers.saveForms(getFormsDAO(), forms);
	}

	@Override
	public void changeFormDefinition(Form updatedForm) {
		FormsDAO formsDAO = getFormsDAO();
		formsDAO.updateForm(updatedForm);
	}

	private ProjectsDAO projectsDAO;

	private ProjectsDAO getProjectsDAO() {
		if (projectsDAO == null) {
			projectsDAO = new SQLProjectsDAO();
		}
		return projectsDAO;
	}

	private ApplicationsDAO appliApplicationsDAO;

	private ApplicationsDAO getApplicationsDAO() {
		if (appliApplicationsDAO == null) {
			appliApplicationsDAO = new SQLApplicationsDAO();
		}
		return appliApplicationsDAO;
	}

	@Override
	public void fullUpdateApplicationsAndProjects(List<Application> applications) {
		MetadataSynchronizationHelpers.saveApps(getApplicationsDAO(), applications);
		for (Application app : applications) {
			MetadataSynchronizationHelpers.saveProjects(getProjectsDAO(), app);
		}
	}

	@Override
	public Long getStoredVersion(Form form) {
		FormsDAO formsDAO = new SQLFormsDAO();
		Long version = formsDAO.getMaxDefinedVersion(form.getId());
		return version;
	}

	@Override
	public void syncOfLookupDataFinished() {
		log("download of lookup tables started");
		broadcast(BroadcastActions.ACTION_SYNC_LOOKUP_DATA_FINISHED);
	}

	@Override
	public void syncOfLookupDataStarted() {
		log("downloading lookup tables");
		broadcast(BroadcastActions.ACTION_SYNC_LOOKUP_DATA_STARTED);
	}

	private LookupDataSource _lookupDataSource;

	private LookupDataSource getLookupDataSource() {
		if (_lookupDataSource == null) {
			_lookupDataSource = new SQLLookupDataSource();
		}
		return _lookupDataSource;
	}

	// LOOKUP TABLES SYNC METHODS

	@Override
	public boolean hasLookupTableDefinition(Long id) {
		LookupDataSource ds = getLookupDataSource();
		return ds.hasLookupTableDefinition(id);
	}

	@Override
	public void define(Long tableId, MFDataSetDefinition dataSet) {
		LookupDataSource ds = getLookupDataSource();
		ds.define(tableId, dataSet);
	}

	@Override
	public void endTx(String txId) {
		Log.d(LOG_TAG, "Lookup - end tx " + txId);
		LookupDataSource ds = getLookupDataSource();
		ds.endTx(txId);
		Log.d(LOG_TAG, "Lookup - end tx - end" + txId);
	}

	@Override
	public void startTx(String txId) {
		Log.d(LOG_TAG, "Lookup - start tx " + txId);
		LookupDataSource ds = getLookupDataSource();
		ds.startTx(txId);
		Log.d(LOG_TAG, "Lookup - start tx - end " + txId);
	}

	@Override
	public void applyDML(MFDMLTransport dml) {
		String tx = dml.getTxInfo().getTx();
		Log.d(LOG_TAG, "Lookup - applyDML " + tx);
		long currentTimeMillis0 = System.currentTimeMillis();
		LookupDataSource ds = getLookupDataSource();
		ds.applyDML(dml);
		long currentTimeMillis1 = System.currentTimeMillis();
		Log.d(LOG_TAG, "Lookup - applyDML - end " + tx);
		Log.d(LOG_TAG, "Lookup - applyDML - duration: " + (currentTimeMillis1 - currentTimeMillis0) / 100);
	}

	@Override
	public TXInfo getLastTransaction(Long lookupId) {
		LookupDataSource ds = getLookupDataSource();
		return ds.getTXInfo(lookupId);
	}

	@Override
	public void nowIsSynced(Long lookupId) {
		LookupDataSource ds = getLookupDataSource();
		ds.markAsSynced(lookupId);

	}
}
