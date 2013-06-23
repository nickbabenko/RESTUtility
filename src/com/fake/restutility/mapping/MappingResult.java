package com.fake.restutility.mapping;

import android.util.Log;
import com.fake.restutility.db.Query;
import com.fake.restutility.db.QueryResult;
import com.fake.restutility.object.Column;
import com.fake.restutility.object.Entity;
import com.fake.restutility.object.ManagedObject;
import com.fake.restutility.object.Relation;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.*;

/**
 * Created by nickbabenko on 14/06/13.
 */
public class MappingResult {

	private static final String TAG = "RESTUtility - MappingResult";

	private Class<? extends ManagedObject> entityClass;
	private ManagedObject managedObject;

	private JSONObject jsonObject;
	private JSONArray jsonArray;

	private List<ManagedObject> objects = new ArrayList<ManagedObject>();

	private boolean isRelation = false;

	public MappingResult(Class<? extends ManagedObject> entityClass, JSONObject object) {
		this(entityClass, object, false);
	}

	public MappingResult(Class<? extends ManagedObject> entityClass, JSONObject object, boolean isRelation) {
		this.entityClass 	= entityClass;
		this.jsonObject 	= object;
		this.isRelation 	= isRelation;

		loadManagedObject();
		JSONToManagedObjects();
	}

	public MappingResult(Class<? extends ManagedObject> entityClass, JSONArray array) {
		this(entityClass, array, false);
	}

	public MappingResult(Class<? extends ManagedObject> entityClass, JSONArray array, boolean isRelation) {
		this.entityClass 	= entityClass;
		this.jsonArray 		= array;
		this.isRelation 	= isRelation;

		loadManagedObject();
		JSONToManagedObjects();
	}

