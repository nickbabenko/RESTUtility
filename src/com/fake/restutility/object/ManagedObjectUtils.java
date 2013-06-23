package com.fake.restutility.object;

import android.app.Application;
import android.text.TextUtils;
import android.util.Log;
import dalvik.system.DexFile;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/**
 * Created by nickbabenko on 17/06/2013.
 */
public class ManagedObjectUtils {

	private static final String TAG = "ManagedObjectUtils";

	private static ArrayList<Class<? extends ManagedObject>> managedObjectClasses= new ArrayList<Class<? extends ManagedObject>>();

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

}