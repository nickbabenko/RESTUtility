package com.fake.restutility.test.model;

import com.fake.restutility.object.Column;
import com.fake.restutility.object.Entity;
import com.fake.restutility.object.ManagedObject;
import com.fake.restutility.object.Relation;

@Entity(keyPath = "response.venues")
public class Venue extends ManagedObject {

	@Column(primaryKey = true)
	public String id;
	
	@Column
	public String name;
	
	@Column
	public String canonicalUrl;
	
	@Column
	public boolean verified;
	
	@Column
	public String referralId;
	
	
	// Location
	
	@Column(keyPath = "location.address")
	public String address;
	
	@Column(keyPath = "location.crossStreet")
	public String crossStreet;
	
	@Column(keyPath = "location.lat")
	public double latitude;
	
	@Column(keyPath = "location.lng")
	public double longitude;
	
	@Column(keyPath = "location.postalCode")
	public String postcode;
	
	@Column(keyPath = "location.city")
	public String city;
	
	@Column(keyPath = "location.state")
	public String state;
	
	@Column(keyPath = "location.country")
	public String country;
	
	@Column(keyPath = "location.cc")
	public String countryCode;
	
	
	// Stats
	
	@Column(keyPath = "stats.checkinsCount")
	public int checkinsCount;
	
	@Column(keyPath = "stats.usersCount")
	public int usersCount;
	
	@Column(keyPath = "stats.tipCount")
	public int tipCount;
	
	@Column(keyPath = "hereNow.count")
	public int hereNow;
	
	
	// Relations
	
	@Relation
	public VenueCategory[] categories;
	
}