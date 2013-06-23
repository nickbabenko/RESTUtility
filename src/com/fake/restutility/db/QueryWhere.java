package com.fake.restutility.db;

/**
 * Created by nickbabenko on 15/06/13.
 */
public class QueryWhere {

	private String key;
	private String type;
	private Object value;

	public QueryWhere(String key, String type, Object value) {
		this.key 	= key;
		this.type 	= type;
		this.value 	= value;
	}


	/* Setters */

	/**
	 *
	 * @param key
	 * @return
	 */
	public QueryWhere key(String key) {
		this.key = key;

		return this;
	}

	/**
	 *
	 * @param type
	 * @return
	 */
	public QueryWhere type(String type) {
		this.type = type;

		return this;
	}

	/**
	 *
	 * @param value
	 * @return
	 */
	public QueryWhere value(Object value) {
		this.value = value;

		return this;
	}


	/* Getters */

	/**
	 *
	 * @return
	 */
	public String key() {
		return key;
	}

	/**
	 *
	 * @return
	 */
	public String type() {
		return type;
	}

	/**
	 *
	 * @return
	 */
	public Object value() {
		return value;
	}

}