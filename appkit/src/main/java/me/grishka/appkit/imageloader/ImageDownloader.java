package me.grishka.appkit.imageloader;

import android.graphics.Bitmap;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Created by grishka on 28.07.15.
 */
public abstract class ImageDownloader{

	/**
	 * Check whether this downloader's output can be cached as a file.
	 * @return
	 */
	public abstract boolean isFileBased();

	/**
	 * Download given image into a given output stream.
	 * Does nothing if this downloader is not file-based.
	 * @param uri URI of an image to download
	 * @param to an output stream into which the image should be written
	 */
	public boolean downloadFile(String uri, OutputStream to, ImageCache.ProgressCallback callback, ImageCache.RequestWrapper wrapper) throws IOException{
		return false;
	}

	/**
	 * Get a bitmap from an URI.
	 * @param uri
	 * @return
	 */
	public Bitmap getBitmap(String uri, boolean decode, ImageCache.RequestWrapper wrapper) throws IOException {
		return null;
	}
}
