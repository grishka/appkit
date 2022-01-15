package me.grishka.appkit.imageloader.downloaders;

import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;

import java.io.IOException;
import java.io.OutputStream;

import me.grishka.appkit.imageloader.ImageCache;
import me.grishka.appkit.imageloader.requests.ImageLoaderRequest;

/**
 * Created by grishka on 28.07.15.
 */
public abstract class ImageDownloader{

	/**
	 * Check whether this downloader loads an image from a remote source and thus needs disk cache.
	 * @return
	 */
	public abstract boolean needsDiskCache();
	public abstract boolean canHandleRequest(ImageLoaderRequest req);

	/**
	 * Download given image into a given output stream.
	 * Does nothing if this downloader doesn't need disk cache.
	 * @param req the request
	 * @param to an output stream into which the image should be written
	 */
	public boolean downloadFile(ImageLoaderRequest req, OutputStream to, ImageCache.ProgressCallback callback, ImageCache.RequestWrapper wrapper) throws IOException{
		return false;
	}

	/**
	 * Get a bitmap for a request.
	 * @param req the request
	 * @return
	 */
	public Drawable getDrawable(ImageLoaderRequest req, boolean decode, ImageCache.RequestWrapper wrapper) throws IOException {
		return null;
	}
}
