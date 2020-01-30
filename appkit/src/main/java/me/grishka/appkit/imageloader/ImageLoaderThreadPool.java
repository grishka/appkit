package me.grishka.appkit.imageloader;

import android.os.Process;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import me.grishka.appkit.utils.WorkerThread;

/**
 * Created by grishka on 17.12.14.
 */
public class ImageLoaderThreadPool {
	private static final int THREAD_COUNT=4;
	private static ThreadPoolExecutor networkExecutor=new ThreadPoolExecutor(THREAD_COUNT, THREAD_COUNT, 60, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>(), new LoaderThreadFactory());
	private static ThreadPoolExecutor cacheExecutor=new ThreadPoolExecutor(THREAD_COUNT, THREAD_COUNT, 60, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>(), new LoaderThreadFactory());
	private static WorkerThread canceler;

	static{
		canceler=new WorkerThread("ImageLoader canceler");
		canceler.start();
	}

	/*package*/ static void enqueueTask(Runnable task){
		networkExecutor.execute(task);
	}

	/*package*/ static void enqueueCachedTask(Runnable task){
		cacheExecutor.execute(task);
	}

	/*package*/ static void enqueueCancellation(Runnable task){
		canceler.postRunnable(task, 0);
	}

	private static class LoaderThreadFactory implements ThreadFactory{
		private int num=0;
		@Override
		public Thread newThread(Runnable r) {
			return new Thread(r){
				@Override
				public void run() {
					Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
					setName("ImageLoaderThread #"+(++num));
					super.run();
				}
			};
		}
	}
}
