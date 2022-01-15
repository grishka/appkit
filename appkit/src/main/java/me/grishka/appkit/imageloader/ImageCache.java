package me.grishka.appkit.imageloader;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageDecoder;
import android.graphics.Movie;
import android.graphics.drawable.AnimatedImageDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Looper;
import android.util.Log;
import android.util.LruCache;
import android.util.Size;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;

import androidx.annotation.Nullable;
import me.grishka.appkit.imageloader.disklrucache.DiskLruCache;
import me.grishka.appkit.imageloader.downloaders.ContentImageDownloader;
import me.grishka.appkit.imageloader.downloaders.FileImageDownloader;
import me.grishka.appkit.imageloader.downloaders.HTTPImageDownloader;
import me.grishka.appkit.imageloader.downloaders.ImageDownloader;
import me.grishka.appkit.imageloader.processing.ImageProcessingStep;
import me.grishka.appkit.imageloader.requests.ImageLoaderRequest;
import okhttp3.Call;

/**
 * The main class handling image loading and caching.
 */
public class ImageCache{
	
	public static final boolean DEBUG=false;
	
	private final ArrayList<WeakReference<ListImageLoaderWrapper>> registeredLoaders=new ArrayList<>();

	private LruCache<String, Drawable> cache;
	
	private DiskLruCache diskCache=null;
	private final Object diskCacheLock=new Object();
	private Context appContext;

	private static ImageCache instance=null;
	private static Parameters params=new Parameters();
	private static final ArrayList<ImageDownloader> downloaders=new ArrayList<>();

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

			downloaders.add(new HTTPImageDownloader());
			downloaders.add(new ContentImageDownloader(instance, context.getApplicationContext()));
			downloaders.add(new FileImageDownloader(instance));

