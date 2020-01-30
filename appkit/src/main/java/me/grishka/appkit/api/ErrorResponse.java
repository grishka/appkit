package me.grishka.appkit.api;

import android.content.Context;
import android.view.View;

/**
 * Base class for an API error response, either client-side network error or an arbitrary error returned by an API itself.
 */
public abstract class ErrorResponse {

	/**
	 * Show any information about this error in the error view.
	 * @param view the view
	 */
	public abstract void bindErrorView(View view);

	/**
	 * Show a toast notification with a localized description of this error. This method is always called from the main thread.
	 * @param context Context you can use for your Toast
	 */
	public abstract void showToast(Context context);
}
