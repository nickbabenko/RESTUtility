package com.fake.restutility.object;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Entity {

	String resourcePath() 	default "";
	String table()			default "";
	String requestKeyPath()	default "";
	boolean relationsFirst() default false;

}