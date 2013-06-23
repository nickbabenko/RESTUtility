package com.fake.restutility.db;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;
import android.text.TextUtils;
import android.util.Log;
import com.fake.restutility.object.ManagedObject;
import com.fake.restutility.rest.ObjectManager;

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
		BeginTransaction,
		TransactionSuccessful,
		EndTransaction
	}

	// Static
	private static DBHelper dbHelper;

	// Update / Insert query configuration
	public HashMap<String, Object> data 		= new HashMap<String, Object>();

	// Select query configuration
	private int limit							= 20;
	private int offset							= 0;

	// General query configuration
	private Type type 							= Type.Select;
	private ManagedObject from;
	private ArrayList<QueryWhere> andWhere 		= new ArrayList<QueryWhere>();
	private ArrayList<QueryOrder> order			= new ArrayList<QueryOrder>();

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

	/**
	 *
	 * @param key
	 * @param type
	 * @param value
	 * @return
	 */
	public Query where(String key, String type, Object value) {
		if(value == null)
			return this;

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
		QueryResult result = null;

		try {
			switch(type) {
				case BeginTransaction:
					database().beginTransaction();
					break;
				case TransactionSuccessful:
					database().setTransactionSuccessful();
					break;
				case EndTransaction:
					database().endTransaction();
					break;
				case Insert:
					long insertId = database().insert(tableName(), null, contentValues());

					Log.d(TAG, "Insert Id: " + insertId);

					if(insertId > 0)
						result = new QueryResult(from, from.primaryKeyValue());
					else
						Log.d(TAG, "Insert failed: " + tableName());
					break;
				case Update:
					int updatedRows = database().update(tableName(), contentValues(), updateWhereString(), updateWhereArgs());

					Log.d(TAG, "Updated rows: " + updatedRows);

					if(updatedRows > 0)
						result = new QueryResult(new Query(Type.Select).from(from).where(from.primaryKeyName(), "=", from.primaryKeyValue()).execute().current());
					else
						Log.d(TAG, "Update failed: " + tableName());
					break;
				case Select:
					Log.d(TAG, "Select where: " + selectWhereString() + " - " + TextUtils.join(", ", selectWhereArgs()) + " - " + orderBy());
					result = new QueryResult(from, database().query(tableName(), columnNameArray(), selectWhereString(), selectWhereArgs(), null, null, orderBy(), " " + offset + ", " + limit));
					break;
			}
		}
		catch(Exception e) {
			e.printStackTrace();
		}

		return result;
	}


	/* Database Configuration Methods */

	/**
	 *
	 * @return
	 */
	private String tableName() {
		return from.tableName();
	}

	/**
	 *
	 * @return
	 */
	private ContentValues contentValues() {
		return from.contentValues();
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

			selectWhereString += seperator + where.key() + " " + where.type() + (where.value() != null ? " ?" : "");

			seperator = " AND ";
		}

		return selectWhereString;
	}

	private String[] selectWhereArgs() {
		String[] selectWhereArgs = new String[andWhere.size()];
		Iterator<QueryWhere> whereIterator = andWhere.iterator();
		int inc = 0;

		while(whereIterator.hasNext()) {
			QueryWhere where = whereIterator.next();

			if(where.value() == null)
				continue;

			selectWhereArgs[inc] = "" + where.value();

			inc++;
		}

		return selectWhereArgs;
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

	private SQLiteDatabase database() throws Exception {
		SQLiteDatabase database = null;

		switch(type) {
			case Select:
				database = helper().getReadableDatabase();
				break;
			case Insert:
			case Update:
			case Delete:
			case BeginTransaction:
			case TransactionSuccessful:
			case EndTransaction:
				database = helper().getWritableDatabase();
				break;
		}

		return database;
	}

}