package me.grishka.appkit.imageloader.downloaders;

import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import me.grishka.appkit.imageloader.ImageCache;
import me.grishka.appkit.imageloader.requests.ImageLoaderRequest;
import me.grishka.appkit.imageloader.requests.UrlImageLoaderRequest;
import me.grishka.appkit.utils.NetworkUtils;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Okio;
import okio.Sink;

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
		if(req instanceof UrlImageLoaderRequest ur){
			String scheme=ur.uri.getScheme();
			return "http".equals(scheme) || "https".equals(scheme);
		}
		return false;
	}

	@Override
	public void downloadFile(ImageLoaderRequest _req, OutputStream out, ImageCache.ProgressCallback callback, ImageCache.ImageDownloadInfo info, Runnable onSuccess, Consumer<Throwable> onError){
		synchronized(this){
			if(httpClient==null){
				httpClient=new OkHttpClient.Builder()
						.connectTimeout(15, TimeUnit.SECONDS)
						.readTimeout(15, TimeUnit.SECONDS)
						.writeTimeout(15, TimeUnit.SECONDS)
						.cache(null)
						.build();
			}
		}
		UrlImageLoaderRequest req=(UrlImageLoaderRequest)_req;
		Request hreq=new Request.Builder().url(req.uri.toString()).header("User-Agent", NetworkUtils.getUserAgent()).build();
		Call call=httpClient.newCall(hreq);
		info.httpCall=call;
		call.enqueue(new Callback(){
			@Override
			public void onFailure(Call call, IOException e){
				info.httpCall=null;
				onError.accept(e);
			}

			@Override
			public void onResponse(Call call, Response response) throws IOException{
				try(ResponseBody body=response.body()){
					Sink outSink=Okio.sink(out);
					body.source().readAll(outSink);
					onSuccess.run();
				}catch(Throwable x){
					onError.accept(x);
				}finally{
					info.httpCall=null;
				}
			}
		});
	}
}
