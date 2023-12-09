package py.com.sodep.mobileforms.net.sync;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;

import py.com.sodep.mf.exchange.device.info.DeviceInfo;
import py.com.sodep.mf.exchange.exceptions.AuthenticationException;
import py.com.sodep.mf.exchange.exceptions.HttpResponseException;
import py.com.sodep.mf.exchange.net.ServerConnection;
import py.com.sodep.mf.exchange.objects.data.MFMultiplexedFileSerializer;

import py.com.sodep.mf.exchange.objects.error.ErrorResponse;
import py.com.sodep.mf.exchange.objects.error.ErrorType;
import py.com.sodep.mf.exchange.objects.metadata.Form;
import py.com.sodep.mf.exchange.objects.upload.MFMultiplexedFile;
import py.com.sodep.mf.exchange.objects.upload.UploadHandle;
import py.com.sodep.mf.exchange.objects.upload.UploadProgress;
import py.com.sodep.mf.exchange.objects.upload.UploadStatus;
import py.com.sodep.mf.form.model.MFForm;
import py.com.sodep.mf.form.model.element.MFElement;
import py.com.sodep.mobileforms.application.BroadcastActions;
import py.com.sodep.mobileforms.dataservices.DocumentsDataSource;
import py.com.sodep.mobileforms.dataservices.FormsDAO;
import py.com.sodep.mobileforms.dataservices.sql.SQLDocumentsDataSource;
import py.com.sodep.mobileforms.dataservices.sql.SQLFormsDAO;
import py.com.sodep.mobileforms.net.sync.DocumentSyncResult.ERROR;
import py.com.sodep.mobileforms.settings.AppSettings;
import py.com.sodep.mobileforms.util.Util;
import android.content.Context;
import android.content.Intent;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import android.util.Log;


import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * An instance of this class is used to send/upload data to the server
 * 
 * @author Miguel
 * 
 */
public class DocumentsSync {

	private static final String LOG_TAG = DocumentsSync.class.getSimpleName();

	private Context context;

	private ServerConnection serverConnection;

	private static final JsonFactory factory = new JsonFactory();

	private ObjectMapper mapper = new ObjectMapper();

	private String deviceUniqueId;

	private LocalBroadcastManager localBroadcastManager;

	private ConnectionGuard connectionGuard;

	public static final int MAX_RETRIES = 5;

	private FormsDAO formsDAO = new SQLFormsDAO();

	private MFMultiplexedFileSerializer multiplexedFileSerializer = new MFMultiplexedFileSerializer();

	private DocumentsDataSource ds = new SQLDocumentsDataSource();

	private DateFormat dateFormatISO8601 = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

	public DocumentsSync(Context context, ServerConnection serverConnection) {
		this.localBroadcastManager = LocalBroadcastManager.getInstance(context);
		this.context = context;
		this.serverConnection = serverConnection;
		this.connectionGuard = new ConnectionGuard(serverConnection, localBroadcastManager);
		this.deviceUniqueId = DeviceInfo.getUniqueIdentifier(context);
	}

	/**
	 * 
	 * @return
	 */
	public synchronized SyncSummary syncAll() {
		// No documents can be in progress at this point
		ds.resetAllInProgress();
		// retry until tries == MAX_TRIES
		ds.resetAllFailed(MAX_RETRIES);

		SyncSummary syncSummary = new SyncSummary();

		List<Long> unsyncedDocumentsId = ds.listUnsyncedDocuments(AppSettings.getAppId(context));
		boolean documentsAvailable = unsyncedDocumentsId != null && !unsyncedDocumentsId.isEmpty();
		if (documentsAvailable) {
			// broadcastUploadStarted();
			try {
				for (Long id : unsyncedDocumentsId) {
					DocumentSyncResult result = syncDocument(id);
					syncSummary.addResult(result);
				}
			} catch (Exception e) {
				DocumentSyncResult result = new DocumentSyncResult();
				result.setError(ERROR.EXCEPTION);
				result.setThrownException(e);
				syncSummary.addResult(result);
			}
			// broadcastUploadFinished();
		}
		return syncSummary;
	}

