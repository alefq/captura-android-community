package py.com.sodep.mobileforms.ui;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentTransaction;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.ActionBar.Tab;
import androidx.appcompat.app.ActionBar.TabListener;
import androidx.appcompat.app.AppCompatActivity;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;



import com.google.firebase.crashlytics.FirebaseCrashlytics;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

import io.github.jokoframework.chake.BuildConfig;
import py.com.sodep.mf.exchange.exceptions.AuthenticationException;
import py.com.sodep.mf.exchange.exceptions.AuthenticationException.FailCause;
import py.com.sodep.mf.exchange.exceptions.HttpResponseException;
import py.com.sodep.mf.exchange.exceptions.SodepException;
import py.com.sodep.mf.exchange.net.ServerConnection;
import py.com.sodep.mf.exchange.objects.metadata.Application;
import py.com.sodep.mf.exchange.objects.metadata.Form;
import py.com.sodep.mf.exchange.objects.metadata.Project;
import py.com.sodep.mf.exchange.objects.metadata.SynchronizationError;
import py.com.sodep.mf.exchange.objects.upload.UploadProgress;

import io.github.jokoframework.chake.R;
import io.github.jokoframework.chake.MFApplication;
import py.com.sodep.mobileforms.dataservices.ApplicationsDAO;
import py.com.sodep.mobileforms.dataservices.DocumentsDataSource;
import py.com.sodep.mobileforms.dataservices.FormsDAO;
import py.com.sodep.mobileforms.dataservices.ProjectsDAO;
import py.com.sodep.mobileforms.dataservices.documents.DocumentMetadata;
import py.com.sodep.mobileforms.dataservices.lookup.LookupDataSource;
import py.com.sodep.mobileforms.dataservices.sql.SQLApplicationsDAO;
import py.com.sodep.mobileforms.dataservices.sql.SQLDocumentsDataSource;
import py.com.sodep.mobileforms.dataservices.sql.SQLFormsDAO;
import py.com.sodep.mobileforms.dataservices.sql.SQLLookupDataSource;
import py.com.sodep.mobileforms.dataservices.sql.SQLProjectsDAO;
import py.com.sodep.mobileforms.net.sync.DocumentsSync;
import py.com.sodep.mobileforms.net.sync.services.SyncService;
import py.com.sodep.mobileforms.settings.AppSettings;
import py.com.sodep.mobileforms.ui.list.DocumentsAdapter;
import py.com.sodep.mobileforms.ui.list.Item;
import py.com.sodep.mobileforms.ui.list.ItemAdapter;
import py.com.sodep.mobileforms.ui.list.SectionedAdapter;

import static py.com.sodep.mobileforms.application.BroadcastActions.ACTION_EXIT;
import static py.com.sodep.mobileforms.application.BroadcastActions.ACTION_SYNC_FAILED;
import static py.com.sodep.mobileforms.application.BroadcastActions.ACTION_SYNC_LOOKUP_DATA_FINISHED;
import static py.com.sodep.mobileforms.application.BroadcastActions.ACTION_SYNC_LOOKUP_DATA_STARTED;
import static py.com.sodep.mobileforms.application.BroadcastActions.ACTION_SYNC_METADATA_FINISHED;
import static py.com.sodep.mobileforms.application.BroadcastActions.ACTION_SYNC_METADATA_STARTED;
import static py.com.sodep.mobileforms.application.BroadcastActions.ACTION_SYNC_UNEXPECTED_ERROR;
import static py.com.sodep.mobileforms.application.BroadcastActions.ACTION_UPLOAD_DOCUMENT_STATUS_CHANGED;
import static py.com.sodep.mobileforms.application.BroadcastActions.ACTION_UPLOAD_FINISHED;
import static py.com.sodep.mobileforms.application.BroadcastActions.ACTION_UPLOAD_PROGRESS;
import static py.com.sodep.mobileforms.application.BroadcastActions.ACTION_UPLOAD_STARTED;

public class MainActivity extends AppCompatActivity implements TabListener {

	private static final String LOG_TAG = MainActivity.class.getSimpleName();

	// private String language;

	private boolean showErrorDialogIfSyncFails = false;

	private SynchronizationError synchronizationError;

	private int selectedTabIndex = 0;

	private LocalBroadcastManager localBroadcastManager;

