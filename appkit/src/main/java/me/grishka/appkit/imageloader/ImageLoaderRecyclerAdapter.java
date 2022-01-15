package me.grishka.appkit.imageloader;

import me.grishka.appkit.imageloader.requests.ImageLoaderRequest;

/**
 * Created by grishka on 02.07.15.
 */
public interface ImageLoaderRecyclerAdapter{
	int getImageCountForItem(int position);
	ImageLoaderRequest getImageRequest(int position, int image);
}
