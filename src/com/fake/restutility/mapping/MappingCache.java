package com.fake.restutility.mapping;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * Created by nickbabenko on 12/09/2013.
 */
public class MappingCache {

	private static HashMap<String, ArrayList<Object>> cache;

	private static void init() {
		if(cache == null)
			reset();
	}

	public static void putObject(String table, Object id) {
		init();

		ArrayList<Object> tableCache;

		if(cache.containsKey(table))
			tableCache = cache.get(table);
		else
			tableCache = new ArrayList<Object>();

		// Update if it already exists
		if(!tableCache.contains(id))
			tableCache.add(id);

		cache.put(table, tableCache);
	}

	public static Object getObject(String table, Object id) {
		init();

		if(!cache.containsKey(table))
			return null;

		ArrayList<Object> tableCache = cache.get(table);

		if(!tableCache.contains(id))
			return null;

		return id;
	}

	public static void reset() {
		cache = new HashMap<String, ArrayList<Object>>();
	}

}
