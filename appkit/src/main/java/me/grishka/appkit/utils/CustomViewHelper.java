package me.grishka.appkit.utils;

import android.content.res.Resources;

/**
 * I may or may not go to hell for abusing interfaces like this.
 */
public interface CustomViewHelper{
	Resources getResources();

	default int dp(float dp){
		return Math.round(dp*getResources().getDisplayMetrics().density);
	}
}
