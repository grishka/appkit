package me.grishka.appkit.imageloader;

import android.graphics.Bitmap;
import android.net.Uri;

import java.io.IOException;
import java.io.InputStream;

/**
 * Created by grishka on 28.07.15.
 */
public class ContentImageDownloader extends ImageDownloader {

	private ImageCache cache;

	public ContentImageDownloader(ImageCache cache){
		this.cache=cache;
	}

	@Override
	public boolean isFileBased(){
		return false;
	}

	@Override
	public Bitmap getBitmap(String uri, boolean decode, ImageCache.RequestWrapper wrapper) throws IOException {
		if(!decode)
			return null;

		InputStream in=cache.getAppContext().getContentResolver().openInputStream(Uri.parse(uri));
		Bitmap bmp=cache.decodeImage(in, 0, 0);
		in.close();
		return bmp;
	}
}
