package me.grishka.appkit.imageloader.downloaders;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.TimeUnit;

import me.grishka.appkit.imageloader.ImageCache;
import me.grishka.appkit.imageloader.requests.ImageLoaderRequest;
import me.grishka.appkit.imageloader.requests.UrlImageLoaderRequest;
import me.grishka.appkit.utils.NetworkUtils;
import okhttp3.Call;
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
	public boolean needsDiskCache(){
		return true;
	}

	@Override
	public boolean canHandleRequest(ImageLoaderRequest req){
		if(req instanceof UrlImageLoaderRequest){
			String scheme=((UrlImageLoaderRequest) req).uri.getScheme();
			return scheme.equals("http") || scheme.equals("https");
		}
		return false;
	}

	@Override
	public boolean downloadFile(ImageLoaderRequest _req, OutputStream out, ImageCache.ProgressCallback callback, ImageCache.RequestWrapper w) throws IOException{
		if(httpClient==null){
			httpClient=new OkHttpClient.Builder()
					.connectTimeout(15, TimeUnit.SECONDS)
					.readTimeout(15, TimeUnit.SECONDS)
					.writeTimeout(15, TimeUnit.SECONDS)
					.cache(null)
					.build();
		}
		UrlImageLoaderRequest req=(UrlImageLoaderRequest)_req;
		Request hreq=new Request.Builder().url(req.uri.toString()).header("User-Agent", NetworkUtils.getUserAgent()).build();
		Call call=httpClient.newCall(hreq);
		if(w!=null) w.call=call;
		try(Response resp=call.execute()){
			ResponseBody rb=resp.body();
			InputStream is=rb.byteStream();
			int len=(int)rb.contentLength();
			int loaded=0;
			byte[] rd=new byte[10240];
			int l;
			while((l=is.read(rd))>0){
				out.write(rd, 0, l);
				loaded+=l;
				if(callback!=null){
					callback.onProgressChanged(loaded, len);
				}
			}
			return len<=0 || loaded==len;
		}finally{
			if(w!=null) w.call=null;
		}
	}
}
