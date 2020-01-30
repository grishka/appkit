package me.grishka.appkit.fragments;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStub;

import androidx.annotation.LayoutRes;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import me.grishka.appkit.R;
import me.grishka.appkit.api.APIRequest;
import me.grishka.appkit.api.ErrorResponse;
import me.grishka.appkit.utils.V;

/**
 * Created by grishka on 11.06.15.
 */
public abstract class LoaderFragment extends AppKitFragment implements SwipeRefreshLayout.OnRefreshListener {

	private int layoutID;
	protected View errorView;
	protected View progress;
	protected View content;
	protected ViewGroup contentView;
	public boolean loaded;
	protected boolean dataLoading;
	protected APIRequest currentRequest;

	private BroadcastReceiver receiver=new BroadcastReceiver(){
		@Override
		public void onReceive(Context context, Intent intent){
			if(isInitialStickyBroadcast())
				return;
			if(ConnectivityManager.CONNECTIVITY_ACTION.equals(intent.getAction())){
				boolean isConnected=!intent.getBooleanExtra(ConnectivityManager.EXTRA_NO_CONNECTIVITY, false);
				if(isConnected){
					onErrorRetryClick();
				}
			}
		}
	};
	private boolean errorReceiverRegistered=false;
	private boolean autoRetry=true;

	public LoaderFragment(){
		this(R.layout.loader_fragment);
	}

	protected LoaderFragment(@LayoutRes int layout){
		layoutID=layout;
	}

	@LayoutRes
	protected int getLayout() {
		return layoutID;
	}

	protected void setLayout(int id){
		if(content!=null)
			throw new IllegalStateException("Can't set layout when view is already created");
		layoutID=id;
	}

	public abstract View onCreateContentView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState);
	protected abstract void doLoadData();

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		contentView= (ViewGroup) inflater.inflate(layoutID, null);
		View stub=contentView.findViewById(R.id.content_stub);
		ViewGroup stubParent=(ViewGroup)stub.getParent();
		content=onCreateContentView(inflater, stubParent, savedInstanceState);
		content.setLayoutParams(stub.getLayoutParams());
		stubParent.addView(content, stubParent.indexOfChild(stub));
		stubParent.removeView(stub);
		progress=contentView.findViewById(R.id.loading);
		errorView=contentView.findViewById(R.id.error);
		if(errorView instanceof ViewStub){
			errorView=((ViewStub) errorView).inflate();
			errorView.setVisibility(View.GONE);
		}
		content.setVisibility(loaded ? View.VISIBLE : View.GONE);
		progress.setVisibility(loaded ? View.GONE : View.VISIBLE);

		View retryBtn=errorView.findViewById(R.id.error_retry);
		if(retryBtn!=null){
			retryBtn.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View view) {
					onErrorRetryClick();
				}
			});
		}

		return contentView;
	}

	@Override
	public void onDestroyView() {
		super.onDestroyView();
		content=null;
		errorView=null;
		progress=null;
		contentView=null;
	}

	@Override
	public void onDestroy(){
		super.onDestroy();
		if(currentRequest!=null){
			currentRequest.cancel();
			currentRequest=null;
		}
		if(errorReceiverRegistered){
			try{
				getActivity().unregisterReceiver(receiver);
			}catch(Exception x){}
			errorReceiverRegistered=false;
		}
	}

	protected void onErrorRetryClick(){
		V.setVisibilityAnimated(errorView, View.GONE);
		V.setVisibilityAnimated(progress, View.VISIBLE);
		loadData();
	}

	public void loadData(){
		showProgress();
		dataLoading=true;
		doLoadData();
	}

	public void dataLoaded(){
		loaded=true;
		showContent();
	}

	protected void showContent(){
		if(content!=null){
			V.setVisibilityAnimated(content, View.VISIBLE);
			V.setVisibilityAnimated(errorView, View.GONE);
			V.setVisibilityAnimated(progress, View.GONE);
		}
		if(errorReceiverRegistered){
			try{
				getActivity().unregisterReceiver(receiver);
			}catch(Exception x){}
			errorReceiverRegistered=false;
		}
	}

	protected void showProgress(){
		if(content!=null){
			V.setVisibilityAnimated(content, View.GONE);
			V.setVisibilityAnimated(errorView, View.GONE);
			V.setVisibilityAnimated(progress, View.VISIBLE);
		}
		if(errorReceiverRegistered){
			try{
				getActivity().unregisterReceiver(receiver);
			}catch(Exception x){}
			errorReceiverRegistered=false;
		}
	}

	public void onError(ErrorResponse error){
		dataLoading=false;
		currentRequest=null;
		if(errorView==null) return;
		error.bindErrorView(errorView);
		V.setVisibilityAnimated(errorView, View.VISIBLE);
		V.setVisibilityAnimated(progress, View.GONE);
		V.setVisibilityAnimated(content, View.GONE);
		if(errorReceiverRegistered || !autoRetry) return;
		getActivity().registerReceiver(receiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
		errorReceiverRegistered=true;
	}

	public void setRetryOnNetworkConnect(boolean retry){
		autoRetry=retry;
	}

	public boolean isRetryInNetworkConnect(){
		return autoRetry;
	}
}
