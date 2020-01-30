package me.grishka.appkit.utils;

import android.util.SparseIntArray;

import androidx.recyclerview.widget.RecyclerView;

public class AutoAssignMaxRecycledViewPool extends RecyclerView.RecycledViewPool {

	final private SparseIntArray mMaxScrap = new SparseIntArray();
	final private int mMaxSize;

	public AutoAssignMaxRecycledViewPool(int maxSize) {
		mMaxSize = maxSize;
	}

	@Override
	public void setMaxRecycledViews(int viewType, int max) {
		mMaxScrap.put(viewType, max);
		super.setMaxRecycledViews(viewType, max);
	}

	@Override
	public void putRecycledView(RecyclerView.ViewHolder scrap) {
		final int viewType = scrap.getItemViewType();
		final int max = mMaxScrap.get(viewType, -1);
		if (max == -1) {
			setMaxRecycledViews(viewType, mMaxSize);
		}
		super.putRecycledView(scrap);
	}

	@Override
	public void clear() {
		mMaxScrap.clear();
		super.clear();
	}
}