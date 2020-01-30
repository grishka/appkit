package me.grishka.appkit.example.api;

import android.os.Parcel;
import android.os.Parcelable;

import org.json.JSONException;
import org.json.JSONObject;

public class Photo implements Parcelable{
	public int id;
	public int albumID;
	public String title;
	public String url;
	public String thumbnailUrl;

	public Photo(JSONObject obj) throws JSONException{
		id=obj.getInt("id");
		albumID=obj.getInt("albumId");
		title=obj.getString("title");
		url=obj.getString("url");
		thumbnailUrl=obj.getString("thumbnailUrl");
	}


	@Override
	public int describeContents(){
		return 0;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags){
		dest.writeInt(this.id);
		dest.writeInt(this.albumID);
		dest.writeString(this.title);
		dest.writeString(this.url);
		dest.writeString(this.thumbnailUrl);
	}

	protected Photo(Parcel in){
		this.id=in.readInt();
		this.albumID=in.readInt();
		this.title=in.readString();
		this.url=in.readString();
		this.thumbnailUrl=in.readString();
	}

	public static final Parcelable.Creator<Photo> CREATOR=new Parcelable.Creator<Photo>(){
		@Override
		public Photo createFromParcel(Parcel source){
			return new Photo(source);
		}

		@Override
		public Photo[] newArray(int size){
			return new Photo[size];
		}
	};
}
