package me.grishka.appkit.imageloader;

/**
 * Created by grishka on 19.08.15.
 */
public interface ObservableListImageLoaderAdapter extends ListImageLoaderAdapter {
	void addDataSetObserver(ListImageLoaderWrapper.DataSetObserver observer);
	void removeDataSetObserver(ListImageLoaderWrapper.DataSetObserver observer);
}
