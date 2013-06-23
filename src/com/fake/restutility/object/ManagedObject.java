package com.fake.restutility.object;

import android.content.ContentValues;
import android.database.Cursor;
import android.util.Log;
import com.fake.restutility.db.Query;
import com.fake.restutility.db.QueryResult;
import com.fake.restutility.exception.PrimaryKeyNotDefinedException;
import com.fake.restutility.rest.ObjectManager;
import com.fake.restutility.rest.ObjectManager.ObjectRequestListener;
import org.apache.http.message.BasicNameValuePair;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.*;

/**
 * Created by nickbabenko on 14/06/13.
 */
public class ManagedObject {

	private static final String TAG = "RESTUtility - ManagedObject";


	/* DB References */

	public enum SQLiteType {
		INTEGER, REAL, TEXT, BLOB
	}

	private static final HashMap<Class<?>, SQLiteType> TYPE_MAP = new HashMap<Class<?>, SQLiteType>() {
		{
			put(byte.class, SQLiteType.INTEGER);
			put(short.class, SQLiteType.INTEGER);
			put(int.class, SQLiteType.INTEGER);
			put(long.class, SQLiteType.INTEGER);
			put(float.class, SQLiteType.REAL);
			put(double.class, SQLiteType.REAL);
			put(boolean.class, SQLiteType.INTEGER);
			put(char.class, SQLiteType.TEXT);
			put(byte[].class, SQLiteType.BLOB);
			put(Byte.class, SQLiteType.INTEGER);
			put(Short.class, SQLiteType.INTEGER);
			put(Integer.class, SQLiteType.INTEGER);
			put(Long.class, SQLiteType.INTEGER);
			put(Float.class, SQLiteType.REAL);
			put(Double.class, SQLiteType.REAL);
			put(Boolean.class, SQLiteType.INTEGER);
			put(Character.class, SQLiteType.TEXT);
			put(String.class, SQLiteType.TEXT);
			put(Byte[].class, SQLiteType.BLOB);
			put(Date.class, SQLiteType.INTEGER);
			put(Calendar.class, SQLiteType.INTEGER);
		}
	};


	/* Instance Variables */

	private Entity entity;
	private Column primaryColumn;
	private Field primaryKeyField;
	private String[] columnNames;
	private ArrayList<Column> columns;
	private ArrayList<Field> columnFields;
	private ArrayList<Relation> relations;
	private ArrayList<Field> relationFields;


	/* Construct */

	public ManagedObject() {
		super();

		parseEntityAnnotations();

		try {
			parseAnnotations();
		}
		catch (PrimaryKeyNotDefinedException e) {
			e.printStackTrace();
		}
	}


	/* Mapping Getters */

	public Entity entity() {
		return entity;
	}

	public ArrayList<Column> columns() {
		return columns;
	}

	public ArrayList<Field> columnFields() {
		return columnFields;
	}

	public ArrayList<Relation> relations() {
		return relations;
	}

	public ArrayList<Field> relationFields() {
		return relationFields;
	}

	public Column primaryColumn() {
		return primaryColumn;
	}

	public Field primaryKeyField() {
		return primaryKeyField;
	}

	public String primaryKeyName() {
		return _primaryKeyName();
	}

	public Object primaryKeyValue() {
		if(primaryKeyField != null) {
			try {
				return primaryKeyField.get(this);
			}
			catch (IllegalAccessException e) {
				e.printStackTrace();
			}
		}

		return null;
	}

	public String resourcePath() {
		return (entity != null ? entity.resourcePath() : null);
	}

	private String _primaryKeyName() {
		return (primaryColumn != null ? (primaryColumn.name() == "" ? primaryKeyField.getName() : primaryColumn.name()) : "id");
	}

	private void parseEntityAnnotations() {
		if(entity != null)
			return;

		entity = getClass().getAnnotation(Entity.class);
	}


	/* Annotation Parsing Methods */

