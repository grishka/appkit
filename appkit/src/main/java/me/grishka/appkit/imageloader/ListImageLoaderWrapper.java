package me.grishka.appkit.imageloader;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.ImageView;

import java.util.Objects;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import me.grishka.appkit.imageloader.requests.ImageLoaderRequest;

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
			imgLoader.offsetRange(position+count, list.getLayoutManager().getItemCount(), count);
			reloadRange(position, count);
		}
	};
	private RecyclerView.OnScrollListener scrollListener=new RecyclerView.OnScrollListener(){
		@Override
		public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState){
			realScrollStateChanged(newState);
		}

		@Override
		public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy){
			onScroll(getFirstVisiblePosition(), getVisibleItemCount(), list.getLayoutManager().getItemCount());
		}
	};
	private float prefetchScreens=1;
	private boolean isActive=true;
	
	private static final String TAG="appkit-img-wrapper";

	public ListImageLoaderWrapper(Context context, ListImageLoaderAdapter adapter, RecyclerView listView, Listener listener){
		imgLoader=new ListImageLoader();
		imgLoader.setAdapter(adapter);
		this.listener=listener;
		this.context=context;
		list=listView;
		list.addOnScrollListener(scrollListener);
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
		RecyclerView.LayoutManager lm=Objects.requireNonNull(list.getLayoutManager());
		int count=lm.getChildCount();
		if(count==0)
			return 0;
		return list.getChildAdapterPosition(lm.getChildAt(count-1));
	}

	private int getVisibleItemCount(){
		return list.getLayoutManager().getChildCount();
	}

	private void reloadRange(int position, int count){
		int posMin=getFirstVisiblePosition()-Math.round(getVisibleItemCount()*prefetchScreens);
		int posMax=getLastVisiblePosition()+Math.round(getVisibleItemCount()*prefetchScreens);
		if(position+count<posMin || position>posMax) return;
		int start=Math.max(posMin, position);
		int end=Math.min(posMax, position+count);
		imgLoader.loadRange(start, end, context, true);
	}
	
	private void doUpdateImages(){
		//imgLoader.setIsScrolling(isOuterScrolling);
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
		public void onScroll(int firstItem, int visibleCount, int total);
	}

	public void setPrefetchAmount(float screens){
		prefetchScreens=screens;
	}

	public void onScroll(int firstVisibleItem, int visibleItemCount, int totalItemCount) {
		if(!isActive)
			return;
		if(viStart!=firstVisibleItem)
			lastScrollFwd=viStart<firstVisibleItem;
		viCount=visibleItemCount;
		viStart=firstVisibleItem;
		//Log.i("appkit", "on scroll "+viCount+", "+viStart);
		int visLast=viCount+viStart;
		if(visLast!=prevVisLast /*|| viStart!=prevVisFirst*/){
			long tDiff=System.currentTimeMillis()-lastChangeTime;
			//Log.v(TAG, "Items changed, prev "+prevVisFirst+" - "+prevVisLast+", now "+viStart+" - "+visLast+", time since last "+tDiff);
			if(tDiff>300){
				if(wasFastScrolling){
					//imgLoader.setIsScrolling(true);
					imgLoader.setIsScrolling(false);
					wasFastScrolling=false;
					int lastVisiblePos=getLastVisiblePosition();
					if(lastVisiblePos>=0){
						imgLoader.loadRange(getFirstVisiblePosition(), lastVisiblePos, context);
						if(lastScrollFwd){
							imgLoader.loadRange(viStart+viCount, viStart+viCount+Math.round(viCount*prefetchScreens), context);
							imgLoader.loadRange(viStart-Math.round(viCount*prefetchScreens), viStart+viCount, context);
						}else{
							imgLoader.loadRange(viStart-Math.round(viCount*prefetchScreens), viStart+viCount, context);
							imgLoader.loadRange(viStart+viCount, viStart+viCount+Math.round(viCount*prefetchScreens), context);
						}
					}
				}else{
					if(prevVisFirst>viStart){ // top
						//imgLoader.loadSingleItem(viStart);
						imgLoader.loadRange(viStart, prevVisFirst-1, context);
					}
					if(prevVisLast<visLast){ // bottom
						//imgLoader.loadSingleItem(visLast);
						if(visLast-prevVisLast<(visibleItemCount)*4)
							imgLoader.loadRange(prevVisLast+1, visLast, context);
					}
					if(prevVisLast>visLast){
						imgLoader.cancelRange(visLast+1, prevVisLast);
					}
					if(prevVisFirst<viStart){
						imgLoader.cancelRange(prevVisFirst, viStart-1);
					}
				}
			}else{
				if(!wasFastScrolling)
					imgLoader.cancelAll();
				wasFastScrolling=true;
				imgLoader.setIsScrolling(true);
			}
			lastChangeTime=System.currentTimeMillis();
			prevVisFirst=viStart;
			prevVisLast=visLast;
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
		if(scrollState==RecyclerView.SCROLL_STATE_IDLE && listener!=null) listener.onScrollStopped();
		if(scrollState==RecyclerView.SCROLL_STATE_DRAGGING && listener!=null) listener.onScrollStarted();
		/*if(scrollState==SCROLL_STATE_TOUCH_SCROLL){
			imgLoader.cancelAll();
		}*/
		if(scrollState==RecyclerView.SCROLL_STATE_IDLE/* && wasFastScrolling*/){
			imgLoader.setIsScrolling(false);
			//Log.w(TAG, "Scroll state idle, loading");
			wasFastScrolling=false;
			if(viCount<=0)
				return;
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
		}else{
			if(loadRunnable!=null){
				list.removeCallbacks(loadRunnable);
				loadRunnable=null;
			}
			//imgLoader.setIsScrolling(true);
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
			if(isAlreadyLoaded(req))
				holder.setImage(i, get(req));
			else
				holder.clearImage(i);
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
