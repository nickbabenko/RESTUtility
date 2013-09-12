package com.fake.restutility.object;

import android.content.ContentValues;
import android.database.Cursor;
import android.os.Bundle;
import com.fake.restutility.db.Query;
import com.fake.restutility.db.QueryResult;
import com.fake.restutility.exception.PrimaryKeyNotDefinedException;
import com.fake.restutility.rest.ObjectManager;
import com.fake.restutility.rest.ObjectManager.ObjectRequestListener;
import com.fake.restutility.util.Log;
import org.apache.http.message.BasicNameValuePair;

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

	public Object primaryKeyValue(ContentValues contentValues) {
		return contentValues.get(primaryKeyName());
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
		entity = ManagedObjectUtils.getEntityAnnotation(getClass());
	}


	/* Annotation Parsing Methods */

	/**
	 * Takes the current ManagedObject superclass and creates references of its columns and relations
	 *
	 * @throws Exception
	 */
	private void parseAnnotations() throws PrimaryKeyNotDefinedException {
		columns 			= ManagedObjectUtils.columns(getClass());
		columnFields		= ManagedObjectUtils.columnFields(getClass());
		relations			= ManagedObjectUtils.relations(getClass());
		relationFields		= ManagedObjectUtils.relationFields(getClass());
		primaryKeyField		= ManagedObjectUtils.primaryField(getClass());
		primaryColumn		= ManagedObjectUtils.primaryColumn(getClass());
		columnFieldsMap		= ManagedObjectUtils.columnFieldsMap(getClass());
		columnNames			= ManagedObjectUtils.columnNames(getClass());
	}


	/* SQLite Helpers */

	public String[] columnDefinitions() {
		ArrayList<String> definitionList			= new ArrayList<String>();
		Iterator<Field> columnFieldIterator 		= columnFields.iterator();

		while(columnFieldIterator.hasNext()) {
			Field columnField 						= columnFieldIterator.next();
			Column column							= columnField.getAnnotation(Column.class);

			definitionList.add(columnDefinition(column, columnField));
		}

		Iterator<Field> relationFieldIterator		= relationFields.iterator();

		while(relationFieldIterator.hasNext()) {
			Field relationField						= relationFieldIterator.next();
			Relation relation						= relationField.getAnnotation(Relation.class);

			// If the field is an array, it's a one-to-many relation type.
			// These aren't loaded from the current table, so we don't need to create a column
			// Or if we have a separate field to map this relation with, don't create it
			if(relationField.getType().equals(QueryResult.class) ||
			   relation.connectedBy() != "") {
				// Create a to-many foreign key relation from the ID field
				//foreignKeyList.add("FOREIGN KEY (" + _primaryKeyName() + ") REFERENCES " + relationTableName(relation, relationField) + "(" + (relation.connectedBy() == "" ? "id" : relation.connectedBy()) + ")" + onDeleteAction(relation) + onUpdateAction(relation));
				continue;
			}

			definitionList.add(relationDefinition(relation, relationField));
		}

		if(primaryColumn == null)
			definitionList.add(_primaryKeyName() + " " + TYPE_MAP.get(Integer.class).toString() + " PRIMARY KEY AUTOINCREMENT");

		return definitionList.toArray(new String[definitionList.size()]);
	}

	private String columnDefinition(Column column, Field field) {
		String definition 		= null;
		String columnName		= columnName(column, field);

		if (TYPE_MAP.containsKey(field.getType()))
			definition = columnName + " " + TYPE_MAP.get(field.getType()).toString();
		else if(field.getType().isEnum())
			definition = columnName + " " + TYPE_MAP.get(Integer.class).toString();

		if (definition != null) {
			if (column.length() > -1)
				definition += "(" + column.length() + ")";

			if (column.primaryKey())
				definition += " PRIMARY KEY" + (column.autoIncrement() == true ? " AUTOINCREMENT" : "");
			else if(column.unique())
				definition += " UNIQUE";
		}

		if(column.referenceModel() != ManagedObject.class &&
		   column.referenceColumn() != "") {
			definition += " REFERENCES " + Query.instantiate(column.referenceModel()).tableName() + "(" + column.referenceColumn() + ")";

			definition += onUpdateAction(column);
			definition += onDeleteAction(column);
		}

		return definition;
	}

	private String onDeleteAction(Column column) {
		return " ON DELETE " + foreignActionToString(column.onDelete());
	}

	private String onUpdateAction(Column column) {
		if(column.onUpdate() == Column.ForeignAction.Empty)
			return "";

		return " ON UPDATE " + foreignActionToString(column.onUpdate());
	}

	private String foreignActionToString(Column.ForeignAction foreignAction) {
		switch(foreignAction) {
			case NoAction:
				return "NO ACTION";
			case Restrict:
				return "RESTRICT";
			case SetNull:
				return "SET NULL";
			case SetDefault:
				return "SET DEFAULT";
			case Cascade:
				return "CASCADE";
		}

		return "";
	}

	private String columnName(Column column, Field field) {
		return (column.name() == "" ? field.getName() : column.name());
	}

	private String relationDefinition(Relation relation, Field field) {
		String foreignTable		= relationTableName(relation, field);
		String foreignKey		= foreignKey(relation, field);

		String columnName		= (relation.name() == "" ? field.getName() : relation.name());
		String definition 		= columnName + " " + TYPE_MAP.get(Integer.class).toString(); // + " REFERENCES " + foreignTable + "(" + foreignKey + ")" + onDeleteAction(relation) + onUpdateAction(relation);

		return definition;
	}

	private String relationTableName(Relation relation, Field field) {
		ManagedObject object = managedObjectFromRelation(relation, field);

		if(object != null)
			return object.tableName();

		return "";
	}

	private String foreignKey(Relation relation, Field field) {
		ManagedObject object = managedObjectFromRelation(relation, field);

		if(object != null)
			return object.primaryKeyName();

		return "";
	}

	private String onDeleteAction(Relation relation) {
		return " ON DELETE " + foreignActionToString(relation.onDelete());
	}

	private String onUpdateAction(Relation relation) {
		return " ON UPDATE " + foreignActionToString(relation.onUpdate());
	}

	private String foreignActionToString(Relation.ForeignAction foreignAction) {
		switch(foreignAction) {
			case NoAction:
				return "NO ACTION";
			case Restrict:
				return "RESTRICT";
			case SetNull:
				return "SET NULL";
			case SetDefault:
				return "SET DEFAULT";
			case Cascade:
				return "CASCADE";
		}

		return "";
	}

	private ManagedObject managedObjectFromRelation(Relation relation, Field field) {
		Class<? extends ManagedObject> relationModelClass = null;

		if(field.getType().equals(QueryResult.class)) {
			if(relation.model() != ManagedObject.class)
				relationModelClass = relation.model();
		}
		else
			relationModelClass = (Class<? extends ManagedObject>)field.getType();

		if(relationModelClass != null)
			return Query.instantiate(relationModelClass);

		return null;
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
					field.set(this, new Date((cursor.getLong(columnIndex) * 1000)));
				}
				else if(field.getType().equals(Calendar.class)) {
					Calendar calendar = new GregorianCalendar();

					calendar.setTimeInMillis((cursor.getLong(columnIndex) * 1000));

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
		return contentValues(true);
	}

	public ContentValues contentValues(boolean includePrimaryKey) {
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

				if(columnValue == null || (!includePrimaryKey && column.primaryKey()))
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
					contentValues.put(columnName, (((Calendar) columnValue).getTimeInMillis() / 1000));

				else if(columnField.getType().equals(Date.class))
					contentValues.put(columnName, (((Date) columnValue).getTime() / 1000));

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
			catch (IllegalAccessException e) {
				e.printStackTrace();
			}
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

	public boolean deleteLocal() {
		try {
			new Query(Query.Type.Delete)
					.from(this)
					.where("id", "=", primaryKeyValue())
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



	/* Overridable methods */

	public Query cacheQuery(String url, Bundle args) {
		return null;
	}

	public ResponseDescriptor[] responseDescriptors() {
		return null;
	}

	public RequestDescriptor[] requestDescriptors() {
		return null;
	}


	/* Protected helper methods */

	protected boolean matchPath(String path, String patternString) {
		String[] pathParts 		= path.split("/");
		String[] patternParts 	= patternString.split("/");

		int match				= 0;
		int toMatch				= 0;

		for(int i=0; i<pathParts.length; i++) {
			String pathPart 	= pathParts[i];

			if(patternParts.length >= i) {
				String patternPart 	= patternParts[i];

				// Only check the parts that are not variables
				if(patternPart.substring(0, 1).equals(":") == false) {
					toMatch++;

					if(patternPart.equals(pathPart))
						match++;
				}
				else {
					// Increase both for variables as we need to acknowledge they exist
					toMatch++;
					match++;
				}
			}
		}

		return (match == toMatch);
	}

}