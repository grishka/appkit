package me.grishka.appkit.imageloader;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageDecoder;
import android.graphics.Matrix;
import android.graphics.Movie;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.ExifInterface;
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
import java.util.HashMap;
import java.util.Iterator;
import java.util.Objects;
import java.util.function.Consumer;

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
@SuppressLint("NewApi")
public class ImageCache{
	
	public static final boolean DEBUG=false;
	
	private final ArrayList<WeakReference<ListImageLoaderWrapper>> registeredLoaders=new ArrayList<>();

	private LruCache<String, Drawable> cache;
	
	private DiskLruCache diskCache=null;
	private final Object diskCacheLock=new Object();
	private Context appContext;
	private final HashMap<String, ImageDownloadInfo> currentlyLoading=new HashMap<>();
	private final ArrayList<Runnable> runAfterDiskCacheOpens=new ArrayList<>();

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
			instance.cache=new LruCache<>(cacheSize){
				@Override
				protected int sizeOf(String key, Drawable value){
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

	public DiskLruCache getDiskCache(){
		waitForDiskCache();
		return diskCache;
	}

	public File getFile(ImageLoaderRequest req) throws IOException{
		waitForDiskCache();
		DiskLruCache.Value val=diskCache.get(req.getDiskCacheKey());
		return val!=null ? val.getFile(0) : null;
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
			if(DEBUG) Log.i(TAG, "Opening disk cache");
			synchronized(diskCacheLock) {
				try {
					diskCache = DiskLruCache.open(new File(appContext.getCacheDir(), "images"), 1, 1, params.diskCacheSize);
					if(DEBUG) Log.i(TAG, "Done opening disk cache");
					for(WeakReference<ListImageLoaderWrapper> ref:registeredLoaders){
						ListImageLoaderWrapper loader=ref.get();
						if(loader!=null){
							if(DEBUG) Log.d(TAG, "Calling updateImages() on "+loader);
							loader.updateImages();
						}else if(DEBUG){
							Log.d(TAG, "A registered ListImageLoaderWrapper reference was null");
						}
					}
					diskCacheLock.notifyAll();
					synchronized(runAfterDiskCacheOpens){
						for(Runnable r:runAfterDiskCacheOpens){
							if(DEBUG) Log.d(TAG, "Running: "+r);
							r.run();
						}
						runAfterDiskCacheOpens.clear();
					}
					if(DEBUG) Log.i(TAG, "Cache initialization done");
				} catch (Exception x) {
					Log.w(TAG, "Error opening disk cache", x);
				}
			}
		}).start();
	}