	private DocumentSyncResult syncDocument(Long id) throws Exception {
		ds.markDocumentAsInProgress(id);
		broadcastDocumentStatusChanged();
		Map<String, String> doc = ds.getDocumentAsMap(id);
        Long formId =  Long.parseLong(doc.get("form"));
		File file = writeDocumentToFile(id, doc);
		String savedAt = doc.get("saved_at");
		Date date = dateFormatISO8601.parse(savedAt);
		try {
			return syncFile(formId, id, date.getTime(), file);
		} catch (HttpResponseException e) {
			ErrorResponse errorResponse = e.getErrorResponse();
			if (errorResponse != null) {
				ErrorType errorKind = errorResponse.getErrorType();
				switch (errorKind) {
				case DEVICE_NOT_ASSOCIATED:
				case LICENSE_EXPIRED:
					ds.markDocumentAsRejected(id, e.getResponseCode(), errorResponse.getMessageInDefaultLanguage());
                    break;
				default:
					ds.markDocumentAsFailed(id, e.getResponseCode(), errorResponse.getMessageInDefaultLanguage());
				}
			} else {
				ds.markDocumentAsFailed(id, e.getResponseCode(), e.getMessage());
			}
			broadcastDocumentStatusChanged();
			throw e;
		}
	}

	private DocumentSyncResult syncFile(Long formId, Long id, Long time, File file) throws AuthenticationException, IOException,
			HttpResponseException {
		DocumentSyncResult result = new DocumentSyncResult();

		long length = file.length();
		UploadHandle uploadHandle = serverConnection.requestUploadHandle(deviceUniqueId, formId, id + "-" + time, length);

		if (uploadHandle != null && uploadHandle.isAcquired()) {
			UploadStatus status = uploadHandle.getStatus();
			switch (status) {
			case PROGRESS:
				// This is the default path. Once a document is stored it is
				// saved as PROGRESS and then the system will try to upload it
				uploadFile(id, uploadHandle, file, length, result);
				break;
			// If the connection has been dropped after the document was fully
			// uploaded then we need to track what has happened with it
			// Note that this portion is very similar to the last part of the
			// uploadFile method.
			case COMPLETED:
			case SAVING:
				pollForResult(id, uploadHandle, result);
				break;
			case SAVED:
				result.setSuccess(true);
				ds.markDocumentsAsSynced(id);
				break;
			case REJECTED:
			case FAIL: // FIXME why is FAIL rejected? and invalid failed?
				result.setSuccess(false);
				result.setError(ERROR.REJECTED);
				ds.markDocumentAsRejected(id);
				break;
			case INVALID:
				result.setSuccess(false);
				ds.markDocumentAsFailed(id);
				break;
			}
		} else {
			result.setSuccess(false);
			result.setError(ERROR.NO_HANDLE);
			ds.markDocumentAsFailed(id);
		}
		return result;
	}

	private void pollForResult(Long id, UploadHandle uploadHandle, DocumentSyncResult result)
			throws AuthenticationException, IOException {
		String handle = uploadHandle.getHandle();
		for (int i = 0; i < 5; i++) {
			UploadProgress progress = serverConnection.getProgress(handle);
			if (progress != null) {
				switch (progress.getStatus()) {
				case SAVED:
					result.setSuccess(true);
					ds.markDocumentsAsSynced(id);
					break;
				case REJECTED:
				case FAIL:
					result.setSuccess(false);
					result.setError(ERROR.REJECTED);
					ds.markDocumentAsRejected(id);
					break;
				}
			}
			try {
				Thread.sleep(ConnectionGuard.PING_INTERVAL);
			} catch (InterruptedException e) {

			}
		}
	}

