package me.grishka.appkit.imageloader;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.Vector;

import androidx.collection.LongSparseArray;
import me.grishka.appkit.imageloader.requests.ImageLoaderRequest;

public class ListImageLoader {

	public static final boolean DEBUG=false;
	private static final String TAG="appkit-img-loader";

	private volatile ListImageLoaderAdapter adapter;
	private final Vector<RunnableTask> incomplete=new Vector<>();
	private boolean isScrolling;
	private final Handler mainThreadHandler;
	private final LinkedList<RunnableTask> reusableTasks=new LinkedList<>();
	private final LongSparseArray<String> loadedRequests=new LongSparseArray<>(10);
	private LongSparseArray<RunnableTask> pendingPartialCancel;

	public ListImageLoader(){
		mainThreadHandler=new Handler(Looper.getMainLooper());
	}
	
	public void setAdapter(ListImageLoaderAdapter a){
		adapter=a;
		cancelAll();
	}
	
	public ListImageLoaderAdapter getAdapter(){
		return adapter;
	}

	public void loadRange(int start, int end, Context context){
		loadRange(start, end, context, false);
	}
	
	public void loadRange(int start, int end, Context context, boolean force){
		if(start>end)
			throw new IllegalArgumentException("start="+start+" > end="+end);
		if(adapter==null) return;
		try{
			start=Math.max(0, start);
			end=Math.min(end, adapter.getCount()-1);
			if(DEBUG) Log.v(TAG, "loadRange: "+start+" - "+end);
			if(DEBUG && Math.abs(end-start)>30) try{throw new Exception("range: "+start+" - "+end);}catch(Exception x){Log.w("appkit", x);}
			for(int i=start;i<=end;i++)
				loadSingleItem(i, context, force);
		}catch(Exception x){Log.w("appkit", x);}
	}
	
	public synchronized void loadSingleItem(int item, Context context, boolean force){
		try{
			if(DEBUG) Log.v(TAG, "loadItem: "+item);
			int cnt=adapter.getImageCountForItem(item);
			for(int i=0;i<cnt;i++){
				ImageLoaderRequest req=adapter.getImageRequest(item, i);
				if(req==null)
					continue;
				if(pendingPartialCancel!=null){
					RunnableTask t=pendingPartialCancel.get(makeIndex(item, i));
					if(t!=null && t.req.equals(req)){
						pendingPartialCancel.remove(makeIndex(item, i));
						incomplete.add(t);
						if(DEBUG) Log.v(TAG, "Kept ["+item+"/"+i+"] from previous queue");
						continue;
					}
				}
				if(req.getMemoryCacheKey().equals(loadedRequests.get(makeIndex(item, i))) || force){
					if(ImageCache.getInstance(context).isInTopCache(req)){ // (in)sanity check
						if(DEBUG) Log.v(TAG, "Image ["+item+"/"+i+"] already loaded; skipping");
						adapter.imageLoaded(item, i, ImageCache.getInstance(context).getFromTop(req));
						continue;
					}else{
						loadedRequests.remove(makeIndex(item, i));
					}
				}
				RunnableTask task=createTask();
				task.canceled=false;
				task.item=item;
				task.image=i;
				task.req=req;
//				task.set=!isScrolling;
				task.set=true;
				task.context=context;
				if(DEBUG) Log.v(TAG, "Added task: "+task);
				incomplete.add(task);
				if(ImageCache.getInstance(task.context).isInCache(task.req)) {
					ImageLoaderThreadPool.enqueueCachedTask(task);
				}else {
					ImageLoaderThreadPool.enqueueTask(task);
				}
			}
		}catch(Exception x){
			if(DEBUG) Log.w(TAG, x);
		}
	}

	private RunnableTask createTask(){
		synchronized(reusableTasks){
			if(reusableTasks.size()>0){
				if(DEBUG) Log.v(TAG, "Reusing existing task");
				return reusableTasks.remove(0);
			}
		}
		if(DEBUG) Log.w(TAG, "Creating new task");
		return new RunnableTask();
	}

	private void reuseTask(RunnableTask task){
		synchronized(reusableTasks){
			if(DEBUG && reusableTasks.contains(task))
				throw new IllegalStateException("task "+task+" already reused");
			reusableTasks.add(task);
		}
	}

	public synchronized boolean isLoading(int item){
		for(RunnableTask t:incomplete){
			if(t.item==item){
				return true;
			}
		}
		return false;
	}

	public synchronized boolean isLoading(int item, int image){
		for(RunnableTask t:incomplete){
			if(t.item==item && t.image==image){
				return true;
			}
		}
		return false;
	}

	public synchronized void cancel(int item){
		Iterator<RunnableTask> itr=incomplete.iterator();
		while(itr.hasNext()){
			RunnableTask t=itr.next();
			if(t.item==item){
				if(DEBUG) Log.v(TAG, "Canceled: "+t);
				itr.remove();
				t.cancel();
			}
		}
	}
	