    private static final String[] ACTIONS = new String[] { ACTION_EXIT, ACTION_UPLOAD_STARTED, ACTION_UPLOAD_PROGRESS,
			ACTION_UPLOAD_FINISHED, ACTION_SYNC_METADATA_STARTED, ACTION_SYNC_METADATA_FINISHED,
			ACTION_SYNC_LOOKUP_DATA_STARTED, ACTION_SYNC_LOOKUP_DATA_FINISHED, ACTION_SYNC_FAILED,
			ACTION_SYNC_UNEXPECTED_ERROR, ACTION_UPLOAD_DOCUMENT_STATUS_CHANGED };

	private BroadcastReceiver receiver = new BroadcastReceiver() {

        @Override
		public void onReceive(Context context, Intent intent) {
			final String action = intent.getAction();
			if (action.equals(ACTION_UPLOAD_STARTED)) {
				setSupportProgressBarIndeterminateVisibility(true);
                syncButtonEnabled = false;
                supportInvalidateOptionsMenu();
			} else if (action.equals(ACTION_UPLOAD_FINISHED)) {
				Tab selectedTab = getSupportActionBar().getSelectedTab();
				if (selectedTab != null && selectedTab.getPosition() == 2) {
					refreshDocumentsList(null);
				}
				setSupportProgressBarIndeterminateVisibility(false);
			} else if (action.equals(ACTION_UPLOAD_PROGRESS)) {
				Tab selectedTab = getSupportActionBar().getSelectedTab();
				UploadProgress progress = (UploadProgress) intent.getSerializableExtra(UploadProgress.class
						.getSimpleName());
				if (selectedTab != null && selectedTab.getPosition() == 2) {
					refreshDocumentsList(progress);
				}
			} else if (action.equals(ACTION_EXIT)) {
				MainActivity.this.finish();
			} else if (action.equals(ACTION_SYNC_METADATA_STARTED)) {
				Log.d(LOG_TAG, ACTION_SYNC_METADATA_STARTED);
				setSupportProgressBarIndeterminateVisibility(true);
			} else if (action.equals(ACTION_SYNC_METADATA_FINISHED)) {
				metadataSyncEnded();
            }  else if (action.equals(ACTION_SYNC_LOOKUP_DATA_FINISHED)) {
				syncFinished();
				Tab selectedTab = getSupportActionBar().getSelectedTab();
				if (selectedTab != null && selectedTab.getPosition() == 1) {
					refreshFormList();
				}
			} else if (action.equals(ACTION_SYNC_FAILED)) {
				syncFinished();
				synchronizationError = (SynchronizationError) intent.getSerializableExtra("synchronizationError");
				syncFailed();
				if (getSupportActionBar().getSelectedTab().getPosition() == 2) {
					refreshDocumentsList(null);
				}
			} else if (action.equals(ACTION_SYNC_UNEXPECTED_ERROR)) {
				syncFinished();
				Toast.makeText(MainActivity.this, R.string.unexpected_error, Toast.LENGTH_SHORT).show();
			} else if (action.equals(ACTION_UPLOAD_DOCUMENT_STATUS_CHANGED)) {
				Tab selectedTab = getSupportActionBar().getSelectedTab();
				if (selectedTab != null && selectedTab.getPosition() == 2) {
					refreshDocumentsList(null);
				}
			}
		}

		private void syncFinished() {
            syncButtonEnabled = true;
            supportInvalidateOptionsMenu();
			setSupportProgressBarIndeterminateVisibility(false);
		}
	};

    // FIXME a hack because dialog are restored
    // FIXME copy-pasted
    private void removeAndShowDialog(int id, Bundle args) {
        try {
            this.removeDialog(id);
        } catch (Exception e) {

        }
        this.showDialog(id, args);
    }

