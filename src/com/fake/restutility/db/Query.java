package com.fake.restutility.db;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;
import android.text.TextUtils;
import com.fake.restutility.object.ManagedObject;
import com.fake.restutility.rest.ObjectManager;
import com.fake.restutility.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

/**
 * Created by nickbabenko on 15/06/13.
 */
public class Query {

	private static final String TAG				= "REST Utility - db - Query";


	/* Static Query Helpers */

	public static QueryResult find(Class<? extends ManagedObject> entityClass) {
		return find(entityClass, null);
	}

	public static QueryResult find(Class<? extends ManagedObject> entityClass, Object id) {
		ManagedObject from = instantiate(entityClass);

		try {
			return new Query(Type.Select)
				.from(from)
				.where(from.primaryKeyName(), "=", id)
				.limit(1)
				.execute();
		}
		catch (Exception e) {
			e.printStackTrace();

			return null;
		}
	}

	public static QueryResult findFirst(Class<? extends ManagedObject> entityClass) {
		ManagedObject from = instantiate(entityClass);

		try {
			return new Query(Type.Select)
					.from(from)
					.limit(1)
					.execute();
		}
		catch (Exception e) {
			e.printStackTrace();

			return null;
		}
	}

	public static Query select(Class<? extends ManagedObject> entityClass) {
		ManagedObject from = instantiate(entityClass);

		return new Query(Type.Select)
			.from(from);
	}

	public static ManagedObject instantiate(Class<? extends ManagedObject> entityClass) {
		try {
			return entityClass.newInstance();
		}
		catch (Exception e) {
			e.printStackTrace();

			return null;
		}
	}


	/* Enums */

	public enum Type {
		Select,
		Insert,
		Update,
		Delete,
		Replace,
		BeginTransaction,
		TransactionSuccessful,
		EndTransaction
	}

	// Static
	private static DBHelper dbHelper;

	private SQLiteDatabase readableDatabase;
	private SQLiteDatabase writableDatabase;

	// Update / Insert query configuration
	public HashMap<String, Object> data 		= new HashMap<String, Object>();

	// Select query configuration
	private int limit							= 20;
	private int offset							= 0;
	private String rawQuery;
	private String[] rawArgs;

	// General query configuration
	private Type type 							= Type.Select;
	private ManagedObject from;
	private ArrayList<QueryWhere> andWhere 		= new ArrayList<QueryWhere>();
	private ArrayList<QueryOrder> order			= new ArrayList<QueryOrder>();
	private ContentValues contentValues;

	/**
	 *
	 */
	public Query() {

	}

	/**
	 * @param type
	 */
	public Query(Type type) {
		type(type);
	}

	/* Type Methods */

	/**
	 *
	 * @return
	 */
	public Query select() {
		return type(Type.Select);
	}

	/**
	 *
	 * @return
	 */
	public Query insert() {
		return type(Type.Insert);
	}

	/**
	 *
	 * @return
	 */
	public Query update() {
		return type(Type.Update);
	}

	/**
	 *
	 * @return
	 */
	public Query delete() {
		return type(Type.Delete);
	}


	/* General Query Configuration Methods  */

	public Query raw(String raw, String args[]) {
		this.rawQuery 	= raw;
		this.rawArgs	= args;

		return this;
	}

	public Type type() {
		return type;
	}

	/**
	 *
	 * @param type
	 * @return
	 */
	public Query type(Type type) {
		this.type = type;

		return this;
	}

	public Query from(ManagedObject from) {
		this.from = from;

		return this;
	}

	public Query contentValues(ContentValues contentValues) {
		this.contentValues = contentValues;

		return this;
	}

	/**
	 *
	 * @param key
	 * @param type
	 * @param value
	 * @return
	 */
	public Query where(String key, String type, Object value) {
		andWhere.add(new QueryWhere(key, type, value));

		return this;
	}

	public Query order(String column, String type) {
		order.add(new QueryOrder(column, type));

		return this;
	}

	/**
	 *
	 * @param limit
	 * @return
	 */
	public Query limit(int limit) {
		this.limit = limit;

		return this;
	}

