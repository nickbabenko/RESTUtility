package com.fake.restutility.rest;

import android.util.Log;
import com.fake.restutility.object.ManagedObject;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.*;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

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
	private ArrayList<BasicNameValuePair> parameters;
	private ManagedObject object;
	private String OAuth2AccessToken;

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

	/**
	 *
	 */
	public void execute() {
		HttpClient requestClient 		= new DefaultHttpClient();
		HttpRequestBase requestBase 	= null;

		switch(method) {
			case GET:
				if(parameters != null)
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
			addObjectToParameters();

		if(OAuth2AccessToken != null) {
			Log.d(TAG, "TOKEN: " + OAuth2AccessToken);

			requestBase.addHeader("Authorization", "Bearer " + OAuth2AccessToken);
		}

		if(parameters != null && method != Method.GET) {
			HttpParams params = new BasicHttpParams();

			for(BasicNameValuePair nameValuePair : parameters) {
				params.setParameter(nameValuePair.getName(), nameValuePair.getValue());
			}

			requestBase.setParams(params);
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
		} catch (IOException e) {
			return null;
		}
	}

	/**
	 *
	 */
	private void addObjectToParameters() {

	}

	public interface RequestListener {
		public void requestStarted(Request request);
		public void requestFailed(Request request);
		public void requestFinished(Request request, int status);
	}

}