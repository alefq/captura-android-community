package py.com.sodep.mobileforms.dataservices.sql;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;


import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.jokoframework.chake.MFApplication;
import py.com.sodep.mf.exchange.MFDataHelper;
import py.com.sodep.mf.exchange.MFDataSetDefinition;
import py.com.sodep.mf.exchange.MFField;
import py.com.sodep.mf.exchange.MFField.FIELD_TYPE;
import py.com.sodep.mf.exchange.MFManagedDataBasic;
import py.com.sodep.mf.exchange.TXInfo;
import py.com.sodep.mf.exchange.TXInfo.OPERATION;
import py.com.sodep.mf.exchange.objects.lookup.MFDMLTransport;
import py.com.sodep.mf.exchange.objects.metadata.Form;
import py.com.sodep.mf.form.model.element.filter.MFFilter;

import py.com.sodep.mobileforms.dataservices.lookup.LookupDataSource;
import py.com.sodep.mobileforms.ui.rendering.objects.LookupData;

import static py.com.sodep.mobileforms.dataservices.sql.SodepSQLiteOpenHelper.LOOKUP_DATA_TABLE;
import static py.com.sodep.mobileforms.dataservices.sql.SodepSQLiteOpenHelper.LOOKUP_DEFINITION_TABLE;
import static py.com.sodep.mobileforms.dataservices.sql.SodepSQLiteOpenHelper.LOOKUP_MAPPING_TABLE;
import static py.com.sodep.mobileforms.dataservices.sql.SodepSQLiteOpenHelper.LOOKUP_TX_DML_TABLE;
import static py.com.sodep.mobileforms.dataservices.sql.SodepSQLiteOpenHelper.LOOKUP_TX_TABLE;

public class SQLLookupDataSource implements LookupDataSource {

	private static class LookupDataROW {
		private Long id;
		private Long rowId;
		private Long version;
	}

	private static final String LOG_TAG = SQLLookupDataSource.class.getSimpleName();

	private SodepSQLiteOpenHelper sqlHelper;

	private ObjectMapper mapper;

	public SQLLookupDataSource() {
		sqlHelper = MFApplication.sqlHelper;
		mapper = new ObjectMapper();
	}
	
	@Override
	public List<LookupData []> list(Long id, List<String> fields, List<MFFilter> filters) {
		SQLiteDatabase db = sqlHelper.getReadableDatabase();
		Map<String, ColumnInfo> fieldToColumnsMap = fieldToColumnMapping(db, id);

		List<ColumnInfo> selectColumns = new ArrayList<ColumnInfo>();
		for (String field : fields) {
			ColumnInfo columnInfo = fieldToColumnsMap.get(field);
			selectColumns.add(columnInfo);
			if (columnInfo == null) {
				throw new RuntimeException("Invalid field " + field + " for lookup table id : " + id);
			}
		}

		Cursor cursor = getCursor(db, id, selectColumns, filters, fieldToColumnsMap);
		try {
			List<LookupData []> data = new ArrayList<LookupData []>();
			while (cursor.moveToNext()) {
				LookupData [] row = new LookupData [selectColumns.size()];
				data.add(row);
				for(int i = 0; i < row.length; i++){
					String serializedValue = cursor.getString(i);
					ColumnInfo ci = selectColumns.get(i);
					Object object = MFDataHelper.unserialize(ci.type, serializedValue);
					LookupData ithValue = new LookupData(object, ci.type);
					row[i] = ithValue;
				}				
			}

			return data;
		} finally {
			cursor.close();
		}
	}

	private LookupDataROW loadRow(SQLiteDatabase db, Long lookupId, Long rowId) {
		Cursor cursor = db.query(LOOKUP_DATA_TABLE, new String[] { "_ID", "row_id", "row_version" },
				"lookuptable=? and row_id=?", new String[] { lookupId.toString(), rowId.toString() }, null, null, null);
		try {
			LookupDataROW row = null;
			if (cursor.moveToNext()) {
				row = new LookupDataROW();
				row.id = cursor.getLong(0);
				row.rowId = cursor.getLong(1);
				row.version = cursor.getLong(2);
			}
			return row;
		} finally {
			cursor.close();
		}
	}