	public synchronized void cancelRange(int start, int end){
		Iterator<RunnableTask> itr=incomplete.iterator();
		while(itr.hasNext()){
			RunnableTask t=itr.next();
			if(t.item>=start && t.item<=end){
				if(DEBUG) Log.v(TAG, "Canceled: "+t);
				itr.remove();
				t.cancel();
			}
		}
	}
	
	public synchronized void cancelAll(){
		if(DEBUG) Log.w(TAG, "Cancel all");
		for(RunnableTask t:incomplete){
			if(DEBUG) Log.v(TAG, "Canceled(all): "+t);
			t.cancel();
		}
		incomplete.clear();
	}

	public synchronized void preparePartialCancel(){
		if(pendingPartialCancel!=null)
			throw new IllegalStateException("There's already a pending partial cancellation");
		pendingPartialCancel=new LongSparseArray<RunnableTask>();
		for(RunnableTask t:incomplete){
			pendingPartialCancel.put(makeIndex(t.item, t.image), t);
		}
		incomplete.clear();
	}

	public synchronized void commitPartialCancel(){
		if(pendingPartialCancel==null)
			throw new IllegalStateException("There's no pending partial cancellation");
		for(int i=0;i<pendingPartialCancel.size();i++){
			RunnableTask t=pendingPartialCancel.valueAt(i);
			if(DEBUG) Log.v(TAG, "Canceled(partial): "+t);
			t.cancel();
		}
		if(DEBUG) Log.w(TAG, "Partial cancellation completed, canceled "+pendingPartialCancel.size()+" tasks");
		pendingPartialCancel=null;
	}
	
	public synchronized void setIsScrolling(boolean s){
		if(DEBUG) Log.i(TAG, "Set is scrolling "+s);
		//try{throw new Exception("set scrolling");}catch(Exception x){Log.e(TAG, "qwe", x);}
		isScrolling=s;
		for(RunnableTask t:incomplete){
			t.setDecode(!s);
		}
	}
	
	public boolean isScrolling(){
		return isScrolling;
	}

	private static long makeIndex(int position, int image){
		return (((long)position) << 32) | (((long)image) & 0xFFFFFFFFL);
	}

	private static int getPosition(long index){
		return (int)(index >> 32);
	}

	private static int getImage(long index){
		return (int)(index & 0xFFFFFFFF);
	}

	/*package*/ synchronized void onCacheEntryRemoved(String key){
		int index;
		while((index=loadedRequests.indexOfValue(key))>=0){
			long x=loadedRequests.keyAt(index);
			loadedRequests.removeAt(index);
			if(DEBUG) Log.i(TAG, "Removed cache entry for ["+getPosition(x)+"/"+getImage(x)+"] "+key);
		}
	}
	
	private class RunnableTask implements Runnable{
		public int item, image;
		public ImageLoaderRequest req;
		public boolean canceled=false;
		public boolean set=true;
		private ImageCache.RequestWrapper reqWrapper;
		public Context context;

		public void cancel(){
			if(DEBUG) Log.i(TAG, "Cancel: "+this);
			canceled=true;
			ImageLoaderThreadPool.enqueueCancellation(new Runnable() {
				@Override
				public void run() {
					try {
						if (reqWrapper != null) {
							reqWrapper.cancel();
						}
					} catch (Exception x) {
					}
				}
			});
		}
		
		public void run(){
			if(canceled){
				reuseTask(this);
				return;
			}
			try{
				if(DEBUG) Log.v(TAG, "Started: "+this);
				reqWrapper=new ImageCache.RequestWrapper();
				Drawable _bmp=ImageCache.getInstance(context).get(req, reqWrapper, null, set);
				if(set && _bmp==null)
					Log.w(TAG, "error downloading image: "+req);
				if(canceled){
					if(DEBUG) Log.w(TAG, "_Canceled: "+this);
					reuseTask(this);
					return;
				}
				if(DEBUG && set && _bmp==null) Log.e(TAG, "FAILED: "+this);
				if(set && _bmp!=null && !canceled) {
					final Drawable bmp=_bmp;
					mainThreadHandler.post(new Runnable() {
						@Override
						public void run() {
							if(set && !canceled){
								synchronized(ListImageLoader.this){
									loadedRequests.put(makeIndex(item, image), req.getMemoryCacheKey());
								}
								// TODO handle partial adapter updates better
								if(item<adapter.getCount() && image<adapter.getImageCountForItem(item) && req.equals(adapter.getImageRequest(item, image)))
									adapter.imageLoaded(item, image, bmp);
							}
							if(DEBUG) Log.v(TAG, "Completed [UI thread]: "+this);
							reuseTask(RunnableTask.this);
						}
					});
				}else{
					if(DEBUG) Log.v(TAG, "Completed: "+this);
					reuseTask(this);
				}
			}catch(Exception x){
				Log.w(TAG, x);
			}
			synchronized(ListImageLoader.this){
				incomplete.remove(this);
			}
		}
		
		public void setDecode(boolean decode){
//			set=decode;
//			if(reqWrapper!=null)
//				reqWrapper.decode=decode;
		}
		
		public String toString(){
			return "["+item+"/"+image+"] "+req;
		}
	}
}