	private boolean isSerializable(Object o) {
		try {
			new ObjectOutputStream(new ByteArrayOutputStream()).writeObject(o);
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	@Override
	protected void onSaveInstanceState(Bundle outInstanceState) {
    	super.onSaveInstanceState(outInstanceState);
		if (synchronizationError != null && isSerializable(synchronizationError)) {
			outInstanceState.putSerializable("synchronizationError", synchronizationError);
		}
		outInstanceState.putBoolean("showErrorDialogIfSyncFails", showErrorDialogIfSyncFails);
		if (selectedProjectId != null) {
			outInstanceState.putLong("selectedProjectId", selectedProjectId);
		}
		ActionBar actionBar = getSupportActionBar();
		if (actionBar != null) {
			Tab tab = actionBar.getSelectedTab();
			outInstanceState.putInt("selectedTabIndex", tab.getPosition());
		}
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		localBroadcastManager = LocalBroadcastManager.getInstance(this);
		if (savedInstanceState != null) {
			synchronizationError = (SynchronizationError) savedInstanceState.getSerializable("synchronizationError");
			showErrorDialogIfSyncFails = savedInstanceState.getBoolean("showErrorDialogIfSyncFails", false);
			selectedTabIndex = savedInstanceState.getInt("selectedTabIndex");
			if (savedInstanceState.containsKey("selectedProjectId")) {
				selectedProjectId = savedInstanceState.getLong("selectedProjectId");
			}
		}
		requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		// don't delete this line
		getSupportActionBar();
		// http://stackoverflow.com/questions/11402299/intermediate-progress-doesnt-work-with-actionbarsherlock-running-on-gingerbread
		setSupportProgressBarIndeterminateVisibility(false);
		init();
	}

	@Override
	protected void onDestroy() {
		try {
			localBroadcastManager.unregisterReceiver(receiver);
		} catch (IllegalArgumentException e) {

		}
		super.onDestroy();
	}

	private static final int clearDocumentItemId = 5675;

	private static final int copyDBItemId = 5676;

    private boolean syncButtonEnabled = true;

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		ActionBar supportActionBar = getSupportActionBar();
		if (supportActionBar != null && supportActionBar.isShowing()
				&& supportActionBar.getNavigationMode() == ActionBar.NAVIGATION_MODE_TABS) {
			MenuInflater inflater = getMenuInflater();
			inflater.inflate(R.menu.main_menu, menu);
			//TODO: descomentar para habilitar opciones
			/*Tab selectedTab = supportActionBar.getSelectedTab();
			if (selectedTab != null && selectedTab.getPosition() == 2) {
				menu.add(0, clearDocumentItemId, 0, R.string.clear_documents);
			}
			if (BuildConfig.DEBUG) {
				menu.add(0, copyDBItemId, 0, "Copy SQLite file");
			}*/
			MenuItem syncItem = menu.getItem(0);

			if (syncButtonEnabled) {
				syncItem.setActionView(null);
				syncItem.setEnabled(true);
			} else {
				ProgressBar progress = new ProgressBar(this);
				progress.getIndeterminateDrawable().setColorFilter(
						ContextCompat.getColor(this, android.R.color.white),
						android.graphics.PorterDuff.Mode.MULTIPLY);
				syncItem.setActionView(progress);
				syncItem.setEnabled(false);
			}
		}
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(final MenuItem item) {
		switch (item.getItemId()) {
		case R.id.item_settings:
			startActivity(new Intent(this, SettingsPreferenceActivity.class));
			break;
		case R.id.item_sync:
			showErrorDialogIfSyncFails = true;
            syncButtonEnabled = false;
            supportInvalidateOptionsMenu();

			Intent syncService = new Intent(this, SyncService.class);
			startService(syncService);
			break;
		case clearDocumentItemId:
            removeAndShowDialog(DIALOG_CLEAR_DOCUMENTS, null);
			break;
		case copyDBItemId:
			try {
				String file = MFApplication.sqlHelper.copyDataBase();
				String message = getString(R.string.database_copied, file);
				Bundle bundle = new Bundle();
				bundle.putString("message", message);
				removeAndShowDialog(DIALOG_DATABASE_COPIED, bundle);
			} catch (IOException e) {
				Log.e(LOG_TAG, e.getMessage(), e);
			}
			break;
		}
		return false;
	}

	private void init() {
		boolean accountActivated = AppSettings.isAccountActivated(this);

		if (!AppSettings.isFromOldApp(this) && accountActivated) {
			Long appId = AppSettings.getAppId(this);
			if (appId == null || appId == 0L) {
				initAppList();
			} else {
				initMain();
			}
		} else {
			// If not activated in show activation screen
			initActivation();
		}
	}


	private void initAppList() {
		Intent i = new Intent(this, AppListActivity.class);
		startActivity(i);
		finish();
	}

	private void initLogin() {
		// Intent i = new Intent(this, LoginActivity.class);
		// #2507
		// Splash screen, then the login activity will be shown
		Intent i = new Intent(this, SplashActivity.class);
		startActivity(i);
		finish();
	}

	private void initActivation() {
		Intent i = new Intent(this, SplashActivity.class);
		startActivity(i);
		finish();
	}

	private void initMain() {
		Application app = getCurrentApp();
		if (app == null) {
			AppSettings.setAccountActivated(this, false);
			initActivation();
			return;
		}
		getSupportActionBar().setTitle(app.getLabel());
		setContentView(R.layout.main);
		initProjectsListView(app.getId());
		initFormsListView();
		initDocumentsListView();

		ActionBar supportActionBar = getSupportActionBar();
		supportActionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);

		int[] tabTexts = new int[] { R.string.tab_projects, R.string.tab_forms, R.string.tab_documents };
		ActionBar.Tab[] tabs = new ActionBar.Tab[tabTexts.length];
		ActionBar actionBar = getSupportActionBar();

		for (int i = 0; i < tabTexts.length; i++) {
			ActionBar.Tab tab = supportActionBar.newTab();
			tabs[i] = tab;
			tab.setTabListener(this);
			tab.setText(tabTexts[i]);
			actionBar.addTab(tab, false);
		}
		// ref #2546
		Tab selectedTab = null;
		if (selectedTabIndex > 0) {
			selectedTab = tabs[selectedTabIndex];
		} else {
			selectedTab = tabs[0];
		}
		actionBar.selectTab(selectedTab);

		for (String action : ACTIONS) {
			IntentFilter intentFilter = new IntentFilter(action);
			localBroadcastManager.registerReceiver(receiver, intentFilter);
		}

		MFApplication application = (MFApplication) getApplication();
		application.startSync();
	}

	private class DocumentsListOnClickListener implements OnItemClickListener {
		@Override
		public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            DocumentMetadata meta = (DocumentMetadata) parent.getAdapter().getItem(position);
            if (meta == null) {
                // TODO log or do something here
                return;
            }
            if (meta.getStatus() != DocumentMetadata.STATUS_DRAFT
                    && meta.getStatus() != DocumentMetadata.STATUS_IN_PROGRESS) {
                // CAP-96 opens a new activity with the document saved data
                Intent intent = new Intent(MainActivity.this, DocumentInstanceActivity.class);
                intent.putExtra(DocumentInstanceActivity.EXTRA_DOCUMENT_METADATA, meta);
                startActivity(intent);
            } else if (meta.getStatus() == DocumentMetadata.STATUS_DRAFT) {
                startDocumentInfoActivity(meta);
            }
		}


    }