	private void waitForDiskCache(){
		if(diskCache==null){
			if(DEBUG) Log.i(TAG, Thread.currentThread().getName()+": waitForDiskCache()");
			if(Looper.myLooper()!=Looper.getMainLooper()){
				synchronized(diskCacheLock){
					while(diskCache==null){
						try{diskCacheLock.wait();}catch(InterruptedException ignore){}
					}
				}
				if(DEBUG) Log.i(TAG, Thread.currentThread().getName()+": done waiting for disk cache");
			}else if(DEBUG){
				Log.w(TAG, "Skipped waiting for disk cache because we're on the main thread");
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

	/****
	 * Load an image or retrieve it from cache
	 * @param req Request
	 * @param pc ProgressCallback to receive download progress or null
	 * @param callback The callback to be invoked (possibly on a background thread!) when the image has been loaded or when an error occurs
	 * @param decode If false, only download to cache if not downloaded already
	 * @return An object you can use to cancel the request
	 */
	public PendingImageRequest get(ImageLoaderRequest req, ProgressCallback pc, ImageLoaderCallback callback, final boolean decode){
		if(DEBUG) Log.d(TAG, "Get image: "+decode+" "+req);
		String diskKey=req.getDiskCacheKey();
		final ImageDownloadInfo dlInfo;
		final PendingImageRequest pendingRequest;
		synchronized(currentlyLoading){
			if(currentlyLoading.containsKey(diskKey)){
				ImageDownloadInfo existingDlInfo=Objects.requireNonNull(currentlyLoading.get(diskKey));
				pendingRequest=new PendingImageRequest(existingDlInfo, callback);
				pendingRequest.decode=decode;
				existingDlInfo.requests.add(pendingRequest);
				if(DEBUG) Log.d(TAG, "Adding "+req+" to existing download info "+existingDlInfo);
				return pendingRequest;
			}else{
				dlInfo=new ImageDownloadInfo(diskKey);
				pendingRequest=new PendingImageRequest(dlInfo, callback);
				pendingRequest.decode=decode;
				dlInfo.requests.add(pendingRequest);
				currentlyLoading.put(diskKey, dlInfo);
			}
		}
		getInternal(req, pc, dlInfo, pendingRequest);
		return pendingRequest;
	}

	private void getInternal(ImageLoaderRequest req, ProgressCallback pc, final ImageDownloadInfo dlInfo, final PendingImageRequest pendingRequest){
		String memKey=req.getMemoryCacheKey();
		String diskKey=req.getDiskCacheKey();
		try{
			Drawable bmp=cache.get(memKey);
			if(bmp!=null){
				invokeCompletionCallbacks(req, bmp);
				return;
			}
			ImageDownloader downloader=downloaders.stream().filter(candidate->candidate.canHandleRequest(req)).findFirst().orElse(null);
			if(downloader==null){
				throw new IOException("Could not find a downloader to perform request "+req);
			}

			if(DEBUG) Log.v(TAG, "Using downloader "+downloader+" for "+req);

			if(!downloader.needsDiskCache()){
				ImageLoaderThreadPool.enqueueCachedTask(()->{
					try{
						Drawable img=downloader.getDrawable(req, dlInfo.needDecode(), dlInfo);
						if(img!=null)
							cache.put(memKey, img);
						invokeCompletionCallbacks(req, img);
					}catch(IOException x){
						invokeFailureCallbacks(req, x);
					}
				});
				return;
			}

			if(diskCache==null){
				synchronized(runAfterDiskCacheOpens){
					if(DEBUG) Log.d(TAG, "Disk cache not open yet, delaying "+req);
					runAfterDiskCacheOpens.add(()->getInternal(req, pc, dlInfo, pendingRequest));
				}
				return;
			}
			DiskLruCache.Value entry=diskCache.get(diskKey);
			if(entry!=null){
				if(DEBUG) Log.v(TAG, "Found "+req+" in disk cache");
				if(dlInfo.needDecode()){
					decodeImageAsync(entry.getFile(0), null, req, img->{
						if(DEBUG)
							Log.v(TAG, req+" -> [disk] "+img);
						cache.put(memKey, img);
						invokeCompletionCallbacks(req, img);
					}, err->{
						invokeFailureCallbacks(req, err);
					});
				}else{
					invokeCompletionCallbacks(req, null);
				}
				return;
			}
			final DiskLruCache.Editor editor=diskCache.edit(diskKey);
			if(editor==null){
				throw new IllegalStateException("Another thread has this file open -- should never happen");
			}
			OutputStream out=new FileOutputStream(editor.getFile(0));
			downloader.downloadFile(req, out, pc, dlInfo, ()->{
				try{
					out.close();
					editor.commit();
					if(pendingRequest.canceled)
						return;
					DiskLruCache.Value value=diskCache.get(diskKey);
					if(dlInfo.needDecode()){
						if(value!=null){
							decodeImageAsync(value.getFile(0), null, req, img->{
								if(DEBUG) Log.v(TAG, req+" -> [download] "+img);
								cache.put(memKey, img);
								invokeCompletionCallbacks(req, img);
							}, err->{
								invokeFailureCallbacks(req, err);
							});
						}
					}else{
						invokeCompletionCallbacks(req, null);
					}
				}catch(Throwable x){
					Log.w(TAG, x);
					invokeFailureCallbacks(req, x);
				}
			}, err->{
				try{
					out.close();
					editor.abort();
					diskCache.remove(diskKey);
				}catch(IOException x){
					Log.e(TAG, "Failed to remove a failed download from disk cache", x);
				}
				if(!pendingRequest.canceled)
					invokeFailureCallbacks(req, err);
			});

		}catch(Throwable x){
			Log.w(TAG, req.toString(), x);
			invokeFailureCallbacks(req, x);
		}
	}

	private void invokeCompletionCallbacks(ImageLoaderRequest req, Drawable img){
		if(DEBUG) Log.v(TAG, "Invoking completion callbacks for request "+req+", drawable "+img);
		synchronized(currentlyLoading){
			ImageDownloadInfo info=Objects.requireNonNull(currentlyLoading.remove(req.getDiskCacheKey()));
			for(PendingImageRequest pr:info.requests){
				pr.callback.onImageLoaded(req, img);
			}
		}
	}

	private void invokeFailureCallbacks(ImageLoaderRequest req, Throwable error){
		if(DEBUG) Log.v(TAG, "Invoking failure callbacks for request "+req+", error "+error);
		synchronized(currentlyLoading){
			ImageDownloadInfo info=currentlyLoading.remove(req.getDiskCacheKey());
			if(info==null){
				if(DEBUG) Log.w(TAG, "No download info found for "+req);
				return;
			}
			for(PendingImageRequest pr:info.requests){
				pr.callback.onImageLoadingFailed(req, error);
			}
		}
	}

	public void decodeImageAsync(File file, Uri uri, ImageLoaderRequest req, Consumer<Drawable> onSuccess, Consumer<Throwable> onError){
		ImageLoaderThreadPool.enqueueCachedTask(()->{
			Drawable image=null;
			boolean success=false;
			try{
				image=decodeImage(file, uri, req);
				success=true;
			}catch(Throwable x){
				Log.w(TAG, "Failed to decode "+(file==null ? uri : file), x);
				onError.accept(x);
			}
			if(success)
				onSuccess.accept(image);
		});
	}

	public Drawable decodeImage(File file, Uri uri, ImageLoaderRequest req) throws IOException{
		if(file==null && uri==null){
			throw new IllegalArgumentException("file or uri must be non-null");
		}
		if(DEBUG) Log.v(TAG, "Decoding file "+file+", uri "+uri+", req "+req);
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
					decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
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

				if(uri!=null && Build.VERSION.SDK_INT>=Build.VERSION_CODES.N){
					try(InputStream in=appContext.getContentResolver().openInputStream(uri)){
						ExifInterface exif=new ExifInterface(in);
						Bitmap rotated=applyExifRotation(bmp, exif);
						if(rotated!=null)
							bmp=rotated;
					}
				}

				drawable=new BitmapDrawable(bmp);
			}
		}

		for(ImageProcessingStep step:req.processingSteps){
			if(DEBUG) Log.v(TAG, "Applying processing step "+step+" for "+req);
			drawable=step.processDrawable(drawable);
		}

		if(DEBUG) Log.v(TAG, "Decode for "+req+" done, drawable "+drawable);

		return drawable;
	}

	private Bitmap applyExifRotation(Bitmap bitmap, ExifInterface exif){
		int orientation=exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
		int rotation;
		switch(orientation){
			case ExifInterface.ORIENTATION_ROTATE_90:
				rotation=90;
				break;
			case ExifInterface.ORIENTATION_ROTATE_180:
				rotation=180;
				break;
			case ExifInterface.ORIENTATION_ROTATE_270:
				rotation=270;
				break;
			default:
				return null;
		}
		Matrix matrix=new Matrix();
		matrix.setRotate(rotation);
		return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, false);
	}

