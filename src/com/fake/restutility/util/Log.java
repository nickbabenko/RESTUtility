package com.fake.restutility.util;

/**
 * Created by nickbabenko on 24/06/2013.
 */
public class Log {

	private static boolean loggingEnabled = false;

	public static void setLoggingEnabled(boolean enabled) {
		loggingEnabled = enabled;
	}

	public static void d(String tag, String message) {
		d(tag, message, false);
	}

	public static void d(String tag, String message, boolean force) {
		if(loggingEnabled == false && force != true)
			return;

		android.util.Log.d(tag, message);
	}

}