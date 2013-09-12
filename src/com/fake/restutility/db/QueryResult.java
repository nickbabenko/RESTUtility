package com.fake.restutility.db;

import android.database.Cursor;
import com.fake.restutility.object.ManagedObject;

import java.lang.reflect.Field;
import java.util.HashMap;

/**
 * Created by nickbabenko on 15/06/13.
 */
public class QueryResult {

	private static final String TAG = "QueryResult";

	private HashMap<Integer, ManagedObject> resultCache = new HashMap<Integer, ManagedObject>();
	private int currentIndex							= 0;
	private ManagedObject from;
	private Cursor cursor;

	public QueryResult() {

	}

	public QueryResult(ManagedObject from, Object objectId) {
		this.from = from;

		loadSingleObjectWithId(from, objectId);
	}

	public QueryResult(ManagedObject from, Cursor cursor) {
		this.from 	= from;
		this.cursor	= cursor;

		cursor.moveToFirst();
	}

	public Cursor getCursor() {
		return cursor;
	}

	public ManagedObject object(int index) {
		return loadSingleObjectFromCursor(from, cursor, index);
	}

	public int count() {
		return (cursor != null ? cursor.getCount() : 0);
	}

	public boolean isLast() {
		return (cursor != null ? cursor.isLast() : true);
	}

	public ManagedObject current() {
		return object(currentIndex);
	}

	public void gotoFirst() {
		currentIndex = 0;
	}

	public String currentPrimaryKey() {
		return from.primaryKeyName();
	}

	public Object currentPrimaryValue() {
		if(cursor == null || cursor.getCount() == 0)
			return null;

		cursor.moveToPosition(currentIndex);

		int index = cursor.getColumnIndex(currentPrimaryKey());

		if(index == -1)
			return null;

		Field primaryField = from.primaryKeyField();

		if(primaryField.getType().equals(Integer.class) ||
		   primaryField.getType().equals(int.class))
			return cursor.getInt(index);
		else if(primaryField.getType().equals(String.class))
			return cursor.getString(index);
		else if(primaryField.getType().equals(Double.class) ||
				primaryField.getType().equals(double.class))
			return cursor.getDouble(index);
		else if(primaryField.getType().equals(Float.class) ||
			    primaryField.getType().equals(float.class))
			return cursor.getFloat(index);
		else if(primaryField.getType().equals(boolean.class) ||
				primaryField.getType().equals(Boolean.class))
			return (cursor.getInt(index) == 1);
		else
			return null;
	}

	public boolean hasNext() {
		return (currentIndex < count());
	}

	public ManagedObject next() {
		ManagedObject current = current();

		currentIndex++;

		return current;
	}

	private void loadSingleObjectWithId(ManagedObject from, Object objectId) {
		try {
			cursor = new Query(Query.Type.Select)
					.from(from)
					.where(from.primaryKeyName(), "=", objectId)
					.limit(1)
					.execute()
					.getCursor();
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}

	private ManagedObject loadSingleObjectFromCursor(ManagedObject from, Cursor cursor, int inc) {
		try {
			if(resultCache.containsKey(new Integer(inc)))
				return resultCache.get(new Integer(inc));

			ManagedObject result = from.getClass().newInstance();

			cursor.moveToPosition(inc);

			result.setFromCursor(cursor);

			resultCache.put(new Integer(inc), result);

			return result;
		}
		catch (Exception e) {
			return null;
		}
	}

}