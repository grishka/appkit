package me.grishka.appkit.utils;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;

public class WorkerThread extends Thread{
	public Handler handler;
	private final Object handlerSyncObject;

	public WorkerThread(final String threadName){
		super(threadName);
		handlerSyncObject=new Object();
	}

	public void sendMessage(Message msg, int delay){
		synchronized(handlerSyncObject){
			if(handler==null){
				try{
					handlerSyncObject.wait();
				}catch(Exception ignore){}
			}
		}

		if(delay<=0)
			handler.sendMessage(msg);
		else
			handler.sendMessageDelayed(msg, delay);
	}

	public void postRunnable(Runnable runnable, int delay){
		synchronized(handlerSyncObject){
			if(handler==null){
				try{
					handlerSyncObject.wait();
				}catch(Exception ignore){}
			}
		}

		if(delay<=0)
			handler.post(runnable);
		else
			handler.postDelayed(runnable, delay);
	}

	public void run(){
		Looper.prepare();

		synchronized(handlerSyncObject){
			handler=new Handler();
			handlerSyncObject.notifyAll();
		}

		Looper.loop();
	}
}