	/**
	 *
	 * @return
	 */
	public QueryResult execute() {
		QueryResult result 		= null;
		SQLiteDatabase database	= null;

		try {
			database = database();
		}
		catch (Exception e) {
			e.printStackTrace();

			return new QueryResult();
		}

		if(!database.isOpen())
			return new QueryResult();

		switch(type) {
			case BeginTransaction:
				database.beginTransaction();
				break;
			case TransactionSuccessful:
				if(database.inTransaction())
					database.setTransactionSuccessful();
				break;
			case EndTransaction:
				if(database.inTransaction())
					database.endTransaction();

				writableDatabase = null;
				readableDatabase = null;
				break;
			case Insert:
				long insertId = database.insert(tableName(), null, contentValues());

				Log.d(TAG, "Insert row: " + tableName() + " - " + insertId);

				if(insertId > 0)
					result = new QueryResult(from, insertId);
				else
					Log.d(TAG, "Insert failed: " + tableName());
				break;
			case Update:
				int updatedRows = database.update(tableName(), contentValues(false), updateWhereString(), updateWhereArgs());

				Log.d(TAG, "Updated rows: " + tableName() + " - " + updatedRows + " - " + updateWhereString() + " - " + TextUtils.join(", ", updateWhereArgs()));
				break;
			case Replace:
				long replaceId = database.replace(tableName(), null, contentValues());

				Log.d(TAG, "Replace row: " + tableName() + " - " + replaceId);
				break;
			case Select:
				if(rawQuery != null)
					result = new QueryResult(from, database.rawQuery(rawQuery, rawArgs));
				else
					result = new QueryResult(from, database.query(tableName(), columnNameArray(), selectWhereString(), selectWhereArgs(), null, null, orderBy(), " " + offset + ", " + limit));

				Log.d(TAG, "Select where: " + tableName() + " - " + TextUtils.join(", ", columnNameArray()) + " - " + selectWhereString() + " - " + TextUtils.join(", ", selectWhereArgs()) + " - " + orderBy() + " - " + result.count());
				break;
			case Delete:
				int deleted = database.delete(tableName(), selectWhereString(), selectWhereArgs());

				Log.d(TAG, "Delete rows: " + tableName() + " - " + deleted + " - " + selectWhereString() + " - " + TextUtils.join(", ", selectWhereArgs()));
				break;
		}

		return result;
	}


	/* Database Configuration Methods */

	/**
	 *
	 * @return
	 */
	private String tableName() {
		if(from == null)
			return "";

		return from.tableName();
	}

	private ContentValues contentValues() {
		return contentValues(true);
	}

	/**
	 *
	 * @return
	 */
	private ContentValues contentValues(boolean includePrimaryKey) {
		return (contentValues == null ? from.contentValues(includePrimaryKey) : contentValues);
	}

	/**
	 *
	 * @return
	 */
	private String[] columnNameArray() {
		return from.columnNameArray();
	}

	private String updateWhereString() {
		return from.primaryKeyName() + "=?";
	}

	private String[] updateWhereArgs() {
		return new String[] { ""+from.primaryKeyValue() };
	}

	private String selectWhereString() {
		String selectWhereString = "";
		Iterator<QueryWhere> whereIterator = andWhere.iterator();
		String seperator = "";

		while(whereIterator.hasNext()) {
			QueryWhere where = whereIterator.next();

			if(from.primaryKeyName().equals(where.key()) && where.value() == null)
				continue;

			selectWhereString += seperator + where.key() + " " + where.type();

			if(where.value() instanceof String[] &&
			   (where.type().equals("IN") || where.type().equals("NOT IN"))) {
				selectWhereString += "(" + TextUtils.join(", ", (String[]) where.value()) + ")";
			}
			else {
				selectWhereString += (where.value() != null ? " ?" : "");
			}

			seperator = " AND ";
		}

		return selectWhereString;
	}

	private String[] selectWhereArgs() {
		ArrayList<String> selectWhereArgs = new ArrayList<String>();
		Iterator<QueryWhere> whereIterator = andWhere.iterator();

		while(whereIterator.hasNext()) {
			QueryWhere where = whereIterator.next();

			if(from.primaryKeyName().equals(where.key()) && where.value() == null)
				continue;

			if(where.value() == null ||
			   where.value() instanceof String[])
				continue;

			selectWhereArgs.add("" + where.value());
		}

		return selectWhereArgs.toArray(new String[selectWhereArgs.size()]);
	}

	public String orderBy() {
		String[] orderBy 					= new String[order.size()];
		Iterator<QueryOrder> orderIterator 	= order.iterator();
		int inc								= 0;

		while(orderIterator.hasNext()) {
			orderBy[inc] = orderIterator.next().toString();
		}

		return TextUtils.join(", ", orderBy);
	}


	/* SQLite Instance Helpers */

	private DBHelper helper() throws Exception {
		if(dbHelper == null)
			dbHelper = new DBHelper(ObjectManager.instance().context(), ObjectManager.instance().DBName(), null, ObjectManager.instance().DBVersion());

		return dbHelper;
	}

	public Query database(SQLiteDatabase database) {
		if(database == null)
			return this;

		if(database.isReadOnly())
			readableDatabase = database;
		else
			writableDatabase = database;

		return this;
	}

	public SQLiteDatabase database() throws Exception {
		SQLiteDatabase database = null;

		switch(type) {
			case Select:
				if(readableDatabase != null && readableDatabase.isOpen())
					database = readableDatabase;
				else
					database = helper().getReadableDatabase();
				break;
			case Insert:
			case Update:
			case Delete:
			case Replace:
			case BeginTransaction:
			case TransactionSuccessful:
			case EndTransaction:
				if(writableDatabase != null && writableDatabase.isOpen())
					database = writableDatabase;
				else
					database = helper().getWritableDatabase();
				break;
		}

		database.execSQL("PRAGMA foreign_keys=ON;");

		return database;
	}

}