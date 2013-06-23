package com.fake.restutility.exception;

/**
 * Created by nickbabenko on 16/06/13.
 */
public class PrimaryKeyNotDefinedException extends Exception {

	public PrimaryKeyNotDefinedException(String className) {
		super("Unable to use object (" + className + "). Primary key not defined.");
	}

}