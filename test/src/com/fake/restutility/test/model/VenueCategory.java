package com.fake.restutility.test.model;

import com.fake.restutility.object.Column;
import com.fake.restutility.object.Entity;
import com.fake.restutility.object.ManagedObject;

@Entity
public class VenueCategory extends ManagedObject {

	@Column(primaryKey = true)
	public String id;
	
	@Column
	public String name;
	
	@Column
	public String pluralName;
	
	@Column
	public String shortName;
	
	@Column
	public boolean primary;
	
	@Column(keyPath = "icon.prefix")
	public String iconUrl;
	
	@Column(keyPath = "icon.suffix")
	public String iconExtension;
	
}