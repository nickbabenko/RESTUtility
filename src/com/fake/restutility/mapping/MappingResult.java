package com.fake.restutility.mapping;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.text.TextUtils;
import com.fake.restutility.db.Query;
import com.fake.restutility.db.QueryResult;
import com.fake.restutility.object.*;
import com.fake.restutility.util.Log;
import com.xdesign.sumoinsight.model.UserProfile;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.lang.reflect.Field;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Created by nickbabenko on 14/06/13.
 */
public class MappingResult {

	private static final String TAG = "RESTUtility - MappingResult";

	private Class<? extends ManagedObject> entityClass;
	private ManagedObject managedObject;
	private String keyPath;

	private JSONObject jsonObject;
	private JSONArray jsonArray;

	private List<ManagedObject> objects = new ArrayList<ManagedObject>();
	private Cursor cursor;
	private ArrayList<String> objectReferences = new ArrayList<String>();
	private HashMap<Integer, ManagedObject> resultCache = new HashMap<Integer, ManagedObject>();

	private SQLiteDatabase database;

	private boolean isRelation = false;
	public int count = 0;

	public MappingResult() {

	}

	public MappingResult(Class<? extends ManagedObject> entityClass, JSONObject object) {
		this(entityClass, object, false);
	}

	public MappingResult(Class<? extends ManagedObject> entityClass, JSONObject object, boolean isRelation) {
		this(entityClass, object, isRelation, null);
	}

	public MappingResult(Class<? extends ManagedObject> entityClass, JSONObject object, boolean isRelation, SQLiteDatabase database) {
		this.entityClass 	= entityClass;
		this.jsonObject 	= object;
		this.isRelation 	= isRelation;
		this.database		= database;

		loadManagedObject();
		JSONToManagedObjects();
	}

	public MappingResult(Class<? extends ManagedObject> entityClass, JSONArray array) {
		this(entityClass, array, false);
	}

	public MappingResult(Class<? extends ManagedObject> entityClass, JSONArray array, boolean isRelation) {
		this(entityClass, array, isRelation, null);
	}

	public MappingResult(Class<? extends ManagedObject> entityClass, JSONArray array, boolean isRelation, SQLiteDatabase database) {
		this.entityClass 	= entityClass;
		this.jsonArray 		= array;
		this.isRelation 	= isRelation;
		this.database		= database;

		loadManagedObject();
		JSONToManagedObjects();
	}

	public MappingResult(Class<? extends ManagedObject> entityClass, InputStream inputStream, String keyPath) {
		this(entityClass, inputStream, keyPath, null);
	}

	public MappingResult(Class<? extends ManagedObject> entityClass, InputStream inputStream, String keyPath, SQLiteDatabase database) {
		this.entityClass 	= entityClass;		 // Store the class of the object
		this.keyPath		= keyPath;
		this.database		= database;

		loadManagedObject();
		loadJSONObject(inputStream);
		JSONToManagedObjects();
	}

