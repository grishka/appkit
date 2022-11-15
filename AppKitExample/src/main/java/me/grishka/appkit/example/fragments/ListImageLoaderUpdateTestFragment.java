package me.grishka.appkit.example.fragments;

import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import me.grishka.appkit.fragments.BaseRecyclerFragment;
import me.grishka.appkit.imageloader.ImageCache;
import me.grishka.appkit.imageloader.ImageLoaderRecyclerAdapter;
import me.grishka.appkit.imageloader.ImageLoaderViewHolder;
import me.grishka.appkit.imageloader.requests.ImageLoaderRequest;
import me.grishka.appkit.imageloader.requests.UrlImageLoaderRequest;
import me.grishka.appkit.utils.BindableViewHolder;
import me.grishka.appkit.utils.V;
import me.grishka.appkit.views.UsableRecyclerView;

public class ListImageLoaderUpdateTestFragment extends BaseRecyclerFragment<ListImageLoaderUpdateTestFragment.Item>{

	private CatsAdapter adapter;

	public ListImageLoaderUpdateTestFragment(){
		super(10);
	}

	@Override
	public void onCreate(Bundle savedInstanceState){
		super.onCreate(savedInstanceState);
		try{
			Thread t=new Thread(()->{
				ImageCache.getInstance(getActivity()).clear();
				ImageCache.getInstance(getActivity()).clearTopLevel();
			});
			t.start();
			t.join();
		}catch(InterruptedException iHateTheseThings){}
		data.add(new Item("https://app.requestly.io/delay/3000/https://placekitten.com/200/300"));
		dataLoaded();
	}

	@Override
	protected void doLoadData(int offset, int count){

	}

	@Override
	protected RecyclerView.Adapter getAdapter(){
		return adapter=new CatsAdapter();
	}

	@Override
	public void onViewCreated(View view, Bundle savedInstanceState){
		super.onViewCreated(view, savedInstanceState);
		view.postDelayed(()->{
			data.add(0, new Item("https://placekitten.com/200/200"));
			adapter.notifyItemInserted(0);
			data.remove(1);
			adapter.notifyItemRemoved(1);
		}, 1000);
	}

	private class CatsAdapter extends UsableRecyclerView.Adapter<CatViewHolder> implements ImageLoaderRecyclerAdapter{
		public CatsAdapter(){
			super(imgLoader);
		}

		@NonNull
		@Override
		public CatViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType){
			return new CatViewHolder();
		}

		@Override
		public int getItemCount(){
			return data.size();
		}


		@Override
		public int getImageCountForItem(int position){
			return 1;
		}

		@Override
		public ImageLoaderRequest getImageRequest(int position, int image){
			return data.get(position).req;
		}
	}

	private class CatViewHolder extends BindableViewHolder<Item> implements ImageLoaderViewHolder{
		private ImageView img;

		public CatViewHolder(){
			super(new ImageView(getActivity()));
			img=(ImageView) itemView;
			img.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, V.dp(150)));
		}

		@Override
		public void setImage(int index, Drawable image){
			img.setImageDrawable(image);
		}

		@Override
		public void clearImage(int index){
			img.setImageDrawable(null);
		}

		@Override
		public void onBind(Item item){

		}
	}

	public static class Item{
		public String imgUrl;
		public UrlImageLoaderRequest req;

		public Item(String imgUrl){
			this.imgUrl=imgUrl;
			req=new UrlImageLoaderRequest(imgUrl);
		}
	}
}
