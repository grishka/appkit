package me.grishka.appkit.imageloader.requests;

import android.graphics.Bitmap;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Objects;

import me.grishka.appkit.imageloader.processing.ImageProcessingStep;

/**
 * A base class for an image loader request.
 */
public abstract class ImageLoaderRequest{
	public final Bitmap.Config desiredConfig;
	public final int desiredMaxWidth;
	public final int desiredMaxHeight;
	public final List<ImageProcessingStep> processingSteps;

	private static final MessageDigest MD5;

	static{
		try{
			MD5=MessageDigest.getInstance("MD5");
		}catch(NoSuchAlgorithmException e){
			throw new RuntimeException(e);
		}
	}

	public ImageLoaderRequest(Bitmap.Config desiredConfig, int desiredMaxWidth, int desiredMaxHeight, List<ImageProcessingStep> processingSteps){
		this.desiredConfig=desiredConfig;
		this.desiredMaxWidth=desiredMaxWidth;
		this.desiredMaxHeight=desiredMaxHeight;
		this.processingSteps=processingSteps;
	}

	/**
	 * Get the key that uniquely identifies the drawable loaded by this request.
	 * This should include the size, the config, and any other attributes that change the resulting drawable.
	 * @return an arbitrary string to uniquely identify the drawable
	 */
	public abstract String getMemoryCacheKey();

	/**
	 * Get the key that uniquely identifies the <b>file</b> downloaded by this request.
	 * This should <b>only</b> be influenced by the location from which the file is downloaded.
	 * @return a string to identify the file location, must be safe to use as part of a file name
	 */
	public abstract String getDiskCacheKey();

	public abstract boolean sourcesEqual(ImageLoaderRequest other);

	@Override
	public boolean equals(Object o){
		if(this==o) return true;
		if(o==null || getClass()!=o.getClass()) return false;
		ImageLoaderRequest that=(ImageLoaderRequest) o;
		return desiredMaxWidth==that.desiredMaxWidth && desiredMaxHeight==that.desiredMaxHeight && desiredConfig==that.desiredConfig && processingSteps.equals(that.processingSteps);
	}

	@Override
	public int hashCode(){
		return Objects.hash(desiredConfig, desiredMaxWidth, desiredMaxHeight, processingSteps);
	}

	protected String hashString(String s){
		char[] chars=new char[32];
		String hexDigits="0123456789abcdef";
		int offset=0;
		for(byte b:MD5.digest(s.getBytes(StandardCharsets.UTF_8))){
			int i=(int)b & 0xff;
			chars[offset++]=hexDigits.charAt(i >> 4);
			chars[offset++]=hexDigits.charAt(i & 0x0f);
		}
		return String.valueOf(chars);
	}
}