	private void loadManagedObject() {
		try {
			managedObject = entityClass.newInstance();
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}


	/* Getters / Setters */

	public Cursor getCursor() {
		return cursor;
	}


	/* Parsing Methods */

	private void loadJSONObject(InputStream inputStream) {
		if(inputStream == null)
			return;

		BufferedReader streamReader;

		try {
			streamReader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"));
		}
		catch (UnsupportedEncodingException e) {
			return;
		}

		StringBuilder responseStrBuilder = new StringBuilder();

		String inputStr;

		try {
			while ((inputStr = streamReader.readLine()) != null)
				responseStrBuilder.append(inputStr);
		}
		catch (IOException e) {
			e.printStackTrace();
		}

		Log.d(TAG, "Response string: " + responseStrBuilder.toString());

		try {
			jsonObject = new JSONObject(responseStrBuilder.toString());
		}
		catch (JSONException e) {
			try {
				jsonArray = new JSONArray(responseStrBuilder.toString());
			}
			catch (JSONException e1) {}
		}

		if(jsonArray != null)
			Log.d(TAG, "JSON array received: " + jsonArray.toString());
		else if(jsonObject != null)
			Log.d(TAG, "JSON object Received: " + jsonObject.toString());
		else
			Log.d(TAG, "Invalid JSON received");
	}

	private void JSONToManagedObjects() {
		if(jsonObject != null) {
			Entity entity = managedObject.entity();

			if(entity == null)
				return;

			if(keyPath != null && keyPath.equals("") == false && isRelation == false) {
				String[] keyPathSplit = (keyPath.indexOf(".") == -1 ? new String[] { keyPath } : keyPath.split("."));

				for(int i=0; i<keyPathSplit.length; i++) {
					try {
						jsonObject = jsonObject.getJSONObject(keyPathSplit[i]);
					}
					catch (JSONException e) {
						try {
							JSONArray array = jsonObject.getJSONArray(keyPathSplit[i]);

							JSONArrayToManagedObjects(array);
						}
						catch (JSONException e1) {
							e1.printStackTrace();
						}
					} // Doesn't exist, ignore it
				}
			}

			JSONObjectToManagedObject(jsonObject);
		}
		else if(jsonArray != null) {
			JSONArrayToManagedObjects(jsonArray);
		}

		Log.d(TAG, "Total results: " + managedObject.tableName() + " - " + objectReferences.size());

		//loadCursorForManagedObjectReferences();
	}

	private void JSONArrayToManagedObjects(JSONArray array) {
		for(int i=0; i<array.length(); i++) {
			try {
				JSONObjectToManagedObject(array.getJSONObject(i));
			}
			catch (JSONException e) {
				e.printStackTrace();
			}
		}
	}

	private void JSONObjectToManagedObject(JSONObject object) {
		resetFields();

		Query query 				= new Query().from(managedObject);
		String primaryColumnName 	= managedObject.primaryKeyName();
		Object primaryKeyValue		= null;

		query.database(database)
			 .type(Query.Type.Replace);

		try {
			primaryKeyValue = object.get(primaryColumnName);
			Object cachedIdentifier;

			if((cachedIdentifier = MappingCache.getObject(managedObject.tableName(), primaryKeyValue)) != null) {
				Log.d(TAG, "From cache: " + managedObject.tableName() + " - " + primaryKeyValue + " - " + cachedIdentifier);
				if(objectReferences.contains("" + cachedIdentifier) == false)
					objectReferences.add("" + cachedIdentifier);

				return;
			}
		}
		catch (Exception e) { }

		/* Process columns */

		Iterator<Field> columnFieldIterator = managedObject.columnFields().iterator();
		int inc 							= 0;
		int set								= 0;
		ContentValues contentValues			= new ContentValues();

		while(columnFieldIterator.hasNext()) {
			Field columnField 		= columnFieldIterator.next();
			Column column			= columnField.getAnnotation(Column.class);
			String columnName		= (column.name() == "" ? columnField.getName() : column.name());
			String keyPath 			= (column.keyPath() == "" ? columnField.getName() : column.keyPath());

			try {
				Object columnValue	= object.get(keyPath);

				if(columnField.getType().equals(Date.class) == true) {
					Date date = new Date(dateFormatter(""+columnValue, column.dateFormat()));

					contentValues.put(columnName, (date.getTime() / 1000));
				}
				else if(columnField.getType().equals(Calendar.class) == true) {
					Calendar calendar = new GregorianCalendar();

					calendar.setTimeInMillis(dateFormatter(""+columnValue, column.dateFormat()));

					contentValues.put(columnName, (calendar.getTimeInMillis() / 1000));
				}
				else if(columnField.getType().equals(int.class))
					if(columnValue.getClass().equals(String.class))
						contentValues.put(columnName, Integer.valueOf((String) columnValue));
					else
						contentValues.put(columnName, (Integer) columnValue);
				else if(columnField.getType().equals(Integer.class)) {
					if(columnValue.getClass().equals(String.class))
						contentValues.put(columnName, Integer.valueOf((String) columnValue));
					else
						contentValues.put(columnName, (Integer) columnValue);
				}
				else if(columnField.getType().equals(float.class) ||
						columnField.getType().equals(Float.class)) {
					if(columnValue.getClass().equals(String.class))
						contentValues.put(columnName, Float.valueOf((String)columnValue));
					else
						contentValues.put(columnName, (Float) columnValue);
				}
				else if(columnField.getType().equals(double.class) ||
						columnField.getType().equals(Double.class)) {
					if(columnValue.getClass().equals(String.class))
						contentValues.put(columnName, Double.valueOf((String) columnValue));
					else if(columnValue.getClass().equals(Double.class))
						contentValues.put(columnName, (Double) columnValue);
					else if(columnValue.getClass().equals(Integer.class))
						contentValues.put(columnName, ((Integer)columnValue).doubleValue());
					else
						contentValues.put(columnName, (Double) columnValue);
				}
				else if(columnField.getType().equals(String.class))
					contentValues.put(columnName, (String) columnValue);
				else if(columnField.getType().equals(Boolean.class))
					contentValues.put(columnName, (Boolean) columnValue);

				set++;
			}
			catch(Exception e) {
				Log.d(TAG, "Setting column value from response: " + e.getMessage());
			} // Ignore value if doesn't exist in json response

			inc++;
		}

		// If the record doesn't exist
		if(set > 0 && (query.type() == Query.Type.Insert || query.type() == Query.Type.Replace) && managedObject.entity().relationsFirst() == false) {
			query.contentValues(contentValues);
			query.execute(); // Create it

			objectReferences.add(""+managedObject.primaryKeyValue(contentValues));

			set = 0;
		}

		/* Process relations */

		Iterator<Field> relationFieldIterator = managedObject.relationFields().iterator();

		while(relationFieldIterator.hasNext()) {
			Field field						= relationFieldIterator.next();
			Relation relation				= field.getAnnotation(Relation.class);
			String columnName				= (relation.name() == "" ? field.getName() : relation.name());
			String keyPath 					= (relation.keyPath() == "" ? field.getName() : relation.keyPath());
			MappingResult relationResult;

			try {
				try {
					if(field.getType().equals(QueryResult.class) &&
					   (relation.model().equals(ManagedObject.class) ||
						ManagedObjectUtils.isSubclassOf(relation.model(), ManagedObject.class) == false)) {
						Log.d(TAG, "One-To-Many relations must have a model defined and the class must subclass ManagedObject");

						continue;
					}

					new MappingResult((Class<? extends ManagedObject>) (field.getType().equals(QueryResult.class) ? relation.model() : field.getType()), object.getJSONArray(keyPath), true, database);

					set++;
				}
				catch(JSONException e1) {
					try {
						relationResult = new MappingResult((Class<? extends ManagedObject>) field.getType(), object.getJSONObject(keyPath), true, database);

						contentValues.put(columnName, relationResult.getObjectReferences().get(0));

						set++;
					}
					catch(JSONException e2) {}
				}
			}
			catch(Exception e) {
				e.printStackTrace();
			}
		}

		// If we've set no values, don't bother saving
		if(set == 0)
			return;

		/* Create record */
		try {
			query.contentValues(contentValues);
			query.execute();

			MappingCache.putObject(managedObject.tableName(), managedObject.primaryKeyValue(contentValues));

			if(!objectReferences.contains(""+managedObject.primaryKeyValue(contentValues)))
				objectReferences.add(""+managedObject.primaryKeyValue(contentValues));
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void resetFields() {
		try {
			managedObject = managedObject.getClass().newInstance();
		} catch (Exception e) {}
	}

	private long dateFormatter(String value, Column.DateFormat dateFormat) {
		long storeValue = 0;
		SimpleDateFormat format;

		switch(dateFormat) {
			case Unix:
				storeValue = (Long.valueOf(value) * 1000);
				break;
			case MySql_Date:
				format = new SimpleDateFormat("yyyy-MM-dd");

				try {
					storeValue = format.parse(value).getTime();
				} catch (ParseException e) {}
				break;
			case MySql_DateTime:
				format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

				try {
					storeValue = format.parse(value).getTime();
				} catch (ParseException e) {}
				break;
			case Default:
			default:
				format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZZZZZ");

				try {
					storeValue = format.parse(value).getTime();
				} catch (ParseException e) { e.printStackTrace(); }
				break;
		}

		return storeValue;
	}

	public MappingResult loadCursorForManagedObjectReferences() {
		if(objectReferences.size() == 0)
			return this;

		cursor = Query.select(entityClass)
				.raw("SELECT * FROM " + managedObject.tableName() + " WHERE " + managedObject.primaryKeyName() + " IN (?)", new String[] { TextUtils.join(", ", objectReferences.toArray(new String[objectReferences.size()])) })
				.execute()
				.getCursor();

		return this;
	}


	/* Data Utility Methods */

	public ArrayList<String> getObjectReferences() {
		return objectReferences;
	}

	public Object firstPrimaryValue() {
		if(managedObject.getClass().equals(UserProfile.class)) {
			Log.d(TAG, "First primary for user profile: " + count() + " - " + cursor, true);
		}

		if(count() == 0)
			return null;

		cursor.moveToPosition(0);

		int index = cursor.getColumnIndex(managedObject.primaryKeyName());

		if(managedObject.getClass().equals(UserProfile.class)) {
			Log.d(TAG, "First primary for user profile: " + index, true);
		}

		if(managedObject.primaryKeyField().getType().equals(Integer.class))
			return cursor.getInt(index);

		return null;
	}

	public ManagedObject firstObject() {
		return (count() >= 1 ? object(0) : null);
	}

	public int count() {
		return (cursor == null ? count : cursor.getCount());
	}

	public ManagedObject object(int inc) {
		try {
			if(resultCache.containsKey(new Integer(inc)))
				return resultCache.get(new Integer(inc));

			ManagedObject result = managedObject.getClass().newInstance();

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