	/**
	 * Takes the current ManagedObject superclass and creates references of its columns and relations
	 *
	 * @throws Exception
	 */
	private void parseAnnotations() throws PrimaryKeyNotDefinedException {
		if(columns != null && relations != null)
			return;

		// Get an array of this managed objects annotations
		Field[] fields 					= getClass().getFields();

		// Instantiate column and relation arrays
		columns 						= new ArrayList<Column>();
		relations						= new ArrayList<Relation>();
		columnFields					= new ArrayList<Field>();
		relationFields					= new ArrayList<Field>();

		// Iterate annotations
		for(Field field : fields) {
			Annotation[] annotations 	= field.getAnnotations();

			for(Annotation annotation : annotations) {
				// If the current annotation is marked as a column
				if(annotation.annotationType().equals(Column.class)) {
					columns.add((Column) annotation); // Add to the columns array
					columnFields.add(field);

					// If the current annotation is marked as the primary key
					if(((Column) annotation).primaryKey()) {
						primaryColumn 	= (Column) annotation; // Keep a reference to it
						primaryKeyField = field;
					}
				}
				// If the current annotation is marked as a relation
				else if(annotation.annotationType().equals(Relation.class)) {
					relations.add((Relation) annotation); // Add it to the relations array
					relationFields.add(field);
				}
			}
		}

		// Instantiate column name array for query reference using the column array size
		columnNames = new String[(columns.size() + relations.size())];
		int inc = 0;

		Iterator<Column> columnIterator = columns.iterator();
		int columnInc							= 0;

		// Iterate all columns
		while(columnIterator.hasNext()) {
			Column column = columnIterator.next();
			Field columnField = columnFields.get(columnInc);

			columnNames[inc] = (column.name() == "" ? columnField.getName() : column.name()); // Append to column name array

			columnInc++;
			inc++;
		}

		// Iterate all relations
		Iterator<Relation> relationIterator = relations.iterator();
		int relationInc						= 0;

		while(relationIterator.hasNext()) {
			Relation relation = relationIterator.next();
			Field relationField	= relationFields.get(relationInc);

			// If the field is an array, it's a one-to-many relation type.
			// These aren't loaded from the current table, so we don't need to load it
			// Or we have a separate field to reference this relation with
			if(relationField.getType().isArray() ||
			   relation.connectedBy() != "")
				continue;

			columnNames[inc] = (relation.name() == "" ? relationField.getName() : relation.name());

			relationInc++;
			inc++;
		}
	}


	/* SQLite Helpers */

	public String[] columnDefinitions() {
		List<String> definitionList				= new ArrayList<String>();
		Iterator<Column> columnIterator 		= columns.iterator();
		int inc									= 0;

		while(columnIterator.hasNext()) {
			Column column 						= columnIterator.next();
			Field field							= columnFields.get(inc);

			definitionList.add(columnDefinition(column, field));

			inc++;
		}

		Iterator<Relation> relationIterator		= relations.iterator();
		int relationInc							= 0;

		while(relationIterator.hasNext()) {
			Relation relation					= relationIterator.next();
			Field relationField					= relationFields.get(relationInc);

			// If the field is an array, it's a one-to-many relation type.
			// These aren't loaded from the current table, so we don't need to create a column
			// Or if we have a seperate field to map this relation with, don't create it
			if(relationField.getType().isArray() ||
			   relation.connectedBy() != "")
				continue;

			definitionList.add(relationDefinition(relation, relationField));

			relationInc++;
		}

		if(primaryColumn == null)
			definitionList.add(_primaryKeyName() + " " + TYPE_MAP.get(Integer.class).toString() + " PRIMARY KEY AUTOINCREMENT");

		return definitionList.toArray(new String[definitionList.size()]);
	}

	private String columnDefinition(Column column, Field field) {
		String definition 		= null;
		String columnName		= (column.name() == "" ? field.getName() : column.name());

		if (TYPE_MAP.containsKey(field.getType()))
			definition = columnName + " " + TYPE_MAP.get(field.getType()).toString();
		else if(field.getType().isEnum())
			definition = columnName + " " + TYPE_MAP.get(Integer.class).toString();

		if (definition != null) {
			if (column.length() > -1)
				definition += "(" + column.length() + ")";

			if (column.primaryKey())
				definition += " PRIMARY KEY" + (column.autoIncrement() == true ? " AUTOINCREMENT" : "");
		}

		return definition;
	}

