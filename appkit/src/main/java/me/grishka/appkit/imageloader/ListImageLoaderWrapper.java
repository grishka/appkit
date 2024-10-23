package me.grishka.appkit.imageloader;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.ImageView;

import java.util.Objects;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import me.grishka.appkit.imageloader.requests.ImageLoaderRequest;
import me.grishka.appkit.utils.VelocityTracker1D;

public class ListImageLoaderWrapper{

	private ListImageLoader imgLoader;
	private RecyclerView list;
	private Listener listener;
	private int viStart, viCount;
	private boolean lastScrollFwd=false;
	private int prevVisLast, prevVisFirst;
	private long lastChangeTime=0;
	private boolean wasFastScrolling=false;
	private Runnable loadRunnable;
	private Runnable scrollStopRunnable;
	private Context context;
	private DataSetObserver observer=new DataSetObserver(){
		@Override
		public void onEverythingChanged(){
			imgLoader.clearFailedRequests();
			updateImages();
		}

		@Override
		public void onItemRangeChanged(int position, int count){
			reloadRange(position, count);
		}

		@Override
		public void onItemRangeInserted(int position, int count){
//			imgLoader.offsetRange(position+count, list.getLayoutManager().getItemCount(), count);
//			reloadRange(position, count);
			updateImages();
		}
	};
	private RecyclerView.OnScrollListener scrollListener=new RecyclerView.OnScrollListener(){
		@Override
		public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState){
			realScrollStateChanged(newState);
		}

