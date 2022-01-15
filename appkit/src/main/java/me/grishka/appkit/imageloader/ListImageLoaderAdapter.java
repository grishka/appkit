package me.grishka.appkit.imageloader;

import android.graphics.drawable.Drawable;

import me.grishka.appkit.imageloader.requests.ImageLoaderRequest;

public interface ListImageLoaderAdapter {
	int getCount();
	int getImageCountForItem(int item);
	ImageLoaderRequest getImageRequest(int item, int image);
	void imageLoaded(int item, int image, Drawable drawable);

}
