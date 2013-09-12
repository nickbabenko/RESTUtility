package com.fake.restutility.object;

import android.app.Application;
import android.text.TextUtils;
import android.util.Log;
import com.fake.restutility.db.QueryResult;
import dalvik.system.DexFile;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.net.URL;
import java.util.*;

/**
 * Created by nickbabenko on 17/06/2013.
 */
public class ManagedObjectUtils {

	private static final String TAG = "ManagedObjectUtils";

	private static ArrayList<Class<? extends ManagedObject>> managedObjectClasses = new ArrayList<Class<? extends ManagedObject>>();

	public static void init(Application application) {
		fetchManagedObjects(application);
	}

	public static ArrayList<Class<? extends ManagedObject>> managedObjectClasses() {
		return managedObjectClasses;
	}

	private static void fetchManagedObjects(Application application) {
		Log.d(TAG, "Fetch Managed Objects");

		String packageName 					= application.getPackageName();
		String sourcePath 					= application.getApplicationInfo().sourceDir;
		List<String> paths 					= new ArrayList<String>();

		if (sourcePath != null) {
			DexFile dexfile 				= null;

			try {
				dexfile = new DexFile(sourcePath);
			}
			catch (IOException e) {
				e.printStackTrace();
			}

			if(dexfile != null) {
				Enumeration<String> entries 	= dexfile.entries();

				while (entries.hasMoreElements())
					paths.add(entries.nextElement());
			}
		}
		else {
			ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
			Enumeration<URL> resources = null;

			try {
				resources = classLoader.getResources("");
			}
			catch (IOException e) {}

			if(resources != null) {
				while (resources.hasMoreElements()) {
					String path = resources.nextElement().getFile();

					if (path.contains("bin") || path.contains("classes"))
						paths.add(path);
				}
			}
		}

		for (String path : paths) {
			File file = new File(path);

			scanForManagedObjects(file, packageName, application.getClass().getClassLoader());
		}
	}

	private static void scanForManagedObjects(File path, String packageName, ClassLoader classLoader) {
		if(path.isDirectory()) {
			for(File file : path.listFiles())
				scanForManagedObjects(file, packageName, classLoader);
		}
		else {
			String className = path.getName();

			try {
				Class<?> foundClass = Class.forName(className, false, classLoader);

				if(isSubclassOf(foundClass, ManagedObject.class))
					managedObjectClasses.add((Class<? extends ManagedObject>) foundClass);
			}
			catch (ClassNotFoundException e) {}
		}
	}

	public static boolean isSubclassOf(Class<?> type, Class<?> superClass) {
		if (type.getSuperclass() != null) {
			if (type.getSuperclass().equals(superClass)) {
				return true;
			}

			return isSubclassOf(type.getSuperclass(), superClass);
		}

		return false;
	}

	public static String createTableDefinition(Class<? extends ManagedObject> managedObjectClass) {
		ManagedObject managedObjectInstance = null;

		Log.d(TAG, "Class: " + managedObjectClass);

		try {
			managedObjectInstance = (ManagedObject) managedObjectClass.newInstance();
		}
		catch (Exception e) {
			return "";
		}

		String query = String.format("CREATE TABLE IF NOT EXISTS %s (%s);", managedObjectInstance.tableName(), TextUtils.join(", ", managedObjectInstance.columnDefinitions()));

		Log.d(TAG, "Query: " + query);

		return query;
	}

	private static HashMap<Class<? extends ManagedObject>, Entity> entityAnnotations 									= new HashMap<Class<? extends ManagedObject>, Entity>();
	private static HashMap<Class<? extends ManagedObject>, Field[]> entityFields 										= new HashMap<Class<? extends ManagedObject>, Field[]>();
	private static HashMap<Class<? extends ManagedObject>, ArrayList<Column>> entityColumns 							= new HashMap<Class<? extends ManagedObject>, ArrayList<Column>>();
	private static HashMap<Class<? extends ManagedObject>, ArrayList<Relation>> entityRelations 						= new HashMap<Class<? extends ManagedObject>, ArrayList<Relation>>();
	private static HashMap<Class<? extends ManagedObject>, ArrayList<Field>> entityColumnFields 						= new HashMap<Class<? extends ManagedObject>, ArrayList<Field>>();
	private static HashMap<Class<? extends ManagedObject>, HashMap<String, Field>> entityColumnFieldsMap 				= new HashMap<Class<? extends ManagedObject>, HashMap<String, Field>>();
	private static HashMap<Class<? extends ManagedObject>, ArrayList<Field>> entityRelationFields						= new HashMap<Class<? extends ManagedObject>, ArrayList<Field>>();
	private static HashMap<Class<? extends ManagedObject>, ArrayList<String>> entityColumnNames							= new HashMap<Class<? extends ManagedObject>, ArrayList<String>>();
	private static HashMap<Class<? extends ManagedObject>, Column> entityPrimaryColumns									= new HashMap<Class<? extends ManagedObject>, Column>();
	private static HashMap<Class<? extends ManagedObject>, Field> entityPrimaryFields									= new HashMap<Class<? extends ManagedObject>, Field>();

