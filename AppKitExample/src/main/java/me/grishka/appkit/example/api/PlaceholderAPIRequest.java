package me.grishka.appkit.example.api;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;

import me.grishka.appkit.api.APIRequest;
import okhttp3.Headers;

public abstract class PlaceholderAPIRequest<T> extends APIRequest<T>{

	public String path;
	public HashMap<String, String> query=new HashMap<>();

	public PlaceholderAPIRequest(String path){
		this.path=path;
	}

	@Override
	public void cancel(){

	}

	@Override
	public PlaceholderAPIRequest<T> exec(){
		PlaceholderAPIController.getInstance().sendAsync(this);
		return this;
	}

	protected abstract T parse(JSONArray response, Headers headers) throws JSONException;

	/*package*/ void parseAndInvokeCallback(String response, Headers headers) throws JSONException{
		invokeSuccessCallback(parse(new JSONArray(response), headers));
	}

	/*package*/ void processException(Exception x){
		invokeErrorCallback(new PlaceholderAPIError(x.getLocalizedMessage()));
	}
}
