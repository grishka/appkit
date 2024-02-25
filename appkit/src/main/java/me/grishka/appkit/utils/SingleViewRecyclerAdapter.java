package me.grishka.appkit.utils;

import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

public class SingleViewRecyclerAdapter extends RecyclerView.Adapter<SingleViewRecyclerAdapter.ViewViewHolder>{

	protected final View view;
	protected final int id;

	public SingleViewRecyclerAdapter(View view){
		this.view=view;
		id=View.generateViewId();
	}

	@NonNull
	@Override
	public ViewViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType){
		return new ViewViewHolder(view);
	}

	@Override
	public void onBindViewHolder(@NonNull ViewViewHolder holder, int position){

	}

	@Override
	public int getItemCount(){
		return 1;
	}

	@Override
	public int getItemViewType(int position){
		return id;
	}

	@Override
	public String toString(){
		return "SingleViewRecyclerAdapter{"+
				"view="+view+
				'}';
	}

	public static class ViewViewHolder extends RecyclerView.ViewHolder{
		public ViewViewHolder(@NonNull View itemView){
			super(itemView);
		}
	}
}
