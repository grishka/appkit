package me.grishka.appkit.imageloader;

import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;

import java.util.ArrayList;

import me.grishka.appkit.imageloader.requests.ImageLoaderRequest;

public class MergeImageLoaderAdapter implements ListImageLoaderAdapter {

	private ArrayList<ListImageLoaderAdapter> pieces=new ArrayList<>();
	
	public MergeImageLoaderAdapter() {
		// TODO Auto-generated constructor stub
	}
	
	public void addAdapter(ListImageLoaderAdapter adapter) {
        pieces.add(adapter);
    }

	@Override
	public int getCount() {
		 int total = 0;

	        for (ListImageLoaderAdapter piece : pieces) {
	            total += piece.getCount();
	        }

	        return (total);
	}

	@Override
	public int getImageCountForItem(int position) {
		for (ListImageLoaderAdapter piece : pieces) {
            int size = piece.getCount();

            if (position < size) {
                return piece.getImageCountForItem(position);
            }

            position -= size;
        }
		return 0;
	}

	@Override
	public ImageLoaderRequest getImageRequest(int position, int image) {
		for (ListImageLoaderAdapter piece : pieces) {
            int size = piece.getCount();

            if (position < size) {
                return piece.getImageRequest(position, image);
            }

            position -= size;
        }
		return null;
	}

	@Override
	public void imageLoaded(int position, int image, Drawable drawable) {
		int _pos=position;
		for (ListImageLoaderAdapter piece : pieces) {
            int size = piece.getCount();

            if (position < size) {
                //piece.imageLoaded(position, image, bitmap);
            	piece.imageLoaded(_pos, image, drawable);
                return;
            }

            position -= size;
        }
	}

}
