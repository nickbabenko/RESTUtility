package com.fake.restutility.test.app;

import com.fake.restutility.rest.ObjectManager;

public class Application extends android.app.Application {

	public static final String Foursquare_ClientId 		= "4ZLYVWRGFMZQVQHXJ3STYDL2IZY3TAYPUZUYPEO0C5WWG5ZX";
	public static final String Foursquare_ClientSecret		= "HIPPCJNYRW3H3XCZOLPECQFROC2ZGSCMFXICKTVON2BABLRW";
	public static final String Foursquare_BaseURL			= "https://api.foursquare.com/v2/";
	
	private static final String DB_Name						= "Foursquare-Test.sqlite";
	private static final int 	DB_Version					= 1;
	
	@Override
	public void onCreate() {
		super.onCreate();
		
		ObjectManager.init(this, Foursquare_BaseURL, DB_Name, DB_Version);
		ObjectManager.globalParameter("client_id", Foursquare_ClientId);
		ObjectManager.globalParameter("client_secret", Foursquare_ClientSecret);
	}
	
}