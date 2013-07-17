package com.fake.restutility.rest;

import android.webkit.MimeTypeMap;
import com.fake.restutility.object.Column;
import com.fake.restutility.object.ManagedObject;
import com.fake.restutility.object.ManagedObjectUtils;
import com.fake.restutility.util.Log;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.*;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Field;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;

/**
 * Created by nickbabenko on 15/06/13.
 */
public class Request {

	private static final String TAG = "RESTUtility - Request";

	public enum Method {
		GET,
		POST,
		PUT,
		DELETE
	};

	private final Request self = this;

	private Method method;
	private String url;
	private RequestListener listener;
	private ArrayList<BasicNameValuePair> parameters = new ArrayList<BasicNameValuePair>();
	private ManagedObject object;
	private String OAuth2AccessToken;
	private HashMap<String, File> files = new HashMap<String, File>();

	private HttpResponse response;

	public Request(Method method, String url, RequestListener listener, ArrayList<BasicNameValuePair> parameters) {
		this.method			= method;
		this.url			= url;
		this.listener		= listener;
		this.parameters		= parameters;

		Log.d(TAG, "New request to URL: " + url);
	}

	/**
	 *
	 * @param object
	 * @return
	 */
	public Request object(ManagedObject object) {
		this.object = object;

		return this;
	}

	public Request OAuth2AccessToken(String token) {
		this.OAuth2AccessToken = token;

		return this;
	}

	public Request addFile(String name, File file) {
		files.put(name, file);

		return this;
	}

	public Request setFiles(HashMap<String, File> files) {
		this.files = files;

		return this;
	}

	/**
	 *
	 */
	public void execute() {
		HttpClient requestClient 		= new DefaultHttpClient();
		HttpRequestBase requestBase 	= null;

		switch(method) {
			case GET:
				if(parameters != null && parameters.size() > 0)
					url += "?" + URLEncodedUtils.format(parameters, "utf-8");

				Log.d(TAG, "URL: " + url);

				requestBase = new HttpGet(url);
				break;
			case POST:
				requestBase = new HttpPost(url);
				break;
			case PUT:
				requestBase = new HttpPut(url);
				break;
			case DELETE:
				requestBase = new HttpDelete(url);
				break;
		}

		// If we have an object and we aren't performing a GET request, add its values as parameters
		if(object != null && method != Method.GET)
			addObjectToParameters(object);

		if(OAuth2AccessToken != null) {
			Log.d(TAG, "TOKEN: " + OAuth2AccessToken);

			requestBase.addHeader("Authorization", "Bearer " + OAuth2AccessToken);
		}

		if(parameters != null && method != Method.GET) {
			HttpParams params = new BasicHttpParams();

			MultipartEntity requestEntity = new MultipartEntity(HttpMultipartMode.BROWSER_COMPATIBLE);

			try {
				for(BasicNameValuePair nameValuePair : parameters) {
					params.setParameter(nameValuePair.getName(), nameValuePair.getValue());

					requestEntity.addPart(nameValuePair.getName(), new StringBody(nameValuePair.getValue()));
				}

				if(files != null) {
					Iterator<String> fileIterator = files.keySet().iterator();

					while(fileIterator.hasNext()) {
						String name 		= fileIterator.next();
						File file			= files.get(name);
						String extension 	= MimeTypeMap.getFileExtensionFromUrl(file.getAbsolutePath());
						String mimeType		= MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);

						if(mimeType == null)
							mimeType = "application/octet-stream";

						Log.d(TAG, "File: " + name + ", " + file.getName() + ", " + mimeType);

						requestEntity.addPart(name, new FileBody(file, file.getName(), mimeType, "utf-8"));
					}
				}

				if(requestBase.getClass().equals(HttpPost.class)) {
					((HttpPost)requestBase).setEntity(requestEntity);
				}
				else if(requestBase.getClass().equals(HttpPut.class)) {
					((HttpPut)requestBase).setEntity(requestEntity);
				}
			}
			catch (UnsupportedEncodingException e) {}
		}

