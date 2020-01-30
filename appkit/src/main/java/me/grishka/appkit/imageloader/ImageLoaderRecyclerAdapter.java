package me.grishka.appkit.imageloader;

/**
 * Created by grishka on 02.07.15.
 */
public interface ImageLoaderRecyclerAdapter{
	public int getImageCountForItem(int position);
	public String getImageURL(int position, int image);
}
