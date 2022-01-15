package me.grishka.appkit.imageloader.downloaders;

import android.content.Context;
import android.graphics.drawable.Drawable;

import java.io.IOException;
import java.io.InputStream;

import me.grishka.appkit.imageloader.ImageCache;
import me.grishka.appkit.imageloader.requests.ImageLoaderRequest;
import me.grishka.appkit.imageloader.requests.UrlImageLoaderRequest;

/**
 * Created by grishka on 28.07.15.
 */
public class ContentImageDownloader extends ImageDownloader {

	private ImageCache cache;
	private Context context;

	public ContentImageDownloader(ImageCache cache, Context context){
		this.cache=cache;
		this.context=context;
	}

	@Override
	public boolean needsDiskCache(){
		return false;
	}

	@Override
	public boolean canHandleRequest(ImageLoaderRequest req){
		return req instanceof UrlImageLoaderRequest && ((UrlImageLoaderRequest) req).uri.getScheme().equals("content");
	}

	@Override
	public Drawable getDrawable(ImageLoaderRequest _req, boolean decode, ImageCache.RequestWrapper wrapper) throws IOException {
		if(!decode)
			return null;

		UrlImageLoaderRequest req=(UrlImageLoaderRequest)_req;
		return cache.decodeImage(null, req.uri, req);
	}
}
