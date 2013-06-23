package com.fake.restutility.rest;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.util.Log;
import com.fake.restutility.db.Query;
import com.fake.restutility.mapping.MappingResult;
import com.fake.restutility.object.ManagedObject;
import com.fake.restutility.object.ManagedObjectUtils;
import org.apache.http.message.BasicNameValuePair;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

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
	
	// Global References
	private static HashMap<String, String> globalParameters;
	
	public static void globalParameter(String key, String value) {
		if(globalParameters == null)
			return;
		
		globalParameters.put(key, value);
	}

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
		globalParameters 	= new HashMap<String, String>();
		instance 			= new ObjectManager(application, baseURL, DBName, DBVersion);
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
		this.baseURL	= baseURL;
		this.DBName		= DBName;
		this.DBVersion	= DBVersion;

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

		getObjects(entityClass, object.resourcePath(), objectRequestListener, null);
	}

	/**
	 *
	 * @param path
	 * @param objectRequestListener
	 */
	public void getObjects(Class<? extends ManagedObject> entityClass, String path, ObjectRequestListener objectRequestListener) {
		getObjects(entityClass, path, objectRequestListener, null);
	}

	/**
	 * Creates a request and sets the method type to GET
	 *
	 * @param resourcePath 	- The resource path of the request
	 * @param listener		-
	 * @param parameters
	 */
	public void getObjects(Class<? extends ManagedObject> entityClass, String resourcePath, ObjectRequestListener listener, ArrayList<BasicNameValuePair> parameters) {
		request(Request.Method.GET, entityClass, null, resourcePath, listener, parameters);
	}

	/**
	 * Creates a request and set the method type to POST
	 *
	 * @param object
	 * @param resourcePath
	 * @param listener
	 * @param parameters
	 */
	public void postObject(ManagedObject object, String resourcePath, ObjectRequestListener listener, ArrayList<BasicNameValuePair> parameters) {
		request(Request.Method.POST, object.getClass(), object, resourcePath, listener, parameters);
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
		request(Request.Method.PUT, object.getClass(), object, resourcePath, listener, parameters);
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
		request(Request.Method.DELETE, object.getClass(), object, resourcePath, listener, parameters);
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
	private void request(final Request.Method method, final Class<? extends ManagedObject> entityClass, final ManagedObject object, String resourcePath, final ObjectRequestListener listener, final ArrayList<BasicNameValuePair> parameters) {
		String _baseURL 		= (baseURL.endsWith("/") == false ? baseURL + "/" : baseURL);							// Make sure the baseURL does end with a forward slash
		String _resourcePath	= (resourcePath.startsWith("/") == true ? resourcePath.substring(1) : resourcePath);	// Make sure the resourcePath doesn't started with a forward slash
		final String url 		= _baseURL +  _resourcePath;															// Concatenate the baseURL and resourcePath

		addGlobalParameters(parameters);
		
		Log.d(TAG, "New Request: " + OAuth2AccessToken);

		new Thread(new Runnable() {
			@Override
			public void run() {
				new Request(method, url, new Request.RequestListener() {
					@Override
					public void requestStarted(Request request) {}

					@Override
					public void requestFailed(Request request) {
						Activity activity;

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

					@Override
					public void requestFinished(Request request, final int status) {
						Log.d(TAG, "Request finished: " + status);

						if(status >= 400) {
							Log.d(TAG, "Object request failed with status: " + status);

							Activity activity;

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
						else {
							Log.d(TAG, "Object request succeeded.");

							final MappingResult result = new MappingResult(entityClass, request.getResponseStream());
							Activity activity;

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
				}, parameters)
				.OAuth2AccessToken(OAuth2AccessToken)
				.object(object)
				.execute();
			}
		}).start();
	}
	
	private void addGlobalParameters(ArrayList<BasicNameValuePair> parameters) {
		Iterator<String> globalParameterIterator = globalParameters.keySet().iterator();
		
		while(globalParameterIterator.hasNext()) {
			String key = globalParameterIterator.next();
			
			parameters.add(new BasicNameValuePair(key, globalParameters.get(key)));
		}
	}


	/* Object Interfaces */

	public interface ObjectRequestListener {
		public void success(MappingResult mappingResult);
		public void failure(int status);
	}

}