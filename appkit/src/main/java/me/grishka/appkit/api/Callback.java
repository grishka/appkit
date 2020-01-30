package me.grishka.appkit.api;

/**
 * A callback for asynchronous API requests.
 */
public interface Callback<T> {
	public void onSuccess(T result);
	public void onError(ErrorResponse error);
}
