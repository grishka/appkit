package me.grishka.appkit.imageloader.processing;

import android.graphics.drawable.Drawable;

public abstract class ImageProcessingStep{
	public abstract Drawable processDrawable(Drawable input);
	public abstract String getMemoryCacheKey();
}