	private Cursor getCursor(SQLiteDatabase db, Long id, List<ColumnInfo> select,
			List<MFFilter> filters, Map<String, ColumnInfo> fieldToColumnsMap) {
		StringBuilder sb = new StringBuilder();
		String[] params = new String[filters.size() + 1];
		params[0] = id.toString();
		for (int i = 0; i < filters.size(); i++) {
			MFFilter f = filters.get(i);
			sb.append(" AND ");
			ColumnInfo columnInfo = fieldToColumnsMap.get(f.getColumn());
			sb.append(columnInfo.column);
			switch (f.getOperator()) {
			case EQUALS:
				sb.append("=");
				break;
			case DISTINCT:
				sb.append("<>");
				break;
			case CONTAINS:
				sb.append(" LIKE ");
				break;
			}

			sb.append("?");
			switch (f.getOperator()) {
			case CONTAINS:
				params[i + 1] = "%" + f.getRightValue() + "%";
				break;
			default:
				params[i + 1] = f.getRightValue();
			}
		}
		
		String[] columns = new String[select.size()];
		for (int i = 0; i < columns.length; i++) {
			columns[i] = select.get(i).column;
		}

		Cursor cursor = db.query(LOOKUP_DATA_TABLE, columns,
				"lookuptable=?" + sb.toString(), params, null, null, null);
		return cursor;
	}

	@Override
	public Cursor query(Long id) {
		throw new RuntimeException("NOT YET IMPLEMENTED");
	}

	@Override
	public Cursor query(Long id, List<MFFilter> conditions) {
		throw new RuntimeException("NOT YET IMPLEMENTED");
	}

