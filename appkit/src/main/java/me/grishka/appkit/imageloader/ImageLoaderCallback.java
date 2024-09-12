package me.grishka.appkit.imageloader;

import android.graphics.drawable.Drawable;

import me.grishka.appkit.imageloader.requests.ImageLoaderRequest;

public interface ImageLoaderCallback{
	void onImageLoaded(ImageLoaderRequest req, Drawable image);
	default void onImageLoadingFailed(ImageLoaderRequest req, Throwable error){}
}