	private void uploadFile(Long id, UploadHandle uploadHandle, File file, long length, DocumentSyncResult result)
			throws IOException {
		FileInputStream fis = null;
		try {
			String handle = uploadHandle.getHandle();
			long receivedBytes = uploadHandle.getReceivedBytes();

            fis = new FileInputStream(file);
            HttpURLConnection c = serverConnection.uploadConnection(uploadHandle);
            c.setConnectTimeout(0); // the connection guard takes care of
									// this
			c.setReadTimeout(0); // and this
            // chunklen - The number of bytes to write in each chunk. If chunklen is less than or equal to zero, a default value will be used.
            c.setChunkedStreamingMode(0);
            // or multipart/form-data?
            c.setRequestProperty("Content-Type", "application/octet-stream");
            addRequestHeaders(c, receivedBytes, length);
			// start guarding the connection
			connectionGuard.guardConnection(c, handle);
            Log.d(LOG_TAG, "Writing to output");
            writeToOutputStream(fis, receivedBytes, c.getOutputStream());
            Log.d(LOG_TAG, "Waiting for responde code");
			int responseCode = c.getResponseCode();
            Log.d(LOG_TAG, "Got response code " + responseCode);
			if (responseCode == 200) {
				// FIXME use the server answer

				// Wait for the server answer. When the ConnectionGuard ends it
				// means that the handle reached an end status
                Log.d(LOG_TAG, "Join Connection guard");
				connectionGuard.join();
                Log.d(LOG_TAG, "Connection guard finished");

				// TODO use the result pollForResult
				UploadProgress lastProgress = connectionGuard.getLastProgress();
				if (lastProgress != null) {
					UploadStatus status = lastProgress.getStatus();

					switch (status) {
					// FIXME this block is almost identical to the method
					// pollForResult
					case SAVED:
						result.setSuccess(true);
						ds.markDocumentsAsSynced(id);
						break;
					case REJECTED:
					case FAIL:
						result.setSuccess(false);
						result.setError(ERROR.REJECTED);
						ds.markDocumentAsRejected(id);
						break;
					case INVALID:
						result.setSuccess(false);
						ds.markDocumentAsFailed(id);
						break;
					}
					file.delete();
					broadcastDocumentStatusChanged();
				} else {
					result.setSuccess(false);
					ds.markDocumentAsFailed(id);
				}
			} else {
				packResultBasedOnStatusCode(result, responseCode);
				ds.markDocumentAsFailed(id, responseCode);
				broadcastDocumentStatusChanged();
				result.setSuccess(false);
			}
		} catch (Exception e) {
			// TODO catch if there was a socketException and if the socket
			// was closed by the guard
			// In that case, mark as a SERVER UNREACHABLE FAILURE
			ds.markDocumentAsFailed(id, 0, e.getMessage());
			broadcastDocumentStatusChanged();
			packResult(result, e);
		} finally {
			if (fis != null) {
				fis.close();
			}
			connectionGuard.releaseGuard();
		}
	}

	private void addRequestHeaders(HttpURLConnection c, long position, long length) {
		c.setRequestProperty("Content-Range", String.format("bytes %d-%d/%d", position, length - 1, length));
	}

	private File writeDocumentToFile(Long id, Map<String, String> doc) throws JsonParseException, JsonMappingException,
			IOException {
		File file = new File(context.getCacheDir(), "mf-" + id + ".doc");
		if (!file.exists()) {
			writeDocumentToFile(file, id, doc);
			String fileMD5 = Util.getMD5Checksum(file);
			if (fileMD5 != null) {
				ds.setMD5(id, fileMD5);
			}
		} else {
			String savedMD5 = ds.getMD5(id);
			String fileMD5 = Util.getMD5Checksum(file);
			if (savedMD5 != null && fileMD5 != null && !savedMD5.equals(fileMD5)) {
				file.delete();
				writeDocumentToFile(file, id, doc);
				ds.setMD5(id, fileMD5);
			} else if (savedMD5 == null && fileMD5 == null) {
				file.delete();
				writeDocumentToFile(file, id, doc);
			}
		}
		return file;
	}

