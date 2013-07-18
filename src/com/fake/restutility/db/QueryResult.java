package com.fake.restutility.db;

import android.database.Cursor;
import com.fake.restutility.object.ManagedObject;

import java.util.HashMap;

/**
 * Created by nickbabenko on 15/06/13.
 */
public class QueryResult {

	private static final String TAG = "QueryResult";

	private HashMap<Integer, ManagedObject> resultCache = new HashMap<Integer, ManagedObject>();
	private int currentIndex			= 0;
	private ManagedObject from;
	private Cursor cursor;

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
		return cursor.getCount();
	}

	public boolean isLast() {
		return cursor.isLast();
	}

	public ManagedObject current() {
		return object(currentIndex);
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