	public MappingResult(Class<? extends ManagedObject> entityClass, InputStream inputStream) {
		this.entityClass = entityClass;		 // Store the class of the object

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


	/* Parsing Methods */

	private void loadJSONObject(InputStream inputStream) {
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
		//new Query(Query.Type.BeginTransaction).execute();

		try {
			if(jsonObject != null) {
				Entity entity = managedObject.entity();

				if(entity == null)
					return;

				if(entity.keyPath() != "" && isRelation == false) {
					String[] keyPathSplit = (entity.keyPath().indexOf(".") == -1 ? new String[] { entity.keyPath() } : entity.keyPath().split("."));

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

							return;
						} // Doesn't exist, ignore it
					}
				}

				JSONObjectToManagedObject(jsonObject);
			}
			else if(jsonArray != null) {
				JSONArrayToManagedObjects(jsonArray);
			}

			//new Query(Query.Type.TransactionSuccessful).execute();
		}
		finally {
			//new Query(Query.Type.EndTransaction).execute();
		}
	}

	private void JSONArrayToManagedObjects(JSONArray array) {
		for(int i=0; i<array.length(); i++) {
			try {
				JSONObjectToManagedObject(array.getJSONObject(i));
			}
			catch (JSONException e) {}
		}
	}

	private void JSONObjectToManagedObject(JSONObject object) {
		/* Process columns */

		Column primaryColumn;
		Query query 					= new Query().from(managedObject);
		Iterator<Column> columnIterator = managedObject.columns().iterator();
		int inc 						= 0;

		while(columnIterator.hasNext()) {
			Column column 		= columnIterator.next();
			Field field			= managedObject.columnFields().get(inc);
			String keyPath 		= (column.keyPath() == "" ? field.getName() : column.keyPath());

			try {
				Object columnValue	= object.get(keyPath);

				if(field.getType().equals(Date.class) == true) {
					Long _columnValue;

					if(columnValue.getClass().equals(String.class))
						_columnValue = Long.valueOf((String)columnValue);
					else
						_columnValue = (Long)columnValue;

					Date date = new Date(dateFormatter(_columnValue, column.dateFormat()));

					field.set(managedObject, date);
				}
				else if(field.getType().equals(Calendar.class) == true) {
					Long _columnValue;

					if(columnValue.getClass().equals(String.class))
						_columnValue = Long.valueOf((String)columnValue);
					else
						_columnValue = (Long)columnValue;

					Calendar calendar = new GregorianCalendar();

					calendar.setTimeInMillis(dateFormatter(_columnValue, column.dateFormat()));

					field.set(managedObject, calendar);
				}
				else if(field.getType().equals(float.class))
					if(columnValue.getClass().equals(String.class))
						field.setFloat(managedObject, Float.valueOf((String)columnValue));
					else if(columnValue.getClass().equals(Double.class))
						field.setFloat(managedObject, ((Double)columnValue).floatValue());
					else
						field.setFloat(managedObject, (Float)columnValue);
				else {
					field.set(managedObject, columnValue);
				}
			}
			catch(Exception e) {
				Log.d(TAG, "Setting column value from response: " + e.getMessage());
			} // Ignore value if doesn't exist in json response

			inc++;
		}

		String primaryColumnName = managedObject.primaryKeyName();

		try {
			Object primaryKeyValue = object.get(primaryColumnName);

			ManagedObject existingObject = new Query(Query.Type.Select)
				.where(primaryColumnName, "=", primaryKeyValue)
				.from(managedObject)
				.limit(1)
				.execute()
				.current();

			if(existingObject != null) {
				Log.d(TAG, "Mapping object (" + existingObject.getClass().getName() + ") from existing local object mapped by " + primaryColumnName + " = " + primaryKeyValue);

				query.type(Query.Type.Update)
					 .where(primaryColumnName, "=", primaryKeyValue);
			}
			else {
				Log.d(TAG, "Creating new object with remote identifier: " + primaryKeyValue);

				query.type(Query.Type.Insert);
			}
		}
		catch (Exception e) {
			e.printStackTrace();

			Log.d(TAG, "Creating new object");

			query.type(Query.Type.Insert);
		}


		/* Process relations */

		Iterator<Relation> relationIterator = managedObject.relations().iterator();
		int relationInc 					= 0;

		while(relationIterator.hasNext()) {
			Relation relation 				= relationIterator.next();
			Field field						= managedObject.relationFields().get(relationInc);
			String keyPath 					= (relation.keyPath() == "" ? field.getName() : relation.keyPath());
			MappingResult relationResult	= null;

			try {
				try {
					relationResult = new MappingResult((Class<? extends ManagedObject>) (field.getType().isArray() ? field.getType().getComponentType() : field.getType()), object.getJSONArray(keyPath), true);

					field.set(managedObject, relationResult.array());
				}
				catch(JSONException e1) {
					try {
						relationResult = new MappingResult((Class<? extends ManagedObject>) field.getType(), object.getJSONObject(keyPath), true);

						field.set(managedObject, relationResult.firstObject());
					}
					catch(JSONException e2) {
						Log.d(TAG, "Setting relation value from response: " + e2.getMessage());
					}
				}
			}
			catch(Exception e) {
				e.printStackTrace();
			}
		}


		/* Create record */

		try {
			QueryResult result = query.execute();

			Log.d(TAG, "Objects: " + objects + " - " + result);

			objects.add(result.current());

			Log.d(TAG, "Current: " + result.current());
			Log.d(TAG, "Object: " + objects.get(0));
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}

	private long dateFormatter(Long value, Column.DateFormat dateFormat) {
		switch(dateFormat) {
			case Unix:
				value = (value * 1000);
				break;
		}

		return value;
	}


	/* Data Utility Methods */

	public <T extends ManagedObject> T[] array() {
		return objects.toArray((T[]) Array.newInstance(managedObject.getClass(), objects.size()));
	}

	public ManagedObject firstObject() {
		return (count() >= 1 ? objects.get(0) : null);
	}

	public int count() {
		return objects.size();
	}

}