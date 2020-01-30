package me.grishka.appkit.imageloader;

import android.app.ActivityManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;

import com.jakewharton.disklrucache.DiskLruCache;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;

import androidx.annotation.Nullable;
import me.grishka.appkit.utils.NetworkUtils;
import okhttp3.Call;

/**
 * The main class handling image loading and caching.
 */
public class ImageCache{
	
	public static final boolean DEBUG=false;
	
	private ArrayList<WeakReference<ListImageLoaderWrapper>> registeredLoaders=new ArrayList<WeakReference<ListImageLoaderWrapper>>();

	private LruCache<String, Bitmap> cache;
	
	private DiskLruCache diskCache=null;
	private final Object diskCacheLock=new Object();
	private Context appContext;

	private static ImageCache instance=null;
	private static Parameters params=new Parameters();
	private static HashMap<String, ImageDownloader> downloaders=new HashMap<>();

	private static final String TAG="AppKit_ImageCache";

	public static void setParams(Parameters p){
		if(instance!=null)
			throw new IllegalStateException("ImageCache is already initialized");
		params=p;
	}

	/**
	 * Get the singleton instance of ImageCache creating one on first call.
	 * @param context Any context will work
	 * @return The shared ImageCache instance
	 */
	public static ImageCache getInstance(Context context){
		if(instance==null){
			instance=new ImageCache(context.getApplicationContext());

			HTTPImageDownloader httpDownloader=new HTTPImageDownloader();
			downloaders.put("http", httpDownloader);
			downloaders.put("https", httpDownloader);
			ContentImageDownloader contentDownloader=new ContentImageDownloader(instance);
			downloaders.put("content", contentDownloader);
			FileImageDownloader fileDownloader=new FileImageDownloader(instance);
			downloaders.put("file", fileDownloader);
			downloaders.put("", fileDownloader);

			instance.appContext=context.getApplicationContext();
			int cacheSize=Math.min(10*1024*1024, ((ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE)).getMemoryClass()/params.memoryCacheSize*1024*1024);
			instance.cache=new LruCache<String, Bitmap>(cacheSize) {
				protected int sizeOf(String key, Bitmap value) {
					return value.getRowBytes() * value.getHeight();
				}
				protected void entryRemoved (boolean evicted, String key, Bitmap oldValue, Bitmap newValue){
					//Log.i(TAG, "cache: entry removed");
					if(newValue!=null) return;
					for(WeakReference<ListImageLoaderWrapper> ref:instance.registeredLoaders){
						if(ref.get()!=null)
							ref.get().onCacheEntryRemoved(key);
						//else
						//	Log.w(TAG, "registered image loader is null");
					}
				}
			};
		}
		return instance;
	}

	private ImageCache(Context context){
		open();
		appContext=context;
	}
	
	public void clearTopLevel(){
		cache.evictAll();
	}
	
	public LruCache<String, Bitmap> getLruCache(){
		return cache;
	}
	
	public void remove(String url){
		cache.remove(url);
	}
	
	public void put(String url, Bitmap bmp){
		cache.put(url, bmp);
	}

	public void registerDownloader(String scheme, ImageDownloader downloader){
		downloaders.put(scheme, downloader);
	}
	
	public boolean isInTopCache(String url){
		return cache!=null && url!=null && cache.contains(url) && cache.get(url)!=null;
	}
	
	public Bitmap getFromTop(String url){
		return cache.get(url);
	}
	
	public boolean isInCache(String url){
		if(cache.contains(url))
			return true;
		try{
			waitForDiskCache();
			if(diskCache.get(fn(url))!=null)
				return true;
		}catch(Exception x){}
		return false;
	}
	
	private static String fn(String url){
		return md5(url);
	}

	private static String md5(String h){
		try {
			MessageDigest md=MessageDigest.getInstance("MD5");
			byte[] s=md.digest(h.getBytes("UTF-8"));
			return String.format("%032x", new BigInteger(1, s));
		} catch (Exception ex) {
		}
		return "";
	}
	