			instance.appContext=context.getApplicationContext();
			ActivityManager am=(ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
			int memoryClass=(context.getApplicationInfo().flags & ApplicationInfo.FLAG_LARGE_HEAP)==0 ? am.getMemoryClass() : am.getLargeMemoryClass();
			int cacheSize=Math.min(params.maxMemoryCacheSize, memoryClass/params.memoryCacheSize*1024*1024);
			instance.cache=new LruCache<String, Drawable>(cacheSize) {
				@Override
				protected int sizeOf(String key, Drawable value) {
					if(value instanceof BitmapDrawable)
						return ((BitmapDrawable) value).getBitmap().getAllocationByteCount();
					return value.getIntrinsicWidth()*value.getIntrinsicHeight()*4; // probably very conservative
				}

				@Override
				protected void entryRemoved(boolean evicted, String key, Drawable oldValue, Drawable newValue){
					if(newValue!=null) return;
					for(WeakReference<ListImageLoaderWrapper> ref:instance.registeredLoaders){
						if(ref.get()!=null)
							ref.get().onCacheEntryRemoved(key);
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
	
	public LruCache<String, Drawable> getLruCache(){
		return cache;
	}
	
	public void remove(ImageLoaderRequest req){
		cache.remove(req.getMemoryCacheKey());
	}
	
	public void put(ImageLoaderRequest req, Drawable bmp){
		cache.put(req.getMemoryCacheKey(), bmp);
	}

	public static void registerDownloader(ImageDownloader downloader){
		downloaders.add(downloader);
	}

	public static void registerDownloader(int index, ImageDownloader downloader){
		downloaders.add(index, downloader);
	}

	public boolean isInTopCache(ImageLoaderRequest req){
		return cache!=null && req!=null && cache.get(req.getMemoryCacheKey())!=null;
	}
	
	public Drawable getFromTop(ImageLoaderRequest req){
		return cache.get(req.getMemoryCacheKey());
	}
	
	public boolean isInCache(ImageLoaderRequest req){
		if(cache.get(req.getMemoryCacheKey())!=null)
			return true;
		try{
			waitForDiskCache();
			if(diskCache==null) // it still might be if called from UI thread
				return false;
			if(diskCache.get(req.getDiskCacheKey())!=null)
				return true;
		}catch(IOException x){
			Log.w(TAG, x);
		}
		return false;
	}

	private void open(){
		new Thread(()->{
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
		}).start();
	}

	private void waitForDiskCache(){
		if(diskCache==null && Looper.myLooper()!=Looper.getMainLooper()) {
			synchronized (diskCacheLock) {
				while(diskCache==null){
					try{diskCacheLock.wait();}catch(InterruptedException ignore){}
				}
			}
		}
	}

	public void registerLoader(ListImageLoaderWrapper loader){
		registeredLoaders.add(new WeakReference<>(loader));
		Iterator<WeakReference<ListImageLoaderWrapper>> itr=registeredLoaders.iterator();
		while(itr.hasNext()){
			WeakReference<ListImageLoaderWrapper> ref=itr.next();
			if(ref.get()==null)
				itr.remove();
		}
	}

	public Drawable get(ImageLoaderRequest req, @Nullable String persistentPath){
		return get(req, persistentPath, null, null, true);
	}

	public Drawable get(ImageLoaderRequest req, RequestWrapper w, ProgressCallback pc, boolean decode){
		return get(req, null, w, pc, decode);
	}

	/****
	 * Simplified version of {@link #get(ImageLoaderRequest, RequestWrapper, ProgressCallback, boolean)}
	 * @param req Request
	 * @return Bitmap from cache
	 */
	public Drawable get(ImageLoaderRequest req){
		return get(req, null, null, null, true);
	}
	
	/****
	 * Get an image from cache (blocking)
	 * @param req Request
	 * @param w RequestWrapper to cancel request or null
	 * @param pc ProgressCallback to receive download progress or null
	 * @param decode If false, only download to cache if not downloaded already 
	 * @return Bitmap from cache or null if decode==false
	 */
	public Drawable get(ImageLoaderRequest req, @Nullable String persistentPath, RequestWrapper w, ProgressCallback pc, boolean decode){
		if(DEBUG) Log.d(TAG, "Get image: "+decode+" "+req);
		try{
			String memKey=req.getMemoryCacheKey();
			Drawable bmp=cache.get(memKey);
			if(bmp!=null){
				return bmp;
			}
			waitForDiskCache();
			DiskLruCache.Value entry;
			ImageDownloader downloader=null;
			for(ImageDownloader candidate:downloaders){
				if(candidate.canHandleRequest(req)){
					downloader=candidate;
					break;
				}
			}
			if(downloader==null){
				Log.w(TAG, "Could not find a downloader to perform request "+req);
				return null;
			}

			if(!downloader.needsDiskCache()){
				bmp=downloader.getDrawable(req, decode, w);
				if(bmp!=null)
					cache.put(memKey, bmp);
				return bmp;
			}

			String diskKey=req.getDiskCacheKey();

			if(persistentPath==null){
				waitForDiskCache();
				entry = diskCache.get(diskKey);
				if(entry!=null){
					bmp=decode ? decodeImage(entry.getFile(0), null, req) : null;
					if(decode)
						cache.put(memKey, bmp);
					if(DEBUG)
						Log.v(TAG, req + " -> [disk] " + bmp);
					return bmp;
				}
			}else{
				File file = new File(persistentPath);
				if(file.exists()) {
					bmp=decodeImage(file, null, req);
					if(bmp==null){
						file.delete();
						File parent = file.getParentFile();
						if(parent.exists() || parent.mkdirs()){
							if(file.createNewFile()){
								try(FileOutputStream out=new FileOutputStream(file)){
									downloadFile(req, w, pc, out);
								}
								return decodeImage(file, null, req);
							}
						}
					}
					cache.put(memKey, bmp);
					return bmp;
				} else {
					File parent = file.getParentFile();
					if(parent.exists() || parent.mkdirs()) {
						if(file.createNewFile()) {
							try(FileOutputStream out=new FileOutputStream(file)){
								downloadFile(req, w, pc, out);
							}
							return decodeImage(file, null, req);
						}
					}
					return null;
				}
			}
			DiskLruCache.Editor editor=null;
			try{
				editor=diskCache.edit(diskKey);
				if(editor==null){
					while((editor=diskCache.edit(diskKey))==null){
						Thread.sleep(10);
					}
					editor.abort();
					return get(req, null, w, pc, decode);
				}
				boolean ok;
				try(OutputStream out=new FileOutputStream(editor.getFile(0))){
					ok=downloader.downloadFile(req, out, pc, w);
				}
				if(ok){
					editor.commit();
				}else{
					editor.abort();
					diskCache.remove(diskKey);
					return null;
				}
			}catch(Exception x){
				if(w==null || !w.canceled)
					Log.w(TAG, x);
				if(editor!=null)
					editor.abort();
			}
			entry=diskCache.get(diskKey);
			if(w!=null) decode=w.decode;
			if(entry!=null){
				bmp=decode ? decodeImage(entry.getFile(0), null, req) : null;
			}
			if(decode && bmp!=null){
				cache.put(memKey, bmp);
			}
			if(DEBUG) Log.v(TAG, req+" -> [download] "+bmp);
			return bmp;
		}catch(Throwable x){
			Log.w(TAG, req.toString(), x);
		}
		return null;
	}

	public Drawable decodeImage(File file, Uri uri, ImageLoaderRequest req){
		if(file==null && uri==null){
			Log.w(TAG, "tried to decode null image");
			return null;
		}
		try{
			Drawable drawable;
			if(Build.VERSION.SDK_INT>=28){
				ImageDecoder.Source source;
				if(file!=null)
					source=ImageDecoder.createSource(file);
				else
					source=ImageDecoder.createSource(appContext.getContentResolver(), uri);
				drawable=ImageDecoder.decodeDrawable(source, (decoder, info, src) -> {
					if(req.desiredMaxWidth!=0 || req.desiredMaxHeight!=0){
						Size size=info.getSize();
						int w=size.getWidth();
						int h=size.getHeight();
						if(req.desiredMaxHeight!=0 && req.desiredMaxWidth!=0 && (w>req.desiredMaxWidth || h>req.desiredMaxHeight)){
							float ratio=Math.max(w/(float)req.desiredMaxWidth, h/(float)req.desiredMaxHeight);
							decoder.setTargetSize(Math.round(w/ratio), Math.round(h/ratio));
						}else if(req.desiredMaxHeight==0 && w>req.desiredMaxWidth){
							decoder.setTargetSize(req.desiredMaxWidth, Math.round(req.desiredMaxWidth/(float)w*h));
						}else if(req.desiredMaxWidth==0 && h>req.desiredMaxHeight){
							decoder.setTargetSize(Math.round(req.desiredMaxHeight/(float)h*w), req.desiredMaxHeight);
						}
					}
					if(req.desiredConfig!=Bitmap.Config.HARDWARE){
						decoder.setMutableRequired(true);
					}
				});
			}else{
				boolean isAnimatedGif=false;
				BitmapFactory.Options opts1=new BitmapFactory.Options();
				opts1.inJustDecodeBounds=true;
				if(file!=null){
					try(FileInputStream in=new FileInputStream(file)){
						BitmapFactory.decodeStream(in, null, opts1);
						in.getChannel().position(0);
						isAnimatedGif=isAnimatedGif(in);
					}
				}else{
					try(InputStream in=appContext.getContentResolver().openInputStream(uri)){
						BitmapFactory.decodeStream(in, null, opts1);
					}
				}
				if(isAnimatedGif){
					Movie movie=Movie.decodeFile(file.getAbsolutePath());
					drawable=new MovieDrawable(movie);
				}else{
					int sampleSize=1;
					int w=req.desiredMaxWidth;
					int h=req.desiredMaxHeight;
					if(w!=0 || h!=0){
						if(w!=0 && h!=0){
							sampleSize=Math.max((int) Math.floor(opts1.outWidth/(float) w), (int) Math.floor(opts1.outHeight/(float) h));
						}else if(w!=0){
							sampleSize=(int) Math.floor(opts1.outWidth/(float) w);
						}else if(h!=0){
							sampleSize=(int) Math.floor(opts1.outHeight/(float) h);
						}
					}

					BitmapFactory.Options opts=new BitmapFactory.Options();
					opts.inDither=false;
					opts.inSampleSize=sampleSize;
					opts.inPreferredConfig=req.desiredConfig;
					Bitmap bmp;
					if(file!=null){
						bmp=BitmapFactory.decodeFile(file.getAbsolutePath(), opts);
					}else{
						try(InputStream in=appContext.getContentResolver().openInputStream(uri)){
							bmp=BitmapFactory.decodeStream(in, null, opts);
						}
					}
					if(w!=0){
						int dw=w;
						int dh=Math.round((float) bmp.getHeight()/bmp.getWidth()*w);
						bmp=Bitmap.createScaledBitmap(bmp, dw, dh, true);
					}else if(h!=0){
						int dw=Math.round((float) bmp.getWidth()/bmp.getHeight()*h);
						int dh=h;
						bmp=Bitmap.createScaledBitmap(bmp, dw, dh, true);
					}
					drawable=new BitmapDrawable(bmp);
				}
			}

			for(ImageProcessingStep step:req.processingSteps)
				drawable=step.processDrawable(drawable);

			return drawable;
		}catch(Throwable t){ // We don't want to crash
			Log.e(TAG, "OH SHI~", t);
			if(cache.size()>1024){
				cache.evictAll();
				System.gc();
				
				return decodeImage(file, uri, req);
			}
		}
		Log.e(TAG, "WTF?!");
		return null;
	}

	public boolean downloadFile(ImageLoaderRequest req, ImageCache.RequestWrapper w, ImageCache.ProgressCallback pc, OutputStream out){
		ImageDownloader downloader=null;
		for(ImageDownloader candidate:downloaders){
			if(candidate.canHandleRequest(req)){
				downloader=candidate;
				break;
			}
		}
		if(downloader==null){
			Log.w(TAG, "Could not find a downloader to perform request "+req);
			return false;
		}
		if(!downloader.needsDiskCache())
			return false;
		try{
			return downloader.downloadFile(req, out, pc, w);
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

	private boolean isAnimatedGif(InputStream in) throws IOException{
		byte[] magic=new byte[6];
		if(in.read(magic)!=6 || magic[0]!='G' || magic[1]!='I' || magic[2]!='F' || magic[3]!='8' || (magic[4]!='9' && magic[4]!='7') || magic[5]!='a')
			return false; // it's not a gif in the first place
		if(in.skip(4)<4) // width & height
			return false;
		int flags=in.read();
		if(in.skip(2)<2) // background color & pixel aspect
			return false;
		if((flags & 0x80)==0x80){ // global color table
			int tableLength=(2 << (flags & 7))*3;
			if(in.skip(tableLength)<tableLength)
				return false;
		}
		int extCode=in.read();
		while(extCode==0x21){
			int extType=in.read();
			if(extType==0xFF){ // application extension, specifies loop count, so this is an animation
				return true;
			}else if(extType==0x01){ // plain text extension
				int length=in.read();
				if(in.skip(length)<length)
					return false;
			}else if(extType==0xF9){ // graphics control extension
				if(in.skip(1)!=1) // length
					return false;
				int controlFlags=in.read();
				return (controlFlags & 0x1C)!=0; // it's probably an animation if it has something in "disposal method"
			}
			extCode=in.read();
		}
		return false;
	}

	public interface ProgressCallback{
		void onProgressChanged(int progress, int total);
	}

	public static class Parameters{
		/**
		 * The max size of the in-memory cache as a fraction of total RAM available to the app as per ActivityManager.getMemoryClass()
		 */
		public int memoryCacheSize=5;

		/**
		 * Limit to the size of in-memory cache in bytes
		 */
		public int maxMemoryCacheSize=50*1024*1024;

		/**
		 * Max size of the disk cache in bytes
		 */
		public int diskCacheSize=20*1024*1024;
	}
}
