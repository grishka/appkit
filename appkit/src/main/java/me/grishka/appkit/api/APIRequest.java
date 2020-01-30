package me.grishka.appkit.api;

import android.os.Handler;
import android.os.Looper;

/**
 * Base class for all your API requests
 */
public abstract class APIRequest<T> {

	protected Callback<T> callback;
	protected Handler uiThreadHandler=new Handler(Looper.getMainLooper());

	public abstract void cancel();
	public abstract APIRequest<T> exec();

	public APIRequest<T> setCallback(Callback<T> callback){
		this.callback=callback;
		return this;
	}

	protected void invokeSuccessCallback(final T result){
		if(callback==null)
			return;
		uiThreadHandler.post(new Runnable(){
			@Override
			public void run(){
				callback.onSuccess(result);
			}
		});
	}

	protected void invokeErrorCallback(final ErrorResponse error){
		if(callback==null)
			return;
		uiThreadHandler.post(new Runnable(){
			@Override
			public void run(){
				callback.onError(error);
			}
		});
	}
}
