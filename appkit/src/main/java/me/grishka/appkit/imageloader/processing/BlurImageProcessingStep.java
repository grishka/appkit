package me.grishka.appkit.imageloader.processing;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.Log;

import java.util.Objects;

import me.grishka.appkit.imageloader.StackBlur;

public class BlurImageProcessingStep extends ImageProcessingStep{
	private static final String TAG="appkit-img-loader";
	private final int radius;

	public BlurImageProcessingStep(int radius){
		this.radius=radius;
	}

	@Override
	public Drawable processDrawable(Drawable input){
		if(input instanceof BitmapDrawable){
			Bitmap bmp=((BitmapDrawable) input).getBitmap();
			if(Build.VERSION.SDK_INT>=29 && bmp.getConfig()==Bitmap.Config.HARDWARE){
				Log.w(TAG, "Can't blur a bitmap with HARDWARE config");
				return input;
			}
			Bitmap output=Bitmap.createBitmap(bmp.getWidth(), bmp.getHeight(), Bitmap.Config.ARGB_8888);
			new Canvas(output).drawBitmap(bmp, 0, 0, null);
			StackBlur.blurBitmap(output, radius);
			return new BitmapDrawable(output);
		}
		return input;
	}

	@Override
	public String getMemoryCacheKey(){
		return "blur"+radius;
	}

	@Override
	public boolean equals(Object o){
		if(this==o) return true;
		if(o==null || getClass()!=o.getClass()) return false;
		BlurImageProcessingStep that=(BlurImageProcessingStep) o;
		return radius==that.radius;
	}

	@Override
	public int hashCode(){
		return Objects.hash(radius);
	}
}
