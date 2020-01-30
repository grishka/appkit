package me.grishka.appkit.fragments;

import android.app.Activity;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.StringRes;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import me.grishka.appkit.R;
import me.grishka.appkit.api.ErrorResponse;
import me.grishka.appkit.api.PaginatedList;
import me.grishka.appkit.imageloader.ListImageLoaderAdapter;
import me.grishka.appkit.imageloader.ListImageLoaderWrapper;
import me.grishka.appkit.imageloader.RecyclerViewDelegate;
import me.grishka.appkit.utils.Preloader;
import me.grishka.appkit.utils.V;
import me.grishka.appkit.views.EmptyViewCapable;
import me.grishka.appkit.views.UsableRecyclerView;

/**
 * Created by grishka on 16.06.15.
 */
public abstract class BaseRecyclerFragment<T> extends LoaderFragment implements SwipeRefreshLayout.OnRefreshListener, ListImageLoaderWrapper.Listener, Preloader.Callback<T> {

	protected int itemsPerPage;
	protected RecyclerView list;
	protected View emptyView;
	protected SwipeRefreshLayout refreshLayout;

	protected View footerView, footerProgress, footerError;
	protected FrameLayout contentWrap;
	protected Preloader<T> preloader;
	protected ArrayList<T> data, preloadedData;
	protected CharSequence emptyText, emptyButtonText;
	protected boolean emptyButtonVisible;
	protected Button emptyButton;

	protected ListImageLoaderWrapper imgLoader;
	protected boolean refreshing=false;
	private boolean refreshEnabled=true;
	private boolean refreshAfterCreate=false;
	private boolean preloadingFailed=false;

	public void setListLayoutId(int listLayoutId) {
		this.listLayoutId = listLayoutId;
	}

	private int listLayoutId = R.layout.recycler_fragment;

	public BaseRecyclerFragment(int perPage){
		itemsPerPage=perPage;
		preloader=new Preloader<>(this, perPage);
		data=preloader.getData();
		preloadedData=preloader.getPreloadedData();
	}

	@Override
	public void onAttach(Activity activity){
		if(TextUtils.isEmpty(emptyText))
			emptyText=activity.getString(R.string.empty_list);
		super.onAttach(activity);
	}

	public BaseRecyclerFragment(int layout, int perPage){
		super(layout); // it is not this(perPage) !!!
		itemsPerPage=perPage;
		preloader=new Preloader<>(this, perPage);
		data=preloader.getData();
		preloadedData=preloader.getPreloadedData();
	}

	protected View onCreateFooterView(LayoutInflater inflater){
		return inflater.inflate(R.layout.appkit_load_more, null);
	}

	protected int getSpanCount(){
		return 1;
	}

	protected RecyclerView.LayoutManager onCreateLayoutManager(){
		return new GridLayoutManager(getActivity(), getSpanCount());
	}

