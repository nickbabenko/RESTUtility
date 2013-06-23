package com.fake.restutility.test;

import com.fake.restutility.mapping.MappingResult;
import com.fake.restutility.rest.ObjectManager;
import com.fake.restutility.rest.ObjectManager.ObjectRequestListener;
import com.fake.restutility.test.app.Application;
import com.fake.restutility.test.model.Venue;

public class FetchTest extends RESTUtilityTestCase {

	public void testFetch() {		
		ObjectManager.instance().getObjects(Venue.class, Application.Foursquare_BaseURL + "venues/search", new ObjectRequestListener() {

			@Override
			public void success(MappingResult mappingResult) {
				assertTrue(mappingResult.array().length > 0);
			}

			@Override
			public void failure(int status) {
				
			}
			
		});
	}
	
}