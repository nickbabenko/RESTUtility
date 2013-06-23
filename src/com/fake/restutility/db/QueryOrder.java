package com.fake.restutility.db;

/**
 * Created by nickbabenko on 21/06/2013.
 */
public class QueryOrder {

	public static final String Type_ASC 		= "ASC";
	public static final String Type_DESC		= "DESC";

	private String column;
	private String type;

	public QueryOrder(String column, String type) {
		this.column = column;
		this.type = type;
	}

	public QueryOrder column(String column) {
		this.column = column;

		return this;
	}

	public QueryOrder type(String type) {
		this.type = type;

		return this;
	}

	public String column() {
		return column;
	}

	public String type() {
		return type;
	}

	@Override
	public String toString() {
		return column + " " + type;
	}

}