	private String relationDefinition(Relation relation, Field field) {
		String columnName		= (relation.name() == "" ? field.getName() : relation.name());
		String definition 		= columnName + " " + TYPE_MAP.get(Integer.class).toString();

		return definition;
	}

	public void setFromCursor(Cursor cursor) {

		Log.d(TAG, "Set from cursor");

		// Iterate and populate data columns
		Iterator<Column> columnIterator 	= columns.iterator();
		int columnInc 						= 0;

		while(columnIterator.hasNext()) {
			Column column 		= columnIterator.next();
			Field field			= columnFields.get(columnInc);
			String columnName	= (column.name() == "" ? field.getName() : column.name());
			int columnIndex 	= cursor.getColumnIndex(columnName);

			if(columnIndex == -1) {
				columnInc++;

				continue;
			}

			try {
				if(field.getType().equals(String.class)) {
					field.set(this, cursor.getString(columnIndex));
				}
				else if(field.getType().equals(Integer.class) || field.getType().equals(int.class)) {
					field.set(this, cursor.getInt(columnIndex));
				}
				else if(field.getType().equals(Double.class)) {
					field.set(this, cursor.getDouble(columnIndex));
				}
				else if(field.getType().equals(Float.class)) {
					field.set(this, cursor.getFloat(columnIndex));
				}
				else if(field.getType().equals(Date.class)) {
					field.set(this, new Date(cursor.getLong(columnIndex)));
				}
				else if(field.getType().equals(Calendar.class)) {
					Calendar calendar = new GregorianCalendar();

					calendar.setTimeInMillis(cursor.getLong(columnIndex));

					field.set(this, calendar);
				}
			}
			catch(IllegalAccessException e) {
				e.printStackTrace();
			}

			columnInc++;
		}

		Log.d(TAG, "Load relations");

		// Iterate and populate relation objects
		Iterator<Relation> relationIterator = relations.iterator();
		int relationInc						= 0;

		while(relationIterator.hasNext()) {
			Relation relation	= relationIterator.next();
			Field field			= relationFields.get(relationInc);
			String relationName	= (relation.name() == "" ? field.getName() : relation.name());


			Log.d(TAG, "Load relation: " + relationName + " - " + field.getType());

			// If the field, is an array its a one-to-many relation.
			if(field.getType().isArray()) {
				Log.d(TAG, "Is Array");

				try {
					String connectedBy = (relation.connectedBy() == "" ? field.getName() : relation.connectedBy());

					// Execute a second query for the relation and assign to the field
					QueryResult relatedObjects = Query.select((Class<? extends ManagedObject>) field.getType().getComponentType())
						.where(connectedBy, "=", primaryKeyValue())
						.execute();

					Log.d(TAG, "Load many relation: " + relationName + " - " + relatedObjects.count());

					field.set(this, relatedObjects.results());
				}
				catch (Exception e) {
					e.printStackTrace();
				}
			}

			// Otherwise, it's a one-to-one relation.
			else {
				try {
					int connectedByValue = 0;

					// If we have a seperate field to connect this relation with
					if(relation.connectedBy() != "") {
						// Attempt to load it
						int connectedByIndex = cursor.getColumnIndex(relation.connectedBy());

						// If we have found a column
						if(connectedByIndex > -1)
							connectedByValue = cursor.getInt(connectedByIndex); // Load its value
					}

					// If we didn't load a value, set the current field
					if(connectedByValue == 0) {
						int columnIndex 	= cursor.getColumnIndex(relationName);

						if(columnIndex == -1) {
							relationInc++;

							continue;
						}

						connectedByValue = cursor.getInt(columnIndex);
					}

					// Execute a single record query referencing its identifier from the current field
					field.set(this, Query.find((Class<? extends ManagedObject>) field.getType(), connectedByValue).current());
				}
				catch (IllegalAccessException e) {}
			}

			relationInc++;
		}
	}

	/**
	 *
	 * @return
	 * @throws PrimaryKeyNotDefinedException
	 */
	public String[] columnNameArray() {
		return columnNames;
	}

