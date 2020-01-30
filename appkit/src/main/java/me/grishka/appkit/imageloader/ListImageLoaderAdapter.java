package me.grishka.appkit.imageloader;

import android.graphics.Bitmap;

public interface ListImageLoaderAdapter {

	public int getCount();
	public int getImageCountForItem(int item);
	public String getImageURL(int item, int image);
	public void imageLoaded(int item, int image, Bitmap bitmap);

}