	private void open(){
		new Thread(new Runnable() {
			@Override
			public void run() {
				synchronized(diskCacheLock) {
					try {
						diskCache = DiskLruCache.open(new File(appContext.getCacheDir(), "images"), 1, 1, params.diskCacheSize);
						for(WeakReference<ListImageLoaderWrapper> ref:registeredLoaders){
							if(ref.get()!=null)
								ref.get().updateImages();
						}
						diskCacheLock.notifyAll();
					} catch (Exception x) {
						Log.w(TAG, "Error opening disk cache", x);
					}
				}
			}
		}).start();
	}

	private void waitForDiskCache(){
		if(diskCache==null && Looper.myLooper()!=Looper.getMainLooper()) {
			synchronized (diskCacheLock) {
				if(diskCache==null){
					try{diskCacheLock.wait();}catch(Exception x){}
				}
			}
		}
	}

	public void registerLoader(ListImageLoaderWrapper loader){
		registeredLoaders.add(new WeakReference<ListImageLoaderWrapper>(loader));
		Iterator<WeakReference<ListImageLoaderWrapper>> itr=registeredLoaders.iterator();
		while(itr.hasNext()){
			WeakReference<ListImageLoaderWrapper> ref=itr.next();
			if(ref.get()==null)
				itr.remove();
		}
	}

	public Bitmap get(String url, @Nullable String persistentPath){
		return get(url, persistentPath, null, null, true);
	}

	public Bitmap get(String url, RequestWrapper w, ProgressCallback pc, boolean decode){
		return get(url, null, w, pc, decode);
	}

	/****
	 * Simplified version of {@link #get(String, RequestWrapper, ProgressCallback, boolean)}
	 * @param url Image URL
	 * @return Bitmap from cache
	 */
	public Bitmap get(String url){
		return get(url, null, null, null, true);
	}
	