	public String tableName() {
		parseEntityAnnotations();

		String tableName;

		if(entity == null || entity.table() == "")
			tableName = getClass().getSimpleName();
		else
			tableName = entity.table();

		return tableName;
	}

	public ContentValues contentValues() {
		ContentValues contentValues = new ContentValues();

		// Store columns
		Iterator<Field> columnFieldIterator  = columnFields.iterator();
		int inc = 0;

		while(columnFieldIterator.hasNext()) {
			Field columnField 		= columnFieldIterator.next();
			Column column			= columns.get(inc);
			String columnName		= (column.name() == "" ? columnField.getName() : column.name());
			Object columnValue;

			try {
				columnValue	= columnField.get(this);

				if(columnValue == null) {
					inc++;

					continue;
				}

				if(columnField.getType().equals(String.class))
					contentValues.put(columnName, (String) columnValue);
				else if(columnField.getType().equals(Integer.class))
					contentValues.put(columnName, (Integer) columnValue);
				else if(columnField.getType().equals(int.class))
					contentValues.put(columnName, columnField.getInt(this));
				else if(columnField.getType().equals(Float.class))
					contentValues.put(columnName, (Float) columnValue);
				else if(columnField.getType().equals(Calendar.class)) {
					contentValues.put(columnName, ((Calendar) columnValue).getTimeInMillis());
				}
				else if(columnField.getType().equals(Date.class))
					contentValues.put(columnName, ((Date) columnValue).getTime());
			}
			catch (IllegalAccessException e) {}

			inc++;
		}

		// Store Relations
		Iterator<Field> relationFieldIterator = relationFields.iterator();

		inc = 0;

		while(relationFieldIterator.hasNext()) {
			Field relationField					= relationFieldIterator.next();
			Relation relation					= relations.get(inc);
			String columnName					= (relation.name() == "" ? relationField.getName() : relation.name());
			Object relationValue;

			try {
				relationValue = relationField.get(this);

				if(relationValue == null || ManagedObjectUtils.isSubclassOf(relationField.getType(), ManagedObject.class) == false) {
					inc++;

					continue;
				}

				// If the field is an array, it's a one-to-many relation type.
				// These aren't loaded from the current table, so we save its value
				// Or if we have a separate field to map this relation with, don't use it
				if(relationField.getType().isArray() ||
				   relation.connectedBy() != "") {
					inc++;

					continue;
				}

				contentValues.put(columnName, (Integer) (((ManagedObject) relationValue).primaryKeyValue()));
			}
			catch (IllegalAccessException e) {}

			inc++;
		}

		return contentValues;
	}


	/* Instance Helpers */

	/**
	 *
	 * @return
	 */
	public boolean isNew() {
		try {
			primaryKeyField.get(this);

			return true;
		}
		catch (IllegalAccessException e) {
			return false;
		}
	}


	/* REST Helpers */

	/**
	 *
	 * @param listener
	 */
	public void save(ObjectRequestListener listener) {
		save(listener, null);
	}

	/**
	 *
	 * @param listener
	 * @param parameters
	 */
	public void save(ObjectRequestListener listener, ArrayList<BasicNameValuePair> parameters) {
		try {
			if(isNew())
				ObjectManager.instance().postObject(this, resourcePath(), listener, parameters);
			else
				ObjectManager.instance().putObject(this, resourcePath(), listener, parameters);
		}
		catch(Exception e) {
			Log.d(TAG, "Failed to load ObjectManager instance: " + e.getMessage());
		}
	}

	public boolean saveLocal() {
		try {
			new Query(Query.Type.Update)
				.from(this)
				.execute();

			return true;
		}
		catch (Exception e) {
			e.printStackTrace();

			return false;
		}
	}

	/**
	 *
	 * @param listener
	 */
	public void delete(ObjectRequestListener listener) {
		delete(listener);
	}

	/**
	 *
	 * @param listener
	 * @param parameters
	 */
	public void delete(ObjectRequestListener listener, ArrayList<BasicNameValuePair> parameters) throws Exception {
		ObjectManager.instance().deleteObject(this, resourcePath(), listener, parameters);
	}

}