		@Override
		public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy){
			onScroll(getFirstVisiblePosition(), getVisibleItemCount(), list.getLayoutManager().getItemCount(), dx, dy);
		}
	};
	private float prefetchScreens=1;
	private boolean isActive=true;
	private int lastScrollState;
	private final VelocityTracker1D velocityTrackerX=new VelocityTracker1D(), velocityTrackerY=new VelocityTracker1D();
	private int scrollOffsetX, scrollOffsetY;
	private final float displayDensity;
	// When the list is being scrolled faster than this, image loading is paused
	private float fastScrollVelocityThreshold=3000; // dp/s
	
	private static final String TAG="appkit-img-wrapper";

	public ListImageLoaderWrapper(Context context, ListImageLoaderAdapter adapter, RecyclerView listView, Listener listener){
		imgLoader=new ListImageLoader();
		imgLoader.setAdapter(adapter);
		this.listener=listener;
		this.context=context;
		list=listView;
		list.addOnScrollListener(scrollListener);
		displayDensity=list.getResources().getDisplayMetrics().density;
		ImageCache.getInstance(context).registerLoader(this);
		if(adapter instanceof ObservableListImageLoaderAdapter)
			((ObservableListImageLoaderAdapter)adapter).addDataSetObserver(observer);
	}

	public void setAdapter(ListImageLoaderAdapter adapter){
		if(imgLoader.getAdapter() instanceof ObservableListImageLoaderAdapter)
			((ObservableListImageLoaderAdapter)imgLoader.getAdapter()).removeDataSetObserver(observer);
		imgLoader.setAdapter(adapter);
		updateImages();
		if(adapter instanceof ObservableListImageLoaderAdapter)
			((ObservableListImageLoaderAdapter)adapter).addDataSetObserver(observer);
	}

	public void setListView(RecyclerView listView){
		if(list!=null){
			list.removeOnScrollListener(scrollListener);
			if(scrollStopRunnable!=null){
				list.removeCallbacks(scrollStopRunnable);
				scrollStopRunnable=null;
			}
		}
		list=listView;
		list.addOnScrollListener(scrollListener);
	}

	public void updateImages(){
		if(wasFastScrolling)
			return;
		if(!isActive)
			return;
		//Log.d("appkit", "update images");
		final Runnable r=new Runnable() {
			boolean updated=false;
			public void run() {
				//Log.d("appkit", "run");
				if(updated) return;
				updated=true;
				doUpdateImages();
			}
		};
		list.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
			@Override
			public boolean onPreDraw() {
				list.getViewTreeObserver().removeOnPreDrawListener(this);
				r.run();
				return true;
			}
		});
		list.postDelayed(r, 100);
	}

	public void forceUpdateImages(){
		doUpdateImages();
	}

	private int getFirstVisiblePosition(){
		RecyclerView.LayoutManager lm=Objects.requireNonNull(list.getLayoutManager());
		if(lm.getChildCount()==0)
			return 0;
		View topChild=lm.getChildAt(0);
		return list.getChildAdapterPosition(topChild);
	}

	private int getLastVisiblePosition(){
		return getFirstVisiblePosition()+getVisibleItemCount();
	}

	private int getVisibleItemCount(){
		return list.getLayoutManager().getChildCount();
	}

	private void reloadRange(int position, int count){
		int posMin=getFirstVisiblePosition()-Math.round(getVisibleItemCount()*prefetchScreens);
		int posMax=getLastVisiblePosition()+Math.round(getVisibleItemCount()*prefetchScreens);
//		Log.i(TAG, "Reload range "+position+", "+count+"; min-max = "+posMin+" - "+posMax+"; first="+getFirstVisiblePosition()+"; last="+getLastVisiblePosition());
		if(position+count<posMin || position>posMax) return;
		int start=Math.max(posMin, position);
		int end=Math.min(posMax, position+count);
		imgLoader.loadRange(start, end, context, true);
	}
	
	private void doUpdateImages(){
		imgLoader.setIsScrolling(false);
		viStart=getFirstVisiblePosition();
		viCount=getLastVisiblePosition()-viStart;
		if(viCount<=0) viCount=Math.max(5, getLastVisiblePosition()-getFirstVisiblePosition());
		//Log.d(TAG, "Update images: load "+(viStart-getNumHeaders())+" - "+(viStart+viCount-getNumHeaders()));
		imgLoader.preparePartialCancel();
		imgLoader.loadRange(viStart, viStart+viCount, context);
		imgLoader.loadRange(viStart+viCount, viStart+viCount+Math.round(viCount*prefetchScreens), context);
		imgLoader.loadRange(viStart-Math.round(viCount*prefetchScreens), viStart+viCount, context);
		imgLoader.commitPartialCancel();
	}

	public void activate(){
		if(isActive)
			return;
		if(list!=null){
			reloadRange(getFirstVisiblePosition(), getVisibleItemCount());
		}
		isActive=true;
	}
	
	public void deactivate(){
		if(!isActive)
			return;
		imgLoader.cancelAll();
		imgLoader.clearFailedRequests();
		isActive=false;
	}
	
	public boolean isAlreadyLoaded(ImageLoaderRequest req){
		return ImageCache.getInstance(context).isInTopCache(req);
	}
	
	public Drawable get(ImageLoaderRequest req){
		return ImageCache.getInstance(context).getFromTop(req);
	}

	public interface Listener{
		void onScrolledToLastItem();
		void onScrollStarted();
		void onScrollStopped();
	}
	
	public interface ExtendedListener extends Listener{
		void onScroll(int firstItem, int visibleCount, int total);
	}

	public void setPrefetchAmount(float screens){
		prefetchScreens=screens;
	}

	public float getFastScrollVelocityThreshold(){
		return fastScrollVelocityThreshold;
	}

	public void setFastScrollVelocityThreshold(float fastScrollVelocityThreshold){
		this.fastScrollVelocityThreshold=fastScrollVelocityThreshold;
	}

	private void reloadVisibleImages(){
		imgLoader.preparePartialCancel();
		imgLoader.loadRange(viStart, viStart+viCount, context);
		if(lastScrollFwd){
			imgLoader.loadRange(viStart+viCount, viStart+viCount+Math.round(viCount*prefetchScreens), context);
			imgLoader.loadRange(viStart-Math.round(viCount*prefetchScreens), viStart+viCount, context);
		}else{
			imgLoader.loadRange(viStart-Math.round(viCount*prefetchScreens), viStart+viCount, context);
			imgLoader.loadRange(viStart+viCount, viStart+viCount+Math.round(viCount*prefetchScreens), context);
		}
		imgLoader.commitPartialCancel();
	}

	public void onScroll(int firstVisibleItem, int visibleItemCount, int totalItemCount, int dx, int dy) {
		if(!isActive)
			return;
		long now=SystemClock.uptimeMillis();
		scrollOffsetX+=dx;
		scrollOffsetY+=dy;
		velocityTrackerX.addDataPoint(now, scrollOffsetX);
		velocityTrackerY.addDataPoint(now, scrollOffsetY);
		float velocity=Math.max(Math.abs(velocityTrackerX.calculateVelocity()), Math.abs(velocityTrackerY.calculateVelocity()))/displayDensity;
		if(viStart!=firstVisibleItem)
			lastScrollFwd=viStart<firstVisibleItem;
		viCount=visibleItemCount;
		viStart=firstVisibleItem;
		//Log.i("appkit", "on scroll "+viCount+", "+viStart);
		int lastVisibleItem=viCount+viStart;
		if(lastVisibleItem!=prevVisLast || viStart!=prevVisFirst){
			long tDiff=now-lastChangeTime;
			//Log.v(TAG, "Items changed, prev "+prevVisFirst+" - "+prevVisLast+", now "+viStart+" - "+visLast+", time since last "+tDiff);
			if(velocity<fastScrollVelocityThreshold || lastScrollState==RecyclerView.SCROLL_STATE_IDLE){
				if(wasFastScrolling){
					imgLoader.setIsScrolling(false);
					wasFastScrolling=false;
				}
				reloadVisibleImages();
			}else{
				if(!wasFastScrolling)
					imgLoader.cancelAll();
				wasFastScrolling=true;
				imgLoader.setIsScrolling(true);
			}
			lastChangeTime=now;
			prevVisFirst=viStart;
			prevVisLast=lastVisibleItem;
		}

		if(firstVisibleItem+visibleItemCount>=totalItemCount-1 && visibleItemCount!=0 && totalItemCount!=0){
			if(listener!=null){
				list.post(listener::onScrolledToLastItem);
			}
		}
		if(listener!=null && listener instanceof ExtendedListener xl){
			xl.onScroll(firstVisibleItem, visibleItemCount, totalItemCount);
		}
	}

	public void callScrolledToLastItem(){
		if(listener!=null) listener.onScrolledToLastItem();
	}
	
	private void realScrollStateChanged(int scrollState){
		if(!isActive)
			return;
		//Log.d("appkit", "scroll state changed "+scrollState);
		lastScrollState=scrollState;
		if(scrollState==RecyclerView.SCROLL_STATE_IDLE && listener!=null) listener.onScrollStopped();
		if(scrollState==RecyclerView.SCROLL_STATE_DRAGGING && listener!=null) listener.onScrollStarted();
		if(scrollState==RecyclerView.SCROLL_STATE_IDLE/* && wasFastScrolling*/){
			imgLoader.setIsScrolling(false);
			velocityTrackerX.resetTracking();
			velocityTrackerY.resetTracking();
			scrollOffsetX=scrollOffsetY=0;
			//Log.w(TAG, "Scroll state idle, loading");
			wasFastScrolling=false;
			if(viCount<=0)
				return;
			reloadVisibleImages();
		}else{
			if(loadRunnable!=null){
				list.removeCallbacks(loadRunnable);
				loadRunnable=null;
			}
		}
	}

	public void bindImageView(ImageView view, @DrawableRes int placeholderRes, ImageLoaderRequest req){
		bindImageView(view, view.getContext().getResources().getDrawable(placeholderRes), req);
	}

	public void bindImageView(ImageView view, Drawable placeholder, ImageLoaderRequest req){
		if(isAlreadyLoaded(req))
			view.setImageDrawable(get(req));
		else
			view.setImageDrawable(placeholder);
	}

	public void bindViewHolder(ImageLoaderRecyclerAdapter adapter, ImageLoaderViewHolder holder, int position){
		if(!isActive)
			return;
		for(int i=0;i<adapter.getImageCountForItem(position);i++){
			ImageLoaderRequest req=adapter.getImageRequest(position, i);
			if(isAlreadyLoaded(req)){
				holder.setImage(i, get(req));
			}else{
				holder.clearImage(i);
				if(imgLoader.isFailed(position, i))
					holder.onImageLoadingFailed(i, null);
			}
		}
	}

	public void retryFailedRequests(){
		imgLoader.retryFailedRequests(context);
	}

	/*package*/ void onCacheEntryRemoved(String key){
		imgLoader.onCacheEntryRemoved(key);
	}

	public interface DataSetObserver{
		void onEverythingChanged();
		void onItemRangeChanged(int position, int count);
		void onItemRangeInserted(int position, int count);
	}
}