	/****
	 * Get an image from cache (blocking)
	 * @param url Image URL, http or https scheme
	 * @param w RequestWrapper to cancel request or null
	 * @param pc ProgressCallback to receive download progress or null
	 * @param decode If false, only download to cache if not downloaded already 
	 * @return Bitmap from cache or null if decode==false
	 */
	public Bitmap get(String url, @Nullable String persistentPath, RequestWrapper w, ProgressCallback pc, boolean decode){
		if(DEBUG) Log.d(TAG, "Get image: "+decode+" "+url);
		if(TextUtils.isEmpty(url))
			return null;
		try{
			if(cache.contains(url)){
				if(!decode) return null;
				Bitmap bmp=cache.get(url);
				if(DEBUG) Log.v(TAG, url+" -> [ram] "+bmp);
				if(bmp!=null)return bmp;
				else cache.remove(url);
			}
			waitForDiskCache();
			DiskLruCache.Snapshot entry;
			String scheme="";
			for(int i=0;i<url.length();i++){
				if(url.charAt(i)==':'){
					scheme=url.substring(0, i);
					break;
				}
			}
			ImageDownloader downloader=downloaders.get(scheme);

			if(!downloader.isFileBased()){
				Bitmap bmp=downloader.getBitmap(url, decode, w);
				if(bmp!=null)
					cache.put(url, bmp);
				return bmp;
			}

			if(TextUtils.isEmpty(persistentPath)) {
				waitForDiskCache();
				entry = diskCache.get(fn(url));
				if (entry != null && (!decode || isValidBitmap(entry))) {
					InputStream in = entry.getInputStream(0);
					int[] size = {0,0};
					Bitmap bmp = decode ? decodeImage(in, size[0], size[1]) : null;
					in.close();
					if (decode) {
						try {
							cache.put(url, bmp);
						} catch (Exception x) {
						}
					}
					if (DEBUG) Log.v(TAG, url + " -> [disk] " + bmp);
					return bmp;
				}
			} else {
				File file = new File(persistentPath);
				InputStream is = null;
				OutputStream os = null;
				try {
					if(file.exists()) {
						final Bitmap bitmap = decodeImage(is = new FileInputStream(file), 0, 0);
						if(bitmap == null) {
							file.delete();
							File parent = file.getParentFile();
							if(parent.exists() || parent.mkdirs()) {
								if(file.createNewFile()) {
									downloadFile(url, w, pc, os = new FileOutputStream(file));
									return decodeImage(is = new FileInputStream(file), 0, 0);
								}
							}
						}
						cache.put(persistentPath, bitmap);
						return bitmap;
					} else {
						File parent = file.getParentFile();
						if(parent.exists() || parent.mkdirs()) {
							if(file.createNewFile()) {
								downloadFile(url, w, pc, os = new FileOutputStream(file));
								return decodeImage(is = new FileInputStream(file), 0, 0);
							}
						}
						return null;
					}
				} finally {
					try {
						if (is != null) {
							is.close();
						}
						if (os != null) {
							os.close();
						}
					} catch (IOException ignored) {}
				}
			}
			DiskLruCache.Editor editor=null;
			try{
				editor=diskCache.edit(fn(url));
				if(editor==null){
					while((editor=diskCache.edit(fn(url)))==null){
						Thread.sleep(10);
					}
					editor.abort();
					return get(url, persistentPath, w, pc, decode);
				}
				OutputStream out=editor.newOutputStream(0);
				//boolean ok=downloadFile(realURL, w, pc, out);
				boolean ok=downloader.downloadFile(url, out, pc, w);
				out.close();
				if(ok){
					editor.commit();
				}else{
					editor.abort();
					diskCache.remove(fn(url));
					return null;
				}
				editor=null;
			}catch(Exception x){
				Log.w(TAG, x);
				if(editor!=null){
					editor.abort();
				}
			}
			entry=diskCache.get(fn(url));
			InputStream in=entry.getInputStream(0);
			if(w!=null) decode=w.decode;
               if(decode){
                   if(!isValidBitmap(entry)){
                       in.close();
                       diskCache.remove(fn(url));
                       return null;
                   }
               }
			Bitmap bmp=decode ? decodeImage(in, 0, 0) : null;
			if(decode){
				try{
					cache.put(url, bmp);
				}catch(Exception x){}
			}
			if(DEBUG) Log.v(TAG, url+" -> [download] "+bmp);
			return bmp;
		}catch(Throwable x){
			Log.w(TAG, url, x);
		}
		//Log.e(TAG, "WTF2");
		return null;
	}

	private static boolean isValidBitmap(DiskLruCache.Snapshot e){
		InputStream is=e.getInputStream(0);
		//int w=0, h=0;
		try{
			DataInputStream in=new DataInputStream(is);
			int header=in.readInt();
			((FileInputStream)is).getChannel().position(e.getLength(0)-4);
			int trailer=in.readInt();
			//int header=((int)_header[0]&0xFF << 24) | ((int)_header[1] << 16) | ((int)_header[2] << 8) | (int)_header[3];
			//int trailer=((int)_trailer[0] << 24) | ((int)_trailer[1] << 16) | ((int)_trailer[2] << 8) | (int)_trailer[3];
			//Log.i(TAG, String.format("Header=%08X, trailer=%08X", header, trailer));
			if((header & 0xFFFF0000)==0xFFD80000 && (trailer & 0xFFFF)==0xFFD9){ // jpeg
				//Log.v(TAG, "Matched: JPEG");
				return true;
			}
			if(header==0x47494638 && (trailer & 0xFF)==0x3B){ // gif
				//Log.v(TAG, "Matched: GIF");
				return true;
			}
			if(header==0x89504E47 && trailer==0xAE426082){ // png
				//Log.v(TAG, "Matched: PNG");
				return true;
			}
			if(header==0x52494646){ // webp
				((FileInputStream)is).getChannel().position(4);
				int fileSize=Integer.reverseBytes(in.readInt());
				//Log.i(TAG, "Webp file size: "+fileSize+", expected: "+e.getLength(0));
				return e.getLength(0)==fileSize+8;
			}
		}catch(Throwable x){
			Log.w(TAG, x);
		}
		//Log.w(TAG, "No match!");
		//if(w<=0 || h<=0){
			try{
				is.close();
			}catch(Exception x){}
		//}
		//Log.i(TAG, "is valid: "+w+"x"+h);
		return false;
	}
	
