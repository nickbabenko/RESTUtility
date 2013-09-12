package com.fake.restutility.rest;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import com.fake.restutility.db.Query;
import com.fake.restutility.mapping.MappingCache;
import com.fake.restutility.mapping.MappingResult;
import com.fake.restutility.object.ManagedObject;
import com.fake.restutility.object.ManagedObjectUtils;
import com.fake.restutility.util.Log;
import org.apache.http.message.BasicNameValuePair;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Created by nickbabenko on 15/06/13.
 */
public class ObjectManager {

	private static final String TAG = "RESTUtility - ObjectManager";

	// Static References
	private static ObjectManager instance;


	// Enums
	public enum OAuthType {
		OAuthType_OAuth2
	};

	// OAuth Configuration
	private static OAuthType OAuthType 		= ObjectManager.OAuthType.OAuthType_OAuth2;
	private static String OAuth2AccessToken	= null;

	public static void setOAuth2AccessToken(String token) {
		OAuth2AccessToken = token;
	}


	/* Init Methods */

	/**
	 *
	 * @param application
	 */
	public static void init(Application application, String baseURL, String DBName, int DBVersion) {
		instance = new ObjectManager(application, baseURL, DBName, DBVersion);
	}

	public static ObjectManager instance() {
		if(instance == null) {
			try {
				throw new Exception("Unable to instantiate ObjectManager through static instance singleton method. Use the ObjectManager.init method");
			}
			catch (Exception e) {
				e.printStackTrace();

				return null;
			}
		}

		return instance;
	}


	/* Instance */

	private Application application;
	private String baseURL;
	private String DBName;
	private int DBVersion 				= 1;
	private Activity activity;


	/* Construct */

	public ObjectManager(Application application, String baseURL, String DBName, int DBVersion) {
		this.application 	= application;
		this.baseURL		= baseURL;
		this.DBName			= DBName;
		this.DBVersion		= DBVersion;

		ManagedObjectUtils.init(application);
	}


	/* Getters */

	public Context context() {
		return application;
	}

	public String DBName() {
		return DBName;
	}

	public int DBVersion() {
		return DBVersion;
	}

	public Activity currentActivity() {
		return activity;
	}


	/* Setters */

	public void setCurrentActivity(Activity activity) {
		this.activity = activity;
	}

	public ObjectManager baseURL(String baseURL) {
		this.baseURL = baseURL;

		return this;
	}


	/* OAuth Methods */

	/**
	 *
	 * @param _OAuthType
	 */
	public static void setOAuthType(OAuthType _OAuthType) {
		OAuthType = _OAuthType;
	}


	/* Object REST Methods */

	public void getObjects(Class<? extends ManagedObject> entityClass, ObjectRequestListener objectRequestListener) {
		ManagedObject object = Query.instantiate(entityClass);

		getObjects(entityClass, object.resourcePath(), objectRequestListener, null, null);
	}

	/**
	 *
	 * @param path
	 * @param objectRequestListener
	 */
	public void getObjects(Class<? extends ManagedObject> entityClass, String path, ObjectRequestListener objectRequestListener) {
		getObjects(entityClass, path, objectRequestListener, null, null);
	}

	/**
	 *
	 * @param path
	 * @param objectRequestListener
	 */
	public void getObjects(Class<? extends ManagedObject> entityClass, String path, ObjectRequestListener objectRequestListener, String keyPath) {
		getObjects(entityClass, path, objectRequestListener, null, keyPath);
	}

	/**
	 * Creates a request and sets the method type to GET
	 *
	 * @param resourcePath 	- The resource path of the request
	 * @param listener		-
	 * @param parameters
	 */
	public void getObjects(Class<? extends ManagedObject> entityClass, String resourcePath, ObjectRequestListener listener, ArrayList<BasicNameValuePair> parameters) {
		request(Request.Method.GET, entityClass, null, resourcePath, null, listener, parameters, null);
	}

