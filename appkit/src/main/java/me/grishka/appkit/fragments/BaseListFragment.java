package me.grishka.appkit.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.ListAdapter;
import android.widget.ListView;

import java.util.ArrayList;
import java.util.List;

import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import me.grishka.appkit.R;
import me.grishka.appkit.api.ErrorResponse;
import me.grishka.appkit.api.PaginatedList;
import me.grishka.appkit.imageloader.ListImageLoaderAdapter;
import me.grishka.appkit.imageloader.ListImageLoaderWrapper;
import me.grishka.appkit.utils.V;

/**
 * Created by grishka on 16.06.15.
 */
public abstract class BaseListFragment<T> extends LoaderFragment implements ListImageLoaderWrapper.Listener, AdapterView.OnItemClickListener, AdapterView.OnItemLongClickListener {

	protected int itemsPerPage;
	protected ListView list;
	protected View emptyView;
	protected SwipeRefreshLayout refreshLayout;
	protected ArrayList<T> data=new ArrayList<>();
	protected ArrayList<T> preloadedData=new ArrayList<>();
	protected boolean preloading, preloadOnReady, moreAvailable;
	protected View footerView;
	protected View footerProgress, footerError;
	protected boolean preloadingFailed=false;
	protected FrameLayout contentWrap;

	protected ListImageLoaderWrapper imgLoader;
	protected boolean refreshing=false;
	private boolean refreshEnabled=true;
	private boolean refreshAfterCreate=false;
	private AbsListView.OnScrollListener ptrScrollListener=new AbsListView.OnScrollListener() {
		@Override
		public void onScrollStateChanged(AbsListView absListView, int state) {

		}

		@Override
		public void onScroll(AbsListView absListView, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
			int topRowVerticalPosition =
					(list == null || list.getChildCount() == 0) ?
							0 : list.getChildAt(0).getTop();
			refreshLayout.setEnabled(firstVisibleItem == 0 && topRowVerticalPosition >= 0);
		}
	};

	public BaseListFragment(int perPage){
		itemsPerPage=perPage;
	}

	protected View onCreateFooterView(LayoutInflater inflater){
		return inflater.inflate(R.layout.appkit_load_more, null);
	}

