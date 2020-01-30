package me.grishka.appkit.imageloader;

import android.graphics.Bitmap;

/**
 * Created by grishka on 02.07.15.
 */
public interface ImageLoaderViewHolder {
	public void setImage(int index, Bitmap bitmap);
	public void clearImage(int index);
}