	@Override
	public boolean hasLookupTableDefinition(Long id) {
		SQLiteDatabase db = sqlHelper.getReadableDatabase();
		Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM " + LOOKUP_DEFINITION_TABLE + " WHERE id=?",
				new String[] { id.toString() });
		try {
			cursor.moveToFirst();
			Long count = cursor.getLong(0);

			return count != null && count > 0L;
		} finally {
			cursor.close();
		}
	}

	@Override
	public void markAsSynced(Long id) {
		SQLiteDatabase db = sqlHelper.getWritableDatabase();
		ContentValues values = new ContentValues();
		values.put("synced", 1);
		db.update(LOOKUP_DEFINITION_TABLE, values, "id = ?", new String[] { id.toString() });
	}

	@Override
	public boolean isSynced(Long id) {
		SQLiteDatabase db = sqlHelper.getReadableDatabase();
		Cursor cursor = db.query(LOOKUP_DEFINITION_TABLE, new String[] { "synced" }, "id=?",
				new String[] { id.toString() }, null, null, null);
		try {
			if (cursor.moveToFirst()) {
				Integer synced = cursor.getInt(0);

				return synced != null && synced == 1;
			} else {
				return false;
			}
		} finally {
			cursor.close();
		}
	}

	@Override
	public void define(Long lookupTableId, MFDataSetDefinition dataSet) {
		SQLiteDatabase db = null;
		try {
			db = sqlHelper.getWritableDatabase();
			db.beginTransaction();
			insertLookupDefinition(lookupTableId, dataSet, db);
			insertFields(db, lookupTableId, dataSet.getFields());
			db.setTransactionSuccessful();
		} finally {
			if (db != null) {
				if (db.inTransaction()) {
					db.endTransaction();
				}
			}
		}
	}

	private void insertLookupDefinition(Long lookupTableId, MFDataSetDefinition dataSet, SQLiteDatabase db) {
		ContentValues values = new ContentValues();
		values.put("id", lookupTableId);
		values.put("metadata_ref", dataSet.getMetaDataRef());
		values.put("version", dataSet.getVersion());
		db.insertOrThrow(LOOKUP_DEFINITION_TABLE, null, values);
	}

	private void insertFields(SQLiteDatabase db, Long lookupId, List<MFField> fields) {
		ContentValues values = new ContentValues();
		int index = 0;
		for (MFField field : fields) {
			values.put("lookuptable", lookupId);
			values.put("fieldname", field.getColumnName());
			values.put("data_type", field.getType().ordinal());
			values.put("data_index", index);
			db.insertOrThrow(LOOKUP_MAPPING_TABLE, null, values);
			index++;
		}
	}

	@Override
	public void applyDML(MFDMLTransport dml) {
		SQLiteDatabase db = null;
		TXInfo info = dml.getTxInfo();
		String txId = info.getTx();
		Long lookupTable = info.getLookupTable();
		try {
			String serializedDML = serializeDML(dml);
			String serializedTXInfo = serializeTXInfo(info);

			db = sqlHelper.getWritableDatabase();
			db.beginTransaction();
			insertTempDML(serializedDML, db, txId, lookupTable);
			updateLastTXInfo(db, serializedTXInfo, lookupTable);
			db.setTransactionSuccessful();
		} catch (Exception e) {
			Log.e(LOG_TAG, e.getMessage(), e);
			throw new RuntimeException("Exception executing applyDML for tx : " + txId);
		} finally {
			if (db != null) {
				if (db.inTransaction()) {
					db.endTransaction();
				}
			}
		}
	}

	private String serializeTXInfo(TXInfo info) throws IOException, JsonGenerationException, JsonMappingException {
		long t0 = System.currentTimeMillis();
		String serializedTXInfo = mapper.writeValueAsString(info);
		long t1 = System.currentTimeMillis();
		Log.d(LOG_TAG, "TXInfo Serialization time " + (t1 - t0) / 1000);
		return serializedTXInfo;
	}

	private String serializeDML(MFDMLTransport dml) throws IOException, JsonGenerationException, JsonMappingException {
		long t0 = System.currentTimeMillis();
		String serializedDML = mapper.writeValueAsString(dml);
		long t1 = System.currentTimeMillis();
		Log.d(LOG_TAG, "DML Serialization time " + (t1 - t0) / 1000 + " sec.");

		int size = serializedDML.length();
		Log.d(LOG_TAG, "Serialized DML length = " + size);

		return serializedDML;
	}

	private void insertTempDML(String serializedDML, SQLiteDatabase db, String txId, Long lookupTable)
			throws IOException, JsonGenerationException, JsonMappingException {
		long t0 = System.currentTimeMillis();
		ContentValues txValues = new ContentValues();
		txValues.put("tx_id", txId);
		txValues.put("lookuptable", lookupTable);
		txValues.put("dml", serializedDML);
		db.insertOrThrow(LOOKUP_TX_DML_TABLE, null, txValues);
		long t1 = System.currentTimeMillis();
		Log.d(LOG_TAG, "DML insertion time " + (t1 - t0) / 1000);
	}

	private void updateLastTXInfo(SQLiteDatabase db, String serializedTXInfo, Long lookupTable) throws IOException,
			JsonGenerationException, JsonMappingException {
		long t0 = System.currentTimeMillis();
		ContentValues defValues = new ContentValues();
		defValues.put("tx_info", serializedTXInfo);
		int updated = db.update(LOOKUP_DEFINITION_TABLE, defValues, "id=?", new String[] { lookupTable.toString() });
		if (updated == 0) {
			throw new RuntimeException("No lookup with id " + lookupTable + " to apply DML");
		}
		long t1 = System.currentTimeMillis();
		Log.d(LOG_TAG, "Update Last TXInfo time " + (t1 - t0) / 1000);
	}

	@Override
	public void startTx(String txId) {
		SQLiteDatabase db = sqlHelper.getWritableDatabase();
		ContentValues values = new ContentValues();
		values.put("id", txId);
		db.insertWithOnConflict(LOOKUP_TX_TABLE, null, values, SQLiteDatabase.CONFLICT_IGNORE);
	}

	@Override
	public void endTx(String tx) {
		SQLiteDatabase db = null;
		try {
			db = sqlHelper.getWritableDatabase();
			db.beginTransaction();
			long currentTime=System.currentTimeMillis();
			// First lets apply all dmls from this transaction
			List<MFDMLTransport> dmls = listDMLs(tx, db);
			currentTime = System.currentTimeMillis() - currentTime;
			Log.v(LOG_TAG, "listDML took " + currentTime + " ms");
			currentTime = System.currentTimeMillis();
			executeDMLs(db, dmls);
			currentTime = System.currentTimeMillis() - currentTime;
			Log.v(LOG_TAG, "executeDMLs took " + currentTime + " ms");
			// Now, lets delete the transaction
			currentTime=System.currentTimeMillis();
			deleteTransactionTempData(tx, db);
			currentTime=System.currentTimeMillis()-currentTime;
			Log.v(LOG_TAG, "deleteTransactionTempData took "+currentTime+" ms");
			db.setTransactionSuccessful();
		} catch (Exception e) {
			Log.e(LOG_TAG, e.getMessage(), e);
			throw new RuntimeException("Exception executing endTx for tx : " + tx);
		} finally {
			if (db != null) {
				if (db.inTransaction()) {
					db.endTransaction();
				}
			}
		}
	}

	private void deleteTransactionTempData(String tx, SQLiteDatabase db) {
		int affected = db.delete(LOOKUP_TX_TABLE, "id=?", new String[] { tx });
		
		if (affected == 0) {
			throw new RuntimeException("Unknown transaction : " + tx);
		}
		
		affected = db.delete(LOOKUP_TX_DML_TABLE, "tx_id=?", new String[] { tx });
	}

	private void executeDMLs(SQLiteDatabase db, List<MFDMLTransport> dmls) {
		for (MFDMLTransport dml : dmls) {
			OPERATION op = dml.getTxInfo().getOperation();
			switch (op) {
			case INSERT:
				insertOrUpdate(db, dml);
				break;
			case UPDATE:
				insertOrUpdate(db, dml);
				break;
			case DELETE:
				delete(db, dml);
				break;
			}
		}
	}

	private List<MFDMLTransport> listDMLs(String tx, SQLiteDatabase db) {
		Cursor cursor = db.query(LOOKUP_TX_DML_TABLE, new String[] { "dml" }, "tx_id=?", new String[] { tx }, null,
				null, "modified_at");
		try {
			List<MFDMLTransport> dmls = new ArrayList<MFDMLTransport>();
			while (cursor.moveToNext()) {
				String serializedDML = cursor.getString(0);
				MFDMLTransport dml = unserializeMDFMLTransport(serializedDML);
				dmls.add(dml);
			}
			return dmls;
		} finally {
			cursor.close();
		}
	}

	private MFDMLTransport unserializeMDFMLTransport(String serializedDML) {
		MFDMLTransport dml = null;
		try {
			dml = mapper.readValue(serializedDML, MFDMLTransport.class);
		} catch (Exception e) {
			Log.e(LOG_TAG, e.getMessage(), e);
			throw new RuntimeException("Exception executing tx : ");
		}
		return dml;
	}

	private void delete(SQLiteDatabase db, MFDMLTransport dml) {
		TXInfo txInfo = dml.getTxInfo();
		Long startRow = txInfo.getStartRow();
		Long endRow = txInfo.getEndRow();
		int deletedRow = db.delete(LOOKUP_DATA_TABLE, "lookuptable = ? and row_id >= ? and row_id <= ?", new String[] {
				txInfo.getLookupTable().toString(), startRow.toString(), endRow.toString() });

		Log.d(LOG_TAG, "Deleted " + deletedRow + " from lookup table #" + txInfo.getLookupTable());

	}

	private void insertOrUpdate(SQLiteDatabase db, MFDMLTransport dml) {
		TXInfo info = dml.getTxInfo();
		Long lookupId = info.getLookupTable();
		Map<String, ColumnInfo> fieldToColumn = fieldToColumnMapping(db, lookupId);

		if (dml.getData() != null) {
			// Since data might be deleted an insert or update transaction might
			// not longer
			// have the associated data
			for (MFManagedDataBasic mfManagedData : dml.getData()) {
				Long rowId = mfManagedData.getRowId();

				ContentValues values = managedDataToContentValues(fieldToColumn, mfManagedData);
				values.put("lookuptable", lookupId);
				values.put("row_id", rowId);
				values.put("row_version", mfManagedData.getVersion());
				// this should be one single query, returning all rows and should be placed before the loop
				LookupDataROW loadRow = loadRow(db, lookupId, rowId);
				if (loadRow != null) {
					// need to update it
					db.updateWithOnConflict(LOOKUP_DATA_TABLE, values, "_ID =?",
							new String[] { loadRow.id.toString() }, SQLiteDatabase.CONFLICT_REPLACE);
				} else {
					// the row doesn't exists, need to insert it
					db.insertWithOnConflict(LOOKUP_DATA_TABLE, null, values, SQLiteDatabase.CONFLICT_REPLACE);
				}

			}
		}

	}

	private static ContentValues managedDataToContentValues(Map<String, ColumnInfo> fieldToColumn,
			MFManagedDataBasic mfManagedData) {
		ContentValues values = new ContentValues();
		Map<String, ?> data = mfManagedData.getUserData();

		for (String keyField : data.keySet()) {
			ColumnInfo columnInfo = fieldToColumn.get(keyField);
			String column = columnInfo.column;
			String dataStr = null;
			if (data.get(keyField) != null) {
				dataStr = data.get(keyField).toString();
			}
			values.put(column, dataStr);
		}
		return values;
	}
	
	private class ColumnInfo {

		MFField.FIELD_TYPE type;

		String column;

	}

	// TODO use the type
	/**
	 * The data of all lookup tables are being stored on the table
	 * LOOKUP_DATA_TABLE. The table LOOKUP_MAPPING_TABLE contain a mapping from
	 * the lookup field to the column where the data is actually being stored
	 * 
	 * @param db
	 * @param lookupId
	 * @return
	 */
	//FIXME use data_type #2732
	private Map<String, ColumnInfo> fieldToColumnMapping(SQLiteDatabase db, Long lookupId) {
		Cursor cursor = db.query(LOOKUP_MAPPING_TABLE, new String[] { "fieldname", "data_type", "data_index" },
				"lookuptable=?", new String[] { lookupId.toString() }, null, null, null);
		try {
			Map<String, ColumnInfo> map = new HashMap<String, ColumnInfo>();
			while (cursor.moveToNext()) {
				String fieldName = cursor.getString(0);
				int typeOrdinal = cursor.getInt(1);
				int dataIndex = cursor.getInt(2);
				String column = "data" + dataIndex;
				ColumnInfo ci = new ColumnInfo();
				ci.column = column;
				ci.type = FIELD_TYPE.values()[typeOrdinal];
				map.put(fieldName, ci);
			}
			return map;
		} finally {
			cursor.close();
		}
	}

	@Override
	public TXInfo getTXInfo(Long lookupId) {
		SQLiteDatabase db = sqlHelper.getReadableDatabase();
		Cursor cursor = db.query(LOOKUP_DEFINITION_TABLE, new String[] { "tx_info" }, "id=?",
				new String[] { lookupId.toString() }, null, null, null);
		try {
			TXInfo info = null;
			if (cursor.moveToFirst()) {
				String serializedTXInfo = cursor.getString(0);
				if (serializedTXInfo != null) {
					info = unserializeTXInfo(info, serializedTXInfo);
				}
			}

			return info;
		} finally {
			cursor.close();
		}
	}

	private TXInfo unserializeTXInfo(TXInfo info, String serializedTXInfo) {
		try {
			info = mapper.readValue(serializedTXInfo, TXInfo.class);
		} catch (Exception e) {
			Log.e(LOG_TAG, e.getMessage(), e);
			throw new RuntimeException(e);
		}
		return info;
	}

	@Override
	public void deleteLookupData() {
		SQLiteDatabase db = sqlHelper.getWritableDatabase();
		db.delete(LOOKUP_DEFINITION_TABLE, null, null);
	}

	@Override
	public boolean isAllDataAvailable(Form form) {
		List<Long> requiredLookups = form.getRequiredLookupTables();
		if (requiredLookups != null && !requiredLookups.isEmpty()) {
			for (Long l : requiredLookups) {
				if (!isSynced(l)) {
					return false;
				}
			}
		}
		return true;
	}
}