	@Override
	public View onCreateContentView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState){
		View view=inflater.inflate(R.layout.list_fragment, null);

		list=(ListView)view.findViewById(R.id.list);
		emptyView=view.findViewById(R.id.empty);
		refreshLayout = (SwipeRefreshLayout) view.findViewById(R.id.refresh_layout);
		contentWrap = (FrameLayout) view.findViewById(R.id.content_wrap);

		refreshLayout.setOnRefreshListener(this);
		refreshLayout.setEnabled(refreshEnabled);
		list.setEmptyView(emptyView);
		ListAdapter adapter=getAdapter();
		if(adapter instanceof ListImageLoaderAdapter){
			imgLoader=new ListImageLoaderWrapper(getActivity(), (ListImageLoaderAdapter) adapter, list, this);
		}
		footerView=onCreateFooterView(inflater);
		footerProgress=footerView.findViewById(R.id.load_more_progress);
		footerError=footerView.findViewById(R.id.load_more_error);
		footerError.setVisibility(View.GONE);
		list.addFooterView(footerView, null, false);
		list.setAdapter(adapter);
		list.setOnItemClickListener(this);
		list.setOnItemLongClickListener(this);
		if(imgLoader!=null){
			imgLoader.setOnScrollListener(ptrScrollListener);
		}else{
			list.setOnScrollListener(ptrScrollListener);
		}

		footerError.findViewById(R.id.error_retry).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				onErrorRetryClick();
			}
		});

		if(refreshAfterCreate)
			refresh();

		return view;
	}

	@Override
	protected void onErrorRetryClick() {
		if(preloadingFailed){
			preloadingFailed=false;
			V.setVisibilityAnimated(footerProgress, View.VISIBLE);
			V.setVisibilityAnimated(footerError, View.GONE);
			onScrolledToLastItem();
			return;
		}
		super.onErrorRetryClick();
	}

	@Override
	public void onRefresh() {
		refreshing=true;
		footerError.setVisibility(View.GONE);
		footerProgress.setVisibility(View.VISIBLE);
		preloadingFailed=false;
		doLoadData();
	}

	protected abstract ListAdapter getAdapter();

	@Override
	public void onScrolledToLastItem() {
		if(refreshing || preloadingFailed) return;
		if((!dataLoading || preloading) && moreAvailable){
			if(preloading){
				preloading=false;
				preloadOnReady=true;
			}else if(preloadedData.size()>0){
				data.addAll(preloadedData);
				onAppendItems(preloadedData);
				updateList();
				preloadedData.clear();
				preloading=true;
				loadData(data.size(), itemsPerPage);
			}else{
				loadData(data.size(), itemsPerPage*2);
			}
		}
	}

	@Override
	public void onScrollStarted() {

	}

	@Override
	public void onScrollStopped() {

	}

	protected void onAppendItems(List<T> items){

	}

	protected void onPrependItems(List<T> items){

	}

	protected void onClearItems(){

	}

	protected void setRefreshEnabled(boolean enabled){
		refreshEnabled=enabled;
		if(refreshLayout !=null)
			refreshLayout.setEnabled(enabled);
	}

	protected void cancelLoading(){
		if(currentRequest!=null){
			currentRequest.cancel();
			currentRequest=null;
		}
	}

	public void reload(){
		refreshing=true;
		loadData();
	}

	public void refreshDone(){
		refreshing=false;
		refreshLayout.setRefreshing(false);
		refreshLayout.setEnabled(refreshEnabled);
	}

	protected void onDataLoaded(List<T> d){
		dataLoading=false;
		currentRequest=null;
		loaded=true;
		data.clear();
		data.addAll(d);
		updateList();
		if(list==null) return;
		if(refreshing)
			refreshDone();
		V.setVisibilityAnimated(refreshLayout, View.VISIBLE);
		V.setVisibilityAnimated(progress, View.GONE);
	}

	public void updateList(){
		((BaseAdapter)getAdapter()).notifyDataSetChanged();
		if(imgLoader!=null)
			imgLoader.updateImages();
	}

	@Override
	public void onError(ErrorResponse error){
		dataLoading=false;
		currentRequest=null;
		if(errorView==null) return;
		if(refreshing)
			refreshDone();
		if(refreshing) {
			error.showToast(getActivity());
		}else if(data.size()>0){
			preloadingFailed=true;
			error.bindErrorView(footerError);
			V.setVisibilityAnimated(footerError, View.VISIBLE);
			V.setVisibilityAnimated(footerProgress, View.GONE);
		}else{
			super.onError(error);
		}
	}

	@Override
	public void onDestroyView(){
		super.onDestroyView();
		list=null;
		emptyView=null;
		progress=null;
		errorView=null;
		if(imgLoader!=null)
			imgLoader.deactivate();
		imgLoader=null;
	}

	protected String getEmptyText(){
		//return getString(R.string.empty_list);
		return "Empty";
	}

	protected void refresh(){
		if(!loaded){
			loadData();
			return;
		}
		if(refreshLayout !=null){
			refreshLayout.setRefreshing(true);
			refreshLayout.setEnabled(false);
			onRefresh();
			refreshAfterCreate=false;
		}else{
			refreshAfterCreate=true;
		}
	}

	@Override
	protected void doLoadData() {
		//Log.e("appkit", "Load data 2, "+dataLoading);
		//try{throw new Exception("fdsaf");}catch(Exception x){Log.e("appkit", "load data 2", x);}
		doLoadData(0, itemsPerPage*2);
	}

	protected void loadData(int offset, int count){
		//Log.e("appkit", "Load data "+offset+" - "+count+", "+dataLoading);
		dataLoading=true;
		doLoadData(offset, count);
	}

	@Override
	public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
		return false;
	}

	@Override
	public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

	}

	protected abstract void doLoadData(int offset, int count);

	public void onDataLoaded(PaginatedList<T> d){
		onDataLoaded(d, data.size()+preloadedData.size()+d.size()<d.total());
	}

	protected void onDataLoaded(List<T> d, boolean more){
		loaded=true;
		currentRequest=null;
		if(refreshing){
			data.clear();
			preloadedData.clear();
			onClearItems();
		}
		if(preloading){
			preloadedData.addAll(d);
		}else if(d.size()>itemsPerPage && more){
			data.addAll(d.subList(0, itemsPerPage));
			onAppendItems(d.subList(0, itemsPerPage));
			preloadedData.addAll(d.subList(itemsPerPage, d.size()));
		}else{
			data.addAll(d);
			onAppendItems(d);
		}
		preloading=false;
		if(preloadOnReady){
			preloading=true;
			preloadOnReady=false;
			loadData(data.size(), itemsPerPage*2);
		}
		updateList();
		moreAvailable=more;
		dataLoading=false;

		if(refreshing)
			refreshDone();

		updateList();
		if(list==null) return;
		footerProgress.setVisibility(moreAvailable ? View.VISIBLE : View.GONE);
		V.setVisibilityAnimated(refreshLayout, View.VISIBLE);
		V.setVisibilityAnimated(progress, View.GONE);
	}
}
