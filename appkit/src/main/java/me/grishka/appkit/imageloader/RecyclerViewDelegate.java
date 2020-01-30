package me.grishka.appkit.imageloader;

import android.view.View;
import android.widget.AbsListView;

import androidx.recyclerview.widget.RecyclerView;
import me.grishka.appkit.views.UsableRecyclerView;

/**
 * Created by grishka on 16.05.15.
 */
public class RecyclerViewDelegate implements ListImageLoaderWrapper.ListViewDelegate {

	private RecyclerView view;

	public RecyclerViewDelegate(RecyclerView view){
		this.view=view;
	}

	@Override
	public int getVisibleItemCount(){
		return getLastVisiblePosition()-getFirstVisiblePosition();
	}

	@Override
	public int getFirstVisiblePosition(){
		if(view.getAdapter()==null)
			return 0;
		if(view.getChildCount()==0)
			return 0;
		return view.getChildAdapterPosition(view.getChildAt(0));
	}

	@Override
	public int getLastVisiblePosition(){
		if(view.getAdapter()==null)
			return 0;
		if(view.getChildCount()==0)
			return 0;
		return view.getChildAdapterPosition(view.getChildAt(view.getChildCount()-1));
	}

	@Override
	public View getView(){
		return view;
	}

	@Override
	public View getItemView(int index){
		RecyclerView.ViewHolder holder=view.findViewHolderForLayoutPosition(index);
		return holder!=null ? holder.itemView : null;
	}

	private int getCount(){
		if(view.getAdapter()==null)
			return 0;
		return view.getAdapter().getItemCount();
	}

	@Override
	public void setOnScrollListener(final AbsListView.OnScrollListener listener){
		view.setOnScrollListener(new RecyclerView.OnScrollListener(){
			@Override
			public void onScrollStateChanged(RecyclerView recyclerView, int newState){
				int listViewState=-1;
				switch(newState){
					case RecyclerView.SCROLL_STATE_DRAGGING:
						listViewState=AbsListView.OnScrollListener.SCROLL_STATE_TOUCH_SCROLL;
						break;
					case RecyclerView.SCROLL_STATE_SETTLING:
						listViewState=AbsListView.OnScrollListener.SCROLL_STATE_FLING;
						break;
					case RecyclerView.SCROLL_STATE_IDLE:
						listViewState=AbsListView.OnScrollListener.SCROLL_STATE_IDLE;
						break;
				}
				if(listViewState!=-1){
					listener.onScrollStateChanged(null, listViewState);
				}
			}

			@Override
			public void onScrolled(RecyclerView recyclerView, int dx, int dy){
				listener.onScroll(null, getFirstVisiblePosition(), getVisibleItemCount(), getCount());
			}
		});
	}

	@Override
	public boolean isVertical(){
		return true;
	}
}
