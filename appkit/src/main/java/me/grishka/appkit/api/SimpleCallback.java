package me.grishka.appkit.api;

import android.app.Fragment;

import me.grishka.appkit.fragments.LoaderFragment;

/**
 * Created by grishka on 13.07.15.
 */
public abstract class SimpleCallback<T> implements Callback<T> {

	protected final Fragment fragment;

	public SimpleCallback(Fragment f){
		fragment=f;
	}

	@Override
	public void onError(ErrorResponse error){
		if(fragment instanceof LoaderFragment){
			((LoaderFragment)fragment).onError(error);
		}
	}
}
