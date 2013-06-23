package com.fake.restutility.db;

import android.database.Cursor;
import android.util.Log;
import com.fake.restutility.object.ManagedObject;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by nickbabenko on 15/06/13.
 */
public class QueryResult {

	private static final String TAG = "QueryResult";

	private List<ManagedObject> results = new ArrayList<ManagedObject>();
	private int currentIndex			= 0;
	private ManagedObject from;

	public QueryResult(ManagedObject from) {
		results.add(from);
	}

	public QueryResult(ManagedObject from, Object objectId) {
		this.from = from;

		loadSingleObjectWithId(from, objectId);
	}

	public QueryResult(ManagedObject from, Cursor cursor) {
		this.from = from;

		Log.d("TAG", "Results: " + from.getClass() + " - " + cursor.getCount());

		if(cursor.moveToFirst()) {
			for(int i=0; i<cursor.getCount(); i++) {
				loadSingleObjectFromCursor(from, cursor, i);

				cursor.moveToNext();
			}
		}
	}

	public Object object(int index) {
		return results.get(index);
	}

	public int count() {
		return results.size();
	}

	public ManagedObject current() {
		try {
			return results.get(currentIndex);
		}
		catch(IndexOutOfBoundsException e) {
			return null;
		}
	}

	public boolean hasNext() {
		return (currentIndex < results.size());
	}

	public Object next() {
		Object current = current();

		currentIndex++;

		return current;
	}

	@SuppressWarnings("unchecked")
	public <T extends ManagedObject> T[] results() {
		return results.toArray((T[]) Array.newInstance(from.getClass(), results.size()));
	}

	private void loadSingleObjectWithId(ManagedObject from, Object objectId) {
		try {
			Log.d(TAG, "Primary key: " + from.primaryKeyName() + " = " + objectId);

			results.add(new Query(Query.Type.Select)
					.from(from)
					.where(from.primaryKeyName(), "=", objectId)
					.limit(1)
					.execute()
					.current());
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void loadSingleObjectFromCursor(ManagedObject from, Cursor cursor, int inc) {
		try {
			ManagedObject result = from.getClass().newInstance();

			result.setFromCursor(cursor);

			results.add(result);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}