	@Override
	public View onCreateContentView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState){
		View view=inflater.inflate(listLayoutId, null);

		list=(RecyclerView)view.findViewById(R.id.list);
		emptyView=view.findViewById(R.id.empty);
		if(emptyView instanceof ViewStub){
			emptyView=((ViewStub) emptyView).inflate();
		}
		refreshLayout= (SwipeRefreshLayout) view.findViewById(R.id.refresh_layout);
		contentWrap=(FrameLayout)view.findViewById(R.id.content_wrap);
		((TextView)emptyView.findViewById(R.id.empty_text)).setText(emptyText);
		emptyButton=(Button)emptyView.findViewById(R.id.empty_button);
		if(emptyButton!=null){
			emptyButton.setText(emptyButtonText);
			emptyButton.setVisibility(emptyButtonVisible ? View.VISIBLE : View.GONE);
			emptyButton.setOnClickListener(new View.OnClickListener(){
				@Override
				public void onClick(View v){
					onEmptyViewBtnClick();
				}
			});
		}

		RecyclerView.LayoutManager lmgr=onCreateLayoutManager();
		if(lmgr instanceof GridLayoutManager){
			final GridLayoutManager.SpanSizeLookup prevLookup=((GridLayoutManager) lmgr).getSpanSizeLookup();
			((GridLayoutManager) lmgr).setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
				@Override
				public int getSpanSize(int pos) {
					if(list==null) return 1;
					if (list instanceof UsableRecyclerView && pos == ((UsableRecyclerView)list).getRealAdapter().getItemCount() - 1 && preloader.isFooterVisible() && footerView!=null)
						return ((GridLayoutManager) list.getLayoutManager()).getSpanCount();
					return prevLookup==null ? 1 : prevLookup.getSpanSize(pos);
				}
			});
		}
		if(lmgr!=null)
			list.setLayoutManager(lmgr);
		list.setHasFixedSize(true);

		refreshLayout.setOnRefreshListener(this);
		refreshLayout.setEnabled(refreshEnabled);
		if(list instanceof EmptyViewCapable)
			((EmptyViewCapable)list).setEmptyView(emptyView);
		if(list instanceof ListImageLoaderAdapter){
			imgLoader=new ListImageLoaderWrapper(getActivity(), (ListImageLoaderAdapter) list, new RecyclerViewDelegate(list), this);
		}
		RecyclerView.Adapter adapter=getAdapter();
		footerView=onCreateFooterView(inflater);
		list.setAdapter(adapter);
		if(footerView!=null) {
			footerProgress = footerView.findViewById(R.id.load_more_progress);
			footerError = footerView.findViewById(R.id.load_more_error);
			footerError.setVisibility(View.GONE);
			if (list instanceof UsableRecyclerView)
				((UsableRecyclerView) list).addFooterView(footerView);

			footerError.findViewById(R.id.error_retry).setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View view) {
					onErrorRetryClick();
				}
			});
			preloader.setFooterViews(footerProgress, footerError);
		}

		if(refreshAfterCreate)
			refresh();

		return view;
	}

	protected void beforeSetAdapter(){
		/*footerView=new LoadMoreFooterView(getActivity());
		list.addFooterView(footerView, null, false);

		super.beforeSetAdapter();
		footerView.setVisible(moreAvailable);*/
	}

	protected abstract void doLoadData(int offset, int count);
	protected abstract RecyclerView.Adapter getAdapter();

	public void onAppendItems(List<T> items){

	}

	protected void onPrependItems(List<T> items){

	}

	public void onClearItems(){

	}

	@Override
	public void onScrolledToLastItem() {
		if(refreshing || preloadingFailed) return;
		preloader.onScrolledToLastItem();
	}

	@Override
	public void onScrollStarted() {

	}

	@Override
	public void onScrollStopped() {

	}

	protected void onEmptyViewBtnClick(){

	}

	protected void setRefreshEnabled(boolean enabled){
		refreshEnabled=enabled;
		if(refreshLayout!=null)
			refreshLayout.setEnabled(enabled);
	}

	protected void cancelLoading(){
		if(currentRequest!=null){
			currentRequest.cancel();
			currentRequest=null;
		}
	}

	public void reload(){
		loaded=false;
		data.clear();
		onClearItems();
		showProgress();
		loadData();
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
		V.setVisibilityAnimated(errorView, View.GONE);
	}

	public void updateList(){
		if(list!=null && list.getAdapter()!=null)
			list.getAdapter().notifyDataSetChanged();
		if(imgLoader!=null)
			imgLoader.updateImages();
	}

	protected ListImageLoaderAdapter getImageLoaderAdapter(){
		if(list instanceof ListImageLoaderAdapter)
			return (ListImageLoaderAdapter)list;
		return null;
	}

	@Override
	public void onDestroyView(){
		super.onDestroyView();
		if(list!=null){
			list.setAdapter(null);
		}
		list=null;
		emptyView=null;
		emptyButton=null;
		progress=null;
		errorView=null;
		contentWrap=null;
		footerView=footerProgress=footerError=null;
		refreshLayout=null;
		if(imgLoader!=null)
			imgLoader.deactivate();
		imgLoader=null;
	}

	@Override
	public void onRefresh() {
		refreshing=true;
		if(footerView!=null) {
			footerError.setVisibility(View.GONE);
			footerProgress.setVisibility(View.VISIBLE);
		}
		preloadingFailed=false;
		doLoadData();
	}

	public void onDataLoaded(PaginatedList<T> d){
		int curSize=refreshing ? 0 : (data.size()+preloadedData.size());
		onDataLoaded(d, curSize+d.size()<d.total());
	}

	protected void onDataLoaded(List<T> d, boolean more){
		loaded=true;
		currentRequest=null;
		if(refreshing){
			data.clear();
			preloadedData.clear();
			onClearItems();
		}
		dataLoading=false;

		preloader.onDataLoaded(d, more);

		if(refreshing)
			refreshDone();

		V.setVisibilityAnimated(refreshLayout, View.VISIBLE);
		V.setVisibilityAnimated(progress, View.GONE);
	}

	protected void setEmptyText(@StringRes int text){
		setEmptyText(getString(text));
	}

	protected void setEmptyText(CharSequence text){
		emptyText=text;
		if(emptyView!=null)
			((TextView)emptyView.findViewById(R.id.empty_text)).setText(text);
	}

	protected void setEmptyButtonText(@StringRes int text){
		setEmptyButtonText(getString(text));
	}

	protected void setEmptyButtonText(CharSequence text){
		emptyButtonText=text;
		if(emptyButton!=null)
			emptyButton.setText(text);
	}

	protected void setEmptyButtonVisible(boolean visible){
		emptyButtonVisible=visible;
		if(emptyButton!=null)
			emptyButton.setVisibility(visible ? View.VISIBLE : View.GONE);
	}

	protected void refresh(){
		if(!loaded){
			loadData();
			return;
		}
		if(refreshLayout!=null){
			refreshLayout.post(new Runnable() {
				@Override
				public void run() {
					refreshLayout.setRefreshing(true);
					refreshLayout.setEnabled(false);
				}
			});
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

	public void loadData(int offset, int count){
		//Log.e("appkit", "Load data "+offset+" - "+count+", "+dataLoading);
		dataLoading=true;
		doLoadData(offset, count);
	}

	@Override
	public boolean isDataLoading() {
		return dataLoading;
	}

	@Override
	public boolean isRefreshing() {
		return refreshing;
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

	public void refreshDone(){
		refreshing=false;
		if(refreshLayout==null)
			return;
		refreshLayout.setRefreshing(false);
		refreshLayout.setEnabled(refreshEnabled);
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
}