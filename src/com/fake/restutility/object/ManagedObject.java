package com.fake.restutility.object;

import android.content.ContentValues;
import android.database.Cursor;
import com.fake.restutility.db.Query;
import com.fake.restutility.db.QueryResult;
import com.fake.restutility.exception.PrimaryKeyNotDefinedException;
import com.fake.restutility.rest.ObjectManager;
import com.fake.restutility.rest.ObjectManager.ObjectRequestListener;
import com.fake.restutility.util.Log;
import org.apache.http.message.BasicNameValuePair;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
	private ArrayList<String> columnNames;
	private ArrayList<Column> columns;
	private ArrayList<Field> columnFields;
	private HashMap<String, Field> columnFieldsMap;
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
		String resourcePath = (entity != null ? entity.resourcePath() : null);

		if(resourcePath != null) {
			Matcher matcher = Pattern.compile("/:(.*?)/").matcher(resourcePath);

			while(matcher.find()) {
				String match = matcher.group();
				String property = match.replace(":", "").substring(1, (match.length() - 2));

				if(columnFieldsMap.containsKey(property) == true) {
					Field field = columnFieldsMap.get(property);

					try {
						resourcePath = resourcePath.replace(match, "/" + field.get(this) + "/");
					}
					catch (IllegalAccessException e) {}
				}
			}
		}

		return resourcePath;
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
		columnFieldsMap					= new HashMap<String, Field>();
		relationFields					= new ArrayList<Field>();

		// Iterate annotations
		for(Field field : fields) {
			Annotation[] annotations 	= field.getAnnotations();

			for(Annotation annotation : annotations) {
				// If the current annotation is marked as a column
				if(annotation.annotationType().equals(Column.class)) {
					columns.add((Column) annotation); // Add to the columns array
					columnFields.add(field);
					columnFieldsMap.put(field.getName(), field);

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
		columnNames 					= new ArrayList<String>();
		int inc 						= 0;

		Iterator<Field> columnFieldIterator = columnFields.iterator();

		// Iterate all columns
		while(columnFieldIterator.hasNext()) {
			Field columnField 		= columnFieldIterator.next();
			Column column			= columnField.getAnnotation(Column.class);

			columnNames.add((column.name() == "" ? columnField.getName() : column.name())); // Append to column name array

			inc++;
		}

		// Iterate all relations
		Iterator<Field> relationFieldIterator = relationFields.iterator();

		inc = 0;

		while(relationFieldIterator.hasNext()) {
			Field relationField	= relationFieldIterator.next();
			Relation relation = relationField.getAnnotation(Relation.class);

			// If the field is an array, it's a one-to-many relation type.
			// These aren't loaded from the current table, so we don't need to load it
			// Or we have a separate field to reference this relation with
			if(relationField.getType().equals(QueryResult.class) ||
			   relation.connectedBy() != "")
				continue;

			columnNames.add((relation.name() == "" ? relationField.getName() : relation.name()));

			inc++;
		}
	}


	/* SQLite Helpers */

	public String[] columnDefinitions() {
		List<String> definitionList				= new ArrayList<String>();
		Iterator<Field> columnFieldIterator 	= columnFields.iterator();

		while(columnFieldIterator.hasNext()) {
			Field columnField 					= columnFieldIterator.next();
			Column column						= columnField.getAnnotation(Column.class);

			definitionList.add(columnDefinition(column, columnField));
		}

		Iterator<Field> relationFieldIterator	= relationFields.iterator();

		while(relationFieldIterator.hasNext()) {
			Field relationField					= relationFieldIterator.next();
			Relation relation					= relationField.getAnnotation(Relation.class);

			// If the field is an array, it's a one-to-many relation type.
			// These aren't loaded from the current table, so we don't need to create a column
			// Or if we have a seperate field to map this relation with, don't create it
			if(relationField.getType().equals(QueryResult.class) ||
			   relation.connectedBy() != "")
				continue;

			definitionList.add(relationDefinition(relation, relationField));
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
		// Iterate and populate data columns
		Iterator<Field> columnFieldIterator 	= columnFields.iterator();

		while(columnFieldIterator.hasNext()) {
			Field field			= columnFieldIterator.next();
			Column column		= field.getAnnotation(Column.class);
			String columnName	= (column.name() == "" ? field.getName() : column.name());
			int columnIndex 	= cursor.getColumnIndex(columnName);

			if(columnIndex == -1 || cursor.isNull(columnIndex))
				continue;

			try {
				if(field.getType().equals(String.class)) {
					field.set(this, cursor.getString(columnIndex));
				}
				else if(field.getType().equals(Integer.class) ||
						field.getType().equals(int.class)) {
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
				else if(field.getType().equals(boolean.class) ||
						field.getType().equals(Boolean.class)) {
					field.set(this, (cursor.getInt(columnIndex) == 1));
				}
			}
			catch(IllegalAccessException e) {
				e.printStackTrace();
			}
		}

		// Iterate and populate relation objects
		Iterator<Field> relationFieldIterator = relationFields.iterator();

		while(relationFieldIterator.hasNext()) {
			Field field			= relationFieldIterator.next();
			Relation relation	= field.getAnnotation(Relation.class);
			String relationName	= (relation.name() == "" ? field.getName() : relation.name());

			// If the field, is an array its a one-to-many relation.
			if(field.getType().equals(QueryResult.class)) {
				if(field.getType().equals(QueryResult.class) &&
						(relation.model().equals(ManagedObject.class) ||
								ManagedObjectUtils.isSubclassOf(relation.model(), ManagedObject.class) == false)) {
					Log.d(TAG, "One-To-Many relations must have a model defined and the class must subclass ManagedObject");

					continue;
				}

				try {
					String connectedBy = (relation.connectedBy() == "" ? field.getName() : relation.connectedBy());

					// Execute a second query for the relation and assign to the field
					QueryResult relatedObjects = Query.select(relation.model())
						.where(connectedBy, "=", primaryKeyValue())
						.execute();

					field.set(this, relatedObjects);
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

						if(columnIndex == -1)
							continue;

						connectedByValue = cursor.getInt(columnIndex);
					}

					// Execute a single record query referencing its identifier from the current field
					field.set(this, Query.find((Class<? extends ManagedObject>) field.getType(), connectedByValue).current());
				}
				catch (IllegalAccessException e) {
					e.printStackTrace();
				}
			}
		}
	}

	/**
	 *
	 * @return
	 * @throws PrimaryKeyNotDefinedException
	 */
	public String[] columnNameArray() {
		return columnNames.toArray(new String[columnNames.size()]);
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

		while(columnFieldIterator.hasNext()) {
			Field columnField 		= columnFieldIterator.next();
			Column column			= columnField.getAnnotation(Column.class);
			String columnName		= (column.name() == "" ? columnField.getName() : column.name());
			Object columnValue;

			try {
				columnValue	= columnField.get(this);

				if(columnValue == null)
					continue;

				if(columnField.getType().equals(String.class))
					contentValues.put(columnName, (String) columnValue);

				else if(columnField.getType().equals(Integer.class))
					contentValues.put(columnName, (Integer) columnValue);

				else if(columnField.getType().equals(Boolean.class))
					contentValues.put(columnName, (Boolean) columnValue);

				else if(columnField.getType().equals(Float.class))
					contentValues.put(columnName, (Float) columnValue);

				else if(columnField.getType().equals(Double.class))
					contentValues.put(columnName, (Double) columnValue);

				else if(columnField.getType().equals(int.class))
					contentValues.put(columnName, columnField.getInt(this));

				else if(columnField.getType().equals(float.class))
					contentValues.put(columnName, (Float) columnValue);

				else if(columnField.getType().equals(Calendar.class))
					contentValues.put(columnName, ((Calendar) columnValue).getTimeInMillis());

				else if(columnField.getType().equals(Date.class))
					contentValues.put(columnName, ((Date) columnValue).getTime());

				else if(columnField.getType().equals(boolean.class))
					contentValues.put(columnName, columnField.getBoolean(this));
			}
			catch (IllegalAccessException e) {}
		}

		// Store Relations
		Iterator<Field> relationFieldIterator = relationFields.iterator();

		while(relationFieldIterator.hasNext()) {
			Field relationField		= relationFieldIterator.next();
			Relation relation		= relationField.getAnnotation(Relation.class);
			String columnName		= (relation.name() == "" ? relationField.getName() : relation.name());
			Object relationValue;

			try {
				relationValue = relationField.get(this);

				if(relationValue == null || ManagedObjectUtils.isSubclassOf(relationField.getType(), ManagedObject.class) == false)
					continue;

				// If the field is an array, it's a one-to-many relation type.
				// These aren't loaded from the current table, so we save its value
				// Or if we have a separate field to map this relation with, don't use it
				if(relationField.getType().equals(QueryResult.class) ||
				   relation.connectedBy() != "")
					continue;

				contentValues.put(columnName, (Integer) (((ManagedObject) relationValue).primaryKeyValue()));
			}
			catch (IllegalAccessException e) {}
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