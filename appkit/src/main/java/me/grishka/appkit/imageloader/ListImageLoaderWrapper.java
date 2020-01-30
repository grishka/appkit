package me.grishka.appkit.imageloader;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.ListView;

import java.lang.reflect.Method;

import androidx.annotation.DrawableRes;

public class ListImageLoaderWrapper implements AbsListView.OnScrollListener{

	private ListImageLoader imgLoader;
	private ListViewDelegate list;
	private Listener listener;
	private int viStart, viCount;
	private AbsListView.OnScrollListener scrollListener;
	private boolean lastScrollFwd=false;
	private int prevVisLast, prevVisFirst;
	private long lastChangeTime=0;
	private boolean wasFastScrolling=false;
	private Runnable loadRunnable;
	private Runnable scrollStopRunnable;
	private boolean isScrolling=false;
	private Context context;
	private ViewTreeObserver.OnPreDrawListener preDrawListener=new ViewTreeObserver.OnPreDrawListener() {
		double prevPos;
		@Override
		public boolean onPreDraw() {
			if(list.getVisibleItemCount()>0){
				double pos=list.getFirstVisiblePosition();
				View topItem=null;
				for(int i=0;i<list.getVisibleItemCount();i++){
					topItem=list.getItemView(i + list.getFirstVisiblePosition());
					//Log.i(TAG, "Item "+i+", "+topItem);
					if(topItem==null) continue;
					//Log.i(TAG, "Height = "+topItem.getHeight());
					if(topItem.getHeight()>0) break;
				}
				if(topItem==null) return true;
				if(list.isVertical())
					pos+=Math.abs((topItem.getHeight()-topItem.getTop())/(double)topItem.getHeight());
				else
					pos+=Math.abs((topItem.getWidth()-topItem.getLeft())/(double)topItem.getWidth());
				if(pos!=prevPos){
					isScrolling=true;
					//Log.d("appkit", "ScrollPos = "+pos);
					if(scrollStopRunnable!=null){
						list.getView().removeCallbacks(scrollStopRunnable);
						scrollStopRunnable=null;
					}
					if(isScrolling){
						scrollStopRunnable=new ScrollStopDetector();
						list.getView().postDelayed(scrollStopRunnable, 150);
					}
				}
				prevPos=pos;
			}
			return true;
		}
	};
	private DataSetObserver observer=new DataSetObserver(){
		@Override
		public void onEverythingChanged(){
			updateImages();
		}

		@Override
		public void onItemRangeChanged(int position, int count){
			reloadRange(position, count);
		}

		@Override
		public void onItemRangeInserted(int position, int count){
			//reloadRange(position, count);
			onEverythingChanged();
		}
	};
	private float prefetchScreens=1;
	
	private static final String TAG="appkit-img-wrapper";

	public ListImageLoaderWrapper(Context context, ListImageLoaderAdapter adapter, AdapterView<?> listView, Listener listener){
		this(context, adapter, new DefaultListViewDelegate(listView), listener);
	}

