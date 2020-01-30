package me.grishka.appkit.utils;

import android.os.Build;

/**
 * Created by grishka on 08.06.15.
 */
public class NetworkUtils{

	private static String userAgent="AppKit ("+Build.MANUFACTURER+" "+Build.MODEL+"; Android/"+Build.VERSION.RELEASE+")";

	/**
	 * Get the global user agent used for all HTTP(s) requests.
	 * @return The user agent string
	 */
	public static String getUserAgent(){
		return userAgent;
	}

	/**
	 * Set the global user agent used for all HTTP(s) requests.
	 * @param ua The new user agent string
	 */
	public static void setUserAgent(String ua){
		userAgent=ua;
	}

}
