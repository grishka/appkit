package me.grishka.appkit.example.fragments;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import me.grishka.appkit.api.PaginatedList;
import me.grishka.appkit.api.PaginatedListCallback;
import me.grishka.appkit.example.R;
import me.grishka.appkit.example.api.ListPhotosRequest;
import me.grishka.appkit.example.api.Photo;
import me.grishka.appkit.example.api.SimplePaginatedList;
import me.grishka.appkit.fragments.BaseRecyclerFragment;
import me.grishka.appkit.imageloader.ImageLoaderRecyclerAdapter;
import me.grishka.appkit.imageloader.ImageLoaderViewHolder;
import me.grishka.appkit.imageloader.ListImageLoaderWrapper;
import me.grishka.appkit.utils.BindableViewHolder;
import me.grishka.appkit.utils.V;
import me.grishka.appkit.views.UsableRecyclerView;

public class ExampleLoaderFragment extends BaseRecyclerFragment<Photo>{

	private PhotoAdapter adapter;

	public ExampleLoaderFragment(){
		super(10);
	}

	@Override
	public void onCreate(Bundle savedInstanceState){
		super.onCreate(savedInstanceState);
		setTitle("Loader fragment & image loader (also, the title is long and scrolls)");
		if(savedInstanceState!=null){
			onDataLoaded(savedInstanceState.getParcelableArrayList("example:data"));
		}else{
			loadData();
		}
	}

	@Override
	protected void doLoadData(int offset, int count){
		currentRequest=new ListPhotosRequest(offset, count)
				.setCallback(new PaginatedListCallback<Photo, SimplePaginatedList<Photo>>(this))
				.exec();
	}

	@Override
	protected RecyclerView.Adapter getAdapter(){
		if(adapter==null)
			adapter=new PhotoAdapter();
		return adapter;
	}

	@Override
	protected RecyclerView.LayoutManager onCreateLayoutManager(){
		return new GridLayoutManager(getActivity(), 2);
	}

	@Override
	public void onViewCreated(View view, Bundle savedInstanceState){
		super.onViewCreated(view, savedInstanceState);
		int pad=V.dp(8);
		list.setPadding(pad, pad, pad, pad);
		list.setClipToPadding(false);
		list.addItemDecoration(new RecyclerView.ItemDecoration(){
			@Override
			public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state){
				int pad=V.dp(8);
				outRect.set(pad, pad, pad, pad);
			}
		});
		((UsableRecyclerView)list).setDrawSelectorOnTop(true);
	}

	@Override
	public void onSaveInstanceState(Bundle outState){
		super.onSaveInstanceState(outState);
		outState.putParcelableArrayList("example:data", data);
	}

	private class PhotoAdapter extends UsableRecyclerView.Adapter<PhotoViewHolder> implements ImageLoaderRecyclerAdapter{
		public PhotoAdapter(){
			super(imgLoader);
		}

		@NonNull
		@Override
		public PhotoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType){
			return new PhotoViewHolder();
		}

		@Override
		public int getItemCount(){
			return data.size();
		}

		@Override
		public void onBindViewHolder(PhotoViewHolder holder, int position){
			holder.bind(data.get(position));
			super.onBindViewHolder(holder, position);
		}

		@Override
		public int getImageCountForItem(int position){
			return 1;
		}

		@Override
		public String getImageURL(int position, int image){
			return data.get(position).thumbnailUrl;
		}
	}

	private class PhotoViewHolder extends BindableViewHolder<Photo> implements ImageLoaderViewHolder, UsableRecyclerView.Clickable{

		private TextView title;
		private ImageView image;

		public PhotoViewHolder(){
			super(getActivity(), R.layout.loader_item, list);
			title=findViewById(R.id.text);
			image=findViewById(R.id.image);
			itemView.setClipToOutline(true);
		}

		@Override
		public void onBind(Photo item){
			title.setText(item.title);
		}

		@Override
		public void setImage(int index, Bitmap bitmap){
			image.setImageBitmap(bitmap);
		}

		@Override
		public void clearImage(int index){
			image.setImageDrawable(new ColorDrawable(0xFF808080));
		}

		@Override
		public void onClick(){
			Toast.makeText(getActivity(), "You clicked item "+getAdapterPosition(), Toast.LENGTH_SHORT).show();
		}
	}
}
