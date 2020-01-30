package me.grishka.appkit.imageloader;

import android.graphics.Bitmap;
import android.net.Uri;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Created by grishka on 28.07.15.
 */
public class FileImageDownloader extends ImageDownloader {

	private ImageCache cache;

	public FileImageDownloader(ImageCache cache){
		this.cache=cache;
	}

	@Override
	public boolean isFileBased(){
		return false;
	}

	@Override
	public Bitmap getBitmap(String uri, boolean decode, ImageCache.RequestWrapper wrapper) throws IOException{
		if(!decode)
			return null;

		int maxW=0, maxH=0;
		if(uri.startsWith("file://")){
			Uri u=Uri.parse(uri);
			uri=u.getPath();
			if(u.getQueryParameter("max_w")!=null){
				maxW=Integer.parseInt(u.getQueryParameter("max_w"));
			}
			if(u.getQueryParameter("max_h")!=null){
				maxH=Integer.parseInt(u.getQueryParameter("max_h"));
			}
		}
		InputStream in=new FileInputStream(new File(uri));
		Bitmap bmp=cache.decodeImage(in, maxW, maxH);
		in.close();
		return bmp;
	}
}