	public ListImageLoaderWrapper(Context context, ListImageLoaderAdapter adapter, ListViewDelegate listView, Listener listener){
		imgLoader=new ListImageLoader();
		imgLoader.setAdapter(adapter);
		this.listener=listener;
		this.context=context;
		list=listView;
		list.setOnScrollListener(this);
		listView.getView().getViewTreeObserver().addOnPreDrawListener(preDrawListener);
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

	public void setListView(AdapterView<?> listView){
		setListView(new DefaultListViewDelegate(listView));
	}

	public void setListView(ListViewDelegate listView){
		if(list!=null){
			list.setOnScrollListener(null);
			if(scrollStopRunnable!=null){
				list.getView().removeCallbacks(scrollStopRunnable);
				scrollStopRunnable=null;
			}
			list.getView().getViewTreeObserver().removeOnPreDrawListener(preDrawListener);
		}
		list=listView;
		list.setOnScrollListener(this);
		listView.getView().getViewTreeObserver().addOnPreDrawListener(preDrawListener);
	}
	
	public void setOnScrollListener(AbsListView.OnScrollListener sl){
		scrollListener=sl;
	}
	
	public void updateImages(){
		if(wasFastScrolling)
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
		list.getView().getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
			@Override
			public boolean onPreDraw() {
				list.getView().getViewTreeObserver().removeOnPreDrawListener(this);
				r.run();
				return true;
			}
		});
		list.getView().postDelayed(r, 100);
	}

	private void reloadRange(int position, int count){
		int posMin=list.getFirstVisiblePosition()-Math.round(list.getVisibleItemCount()*prefetchScreens);
		int posMax=list.getLastVisiblePosition()+Math.round(list.getVisibleItemCount()*prefetchScreens);
		if(position+count<posMin || position>posMax) return;
		int start=Math.max(posMin, position);
		int end=Math.min(posMax, position+count);
		imgLoader.loadRange(start, end, context, true);
	}
	
	private void doUpdateImages(){
		isScrolling=false;
		//imgLoader.setIsScrolling(isOuterScrolling);
		imgLoader.setIsScrolling(false);
		viStart=list.getFirstVisiblePosition();
		viCount=list.getLastVisiblePosition()-viStart;
		if(viCount<=0) viCount=Math.max(5, list.getLastVisiblePosition()-list.getFirstVisiblePosition());
		//Log.d(TAG, "Update images: load "+(viStart-getNumHeaders())+" - "+(viStart+viCount-getNumHeaders()));
		imgLoader.preparePartialCancel();
		imgLoader.loadRange(viStart-getNumHeaders(), viStart+viCount-getNumHeaders(), context);
		imgLoader.loadRange(viStart+viCount, viStart+viCount+Math.round(viCount*prefetchScreens), context);
		imgLoader.loadRange(viStart-getNumHeaders()-Math.round(viCount*prefetchScreens), viStart+viCount, context);
		imgLoader.commitPartialCancel();
	}
	
	public void clear(){
		//imgLoader.clear();
	}
	
	public void activate(){
		//imgLoader.activate();
		updateImages();
	}
	
	public void deactivate(){
		imgLoader.cancelAll();
		if (list != null) {
			list.getView().getViewTreeObserver().removeOnPreDrawListener(preDrawListener);
		}
	}
	
	public boolean isAlreadyLoaded(String url){
		//return imgLoader.isAlreadyLoaded(url);
		return ImageCache.getInstance(context).isInTopCache(url);
	}
	
	public Bitmap get(String url){
		//return imgLoader.getImage(url);
		return ImageCache.getInstance(context).getFromTop(url);
	}

	public interface Listener{
		public void onScrolledToLastItem();
		public void onScrollStarted();
		public void onScrollStopped();
	}
	
	public interface ExtendedListener extends Listener{
		public void onScroll(int firstItem, int visibleCount, int total);
	}

	public void setPrefetchAmount(float screens){
		prefetchScreens=screens;
	}

	@Override
	public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
		if(scrollListener!=null) scrollListener.onScroll(view, firstVisibleItem, visibleItemCount, totalItemCount);
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
					imgLoader.loadRange(list.getFirstVisiblePosition()-getNumHeaders(), list.getLastVisiblePosition(), context);
					if(lastScrollFwd){
						imgLoader.loadRange(viStart+viCount, viStart+viCount+Math.round(viCount*prefetchScreens), context);
						imgLoader.loadRange(viStart-getNumHeaders()-Math.round(viCount*prefetchScreens), viStart+viCount, context);
					}else{
						imgLoader.loadRange(viStart-getNumHeaders()-Math.round(viCount*prefetchScreens), viStart+viCount, context);
						imgLoader.loadRange(viStart+viCount, viStart+viCount+Math.round(viCount*prefetchScreens), context);
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
			if(listener!=null) listener.onScrolledToLastItem();
		}
		if(listener!=null && listener instanceof ExtendedListener){
			((ExtendedListener) listener).onScroll(firstVisibleItem-getNumHeaders(), visibleItemCount, totalItemCount-getNumHeaders()-getNumFooters());
		}
	}

	@Override
	public void onScrollStateChanged(AbsListView view, int scrollState) {
		//Log.i(TAG, "Scroll state changed: "+scrollState);
		if(scrollState!=SCROLL_STATE_IDLE){
			realScrollStateChanged(scrollState);
			isScrolling=true;
		}
	}
	
	public void callScrolledToLastItem(){
		if(listener!=null) listener.onScrolledToLastItem();
	}
	
	private void realScrollStateChanged(int scrollState){
		//Log.d("appkit", "scroll state changed "+scrollState);
		if(scrollState==SCROLL_STATE_IDLE && listener!=null) listener.onScrollStopped();
		if(scrollState==SCROLL_STATE_TOUCH_SCROLL && listener!=null) listener.onScrollStarted();
		/*if(scrollState==SCROLL_STATE_TOUCH_SCROLL){
			imgLoader.cancelAll();
		}*/
		if(scrollListener!=null) scrollListener.onScrollStateChanged(list.getView() instanceof AbsListView ? ((AbsListView)list.getView()) : null, scrollState);
		if(scrollState==SCROLL_STATE_IDLE/* && wasFastScrolling*/){
			imgLoader.setIsScrolling(false);
			//Log.w(TAG, "Scroll state idle, loading");
			wasFastScrolling=false;
			imgLoader.preparePartialCancel();
			imgLoader.loadRange(viStart-getNumHeaders(), viStart+viCount, context);
			if(lastScrollFwd){
				imgLoader.loadRange(viStart+viCount, viStart+viCount+Math.round(viCount*prefetchScreens), context);
				imgLoader.loadRange(viStart-getNumHeaders()-Math.round(viCount*prefetchScreens), viStart+viCount, context);
			}else{
				imgLoader.loadRange(viStart-getNumHeaders()-Math.round(viCount*prefetchScreens), viStart+viCount, context);
				imgLoader.loadRange(viStart+viCount, viStart+viCount+Math.round(viCount*prefetchScreens), context);
			}
			imgLoader.commitPartialCancel();
		}else{
			if(loadRunnable!=null){
				list.getView().removeCallbacks(loadRunnable);
				loadRunnable=null;
			}
			//imgLoader.setIsScrolling(true);
		}
	}
	
	private int getNumHeaders(){
		if(list instanceof ListView)
			return ((ListView)list).getHeaderViewsCount();
		return 0;
	}
	
	private int getNumFooters(){
		if(list instanceof ListView)
			return ((ListView)list).getFooterViewsCount();
		return 0;
	}

	public void bindImageView(ImageView view, @DrawableRes int placeholderRes, String url){
		bindImageView(view, view.getContext().getResources().getDrawable(placeholderRes), url);
	}

	public void bindImageView(ImageView view, Drawable placeholder, String url){
		if(isAlreadyLoaded(url))
			view.setImageBitmap(get(url));
		else
			view.setImageDrawable(placeholder);
	}

	public void bindViewHolder(ImageLoaderRecyclerAdapter adapter, ImageLoaderViewHolder holder, int position){
		for(int i=0;i<adapter.getImageCountForItem(position);i++){
			String url=adapter.getImageURL(position, i);
			if(isAlreadyLoaded(url))
				holder.setImage(i, get(url));
			else
				holder.clearImage(i);
		}
	}

	/*package*/ void onCacheEntryRemoved(String key){
		imgLoader.onCacheEntryRemoved(key);
	}
	
	/*public AdapterView<?> getListView(){
		return list;
	}*/
	
	private class ScrollStopDetector implements Runnable{
		@Override
		public void run() {
			//Log.e("appkit", "============ scroll stop");
			realScrollStateChanged(AbsListView.OnScrollListener.SCROLL_STATE_IDLE);
			isScrolling=false;
			scrollStopRunnable=null;
		}
	}

	public static interface ListViewDelegate{
		public int getVisibleItemCount();
		public int getFirstVisiblePosition();
		public int getLastVisiblePosition();
		public View getView();
		public View getItemView(int index);
		public void setOnScrollListener(AbsListView.OnScrollListener listener);
		public boolean isVertical();
	}

	public static class DefaultListViewDelegate implements ListViewDelegate{

		private AdapterView<?> adapterView;

		public DefaultListViewDelegate(AdapterView<?> l){
			adapterView=l;
		}

		@Override
		public int getVisibleItemCount() {
			return adapterView.getChildCount();
		}

		@Override
		public int getFirstVisiblePosition() {
			return adapterView.getFirstVisiblePosition();
		}

		@Override
		public int getLastVisiblePosition() {
			return adapterView.getLastVisiblePosition();
		}

		@Override
		public View getView() {
			return adapterView;
		}

		@Override
		public View getItemView(int index) {
			if(index<adapterView.getFirstVisiblePosition() || index>adapterView.getLastVisiblePosition())
				return null;
			return adapterView.getChildAt(index-adapterView.getFirstVisiblePosition());
		}

		@Override
		public void setOnScrollListener(AbsListView.OnScrollListener listener) {
			if(adapterView instanceof AbsListView)
				((AbsListView)adapterView).setOnScrollListener(listener);
			else{
				try{
					Class<?> c=adapterView.getClass();
					Method m=c.getMethod("setOnScrollListener", AbsListView.OnScrollListener.class);
					if(m!=null) m.invoke(adapterView, listener);
				}catch(Exception x){
					Log.w("appkit", x);}
			}
		}

		@Override
		public boolean isVertical(){
			return true;
		}
	}

	public interface DataSetObserver{
		void onEverythingChanged();
		void onItemRangeChanged(int position, int count);
		void onItemRangeInserted(int position, int count);
	}
}
