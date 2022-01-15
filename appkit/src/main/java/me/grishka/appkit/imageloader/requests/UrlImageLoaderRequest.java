package me.grishka.appkit.imageloader.requests;

import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

import me.grishka.appkit.imageloader.processing.ImageProcessingStep;

/**
 * An image loader request to load an image from a URL.
 */
public class UrlImageLoaderRequest extends ImageLoaderRequest{
	public final Uri uri;
	private final String memoryCacheKey, diskCacheKey;

	public UrlImageLoaderRequest(Bitmap.Config desiredConfig, int desiredWidth, int desiredHeight, List<ImageProcessingStep> processingSteps, Uri uri){
		super(desiredConfig, desiredWidth, desiredHeight, processingSteps);
		this.uri=uri;
		StringBuilder sb=new StringBuilder();
		sb.append(desiredConfig).append('_').append(desiredWidth).append('_').append(desiredHeight).append('_').append(uri);
		for(ImageProcessingStep processingStep:processingSteps)
			sb.append('_').append(processingStep.getMemoryCacheKey());
		memoryCacheKey=sb.toString();
		diskCacheKey=hashString(uri.toString());
	}

	public UrlImageLoaderRequest(Uri uri){
		this(Build.VERSION.SDK_INT>=Build.VERSION_CODES.P ? Bitmap.Config.HARDWARE : Bitmap.Config.ARGB_8888, 0, 0, Collections.emptyList(), uri);
	}

	public UrlImageLoaderRequest(Uri uri, int width, int height){
		this(Build.VERSION.SDK_INT>=Build.VERSION_CODES.P ? Bitmap.Config.HARDWARE : Bitmap.Config.ARGB_8888, width, height, Collections.emptyList(), uri);
	}

	public UrlImageLoaderRequest(String uri){
		this(Uri.parse(uri));
	}

	public UrlImageLoaderRequest(String uri, int width, int height){
		this(Uri.parse(uri), width, height);
	}

	@Override
	public String getMemoryCacheKey(){
		return memoryCacheKey;
	}

	@Override
	public String getDiskCacheKey(){
		return diskCacheKey;
	}

	@Override
	public boolean sourcesEqual(ImageLoaderRequest other){
		if(!(other instanceof UrlImageLoaderRequest))
			return false;
		return uri.equals(((UrlImageLoaderRequest) other).uri);
	}

	@Override
	public boolean equals(Object o){
		if(this==o) return true;
		if(o==null || getClass()!=o.getClass()) return false;
		if(!super.equals(o)) return false;
		UrlImageLoaderRequest that=(UrlImageLoaderRequest) o;
		return uri.equals(that.uri);
	}

	@Override
	public int hashCode(){
		return Objects.hash(super.hashCode(), uri);
	}

	@Override
	public String toString(){
		return "UrlImageLoaderRequest{"+
				"desiredConfig="+desiredConfig+
				", desiredWidth="+desiredMaxWidth+
				", desiredHeight="+desiredMaxHeight+
				", uri="+uri+
				", memoryCacheKey='"+memoryCacheKey+'\''+
				", diskCacheKey='"+diskCacheKey+'\''+
				'}';
	}
}
