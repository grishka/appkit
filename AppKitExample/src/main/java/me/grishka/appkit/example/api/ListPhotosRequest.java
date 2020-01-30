package me.grishka.appkit.example.api;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import okhttp3.Headers;

public class ListPhotosRequest extends PlaceholderAPIRequest<SimplePaginatedList<Photo>>{

	public ListPhotosRequest(int offset, int count){
		super("/photos");
		query.put("_start", offset+"");
		query.put("_limit", count+"");
	}

	@Override
	protected SimplePaginatedList<Photo> parse(JSONArray response, Headers headers) throws JSONException{
		int total=Integer.parseInt(headers.get("X-Total-Count"));
		SimplePaginatedList<Photo> photos=new SimplePaginatedList<>(total);
		for(int i=0;i<response.length();i++){
			photos.add(new Photo(response.getJSONObject(i)));
		}
		return photos;
	}
}