	/**
	 * Creates a request and sets the method type to GET
	 *
	 * @param resourcePath 	- The resource path of the request
	 * @param listener		-
	 * @param parameters
	 */
	public void getObjects(Class<? extends ManagedObject> entityClass, String resourcePath, ObjectRequestListener listener, ArrayList<BasicNameValuePair> parameters, String keyPath) {
		request(Request.Method.GET, entityClass, null, resourcePath, keyPath, listener, parameters, null);
	}

	public void postObject(ManagedObject object, String resourcePath, ObjectRequestListener listener) {
		postObject(object, resourcePath, listener, "");
	}

	public void postObject(ManagedObject object, String resourcePath, ObjectRequestListener listener, String keyPath) {
		request(Request.Method.POST, object.getClass(), object, resourcePath, keyPath, listener, null, null);
	}

	public void postObject(ManagedObject object, String resourcePath, ObjectRequestListener listener, ArrayList<BasicNameValuePair> parameters) {
		request(Request.Method.POST, object.getClass(), object, resourcePath, null, listener, new ArrayList<BasicNameValuePair>(), null);
	}

	/**
	 * Creates a request and set the method type to POST
	 *
	 * @param object
	 * @param resourcePath
	 * @param listener
	 * @param parameters
	 */
	public void postObject(ManagedObject object, String resourcePath, ObjectRequestListener listener, ArrayList<BasicNameValuePair> parameters, String keyPath) {
		request(Request.Method.POST, object.getClass(), object, resourcePath, keyPath, listener, parameters, null);
	}

	public void postObject(ManagedObject object, String resourcePath, ObjectRequestListener listener, HashMap<String, File> files) {
		postObject(object, resourcePath, listener, files, null);
	}

	public void postObject(ManagedObject object, String resourcePath, ObjectRequestListener listener, HashMap<String, File> files, String keyPath) {
		request(Request.Method.POST, object.getClass(), object, resourcePath, keyPath, listener, null, files);
	}

	public void putObject(ManagedObject object, String resourcePath, ObjectRequestListener listener, String keyPath) {
		request(Request.Method.PUT, object.getClass(), object, resourcePath, keyPath, listener, new ArrayList<BasicNameValuePair>(), null);
	}

	/**
	 * Creates a request and set the method type to PUT
	 *
	 * @param object
	 * @param resourcePath
	 * @param listener
	 * @param parameters
	 */
	public void putObject(ManagedObject object, String resourcePath, ObjectRequestListener listener, ArrayList<BasicNameValuePair> parameters) {
		request(Request.Method.PUT, object.getClass(), object, resourcePath, null, listener, parameters, null);
	}

	public void deleteObject(ManagedObject object, String resourcePath, ObjectRequestListener listener) {
		request(Request.Method.DELETE, object.getClass(), object, resourcePath, null, listener, new ArrayList<BasicNameValuePair>(), null);
	}

	/**
	 * Created a request and sets the method type to DELETE
	 *
	 * @param object
	 * @param resourcePath
	 * @param listener
	 * @param parameters
	 */
	public void deleteObject(ManagedObject object, String resourcePath, ObjectRequestListener listener, ArrayList<BasicNameValuePair> parameters) {
		request(Request.Method.DELETE, object.getClass(), object, resourcePath, null, listener, parameters, null);
	}

	public String _baseURL(String resourcePath) {
		String _baseURL 		= (baseURL.endsWith("/") == false ? baseURL + "/" : baseURL);							// Make sure the baseURL does end with a forward slash
		String _resourcePath	= (resourcePath.startsWith("/") == true ? resourcePath.substring(1) : resourcePath);	// Make sure the resourcePath doesn't started with a forward slash
		String url 				= _baseURL + _resourcePath;

		return url;
	}


	/* - Private Object REST Methods */

