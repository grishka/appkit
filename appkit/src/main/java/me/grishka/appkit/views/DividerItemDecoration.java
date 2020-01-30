package me.grishka.appkit.views;

import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.recyclerview.widget.RecyclerView;

/**
 * Created by grishka on 30.07.15.
 */
public class DividerItemDecoration extends RecyclerView.ItemDecoration {

	private Drawable divider, top, bottom;
	private int height, topHeight, bottomHeight;
	private Provider provider;
	private boolean useDecoratedVBounds;

	public DividerItemDecoration(Drawable drawable){
		this(drawable, drawable.getIntrinsicHeight());
	}

	public DividerItemDecoration(Drawable drawable, int height){
		this.divider=drawable;
		this.height=height;
	}

	public DividerItemDecoration(Drawable divider, Drawable top, Drawable bottom){
		this(divider, divider.getIntrinsicHeight(), top, top.getIntrinsicHeight(), bottom, bottom.getIntrinsicHeight());
	}

	public DividerItemDecoration(Drawable divider, int dividerHeight, Drawable top, int topHeight, Drawable bottom, int bottomHeight){
		this(divider, dividerHeight);
		this.top=top;
		this.topHeight=topHeight;
		this.bottom=bottom;
		this.bottomHeight=bottomHeight;
	}

	public DividerItemDecoration setProvider(Provider p){
		provider=p;
		return this;
	}

	@Override
	public void getItemOffsets(Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
		outRect.set(0, 0, 0, 0);
		int pos=parent.getChildAdapterPosition(view);
		if(pos==0){
			outRect.top+=topHeight;
		}

		if(pos==parent.getAdapter().getItemCount()-1){
			if(bottomHeight>0)
				outRect.bottom+=bottomHeight;
		}else if(provider==null || (pos<parent.getAdapter().getItemCount() && provider.needDrawDividerAfter(pos))){
			outRect.bottom+=height;
		}
	}

	public boolean isUseDecoratedVBounds() {
		return useDecoratedVBounds;
	}

	/**
	 * Sets whether decorated vertical bounds are used. Useful together with a padding decorator. Only works for middle dividers.
	 * @param useDecoratedVBounds why should I document obvious parameters?
	 */
	public void setUseDecoratedVBounds(boolean useDecoratedVBounds) {
		this.useDecoratedVBounds = useDecoratedVBounds;
	}

	private int getItemBottom(View item, RecyclerView parent){
		if(useDecoratedVBounds)
			return parent.getLayoutManager().getDecoratedBottom(item);
		return item.getBottom()+height;
	}

	private int getItemTop(View item, RecyclerView parent){
		if(useDecoratedVBounds)
			return parent.getLayoutManager().getDecoratedTop(item);
		return item.getTop();
	}

	@Override
	public void onDrawOver(Canvas c, RecyclerView parent, RecyclerView.State state) {
		RecyclerView.LayoutManager lm=parent.getLayoutManager();
		for(int i=0;i<lm.getChildCount();i++){
			View item=lm.getChildAt(i);
			int pos=lm.getPosition(item);
			if(pos==0 && top!=null){
				top.setBounds(item.getLeft(), item.getTop()-topHeight, item.getRight(), item.getTop());
				top.draw(c);
			}
			if(pos==parent.getAdapter().getItemCount()-1){
				if(bottom!=null){
					bottom.setBounds(item.getLeft(), item.getBottom(), item.getRight(), item.getBottom()+bottomHeight);
					bottom.draw(c);
				}
			}else if(provider==null || (pos<parent.getAdapter().getItemCount() && provider.needDrawDividerAfter(pos))){
				divider.setBounds(item.getLeft(), getItemBottom(item, parent)-height, item.getRight(), getItemBottom(item, parent));
				divider.draw(c);
			}
		}
	}

	public interface Provider{
		public boolean needDrawDividerAfter(int position);
	}
}