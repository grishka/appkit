package me.grishka.appkit.imageloader;

import android.util.Log;


import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.TimeUnit;

import me.grishka.appkit.utils.NetworkUtils;
import okhttp3.Call;
import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Created by grishka on 28.07.15.
 */
public class HTTPImageDownloader extends ImageDownloader {
	private OkHttpClient httpClient;

	@Override
	public boolean isFileBased(){
		return true;
	}

	@Override
	public boolean downloadFile(String uri, OutputStream out, ImageCache.ProgressCallback callback, ImageCache.RequestWrapper w) throws IOException{
		if(httpClient==null){
			httpClient=new OkHttpClient.Builder()
					.connectTimeout(15, TimeUnit.SECONDS)
					.readTimeout(15, TimeUnit.SECONDS)
					.writeTimeout(15, TimeUnit.SECONDS)
			//httpClient.setConnectionPool(new ConnectionPool(20, 3*60000));
					.cache(null)
					.build();
		}
		InputStream is=null;
		ResponseBody rb=null;
		try {
			Request req=new Request.Builder().url(uri).header("User-Agent", NetworkUtils.getUserAgent()).build();
			Call call=httpClient.newCall(req);
			if(w!=null) w.call=call;
			Response resp=call.execute();
			rb=resp.body();
			is=rb.byteStream();
			int len=(int)rb.contentLength();
			int loaded=0;
			byte[] rd=new byte[5120];
			int l=0;
			while((l=is.read(rd))>0){
				out.write(rd, 0, l);
				loaded+=l;
				if(callback!=null){
					callback.onProgressChanged(loaded, len);
				}
			}
			if(w!=null) w.call=null;
			return len<=0 || loaded==len;
		} catch (Throwable e) {
			//if(ListImageLoader.DEBUG || ImageCache.DEBUG)
			//	Log.w(TAG, "Error downloading "+url, e);
		} finally {
			if(is!=null){
				try{
					is.close();
				}catch(Exception x){}
			}
			if(rb!=null){
				try {
					rb.close();
				}catch(Exception x){}
			}
		}
		if(w!=null) w.call=null;
		return false;
	}
}