	/**
	 *
	 * @param method
	 * @param object
	 * @param resourcePath
	 * @param listener
	 * @param parameters
	 */
	private void request(final Request.Method method,
						 final Class<? extends ManagedObject> entityClass,
						 final ManagedObject object,
						 final String resourcePath,
						 final String keyPath,
						 final ObjectRequestListener listener,
						 final ArrayList<BasicNameValuePair> parameters,
						 final HashMap<String, File> files) {
		final String url = _baseURL(resourcePath);

		new Thread(new Runnable() {
			@Override
			public void run() {
				new Request(method, url, new Request.RequestListener() {
					@Override
					public void requestStarted(Request request) {}

					@Override
					public void requestFailed(Request request) {
						Activity activity;

						if(listener != null) {
							if((activity = ObjectManager.instance().currentActivity()) != null) {
								activity.runOnUiThread(new Runnable() {
									@Override
									public void run() {
										listener.failure(0);
									}
								});
							}
							else
								listener.failure(0);
						}
					}

					@Override
					public void requestFinished(Request request, final int status) {
						Log.d(TAG, "Request finished: " + status, true);

						if(status >= 400) {
							Log.d(TAG, "Object request failed with status: " + status + " (" + resourcePath + ")", true);

							Activity activity;

							if(listener != null) {
								if((activity = ObjectManager.instance().currentActivity()) != null) {
									activity.runOnUiThread(new Runnable() {
										@Override
										public void run() {
											listener.failure(status);
										}
									});
								}
								else
									listener.failure(status);
							}

							StringBuilder responseStrBuilder = new StringBuilder();

							String inputStr;

							BufferedReader streamReader;

							try {
								streamReader = new BufferedReader(new InputStreamReader(request.getResponseStream(), "UTF-8"));
							}
							catch (Exception e) {
								return;
							}

							try {
								while ((inputStr = streamReader.readLine()) != null)
									responseStrBuilder.append(inputStr);
							}
							catch (IOException e) {
								e.printStackTrace();
							}

							Log.d(TAG, "Response string: " + responseStrBuilder.toString(), true);
						}
						else {
							Log.d(TAG, "Object request succeeded - " + entityClass.getName(), true);

							Query beginTransactionQuery = new Query(Query.Type.BeginTransaction);
							SQLiteDatabase database = null;

							try {
								database = beginTransactionQuery.database();
							} catch (Exception e) {
								e.printStackTrace();
							}

							MappingResult _result;

							try {
								database.beginTransaction();

								requestCacheQuery((object == null ? Query.instantiate(entityClass) : object), resourcePath, database);

								_result = new MappingResult(entityClass, request.getResponseStream(), keyPath, database);

								database.setTransactionSuccessful();
							}
							finally {
								database.endTransaction();
							}

							MappingCache.reset();

							Activity activity;
							final MappingResult result = _result;

							if(listener != null) {
								if((activity = ObjectManager.instance().currentActivity()) != null) {
									activity.runOnUiThread(new Runnable() {
										@Override
										public void run() {
											listener.success(result);
										}
									});
								}
								else
									listener.success(result);
							}
						}
					}
				}, parameters)
				.OAuth2AccessToken(OAuth2AccessToken)
				.object(object)
				.setFiles(files)
				.execute();
			}
		}).start();
	}

	/**
	 * Attempts to request a query object from the class - use the conditional values to execute a delete query
	 * This is used to free up un-wanted data after a valid request. Keeps the data store fresh.
	 *
	 * @param entity
	 * @param path
	 */
	private void requestCacheQuery(ManagedObject entity, String path, SQLiteDatabase database) {
		if(entity != null) {
			Query query = entity.cacheQuery(path, argsFromPath(path));		// Get a query from the class

			// Only continue if we have a valid query
			if(query != null) {
				query.database(database);
				query.type(Query.Type.Delete); 			// Force delete
				query.from(entity);						// Define the entity to execute the request on (Only the table name is accessed through this)

				query.execute();
			}
		}
	}

	private Bundle argsFromPath(String path) {
		Bundle bundle 		= new Bundle();

		/**
		 * TODO: Implement some way of defining a URL pattern with defined parameters
		 * This will allow us to iterate the parts of the patter url and extract the values from the passed path
		 * Then assign them to the bundle using the key from the pattern and value from the path
		 * First we need to be able to have a pattern path - Maybe implement the response descriptors,
		 * which are matched against the url and the valid descriptor is passed through out the response handling methods
		 */

		return bundle;
	}


	/* Object Interfaces */

	public interface ObjectRequestListener {
		public void success(MappingResult mappingResult);
		public void failure(int status);
	}

}