package me.grishka.appkit.api;

import me.grishka.appkit.fragments.BaseRecyclerFragment;

public class PaginatedListCallback<I, T extends PaginatedList<I>> extends SimpleCallback<T>{

	public PaginatedListCallback(BaseRecyclerFragment<I> fragment){
		super(fragment);
	}

	@Override
	public void onSuccess(T result){
		BaseRecyclerFragment<I> f=(BaseRecyclerFragment<I>) fragment;
		f.onDataLoaded(result);
	}
}
