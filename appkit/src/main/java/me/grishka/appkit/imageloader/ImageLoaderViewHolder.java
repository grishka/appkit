package me.grishka.appkit.imageloader;

import android.graphics.drawable.Drawable;

/**
 * Created by grishka on 02.07.15.
 */
public interface ImageLoaderViewHolder {
	void setImage(int index, Drawable image);
	default void clearImage(int index){
		setImage(index, null);
	}
}
