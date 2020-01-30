package me.grishka.appkit.utils;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.IdRes;
import androidx.annotation.LayoutRes;
import me.grishka.appkit.views.UsableRecyclerView;

/**
 * Created by grishka on 03.09.15.
 */
public abstract class BindableViewHolder<T> extends UsableRecyclerView.ViewHolder{

	protected T item;

	public BindableViewHolder(View itemView) {
		super(itemView);
	}

	public BindableViewHolder(Context context, @LayoutRes int layout){
		super(View.inflate(context, layout, null));
	}

	public BindableViewHolder(Context context, @LayoutRes int layout, ViewGroup parent){
		super(((LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE)).inflate(layout, parent, false));
	}

	public final void bind(T item){
		this.item=item;
		onBind(item);
	}

	public abstract void onBind(T item);

	public T getItem() {
		return item;
	}

	protected <VT extends View> VT findViewById(@IdRes int id){
		return itemView.findViewById(id);
	}
}