	public Bitmap decodeImage(InputStream data, int w, int h){
		if(data==null){
			Log.w(TAG, "tried to decode null image");
			return null;
		}
		try{
			BitmapFactory.Options opts1=new BitmapFactory.Options();
			opts1.inJustDecodeBounds=true;
			if(data instanceof FileInputStream) ((FileInputStream)data).getChannel().position(0);
			BitmapFactory.decodeStream(data, null, opts1);
			if(data instanceof FileInputStream) ((FileInputStream)data).getChannel().position(0);
			int sampleSize=1;
			if(w!=0 || h!=0) {
				if(w!=0 && h!=0){
					sampleSize=Math.max((int)Math.floor(opts1.outWidth / (float) w), (int)Math.floor(opts1.outHeight / (float) h));
				}else if(w!=0){
					sampleSize=(int)Math.floor(opts1.outWidth / (float) w);
				}else if(h!=0){
					sampleSize=(int)Math.floor(opts1.outHeight / (float) h);
				}
			}else if(opts1.outWidth>1500 || opts1.outHeight>1500){
				Log.w(TAG, "Image too big: "+opts1.outWidth+"x"+opts1.outHeight);
				sampleSize=Math.max(opts1.outWidth, opts1.outHeight)/1500;
				Log.w(TAG, "Image will be downscaled to "+sampleSize+" times smaller");
				//return null;
			}
			
			BitmapFactory.Options opts=new BitmapFactory.Options();
			opts.inDither=false;
			opts.inSampleSize=sampleSize;
			//return BitmapFactory.decodeByteArray(data, 0, data.length, opts);
			Bitmap bmp=BitmapFactory.decodeStream(data, null, opts);
			if(w!=0){
				int dw=w;
				int dh=Math.round((float) bmp.getHeight() / bmp.getWidth() * w);
				bmp=Bitmap.createScaledBitmap(bmp, dw, dh, true);
			}else if(h!=0){
				int dw=Math.round((float)bmp.getWidth()/bmp.getHeight()*h);
				int dh=h;
				bmp=Bitmap.createScaledBitmap(bmp, dw, dh, true);
			}
			return bmp;
		}catch(Throwable t){ // We don't want to crash
			Log.e(TAG, "OH SHI~", t);
			if(cache.size()>1024){
				cache.evictAll();
				System.gc();
				
				return decodeImage(data, w, h);
			}
		}
		Log.e(TAG, "WTF?!");
		return null;
	}

	public boolean downloadFile(String url, ImageCache.RequestWrapper w, ImageCache.ProgressCallback pc, OutputStream out){
		String scheme="";
		for(int i=0;i<url.length();i++){
			if(url.charAt(i)==':'){
				scheme=url.substring(0, i);
				break;
			}
		}
		ImageDownloader downloader=downloaders.get(scheme);
		if(downloader==null || !downloader.isFileBased())
			return false;
		try{
			return downloader.downloadFile(url, out, pc, w);
		}catch(IOException e){
			return false;
		}
	}

	public void clear(){
		try{
			waitForDiskCache();
			diskCache.delete();
		}catch(Exception x){}
		open();
	}
	
	public static class RequestWrapper{
		public boolean decode=true;
		public Call call;
		private boolean canceled;

		public void cancel(){
			canceled=true;
				if(call!=null)
					call.cancel();
				call=null;
		}

		public boolean isCanceled(){
			return canceled;
		}
	}

	/*package*/ Context getAppContext(){
		return appContext;
	}
	
	public static interface ProgressCallback{
		public void onProgressChanged(int progress, int total);
	}

	public static class Parameters{
		/**
		 * The max size of the in-memory cache as a fraction of total RAM available to the app as per ActivityManager.getMemoryClass()
		 */
		public int memoryCacheSize=7;

		/**
		 * Max size of the disk cache in bytes
		 */
		public int diskCacheSize=20*1024*1024;
	}
}