	private static final int BUFFER_SIZE = 1024 * 16;

	private byte[] buffer = new byte[BUFFER_SIZE];

	private void writeToOutputStream(FileInputStream in, long position, OutputStream os) throws IOException {
		in.getChannel().position(position);
		try {
			int read = 0;
			while ((read = in.read(buffer)) != -1) {
				os.write(buffer, 0, read);
                os.flush();
			}
		} finally {
			os.close();
		}
        Log.d(LOG_TAG, "Wrote all bytes to outputstream");
	}

	private void writeDocumentToFile(File outputFile, long documentId, Map<String, String> doc)
			throws JsonParseException, JsonMappingException, IOException {
		Form form = ds.getFormOfDocument(documentId);
		MFForm mfform = getMFForm(form);

		String saveRequestJSON = documentSaveRequestJSON(mfform, doc);

		MFMultiplexedFile multiplexedFile = new MFMultiplexedFile();
		multiplexedFile.addFile("document", "application/json", saveRequestJSON);

		List<MFElement> elements = mfform.listAllElements();
		OutputStream os = null;
		try {
			for (MFElement element : elements) {
				if (element.getProto().isFile()) {
					String key = element.getInstanceId();
					String path = doc.get(key);
					if (path != null && path.trim().length() > 0) {
						File file = new File(path);
						if (file.exists() && file.length() > 0) {
							FileInputStream fis = new FileInputStream(path);
							// #1999 the content-type should come from the proto
							// or file name?
							String contentType = getContenType(file);
							multiplexedFile.addFile(key, contentType, file.length(), fis);
						} else {
							// FIXME Just ignore if there's a missing file?
						}
					}
				}
			}

			os = new FileOutputStream(outputFile);
			multiplexedFileSerializer.write(multiplexedFile, os);
		} finally {
			if (os != null) {
				os.close();
			}
		}
	}

	private String getContenType(File file) {
		// TODO #1999
		// we currently only support jpeg images
		return "image/jpg";
	}

	private String documentSaveRequestJSON(MFForm mfform, Map<String, String> doc) throws IOException,
            JsonGenerationException {
		ByteArrayOutputStream os = new ByteArrayOutputStream();

		JsonGenerator g = factory.createJsonGenerator(os, JsonEncoding.UTF8);
		Document2JsonWriter.writeDocumentSaveRequest(g, mfform, doc);

		String string = new String(os.toByteArray(), "UTF-8");
		return string;
	}

	private void packResult(DocumentSyncResult result, Exception e) {
		Log.e(LOG_TAG, e.getMessage(), e);
		result.setThrownException(e);
		if (e instanceof SocketTimeoutException) {
			result.setError(ERROR.TIMEOUT);
		} else if (e instanceof AuthenticationException) {
			result.setError(ERROR.INVALID_AUTH);
		} else {
			result.setError(ERROR.EXCEPTION);

		}
	}

	private void packResultBasedOnStatusCode(DocumentSyncResult result, int statusCode) {
		if (statusCode >= 500 && statusCode < 600) {
			result.setError(ERROR.SERVER_ERROR);
		} else {
			result.setError(ERROR.SERVER_UNKOWN_RESPONSE);
		}
	}

	private MFForm getMFForm(Form form) throws IOException, JsonParseException, JsonMappingException {
		formsDAO.loadDefinition(form);
		MFForm mfform = mapper.readValue(form.getDefinition(), MFForm.class);
		return mfform;
	}


	private void broadcastDocumentStatusChanged() {
		Intent i = new Intent();
		i.setAction(BroadcastActions.ACTION_UPLOAD_DOCUMENT_STATUS_CHANGED);
		localBroadcastManager.sendBroadcast(i);
	}
}