	public void clear(){
		try{
			waitForDiskCache();
			diskCache.delete();
		}catch(Exception x){
			Log.w(TAG, x);
		}
		open();
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

	public class ImageDownloadInfo{
		private ArrayList<PendingImageRequest> requests=new ArrayList<>();
		private final String diskCacheKey;
		public Call httpCall;
		private boolean canceled;

		public ImageDownloadInfo(String diskCacheKey){
			this.diskCacheKey=diskCacheKey;
		}

		private void cancel(PendingImageRequest req){
			if(!requests.remove(req))
				return;
			if(DEBUG) Log.v(TAG, "Removed "+req+" for "+this+", "+requests.size()+" requests remaining, httpCall "+httpCall);
			if(requests.isEmpty() && httpCall!=null){
				ImageLoaderThreadPool.enqueueCancellation(()->{
					if(httpCall!=null){
						if(DEBUG) Log.v(TAG, "Canceling httpCall "+httpCall);
						httpCall.cancel();
						httpCall=null;
					}
					synchronized(currentlyLoading){
						canceled=true;
						currentlyLoading.remove(diskCacheKey);
					}
				});
			}
		}

		private boolean needDecode(){
			for(PendingImageRequest req:requests){
				if(req.decode)
					return true;
			}
			return false;
		}
	}

	public class PendingImageRequest{
		private boolean decode=true, canceled=false;
		private final ImageDownloadInfo info;
		private final ImageLoaderCallback callback;

		private PendingImageRequest(ImageDownloadInfo info, ImageLoaderCallback callback){
			this.info=info;
			this.callback=callback;
		}

		public void cancel(){
			if(canceled)
				return;
			if(DEBUG) Log.v(TAG, "Canceling "+this);
			canceled=true;
			info.cancel(this);
		}

		public void setDecode(boolean decode){
			this.decode=decode;
		}
	}
}
