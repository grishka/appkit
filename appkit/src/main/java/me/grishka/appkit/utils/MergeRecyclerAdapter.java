package me.grishka.appkit.utils;

import android.util.SparseArray;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.HashMap;

import androidx.recyclerview.widget.RecyclerView;
import me.grishka.appkit.imageloader.ImageLoaderRecyclerAdapter;

/**
 * A RecyclerView adapter which merges multiple other adapters into a single list.
 *
 * You MUST override getItemViewType() in each of your adapters and make sure the returned values don't intersect across adapters.
 * If they do, bad things™ will happen.
 */
public class MergeRecyclerAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> implements ImageLoaderRecyclerAdapter{

	private ArrayList<RecyclerView.Adapter> adapters=new ArrayList<>();
	private SparseArray<RecyclerView.Adapter> viewTypeMapping=new SparseArray<>();
	private HashMap<RecyclerView.Adapter, InternalDataObserver> observers=new HashMap<>();

	public void addAdapter(RecyclerView.Adapter adapter){
		addAdapter(adapters.size(), adapter);
	}

	public void addAdapter(int index, RecyclerView.Adapter adapter){
		if(adapters.contains(adapter))
			throw new IllegalArgumentException("Adapter "+adapter+" is already added!");
		adapters.add(index, adapter);
		InternalDataObserver observer=new InternalDataObserver(adapter);
		adapter.registerAdapterDataObserver(observer);
		observers.put(adapter, observer);
		notifyDataSetChanged();
	}

	public void removeAdapter(RecyclerView.Adapter adapter){
		if(adapters.remove(adapter)){
			adapter.unregisterAdapterDataObserver(observers.get(adapter));
			observers.remove(adapter);
			notifyDataSetChanged();
		}
	}

	public void removeAdapterAt(int index){
		removeAdapter(adapters.get(index));
	}

	public void removeAllAdapters(){
		for(RecyclerView.Adapter adapter:adapters){
			adapter.unregisterAdapterDataObserver(observers.get(adapter));
			observers.remove(adapter);
		}
		adapters.clear();
		notifyDataSetChanged();
	}

	public RecyclerView.Adapter getAdapterAt(int index){
		return adapters.get(index);
	}

	public int getAdapterCount(){
		return adapters.size();
	}

	public int getAdapterPosition(int pos){
		int count=0;
		for(RecyclerView.Adapter adapter:adapters){
			int c=adapter.getItemCount();
			if(pos>=count && pos<count+c){
				return pos-count;
			}
			count+=c;
		}
		return pos;
	}

	public int getPositionForAdapter(RecyclerView.Adapter adapter){
		int pos=0;
		for(RecyclerView.Adapter a:adapters){
			if(a==adapter)
				return pos;
			pos+=a.getItemCount();
		}
		return pos;
	}

	public RecyclerView.Adapter getAdapterForPosition(int pos){
		int count=0;
		for(RecyclerView.Adapter adapter:adapters){
			int c=adapter.getItemCount();
			if(pos>=count && pos<count+c){
				return adapter;
			}
			count+=c;
		}
		return null;
	}

	public int getAdapterIndexForPosition(int pos){
		int count=0;
		int i=0;
		for(RecyclerView.Adapter adapter:adapters){
			int c=adapter.getItemCount();
			if(pos>=count && pos<count+c){
				return i;
			}
			count+=c;
			i++;
		}
		return -1;
	}

	@Override
	public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
		return viewTypeMapping.get(viewType).onCreateViewHolder(parent, viewType);
	}

	@Override
	public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
		getAdapterForPosition(position).onBindViewHolder(holder, getAdapterPosition(position));
	}

	@Override
	public int getItemViewType(int position) {
		RecyclerView.Adapter adapter=getAdapterForPosition(position);
		int viewType=adapter.getItemViewType(getAdapterPosition(position));
		viewTypeMapping.put(viewType, adapter);
		return viewType;
	}

	@Override
	public int getItemCount() {
		int count=0;
		for(RecyclerView.Adapter adapter:adapters){
			count+=adapter.getItemCount();
		}
		return count;
	}

	@Override
	public int getImageCountForItem(int position) {
		RecyclerView.Adapter adapter=getAdapterForPosition(position);
		if(adapter instanceof ImageLoaderRecyclerAdapter){
			return ((ImageLoaderRecyclerAdapter)adapter).getImageCountForItem(getAdapterPosition(position));
		}
		return 0;
	}

	@Override
	public String getImageURL(int position, int image) {
		RecyclerView.Adapter adapter=getAdapterForPosition(position);
		if(adapter instanceof ImageLoaderRecyclerAdapter){
			return ((ImageLoaderRecyclerAdapter)adapter).getImageURL(getAdapterPosition(position), image);
		}
		return null;
	}

	@Override
	public long getItemId(int position) {
		return getAdapterForPosition(position).getItemId(getAdapterPosition(position));
	}

	private class InternalDataObserver extends RecyclerView.AdapterDataObserver{

		private RecyclerView.Adapter adapter;

		public InternalDataObserver(RecyclerView.Adapter adapter){
			this.adapter=adapter;
		}

		@Override
		public void onChanged(){
			notifyDataSetChanged();
		}

		@Override
		public void onItemRangeChanged(int positionStart, int itemCount){
			notifyItemRangeChanged(getPositionForAdapter(adapter)+positionStart, itemCount);
		}

		@Override
		public void onItemRangeChanged(int positionStart, int itemCount, Object payload){
			notifyItemRangeChanged(getPositionForAdapter(adapter)+positionStart, itemCount, payload);
		}

		@Override
		public void onItemRangeInserted(int positionStart, int itemCount){
			notifyItemRangeInserted(getPositionForAdapter(adapter)+positionStart, itemCount);
		}

		@Override
		public void onItemRangeRemoved(int positionStart, int itemCount){
			notifyItemRangeRemoved(getPositionForAdapter(adapter)+positionStart, itemCount);
		}

		@Override
		public void onItemRangeMoved(int fromPosition, int toPosition, int itemCount){
			if(itemCount!=1) throw new UnsupportedOperationException("Can't move more than one item");
			int offset=getPositionForAdapter(adapter);
			notifyItemMoved(offset+fromPosition, offset+toPosition);
		}
	}
}