    private void startDocumentInfoActivity(DocumentMetadata documentMetadata) {
        Intent intent = null;
        if (documentMetadata.getStatus() == DocumentMetadata.STATUS_DRAFT) {
            intent = new Intent(MainActivity.this, FormActivity.class);
            intent.putExtra(FormActivity.EXTRA_DOCUMENT_METADATA, documentMetadata);
        } else {
            intent = new Intent(MainActivity.this, DocumentInfoActivity.class);
            intent.putExtra(DocumentInfoActivity.EXTRA_DOCUMENT_METADATA, documentMetadata);
        }

        startActivity(intent);
    }

	private void initDocumentsListView() {
		ListView listView = (ListView) findViewById(R.id.listViewDocuments);
		listView.setOnItemClickListener(new DocumentsListOnClickListener());
		listView.setAdapter(documentsAdapter);
		this.registerForContextMenu(listView);
	}

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        int viewId = v.getId();
        switch (viewId) {
            case R.id.listViewDocuments:
                DocumentMetadata documentMetadata = (DocumentMetadata) documentsAdapter.getItem(((AdapterContextMenuInfo) menuInfo).position);
                getMenuInflater().inflate(R.menu.document_context_menu, menu);
                if (documentMetadata.getUploadAttempts() >= DocumentsSync.MAX_RETRIES) {
                    MenuItem item = menu.getItem(2);
                    item.setVisible(true);
                }
                if (documentMetadata.getStatus() != DocumentMetadata.STATUS_DRAFT) {
                    // CAP-96 only display document info menu option for saved documents
                    MenuItem item = menu.getItem(0);
                    item.setVisible(true);
                }
                break;
            case R.id.listViewForms:
                // CAP-75 we are in forms tab
                getMenuInflater().inflate(R.menu.form_context_menu, menu);
                break;
            default:
                break;
        }

    }

	@Override
	public boolean onContextItemSelected(MenuItem item) {
        DocumentsDataSource documentsDataSource = new SQLDocumentsDataSource();
		switch (item.getItemId()) {
            // documents tab
            case R.id.delete_document_option: {
                AdapterContextMenuInfo menuInfo = (AdapterContextMenuInfo) item.getMenuInfo();
                DocumentMetadata documentMetadata = (DocumentMetadata) documentsAdapter.getItem(menuInfo.position);
                documentsDataSource.delete(documentMetadata.getId());
                refreshDocumentsList(null);
                break;
            }
            case R.id.document_info_option: {
                AdapterContextMenuInfo menuInfo = (AdapterContextMenuInfo) item.getMenuInfo();
                DocumentMetadata documentMetadata = (DocumentMetadata) documentsAdapter.getItem(menuInfo.position);
                startDocumentInfoActivity(documentMetadata);
                break;
            }
            case R.id.retry_sync: {
                AdapterContextMenuInfo menuInfo = (AdapterContextMenuInfo) item.getMenuInfo();
                DocumentMetadata documentMetadata = (DocumentMetadata) documentsAdapter.getItem(menuInfo.position);
                documentsDataSource.resetUploadAttempts(documentMetadata.getId());
                refreshDocumentsList(null);
                break;
            }
            // forms tab
            case R.id.form_about_option: {
                // option selected in forms tab
                Form form = getFormFromItem(item);
                showFormInfo(form);
                break;
            }
        }

        return super.onContextItemSelected(item);
	}

    private void showFormInfo(Form form) {
        String title = null;
        String formStatus = null;
        if (form != null) {
            title = getString(R.string.about) + " " + form.getLabel();
            formStatus = ActivityUtils.getFormStatusAsString(this, form);
        } else {
            // error indicating we couldn't get the form info
            // or the form is inactive
            title = getString(R.string.error);
            formStatus = getString(R.string.required_data_not_synced);
        }
        Dialog dialog = ActivityUtils.newSimpleDismissableDialog(this, title, formStatus);
        dialog.show();
    }

    /**
     * Gets the form associated to the MenuItem.
     * <p>
     * Returns null if the form is inactive or we couldn't obtain the definition.
     * </p>
     * @param item
     * @return
     */
    private Form getFormFromItem(MenuItem item) {
        AdapterContextMenuInfo menuInfo = (AdapterContextMenuInfo) item.getMenuInfo();
        //noinspection unchecked
        Item<Form> formItem  = (Item<Form>) formsAdapter.getItem(menuInfo.position);
        if (formItem.isActive()) {
           Form form = formItem.getObject();
           if (form.getDefinition() == null) {
                FormsDAO formsDAO = new SQLFormsDAO();
                formsDAO.loadDefinition(form);
           }
           if (form.getDefinition() != null) {
               return form;
           }
        }
        return null;
    }


    private SectionedAdapter formsAdapter = new SectionedAdapter() {
		protected View getHeaderView(String caption, int index, View convertView, ViewGroup parent) {
			TextView result = null;

			if (convertView == null || !(convertView instanceof TextView)) {
				result = (TextView) getLayoutInflater().inflate(R.layout.header, null);
			} else {
				result = (TextView) convertView;
			}
			result.setText(caption);
			return result;
		}
	};

	private class FormsListOnClickListener implements OnItemClickListener {
		@Override
		public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
			@SuppressWarnings("unchecked")
			Item<Form> item = (Item<Form>) parent.getAdapter().getItem(position);
			if (item.isActive()) {
				Form form = item.getObject();
				Intent i = new Intent(MainActivity.this, FormActivity.class);
				if (form.getDefinition() == null) {
					FormsDAO formsDAO = new SQLFormsDAO();
					formsDAO.loadDefinition(form);
				}
				if (form.getDefinition() != null) {
					i.putExtra(FormActivity.EXTRA_FORM, form);
					startActivity(i);
				} else {
					showDialog(DIALOG_NO_FORM_DEFINITION);
				}
			}
		}
	}

	private void initFormsListView() {
		ListView listView = (ListView) findViewById(R.id.listViewForms);
		listView.setOnItemClickListener(new FormsListOnClickListener());
		listView.setAdapter(formsAdapter);
        this.registerForContextMenu(listView);
	}

	private ItemAdapter<Project> projectsAdapter;

	private Long selectedProjectId = null;

	private class ProjectsListOnClickListener implements OnItemClickListener {
		@Override
		public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
			@SuppressWarnings("unchecked")
			Item<Project> item = (Item<Project>) parent.getAdapter().getItem(position);
			if (item.isActive()) {
				Project project = item.getObject();
				ActionBar actionBar = getSupportActionBar();
				Tab formTab = actionBar.getTabAt(1);
				selectedProjectId = project.getId();
				actionBar.selectTab(formTab);
			}
		}
	}

	private void initProjectsListView(Long appId) {
		ListView listView = (ListView) findViewById(R.id.listViewProjects);
		listView.setOnItemClickListener(new ProjectsListOnClickListener());

		List<Item<Project>> items = listProjectItems(appId);
		projectsAdapter = new ItemAdapter<Project>(this, R.layout.list_item2, R.id.list_item2_textView10, items);

		listView.setAdapter(projectsAdapter);
	}

	private List<Item<Project>> listProjectItems(Long appId) {
		ProjectsDAO projectsDAO = new SQLProjectsDAO();
		List<Project> projects = projectsDAO.listProjects(appId);
		List<Item<Project>> items = new ArrayList<Item<Project>>();
		for (Project p : projects) {
			Item<Project> item = new Item<Project>(p);
			items.add(item);
		}
		return items;
	}

	@Override
	protected void onResume() {
		super.onResume();
        ServerConnection.builder(this);
		Application app = getCurrentApp();
		if (app == null) {
			initAppList();
			return;
		}
	}

	private Application getCurrentApp() {
		ApplicationsDAO appDAO = new SQLApplicationsDAO();
		Long appId = AppSettings.getAppId(this);
		Application app = appDAO.getApplication(appId);
		return app;
	}

	public void metadataSyncEnded() {
		Long appId = AppSettings.getAppId(this);
		List<Item<Project>> items = listProjectItems(appId);
		projectsAdapter.clear();
		for (Item<Project> i : items) {
			projectsAdapter.add(i);
		}
		Tab selectedTab = getSupportActionBar().getSelectedTab();
		if (selectedTab != null && selectedTab.getPosition() == 1) {
			refreshFormList();
		}
    }

	public void syncFailed() {
		if (showErrorDialogIfSyncFails) {
			removeAndShowDialog(DIALOG_ERROR_MESSAGE, null);
			showErrorDialogIfSyncFails = false;
		}
	}

	private void refreshFormList() {
		formsAdapter.clear();

		Long appId = AppSettings.getAppId(this);
		ProjectsDAO projectsDAO = new SQLProjectsDAO();
		final List<Project> projects;
		if (selectedProjectId == null) {
			projects = projectsDAO.listProjects(appId);
		} else {
			Project project = projectsDAO.getProject(selectedProjectId);
			if (project != null) { // it's hard to reproduce condition but it
									// may happen
				// that the selectedProjectId belongs to a recently deleted
				// Project
				// that's why we must chek that project isn't null
				projects = new ArrayList<Project>();
				projects.add(project);
			} else {
				selectedProjectId = null;
				projects = projectsDAO.listProjects(appId);
			}
		}
		FormsDAO formsDAO = new SQLFormsDAO();
		LookupDataSource lookupDS = new SQLLookupDataSource();
		for (Project p : projects) {
			List<Item<Form>> forms = listFormItems(formsDAO, lookupDS, p);
			if (forms.size() > 0) {
				ItemAdapter<Form> adapter = new ItemAdapter<Form>(this, R.layout.list_item2,
						R.id.list_item2_textView10, forms);
				formsAdapter.addSection(p.getLabel(), adapter);
			}
		}
	}

	private List<Item<Form>> listFormItems(FormsDAO formsDAO, LookupDataSource lookupDS, Project p) {
		List<Form> forms = formsDAO.listForms(p.getId());
		List<Item<Form>> items = new ArrayList<Item<Form>>();
		for (Form f : forms) {
			Item<Form> item = new Item<Form>(f);
			boolean active = lookupDS.isAllDataAvailable(f);
			if (!active) {
				item.setInactiveMessage(getString(R.string.required_data_not_synced));
			}
			item.setActive(active);
			items.add(item);
		}
		return items;
	}

	private SectionedAdapter documentsAdapter = new SectionedAdapter() {
		protected View getHeaderView(String caption, int index, View convertView, ViewGroup parent) {
			TextView result = null;

			if (convertView == null || !(convertView instanceof TextView)) {
				result = (TextView) getLayoutInflater().inflate(R.layout.header, null);
			} else {
				result = (TextView) convertView;
			}
			result.setText(caption);
			return result;
		}
	};

	private void refreshDocumentsList(UploadProgress progress) {
		documentsAdapter.clear();
		Long appId = AppSettings.getAppId(this);
		DocumentsDataSource documentsDataSource = new SQLDocumentsDataSource();

		LinkedHashMap<Date, List<DocumentMetadata>> history = documentsDataSource.listHistory(appId);
		java.text.DateFormat dateFormat = DateFormat.getDateFormat(this);

		Set<Date> keySet = history.keySet();
		for (Date date : keySet) {
			List<DocumentMetadata> list = history.get(date);
			DocumentsAdapter adapter = new DocumentsAdapter(this);
			adapter.addAll(list);
			if (progress != null) {
				setProgressPercentage(progress, list);
			}
			String headerText = dateFormat.format(date);
			documentsAdapter.addSection(headerText, adapter);
		}
	}

	private void setProgressPercentage(UploadProgress progress, List<DocumentMetadata> list) {
		for (DocumentMetadata docMeta : list) {
			if (docMeta.getStatus() == DocumentMetadata.STATUS_IN_PROGRESS) {
				long size = progress.getSize();
				if (size > 0) { // size <= 0 shouldn't happen. Just some
								// defensive programming in case
					int percentage = (int) ((double) progress.getReceivedBytes() / (double) size * 100);
					docMeta.setPercentage(percentage);
				}
			}
		}
	}

	private static final int DIALOG_ERROR_MESSAGE = 1;

	private static final int DIALOG_CLEAR_DOCUMENTS = 2;

	private static final int DIALOG_DATABASE_COPIED = 3;

	private static final int DIALOG_NO_FORM_DEFINITION = 4;

	@Override
	protected Dialog onCreateDialog(int id, Bundle bundle) {
		switch (id) {
		case DIALOG_ERROR_MESSAGE:
			return errorMessageDialog();
		case DIALOG_CLEAR_DOCUMENTS:
			return clearDocumentsDialog();
		case DIALOG_DATABASE_COPIED:
			String message = bundle.getString("message");
			return databaseCopiedDialog(message);
		case DIALOG_NO_FORM_DEFINITION:
			return noFormDefinitionDialog();
		}
		return null;
	}

	private Dialog noFormDefinitionDialog() {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);

		builder.setTitle(R.string.no_form_definition_title);
		builder.setMessage(R.string.no_form_definition_message);
		builder.setIcon(android.R.drawable.ic_dialog_alert);

		builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int id) {
				dialog.dismiss();
			}
		});
		return builder.create();
	}

	@Override
	protected Dialog onCreateDialog(int id) {
		return onCreateDialog(id, null);
	}

	private Dialog databaseCopiedDialog(String message) {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);

		builder.setTitle(R.string.database_copied_title);
		builder.setIcon(android.R.drawable.ic_dialog_info);
		builder.setMessage(message);
		builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int id) {
				dialog.dismiss();
			}
		});
		return builder.create();
	}

	private Dialog clearDocumentsDialog() {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);

		builder.setTitle(R.string.clear_documents);
		builder.setMessage(R.string.clear_documents_message);
		builder.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int id) {
				DocumentsDataSource ds = new SQLDocumentsDataSource();
				ds.deleteAll(new int[] { DocumentMetadata.STATUS_SYNCED, DocumentMetadata.STATUS_REJECTED });
				refreshDocumentsList(null);
				dialog.dismiss();
			}
		});

		builder.setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int id) {
				dialog.dismiss();
			}
		});

		return builder.create();
	}

	private Dialog errorMessageDialog() {
		FirebaseCrashlytics crashlytics = FirebaseCrashlytics.getInstance();
		AlertDialog.Builder builder = new AlertDialog.Builder(this);

		builder.setTitle(R.string.sync_error);

		String message = getString(R.string.sync_failed);
		SynchronizationError.ErrorType errorType = synchronizationError.getType();
		Exception synchronizationErrorException = synchronizationError.getException();
		final boolean logout[] = new boolean[1];
		switch (errorType) {
		case AUTHENTICATION_ERROR:
			AuthenticationException exception = (AuthenticationException) synchronizationErrorException;
			FailCause failCause = exception.getFailCause();
			switch (failCause) {
			case INVALID_USER:
				message = getString(R.string.sync_failed_authentication_error);
				logout[0] = true;
				break;
			case NO_COOKIE:
				message = getString(R.string.sync_failed_session_error_try_again);
				crashlytics.recordException(exception);
				logout[0] = true;
				break;
			case INVALID_SERVER_RESPONSE:
				message = getString(R.string.sync_failed_http_error_try_again);
				crashlytics.recordException(exception);
                logout[0] = true;
				break;
			}

			break;
		case BAD_CONFIGURATION:
			message = getString(R.string.sync_failed_bad_configuration);
			crashlytics.recordException(new SodepException(message));
			logout[0] = true;
			break;
		case CONNECTION_FAILED:
			message = getString(R.string.connection_failed);
			if (synchronizationErrorException != null) {
				message += "\nCause: " + synchronizationErrorException.getClass().getSimpleName() + " "
						+ synchronizationErrorException.getMessage();
			}
			break;
		case CONNECTION_TIMEOUT:
			message = getString(R.string.sync_failed_connection_timeout);
			break;
		case PARSE_ERROR:
			message = getString(R.string.sync_failed_parse_error);
			crashlytics.recordException(new SodepException(message));
			break;
		case SERVER_UNREACHABLE:
			message = getString(R.string.server_unreachable);
			crashlytics.recordException(new SodepException(message));
			break;
		case RESPONSE_CODE_NOT_200:
			HttpResponseException ex = (HttpResponseException) synchronizationErrorException;
			message = ActivityUtils.getMessage(this, ex);
			crashlytics.recordException(new SodepException(message));
			break;
		case TOO_MANY_DEVICES:
			break;
		default:
			break;
		}

		builder.setTitle(R.string.sync_error);
		builder.setMessage(message);
		builder.setCancelable(false);
		builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int id) {
				dialog.dismiss();
				if (logout[0]) {
					MFApplication application = (MFApplication) getApplication();
					application.stopSync();
					
					Intent loginActivity = new Intent(MainActivity.this, LoginActivity.class);
					MainActivity.this.startActivity(loginActivity);
					MainActivity.this.finish();
				}
			}
		});

		return builder.create();
	}

	@Override
	public void onTabReselected(Tab tab, FragmentTransaction ft) {
		switch (tab.getPosition()) {
		case 1:
			selectedProjectId = null;
			refreshFormList();
			break;
		}

	}

	@Override
	public void onTabSelected(Tab tab, FragmentTransaction ft) {
		View projectsList = findViewById(R.id.listViewProjects);
		View formsList = findViewById(R.id.listViewForms);
		View documentsList = findViewById(R.id.listViewDocuments);
		supportInvalidateOptionsMenu();
		switch (tab.getPosition()) {
		case 0:
			selectedProjectId = null;
			projectsList.setVisibility(View.VISIBLE);
			formsList.setVisibility(View.GONE);
			documentsList.setVisibility(View.GONE);
			break;
		case 1:
			refreshFormList();
			projectsList.setVisibility(View.GONE);
			formsList.setVisibility(View.VISIBLE);
			documentsList.setVisibility(View.GONE);
			break;
		case 2:
			refreshDocumentsList(null);
			projectsList.setVisibility(View.GONE);
			formsList.setVisibility(View.GONE);
			documentsList.setVisibility(View.VISIBLE);
			break;
		}

	}

	@Override
	public void onTabUnselected(Tab arg0, FragmentTransaction arg1) {

	}
}
