package com.fake.restutility.db;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;
import com.fake.restutility.object.ManagedObject;
import com.fake.restutility.object.ManagedObjectUtils;

/**
 * Created by nickbabenko on 15/06/13.
 */
public class DBHelper extends SQLiteOpenHelper {

	private static final String TAG = "RESTUtility - DBHelper";

	/**
	 *
	 * @param context
	 * @param name
	 */
	public DBHelper(Context context, String name) {
		this(context, name, null, 1);
	}

	/**
	 *
	 * @param context
	 * @param name
	 * @param factory
	 * @param version
	 */
	public DBHelper(Context context, String name, SQLiteDatabase.CursorFactory factory, int version) {
		super(context, name, factory, version);
	}

	@Override
	public void onCreate(SQLiteDatabase db) {
		Log.d(TAG, "DB OnCreate");

		db.beginTransaction();

		try {
			for (Class<? extends ManagedObject> managedObjectClass : ManagedObjectUtils.managedObjectClasses()) {
				db.execSQL(ManagedObjectUtils.createTableDefinition(managedObjectClass));
			}

			db.setTransactionSuccessful();
		}
		finally {
			db.endTransaction();
		}
	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

	}
}
