package me.grishka.appkit.views;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ScrollView;

import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

/**
 * This swipe refresh layout finds scrollable views inside itself automatically
 */
public class RecursiveSwipeRefreshLayout extends SwipeRefreshLayout{
	public RecursiveSwipeRefreshLayout(Context context){
		super(context);
	}

	public RecursiveSwipeRefreshLayout(Context context, AttributeSet attrs){
		super(context, attrs);
	}

	@Override
	public boolean canChildScrollUp(){
		return findScrollableChild(this);
	}

	private boolean findScrollableChild(ViewGroup vg){
		for(int i=0;i<vg.getChildCount();i++){
			View child=vg.getChildAt(i);
			if(child instanceof AdapterView || child instanceof ScrollView || child instanceof RecyclerView){
				return child.canScrollVertically(-1);
			}else if(child instanceof ViewGroup){
				return findScrollableChild((ViewGroup)child);
			}
		}
		return false;
	}
}
