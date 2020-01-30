package me.grishka.appkit.imageloader;

import android.graphics.Bitmap;

import java.util.ArrayList;

public class MergeImageLoaderAdapter implements ListImageLoaderAdapter {

	private ArrayList<ListImageLoaderAdapter> pieces=new ArrayList<ListImageLoaderAdapter>();
	
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
	public String getImageURL(int position, int image) {
		for (ListImageLoaderAdapter piece : pieces) {
            int size = piece.getCount();

            if (position < size) {
                return piece.getImageURL(position, image);
            }

            position -= size;
        }
		return null;
	}

	@Override
	public void imageLoaded(int position, int image, Bitmap bitmap) {
		int _pos=position;
		for (ListImageLoaderAdapter piece : pieces) {
            int size = piece.getCount();

            if (position < size) {
                //piece.imageLoaded(position, image, bitmap);
            	piece.imageLoaded(_pos, image, bitmap);
                return;
            }

            position -= size;
        }
	}

}
