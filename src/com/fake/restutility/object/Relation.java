package com.fake.restutility.object;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Created by nickbabenko on 15/06/13.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Relation {

	String keyPath() 		default "";
	String name()			default "";
	String connectedBy()	default "";
	Class model()			default ManagedObject.class;

}