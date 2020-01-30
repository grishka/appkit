package me.grishka.appkit.utils;

import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by grishka on 17.09.15.
 */
public class Preloader<T> {
	protected ArrayList<T> data=new ArrayList<>();
	protected ArrayList<T> preloadedData=new ArrayList<>();
	protected boolean preloading, preloadOnReady, moreAvailable;
	private View footerProgress, footerError;
	private boolean footerVisible=true;
	private Callback<T> callback;
	private int itemsPerPage;

	public Preloader(Callback<T> callback, int itemsPerPage) {
		this.callback = callback;
		this.itemsPerPage=itemsPerPage;
	}

	public ArrayList<T> getData(){
		return data;
	}

	public ArrayList<T> getPreloadedData(){
		return preloadedData;
	}

	public void setMoreAvailable(boolean a){
		moreAvailable=a;
	}

	public boolean isMoreAvailable(){
		return moreAvailable;
	}

	public void setFooterViews(View progress, View error){
		footerProgress=progress;
		footerError=error;
		if(footerProgress!=null && footerError!=null){
			footerProgress.setVisibility(moreAvailable ? View.VISIBLE : View.GONE);
			if(footerVisible!=moreAvailable){
				footerVisible=moreAvailable;
			}
		}
	}

	public void onScrolledToLastItem(){
		if(!callback.isDataLoading() || preloading){
			if(preloading){
				preloading=false;
				preloadOnReady=true;
			}else if(preloadedData.size()>0){
				data.addAll(preloadedData);
				callback.onAppendItems(preloadedData);
				callback.updateList();
				preloadedData.clear();
				if(moreAvailable) {
					preloading = true;
					callback.loadData(data.size(), itemsPerPage);
				}
			}else if(moreAvailable){
				callback.loadData(data.size(), itemsPerPage*2);
			}
		}
	}

	public boolean isFooterVisible(){
		return footerVisible;
	}

	public void onDataLoaded(List<T> d, boolean more){
		if(callback.isRefreshing()){
			data.clear();
			preloadedData.clear();
			callback.onClearItems();
		}
		if(preloading){
			preloadedData.addAll(d);
		}else if(d.size()>itemsPerPage && more){
			data.addAll(d.subList(0, itemsPerPage));
			callback.onAppendItems(d.subList(0, itemsPerPage));
			preloadedData.addAll(d.subList(itemsPerPage, d.size()));
		}else{
			data.addAll(d);
			callback.onAppendItems(d);
		}
		preloading=false;
		if(preloadOnReady){
			preloading=true;
			preloadOnReady=false;
			callback.loadData(data.size(), itemsPerPage*2);
		}
		callback.updateList();
		moreAvailable=more;

		if(footerProgress==null){
			callback.updateList();
			return;
		}
		footerProgress.setVisibility(moreAvailable ? View.VISIBLE : View.GONE);
		if(footerVisible!=moreAvailable){
			footerVisible=moreAvailable;
		}
		callback.updateList();
	}

	public interface Callback<T>{
		void updateList();
		void loadData(int offset, int count);
		void onAppendItems(List<T> items);
		void onClearItems();
		boolean isDataLoading();
		boolean isRefreshing();
	}
}
