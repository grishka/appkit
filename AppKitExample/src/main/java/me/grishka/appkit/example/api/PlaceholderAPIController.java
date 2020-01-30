package me.grishka.appkit.example.api;

import android.net.Uri;

import org.json.JSONException;

import java.io.IOException;
import java.util.HashMap;

import me.grishka.appkit.utils.WorkerThread;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class PlaceholderAPIController{

	private static PlaceholderAPIController instance=new PlaceholderAPIController();
	private static final String API_URL="https://jsonplaceholder.typicode.com/";

	private WorkerThread thread=new WorkerThread("PlaceholderAPIController");
	private OkHttpClient httpClient=new OkHttpClient.Builder().build();

	public static PlaceholderAPIController getInstance(){
		return instance;
	}

	private PlaceholderAPIController(){
		thread.start();
	}

	public void sendAsync(PlaceholderAPIRequest req){
		thread.postRunnable(new RequestRunner(req), 0);
	}

	private class RequestRunner implements Runnable{
		private PlaceholderAPIRequest<?> req;

		public RequestRunner(PlaceholderAPIRequest<?> req){
			this.req=req;
		}

		@Override
		public void run(){
			try{
				Uri.Builder uriBuilder=Uri.parse(API_URL).buildUpon()
						.path(req.path);
				for(HashMap.Entry<String, String> e:req.query.entrySet()){
					uriBuilder.appendQueryParameter(e.getKey(), e.getValue());
				}
				Request r=new Request.Builder()
						.url(uriBuilder.build().toString())
						.build();
				Call call=httpClient.newCall(r);
				Response resp=call.execute();
				try(ResponseBody body=resp.body()){
					req.parseAndInvokeCallback(body.string(), resp.headers());
				}
			}catch(Exception x){
				req.processException(x);
			}
		}
	}
}
