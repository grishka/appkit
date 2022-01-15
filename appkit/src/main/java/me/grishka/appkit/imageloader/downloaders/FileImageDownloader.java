package me.grishka.appkit.imageloader.downloaders;

import android.graphics.drawable.Drawable;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import me.grishka.appkit.imageloader.ImageCache;
import me.grishka.appkit.imageloader.requests.ImageLoaderRequest;
import me.grishka.appkit.imageloader.requests.UrlImageLoaderRequest;

/**
 * Created by grishka on 28.07.15.
 */
public class FileImageDownloader extends ImageDownloader {

	private ImageCache cache;

	public FileImageDownloader(ImageCache cache){
		this.cache=cache;
	}

	@Override
	public boolean canHandleRequest(ImageLoaderRequest req){
		return req instanceof UrlImageLoaderRequest && ((UrlImageLoaderRequest) req).uri.getScheme().equals("file");
	}

	@Override
	public boolean needsDiskCache(){
		return false;
	}

	@Override
	public Drawable getDrawable(ImageLoaderRequest _req, boolean decode, ImageCache.RequestWrapper wrapper) throws IOException{
		if(!decode)
			return null;

		UrlImageLoaderRequest req=(UrlImageLoaderRequest) _req;
		return cache.decodeImage(new File(req.uri.getPath()), null, req);
	}
}