	public static Entity getEntityAnnotation(Class<? extends ManagedObject> clazz) {
		if(entityAnnotations.containsKey(clazz))
			return entityAnnotations.get(clazz);

		Entity entity = clazz.getAnnotation(Entity.class);

		entityAnnotations.put(clazz, entity);

		return entity;
	}

	private static Field[] fields(Class<? extends ManagedObject> clazz) {
		if(entityFields.containsKey(clazz))
			return entityFields.get(clazz);

		Field[] fields = clazz.getFields();

		entityFields.put(clazz, fields);

		return fields;
	}

	private static void mapFields(Class<? extends ManagedObject> clazz) {
		Field[] fields										= fields(clazz);
		ArrayList<Column> columns 							= new ArrayList<Column>();
		ArrayList<Relation> relations						= new ArrayList<Relation>();
		ArrayList<Field> columnFields						= new ArrayList<Field>();
		HashMap<String, Field> columnFieldsMap				= new HashMap<String, Field>();
		ArrayList<Field> relationFields						= new ArrayList<Field>();

		Column primaryColumn								= null;
		Field primaryKeyField								= null;

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

		entityColumns.put(clazz, columns);
		entityRelations.put(clazz, relations);
		entityColumnFields.put(clazz, columnFields);
		entityRelationFields.put(clazz, relationFields);
		entityColumnFieldsMap.put(clazz, columnFieldsMap);
		entityPrimaryColumns.put(clazz, primaryColumn);
		entityPrimaryFields.put(clazz, primaryKeyField);
	}

	public static Column primaryColumn(Class<? extends ManagedObject> clazz) {
		if(!entityPrimaryColumns.containsKey(clazz))
			mapFields(clazz);

		return entityPrimaryColumns.get(clazz);
	}

	public static Field primaryField(Class<? extends ManagedObject> clazz) {
		if(!entityPrimaryFields.containsKey(clazz))
			mapFields(clazz);

		return entityPrimaryFields.get(clazz);
	}

	public static ArrayList<Column> columns(Class<? extends ManagedObject> clazz) {
		if(!entityColumns.containsKey(clazz))
			mapFields(clazz);

		return  entityColumns.get(clazz);
	}

	public static ArrayList<Relation> relations(Class<? extends ManagedObject> clazz) {
		if(!entityRelations.containsKey(clazz))
			mapFields(clazz);

		return entityRelations.get(clazz);
	}

	public static ArrayList<Field> columnFields(Class<? extends ManagedObject> clazz) {
		if(!entityColumnFields.containsKey(clazz))
			mapFields(clazz);

		return entityColumnFields.get(clazz);
	}

	public static HashMap<String, Field> columnFieldsMap(Class<? extends ManagedObject> clazz) {
		if(!entityColumnFieldsMap.containsKey(clazz))
			mapFields(clazz);

		return entityColumnFieldsMap.get(clazz);
	}

	public static ArrayList<Field> relationFields(Class<? extends ManagedObject> clazz) {
		if(!entityRelationFields.containsKey(clazz))
			mapFields(clazz);

		return entityRelationFields.get(clazz);
	}

	public static ArrayList<String> columnNames(Class<? extends ManagedObject> clazz) {
		if(entityColumnNames.containsKey(clazz))
			return entityColumnNames.get(clazz);

		ArrayList<String> columnNames 					= new ArrayList<String>();
		int inc 						= 0;
		ArrayList<Field> columnFields = columnFields(clazz);

		Iterator<Field> columnFieldIterator = columnFields.iterator();

		// Iterate all columns
		while(columnFieldIterator.hasNext()) {
			Field columnField 		= columnFieldIterator.next();
			Column column			= columnField.getAnnotation(Column.class);

			columnNames.add((column.name() == "" ? columnField.getName() : column.name())); // Append to column name array

			inc++;
		}

		// Iterate all relations
		ArrayList<Field> relationFields			= relationFields(clazz);
		Iterator<Field> relationFieldIterator 	= relationFields.iterator();

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

		entityColumnNames.put(clazz, columnNames);

		return columnNames;
	}

}