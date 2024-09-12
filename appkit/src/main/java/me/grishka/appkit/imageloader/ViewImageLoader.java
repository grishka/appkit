package me.grishka.appkit.imageloader;

import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;

import me.grishka.appkit.R;
import me.grishka.appkit.imageloader.requests.ImageLoaderRequest;

/**
 * Created by grishka on 17.12.14.
 */
public class ViewImageLoader{
	private static final String TAG="ViewImageLoader";

	private ViewImageLoader() {
	}

	private static Handler uiHandler = new Handler(Looper.getMainLooper());

	public static void load(ImageView view, Drawable placeholder, ImageLoaderRequest req) {
		load(new ImageViewTarget(view), placeholder, req);
	}

	public static void load(Target target, Drawable placeholder, ImageLoaderRequest req) {
		load(target, placeholder, req, true);
	}

	public static void load(Target target, Drawable placeholder, ImageLoaderRequest req, boolean animate) {
		load(target, placeholder, req, animate, false);
	}

	public static void loadWithoutAnimation(ImageView view, Drawable placeholder, ImageLoaderRequest req){
		load(new ImageViewTarget(view), placeholder, req, false);
	}

	public static void load(Target target, Drawable placeholder, ImageLoaderRequest req, boolean animate, boolean allowMultiple) {
		View view=target.getView();
		if(!allowMultiple){
			LoadTask prevTask=(LoadTask) view.getTag(R.id.tag_image_load_task);
			if(prevTask!=null){
				prevTask.cancel();
				target.getView().setTag(R.id.tag_image_load_task, null);
			}
		}

		if (ImageCache.getInstance(target.getView().getContext()).isInTopCache(req)) {
			target.setImageDrawable(ImageCache.getInstance(target.getView().getContext()).getFromTop(req));
			return;
		}
		target.setImageDrawable(placeholder);

		LoadTask task = new LoadTask();
		task.target = target;
		task.req = req;
		task.animate = animate;
		view.setTag(R.id.tag_image_load_task, task);
		View.OnAttachStateChangeListener detachListener=new View.OnAttachStateChangeListener() {
			@Override
			public void onViewAttachedToWindow(View view) {

			}

			@Override
			public void onViewDetachedFromWindow(View view) {
				LoadTask task = (LoadTask) view.getTag(R.id.tag_image_load_task);
				if (task != null) {
					task.cancel();
					view.setTag(R.id.tag_image_load_task, null);
				}
			}
		};
		view.setTag(R.id.tag_detach_listener, detachListener);
		view.addOnAttachStateChangeListener(detachListener);
		task.run();
	}

	public interface Target {
		void setImageDrawable(Drawable d);
		View getView();
	}

	public static class ImageViewTarget implements Target {

		private ImageView view;

		public ImageViewTarget(ImageView v) {
			view = v;
		}

		@Override
		public void setImageDrawable(Drawable d) {
			view.setImageDrawable(d);
		}

		@Override
		public View getView() {
			return view;
		}
	}

	private static class LoadTask implements Runnable {

		private boolean canceled = false;
		private ImageCache.PendingImageRequest pendingRequest;
		public Target target;
		public ImageLoaderRequest req;
		public boolean animate;

		public void cancel() {
			Log.i(TAG, "Canceled "+req);
			canceled = true;
			if(pendingRequest!=null)
				pendingRequest.cancel();
		}

		private void removeDetachListener(){
			View view=target.getView();
			View.OnAttachStateChangeListener listener=(View.OnAttachStateChangeListener) view.getTag(R.id.tag_detach_listener);
			view.setTag(R.id.tag_detach_listener, null);
			view.removeOnAttachStateChangeListener(listener);
		}

		@Override
		public void run() {
			if (canceled) {
				return;
			}
			pendingRequest=ImageCache.getInstance(target.getView().getContext()).get(req, null, new ImageLoaderCallback(){
				@Override
				public void onImageLoaded(ImageLoaderRequest req, Drawable image){
					if(!canceled){
						uiHandler.post(()->{
							removeDetachListener();
							if (canceled) {
								return;
							}
							target.setImageDrawable(image);
							if (animate) {
								target.getView().setAlpha(0);
								target.getView().animate().alpha(1).setDuration(200).start();
							}
							if(image instanceof Animatable anim)
								anim.start();
						});
					}
				}

				@Override
				public void onImageLoadingFailed(ImageLoaderRequest req, Throwable error){
					uiHandler.post(LoadTask.this::removeDetachListener);
				}
			}, true);
		}
	}
}
