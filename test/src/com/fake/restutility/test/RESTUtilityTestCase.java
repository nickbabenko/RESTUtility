package com.fake.restutility.test;

import com.fake.restutility.test.app.Application;

import android.test.ApplicationTestCase;

public abstract class RESTUtilityTestCase extends ApplicationTestCase<Application> {

	public RESTUtilityTestCase() {
		super(Application.class);
	}

	protected void setUp() throws Exception {
		super.setUp();
		
		this.createApplication();
	}

	protected void tearDown() throws Exception {
		super.tearDown();
	}

}