		if(listener != null)
			listener.requestStarted(self);

		try {
			response = requestClient.execute(requestBase);
		}
		catch (Exception e) {
			Log.d(TAG, "Failed to make request: " + e.getMessage());

			e.printStackTrace();

			if(listener != null)
				listener.requestFailed(self);

			return;
		}

		StatusLine statusLine = response.getStatusLine();

		if(listener != null)
			listener.requestFinished(self, statusLine.getStatusCode());
	}

	/**
	 *
	 * @return
	 */
	public InputStream getResponseStream() {
		try {
			return response.getEntity().getContent();
		}
		catch (IOException e) {
			return null;
		}
	}

	/**
	 *
	 */
	private void addObjectToParameters(ManagedObject _object) {
		addObjectToParameters(_object, "");
	}

	private void addObjectToParameters(ManagedObject _object, String prefixKeyPath) {
		if(parameters == null)
			parameters = new ArrayList<BasicNameValuePair>();

		if(_object == null)
			return;

		String _keyPath					= (_object.entity().requestKeyPath() == "" ? "" : _object.entity().requestKeyPath());
		String keyPath 					= (prefixKeyPath == "" ? _keyPath : (prefixKeyPath + "[" + _keyPath + "]"));

		Iterator<Field> fieldIterator 	= _object.columnFields().iterator();

		while(fieldIterator.hasNext()) {
			Field field 	= fieldIterator.next();
			Column column	= field.getAnnotation(Column.class); //object.columns().get(inc);
			String key 		= (keyPath != "" ? keyPath + "[" : "") +
					(column.keyPath() == "" ? field.getName() : column.keyPath()) +
					(keyPath != "" ? "]" : "");

			try {
				if(field.getType().equals(Date.class)) {
					if(field.get(_object) == null)
						continue;

					Date date 				= (Date) field.get(_object);
					SimpleDateFormat format = null;

					switch(column.dateFormat()) {
						case Unix:
							parameters.add(new BasicNameValuePair(key, "" + date.getTime()));
							break;
						case MySql_Date:
							format = new SimpleDateFormat("yyyy-MM-dd");
							break;
						case MySql_DateTime:
							format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
							break;
						case Default:
						default:
							format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
							break;
					}

					if(format != null)
						parameters.add(new BasicNameValuePair(key, format.format(date)));
				}
				else {
					if(field.getType().equals(int.class) == false ||
							column.ignoreIfZero() == false ||
							(column.ignoreIfZero() == true && field.getInt(_object) > 0) ||
							(column.primaryKey() == true && field.getInt(_object) > 0))
						parameters.add(new BasicNameValuePair(key, field.get(_object).toString()));
				}
			}
			catch (Exception e) {}

			Log.d(TAG, "parameters: " + parameters);
		}

		ArrayList<Field> relationFields		= _object.relationFields();
		Iterator<Field> relationIterator	= relationFields.iterator();

		Log.d(TAG, "Relation count: " + relationFields.size());

		while(relationIterator.hasNext()) {
			Field relationField				= relationIterator.next();

			Log.d(TAG, "Relations: " + relationField);

			try {
				if(relationField.getType().isArray() &&
					ManagedObjectUtils.isSubclassOf(relationField.getType().getComponentType(), ManagedObject.class)) {
					ManagedObject[] values = (ManagedObject[]) relationField.get(_object);

					for(ManagedObject value : values) {
						addObjectToParameters(value, keyPath);
					}
				}
				else if(ManagedObjectUtils.isSubclassOf(relationField.getType(), ManagedObject.class)) {
					ManagedObject value = (ManagedObject) relationField.get(_object);

					addObjectToParameters(value, keyPath);
				}
			} catch (Exception e) {}
		}
	}

	public interface RequestListener {
		public void requestStarted(Request request);
		public void requestFailed(Request request);
		public void requestFinished(Request request, int status);
	}

}