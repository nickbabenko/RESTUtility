package com.fake.restutility.object;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Created by nickbabenko on 15/06/13.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Column {

	public enum DateFormat {
		Unix,
		MySql_Date,
		MySql_DateTime,
		Default
	};

	public enum ForeignAction {
		NoAction,
		Restrict,
		SetNull,
		SetDefault,
		Cascade,
		Empty
	};

	boolean autoIncrement() 	default false;
	boolean primaryKey() 		default false;	// Indicates wether this is the primary key field and used to reference previous objects
	String name() 				default "";     // Used to reference the column in the database
	String keyPath() 			default "";     // Used to reference the key in the REST response
	int length() 				default -1;
	DateFormat dateFormat() 	default DateFormat.Default;
	boolean ignoreIfZero()		default false;
	boolean unique()			default false;

	Class<? extends ManagedObject> referenceModel()		default ManagedObject.class;
	String referenceColumn()							default "";
	ForeignAction onDelete()	default ForeignAction.NoAction;
	ForeignAction onUpdate()	default ForeignAction.Empty;

}