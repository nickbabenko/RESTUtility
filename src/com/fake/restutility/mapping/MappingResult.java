package com.fake.restutility.mapping;

import com.fake.restutility.db.Query;
import com.fake.restutility.db.QueryResult;
import com.fake.restutility.object.*;
import com.fake.restutility.util.Log;
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
	private String keyPath;

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

	public MappingResult(Class<? extends ManagedObject> entityClass, InputStream inputStream, String keyPath) {
		this.entityClass 	= entityClass;		 // Store the class of the object
		this.keyPath		= keyPath;

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

		Log.d(TAG, "Response string: " + responseStrBuilder.toString(), true);

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

				if(keyPath != null && isRelation == false) {
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
			catch (JSONException e) {
				e.printStackTrace();
			}
		}
	}

	private void JSONObjectToManagedObject(JSONObject object) {
		Query query 				= new Query().from(managedObject);
		String primaryColumnName 	= managedObject.primaryKeyName();

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

				managedObject = existingObject;

				query.from(managedObject)
					 .type(Query.Type.Update)
					 .where(primaryColumnName, "=", primaryKeyValue);
			}
			else {
				Log.d(TAG, "Creating new object with remote identifier: " + primaryKeyValue);

				query.type(Query.Type.Insert);
			}
		}
		catch (Exception e) {
			//.printStackTrace();

			Log.d(TAG, "Creating new object");

			query.type(Query.Type.Insert);
		}

		/* Process columns */

		Iterator<Field> columnFieldIterator = managedObject.columnFields().iterator();
		int inc 							= 0;
		int set								= 0;

		while(columnFieldIterator.hasNext()) {
			Field columnField 		= columnFieldIterator.next();
			Column column			= columnField.getAnnotation(Column.class);
			String keyPath 			= (column.keyPath() == "" ? columnField.getName() : column.keyPath());

			try {
				Object columnValue	= object.get(keyPath);

				if(columnField.getType().equals(Date.class) == true) {
					Long _columnValue;

					if(columnValue.getClass().equals(String.class))
						_columnValue = Long.valueOf((String)columnValue);
					else
						_columnValue = (Long)columnValue;

					Date date = new Date(dateFormatter(_columnValue, column.dateFormat()));

					columnField.set(managedObject, date);
				}
				else if(columnField.getType().equals(Calendar.class) == true) {
					Long _columnValue;

					if(columnValue.getClass().equals(String.class))
						_columnValue = Long.valueOf((String)columnValue);
					else
						_columnValue = (Long)columnValue;

					Calendar calendar = new GregorianCalendar();

					calendar.setTimeInMillis(dateFormatter(_columnValue, column.dateFormat()));

					columnField.set(managedObject, calendar);
				}
				else if(columnField.getType().equals(int.class))
					if(columnValue.getClass().equals(String.class))
						columnField.setInt(managedObject, Integer.valueOf((String) columnValue));
					else
						columnField.setInt(managedObject, (Integer) columnValue);
				else if(columnField.getType().equals(Integer.class)) {
					if(columnValue.getClass().equals(String.class))
						columnField.set(managedObject, Integer.valueOf((String) columnValue));
					else
						columnField.set(managedObject, columnValue);
				}
				else if(columnField.getType().equals(float.class) ||
						columnField.getType().equals(Float.class)) {
					if(columnValue.getClass().equals(String.class))
						columnField.setFloat(managedObject, Float.valueOf((String)columnValue));
					else if(columnValue.getClass().equals(Float.class))
						columnField.set(managedObject, columnValue);
					else
						columnField.setFloat(managedObject, (Float)columnValue);
				}
				else if(columnField.getType().equals(double.class) ||
						columnField.getType().equals(Double.class)) {
					if(columnValue.getClass().equals(String.class))
						columnField.setDouble(managedObject, Double.valueOf((String) columnValue));
					else if(columnValue.getClass().equals(Double.class))
						columnField.set(managedObject, columnValue);
					else
						columnField.setDouble(managedObject, (Double) columnValue);
				}
				else
					columnField.set(managedObject, columnValue);

				set++;
			}
			catch(Exception e) {
				Log.d(TAG, "Setting column value from response: " + e.getMessage());
			} // Ignore value if doesn't exist in json response

			inc++;
		}


		/* Process relations */

		Iterator<Field> relationFieldIterator = managedObject.relationFields().iterator();

		while(relationFieldIterator.hasNext()) {
			Field field						= relationFieldIterator.next();
			Relation relation				= field.getAnnotation(Relation.class);
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

					relationResult = new MappingResult((Class<? extends ManagedObject>) (field.getType().equals(QueryResult.class) ? relation.model() : field.getType()), object.getJSONArray(keyPath), true);

					field.set(managedObject, relationResult.array());

					set++;
				}
				catch(JSONException e1) {
					try {
						relationResult = new MappingResult((Class<? extends ManagedObject>) field.getType(), object.getJSONObject(keyPath), true);

						field.set(managedObject, relationResult.firstObject());

						set++;
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

		// If we've set no values, don't bother saving
		if(set == 0)
			return;


		/* Create record */

		try {
			QueryResult result = query.execute();

			objects.add(result.current());
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