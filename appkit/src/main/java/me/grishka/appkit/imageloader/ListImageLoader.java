package me.grishka.appkit.imageloader;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;

import java.util.Iterator;
import java.util.Vector;

import androidx.collection.LongSparseArray;

public class ListImageLoader {

	public static final boolean DEBUG=false;
	private static final String TAG="appkit-img-loader";

	private volatile ListImageLoaderAdapter adapter;
	private Vector<RunnableTask> incomplete=new Vector<RunnableTask>();
	private boolean isScrolling;
	private Handler mainThreadHandler;
	private Vector<RunnableTask> reusableTasks=new Vector<RunnableTask>();
	private LongSparseArray<String> loadedUrls=new LongSparseArray<>(10);
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
		if(adapter==null) return;
		try{
			start=Math.max(0, start);
			end=Math.min(end, adapter.getCount()-1);
			if(DEBUG) Log.v(TAG, "loadRange: "+start+" - "+end);
			if(Math.abs(end-start)>30) try{throw new Exception("range: "+start+" - "+end);}catch(Exception x){Log.w("appkit", x);}
			for(int i=start;i<=end;i++)
				loadSingleItem(i, context, force);
		}catch(Exception x){Log.w("appkit", x);}
	}
	
	public synchronized void loadSingleItem(int item, Context context, boolean force){
		try{
			if(DEBUG) Log.v(TAG, "loadItem: "+item);
			int cnt=adapter.getImageCountForItem(item);
			for(int i=0;i<cnt;i++){
				String url=adapter.getImageURL(item, i);
				if(TextUtils.isEmpty(url))
					continue;
				if(pendingPartialCancel!=null){
					RunnableTask t=pendingPartialCancel.get(makeIndex(item, i));
					if(t!=null && t.url.equals(url)){
						pendingPartialCancel.remove(makeIndex(item, i));
						incomplete.add(t);
						if(DEBUG) Log.v(TAG, "Kept ["+item+"/"+i+"] from previous queue");
						continue;
					}
				}
				if(url.equals(loadedUrls.get(makeIndex(item, i))) || force){
					if(ImageCache.getInstance(context).isInTopCache(url)){ // (in)sanity check
						if(DEBUG) Log.v(TAG, "Image ["+item+"/"+i+"] already loaded; skipping");
						adapter.imageLoaded(item, i, ImageCache.getInstance(context).getFromTop(url));
						continue;
					}else{
						loadedUrls.remove(makeIndex(item, i));
					}
				}
				RunnableTask task=createTask();
				task.canceled=false;
				task.item=item;
				task.image=i;
				task.url=url;
				task.set=!isScrolling;
				task.context=context;
				if(DEBUG) Log.v(TAG, "Added task: "+task);
				incomplete.add(task);
				if(ImageCache.getInstance(task.context).isInCache(task.url)) {
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
		if(reusableTasks.size()>0) {
			if(DEBUG) Log.v(TAG, "Reusing existing task");
			return reusableTasks.remove(0);
		}
		if(DEBUG) Log.w(TAG, "Creating new task");
		return new RunnableTask();
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
		while((index=loadedUrls.indexOfValue(key))>=0){
			long x=loadedUrls.keyAt(index);
			loadedUrls.removeAt(index);
			if(DEBUG) Log.i(TAG, "Removed cache entry for ["+getPosition(x)+"/"+getImage(x)+"] "+key);
		}
	}
	
	private class RunnableTask implements Runnable{
		public int item, image;
		public String url;
		public boolean canceled=false;
		public boolean set=true;
		private ImageCache.RequestWrapper reqWrapper;
		public Context context;

		public void cancel(){
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
				if(!reusableTasks.contains(RunnableTask.this))
					reusableTasks.add(RunnableTask.this);
				return;
			}
			try{
				if(DEBUG) Log.v(TAG, "Started: "+this);
				reqWrapper=new ImageCache.RequestWrapper();
				Bitmap _bmp=null;
				/*if(url.startsWith("M")){ // [M]ultichat
					String[] parts=url.split("\\|");
					int len=Math.min(parts.length-1, 4);
					Bitmap[] bmps=new Bitmap[len];
					for(int i=1;i<len+1;i++){
						bmps[i-1]=ImageCache.get(parts[i], reqWrapper, null, set);
					}
					if(set){
						_bmp=drawMultichatPhoto(bmps);
						ImageCache.put(url, _bmp);
					}
				}else */if(url.startsWith("A")){ // [A]lternative
					String[] parts=url.split("\\|");
					for(int i=1;i<parts.length;i++){
						_bmp=ImageCache.getInstance(context).get(parts[i], reqWrapper, null, set);
						if(DEBUG) Log.w(TAG, "Get "+parts[i]+": "+_bmp);
						if(_bmp!=null || !set) break;
					}
				}else if(url.startsWith("B")){ // [B]lur
					String[] parts=url.split("\\|");
					int radius=Integer.parseInt(parts[1]);
					_bmp=ImageCache.getInstance(context).get(parts[2]);
					Bitmap tmp=Bitmap.createBitmap(_bmp.getWidth(), _bmp.getHeight(), Bitmap.Config.ARGB_8888);
					new Canvas(tmp).drawBitmap(_bmp, 0, 0, null);
					_bmp=tmp;
					StackBlur.blurBitmap(_bmp, radius);
					ImageCache.getInstance(context).put(url, _bmp);
				}else{
					_bmp=ImageCache.getInstance(context).get(url, reqWrapper, null, set);
					if(set && _bmp==null)
						Log.w(TAG, "error downloading image: "+url);
				}
				if(canceled){
					if(DEBUG) Log.w(TAG, "_Canceled: "+this);
					if(!reusableTasks.contains(RunnableTask.this))
						reusableTasks.add(RunnableTask.this);
					return;
				}
				if(DEBUG && set && _bmp==null) Log.e(TAG, "FAILED: "+this);
				if(set && _bmp!=null && !canceled) {
					final Bitmap bmp=_bmp;
					mainThreadHandler.post(new Runnable() {
						@Override
						public void run() {
							if(!reusableTasks.contains(RunnableTask.this))
								reusableTasks.add(RunnableTask.this);
							if(set && !canceled){
								synchronized(ListImageLoader.this){
									loadedUrls.put(makeIndex(item, image), url);
								}
								if(url.equals(adapter.getImageURL(item, image)))
									adapter.imageLoaded(item, image, bmp);
							}
						}
					});
				}else{
					if(!reusableTasks.contains(RunnableTask.this))
						reusableTasks.add(RunnableTask.this);
				}
				if(DEBUG) Log.v(TAG, "Completed: "+this);
			}catch(Exception x){
				Log.w(TAG, x);
			}
			synchronized(ListImageLoader.this){
				incomplete.remove(this);
			}
		}
		
		public void setDecode(boolean decode){
			set=decode;
			if(reqWrapper!=null)
				reqWrapper.decode=decode;
		}
		
		public String toString(){
			return "["+item+"/"+image+"] "+url;
		}
	}
}
