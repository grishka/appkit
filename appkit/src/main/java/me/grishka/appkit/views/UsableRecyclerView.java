package me.grishka.appkit.views;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;

import java.util.ArrayList;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;
import me.grishka.appkit.imageloader.ImageLoaderViewHolder;
import me.grishka.appkit.imageloader.ListImageLoaderWrapper;
import me.grishka.appkit.imageloader.ObservableListImageLoaderAdapter;
import me.grishka.appkit.imageloader.ImageLoaderRecyclerAdapter;
import me.grishka.appkit.imageloader.requests.ImageLoaderRequest;
import me.grishka.appkit.utils.AutoAssignMaxRecycledViewPool;

/**
 * A RecyclerView you can actually use right away. Compared to the default RecyclerView, this subclass has the following features added:
 * <ul>
 *     <li>Click and long click handling on items</li>
 *     <li>Highlight an item when it is touched</li>
 *     <li>Empty view, as in ListView</li>
 * </ul>
 * All these are for touchscreens only; d-pad navigation is not yet supported at all.
 */
public class UsableRecyclerView extends RecyclerView implements ObservableListImageLoaderAdapter, EmptyViewCapable{

	private ViewHolder clickingViewHolder;
	private View highlightedView;
	private Rect highlightBounds=new Rect();
	@Nullable
	private Drawable highlight;
	private AdapterDataObserver emptyViewObserver=new AdapterDataObserver() {
		@Override
		public void onChanged() {
			updateEmptyViewVisibility();
			for(ListImageLoaderWrapper.DataSetObserver dso:imgLoaderObservers)
				dso.onEverythingChanged();
		}

		@Override
		public void onItemRangeChanged(int positionStart, int itemCount){
			for(ListImageLoaderWrapper.DataSetObserver dso:imgLoaderObservers)
				dso.onItemRangeChanged(positionStart, itemCount);
		}

		@Override
		public void onItemRangeInserted(int positionStart, int itemCount){
			updateEmptyViewVisibility();
			for(ListImageLoaderWrapper.DataSetObserver dso:imgLoaderObservers)
				dso.onItemRangeInserted(positionStart, itemCount);
		}

		@Override
		public void onItemRangeMoved(int fromPosition, int toPosition, int itemCount){
			for(ListImageLoaderWrapper.DataSetObserver dso:imgLoaderObservers)
				dso.onEverythingChanged();
		}

		@Override
		public void onItemRangeRemoved(int positionStart, int itemCount){
			updateEmptyViewVisibility();
			for(ListImageLoaderWrapper.DataSetObserver dso:imgLoaderObservers)
				dso.onEverythingChanged();
		}

		@Override
		public void onItemRangeChanged(int positionStart, int itemCount, Object payload){
			for(ListImageLoaderWrapper.DataSetObserver dso:imgLoaderObservers)
				dso.onItemRangeChanged(positionStart, itemCount);
		}
	};
	private View emptyView;
	private ArrayList<View> footerViews=new ArrayList<View>();
	private FooterRecyclerAdapter footerAdapter;
	private boolean drawHighlightOnTop=false;
	private SelectorBoundsProvider highlightBoundsProvider;
	private ArrayList<ListImageLoaderWrapper.DataSetObserver> imgLoaderObservers=new ArrayList<>();
	private GestureDetector gestureDetector;
	private boolean trackingTouch;
	private boolean didHighlight, didClick;
	private boolean includeMarginsInItemHitbox;
	private Rect tmpRect=new Rect();

	public UsableRecyclerView(Context context) {
		super(context);
		init();
	}

	public UsableRecyclerView(Context context, AttributeSet attrs) {
		super(context, attrs);
		init();
	}

	public UsableRecyclerView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		init();
	}

	private void init(){
		TypedArray ta=getContext().obtainStyledAttributes(new int[]{android.R.attr.selectableItemBackground});
		setSelector(ta.getDrawable(0));
		ta.recycle();

		setRecycledViewPool(new AutoAssignMaxRecycledViewPool(25));

		gestureDetector=new GestureDetector(getContext(), new ItemTapGestureListener());
	}

	@Override
	public boolean onTouchEvent(MotionEvent e) {
		if(highlight!=null){
			highlight.setHotspot(e.getX(), e.getY());
		}
		if(trackingTouch || getScrollState()==SCROLL_STATE_IDLE){
			if(e.getAction()==MotionEvent.ACTION_DOWN){
				didClick=didHighlight=false;
			}
			gestureDetector.onTouchEvent(e);
			if(e.getAction()==MotionEvent.ACTION_DOWN){
				trackingTouch=true;
			}else if(e.getAction()==MotionEvent.ACTION_UP || e.getAction()==MotionEvent.ACTION_CANCEL || getScrollState()!=SCROLL_STATE_IDLE){
				trackingTouch=false;
				if(didClick && !didHighlight){
					showHighlight();
					postDelayed(this::endClick, 32);
				}else{
					endClick();
				}
			}
		}

		return super.onTouchEvent(e);
	}

	public void setSelector(@DrawableRes int drawableRes){
		setSelector(getResources().getDrawable(drawableRes));
	}

	public void setSelector(Drawable selector){
		if(highlight!=null){
			highlight.setCallback(null);
		}
		highlight=selector;
		if(highlight==null)
			return;
		highlight.setCallback(this);
	}

	public void setDrawSelectorOnTop(boolean drawOnTop){
		drawHighlightOnTop=drawOnTop;
	}

	public void setSelectorBoundsProvider(SelectorBoundsProvider provider){
		highlightBoundsProvider=provider;
	}

	@Override
	protected boolean verifyDrawable(Drawable who) {
		return super.verifyDrawable(who) || who==highlight;
	}

	@Override
	protected void dispatchDraw(Canvas canvas) {
		if(drawHighlightOnTop)
			super.dispatchDraw(canvas);
		if(highlight!=null) {
			if (highlightedView!=null) {
				if(highlightBoundsProvider!=null){
					highlightBoundsProvider.getSelectorBounds(highlightedView, highlightBounds);
				}else{
					if(includeMarginsInItemHitbox){
						getDecoratedBoundsWithMargins(highlightedView, highlightBounds);
						highlightBounds.offset(Math.round(highlightedView.getTranslationX()), Math.round(highlightedView.getTranslationY()));
					}else{
						int x=Math.round(highlightedView.getX());
						int y=Math.round(highlightedView.getY());
						highlightBounds.set(x, y, x+highlightedView.getWidth(), y+highlightedView.getHeight());
					}
				}
			}
			highlight.setBounds(highlightBounds);
			highlight.draw(canvas);
		}
		if(!drawHighlightOnTop)
			super.dispatchDraw(canvas);
	}

	/**
	 * Set a view which is displayed when this RecyclerView's adapter is empty and hidden otherwise.
	 * @param view The view
	 */
	public void setEmptyView(View view){
		emptyView=view;
		updateEmptyViewVisibility();
	}

	/**
	 * Get whether this view's adapter has zero items hence the view displays nothing.
	 * @return true if the view is empty
	 */
	public boolean isEmpty() {
		return getAdapter() != null && getAdapter().getItemCount() == 0;
	}

	@Override
	public void setAdapter(RecyclerView.Adapter adapter) {
		if(getAdapter()!=null){
			getAdapter().unregisterAdapterDataObserver(emptyViewObserver);
		}
		super.setAdapter(adapter);
		if(adapter!=null)
			adapter.registerAdapterDataObserver(emptyViewObserver);
		updateEmptyViewVisibility();
	}

	public void addFooterView(View view){
		view.setLayoutParams(new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		footerViews.add(view);
		if(footerAdapter==null){
			footerAdapter=new FooterRecyclerAdapter(getAdapter());
			footerAdapter.setHasStableIds(getAdapter().hasStableIds());
			super.setAdapter(footerAdapter);
		}
	}

	@Override
	public RecyclerView.Adapter getAdapter() {
		RecyclerView.Adapter adapter=super.getAdapter();
		if(adapter instanceof FooterRecyclerAdapter)
			return ((FooterRecyclerAdapter)adapter).wrapped;
		return adapter;
	}

	public RecyclerView.Adapter getRealAdapter(){
		return super.getAdapter();
	}

	private void updateEmptyViewVisibility(){
		if(emptyView!=null){
			emptyView.setVisibility(isEmpty() ? VISIBLE : GONE);
		}
	}

	@Override
	public int getCount() {
		final RecyclerView.Adapter adapter=getAdapter();
		return adapter!=null?adapter.getItemCount():0;
	}

	@Override
	public int getImageCountForItem(int item) {
		RecyclerView.Adapter adapter=getAdapter();
		if(adapter instanceof ImageLoaderRecyclerAdapter){
			return ((ImageLoaderRecyclerAdapter)adapter).getImageCountForItem(item);
		}
		return 0;
	}

	@Override
	public ImageLoaderRequest getImageRequest(int item, int image) {
		RecyclerView.Adapter adapter=getAdapter();
		if(adapter instanceof ImageLoaderRecyclerAdapter){
			return ((ImageLoaderRecyclerAdapter)adapter).getImageRequest(item, image);
		}
		return null;
	}

	@Override
	public void imageLoaded(int item, int image, Drawable drawable) {
		ViewHolder holder=findViewHolderForAdapterPosition(item);
		if(holder instanceof ImageLoaderViewHolder){
			((ImageLoaderViewHolder)holder).setImage(image, drawable);
		}
	}

	@Override
	public void addDataSetObserver(ListImageLoaderWrapper.DataSetObserver observer){
		imgLoaderObservers.add(observer);
	}

	@Override
	public void removeDataSetObserver(ListImageLoaderWrapper.DataSetObserver observer){
		imgLoaderObservers.remove(observer);
	}

	public boolean isIncludeMarginsInItemHitbox(){
		return includeMarginsInItemHitbox;
	}

	public void setIncludeMarginsInItemHitbox(boolean includeMarginsInItemHitbox){
		this.includeMarginsInItemHitbox=includeMarginsInItemHitbox;
	}

	private void endClick(){
		if(clickingViewHolder!=null){
			clickingViewHolder.itemView.setPressed(false);
			if(highlight!=null)
				highlight.setState(ENABLED_STATE_SET);
			clickingViewHolder=null;
		}
	}

	private void showHighlight(){
		if(clickingViewHolder!=null){
			didHighlight=true;
			if(highlight!=null)
				highlight.setState(PRESSED_ENABLED_FOCUSED_STATE_SET);
			clickingViewHolder.itemView.setPressed(true);
			invalidate();
		}
	}

	public View findChildViewUnderWithMargins(float x, float y){
		int _x=Math.round(x), _y=Math.round(y);
		for(int i=0;i<getChildCount();i++){
			View child=getChildAt(i);
			getDecoratedBoundsWithMargins(child, tmpRect);
			tmpRect.offset(Math.round(child.getTranslationX()), Math.round(child.getTranslationY()));
			if(tmpRect.contains(_x, _y))
				return child;
		}
		return null;
	}

	public static abstract class Adapter<VH extends ViewHolder> extends RecyclerView.Adapter<VH>{

		private ListImageLoaderWrapper imgLoader;

		public Adapter(ListImageLoaderWrapper imgLoader) {
			this.imgLoader = imgLoader;
		}

		@Override
		public void onBindViewHolder(VH holder, int position) {
			if(holder instanceof ImageLoaderViewHolder){
				imgLoader.bindViewHolder((ImageLoaderRecyclerAdapter) this, (ImageLoaderViewHolder)holder, position);
			}
		}
	}

	/**
	 * Implement this in your ViewHolder if you want to handle clicks on that item.
	 */
	public static interface Clickable{
		/**
		 * Called when this item has been clicked and the click is confirmed (after the ACTION_UP touch event).
		 */
		public void onClick();
	}

	/**
	 * An extended version of Clickable which allows disabling particular items making them not clickable and not highlightable when touched.
	 */
	public static interface DisableableClickable extends Clickable{
		/**
		 * Called on ACTION_DOWN to determine if this item is clickable at all.
		 * @return Whether the item is clickable.
		 */
		public boolean isEnabled();
	}

	/**
	 * Implement this in your ViewHolder if you want to handle long clicks on this item.
	 */
	public static interface LongClickable{
		/**
		 * Called when a long click on this item is confirmed.
		 * @return true if you've handled the long click so a haptic feedback will be performed and the highlight will disappear.
		 */
		public boolean onLongClick();
	}

	private class FooterRecyclerAdapter extends RecyclerView.Adapter<ViewHolder> {
		private RecyclerView.Adapter wrapped;
		private static final int FOOTER_TYPE_FIRST=-1000;

		public FooterRecyclerAdapter(RecyclerView.Adapter wrapped){
			this.wrapped=wrapped;
		}

		@Override
		public int getItemCount() {
			return wrapped.getItemCount()+footerViews.size();
		}

		@Override
		public long getItemId(int position) {
			if(position<wrapped.getItemCount())
				return wrapped.getItemId(position);
			return 0;
		}

		@Override
		public int getItemViewType(int position) {
			if(position<wrapped.getItemCount())
				return wrapped.getItemViewType(position);
			return FOOTER_TYPE_FIRST+position-wrapped.getItemCount();
		}

		@Override
		public void onAttachedToRecyclerView(RecyclerView recyclerView) {
			wrapped.onAttachedToRecyclerView(recyclerView);
		}

		@Override
		public void onBindViewHolder(ViewHolder holder, int position) {
			if(position<wrapped.getItemCount())
				wrapped.onBindViewHolder(holder, position);
		}

		@Override
		public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
			if(viewType>=FOOTER_TYPE_FIRST && viewType<FOOTER_TYPE_FIRST+footerViews.size()){
				int footerIndex=viewType-FOOTER_TYPE_FIRST;
				return new FooterViewHolder(footerViews.get(footerIndex));
			}
			return wrapped.onCreateViewHolder(parent, viewType);
		}

		@Override
		public void onDetachedFromRecyclerView(RecyclerView recyclerView) {
			wrapped.onDetachedFromRecyclerView(recyclerView);
		}

		@Override
		public boolean onFailedToRecycleView(ViewHolder holder) {
			if(!(holder instanceof FooterViewHolder))
				return wrapped.onFailedToRecycleView(holder);
			return false;
		}

		@Override
		public void onViewAttachedToWindow(ViewHolder holder) {
			if(!(holder instanceof FooterViewHolder))
				wrapped.onViewAttachedToWindow(holder);
		}

		@Override
		public void onViewDetachedFromWindow(ViewHolder holder) {
			if(!(holder instanceof FooterViewHolder))
				wrapped.onViewDetachedFromWindow(holder);
		}

		@Override
		public void onViewRecycled(ViewHolder holder) {
			if(!(holder instanceof FooterViewHolder))
				wrapped.onViewRecycled(holder);
		}

		@Override
		public void registerAdapterDataObserver(AdapterDataObserver observer) {
			super.registerAdapterDataObserver(observer);
			wrapped.registerAdapterDataObserver(observer);
		}

		/*@Override
		public void setHasStableIds(boolean hasStableIds) {
			wrapped.setHasStableIds(hasStableIds);
		}*/

		@Override
		public void unregisterAdapterDataObserver(AdapterDataObserver observer) {
			super.unregisterAdapterDataObserver(observer);
			wrapped.unregisterAdapterDataObserver(observer);
		}
	}

	private static class FooterViewHolder extends ViewHolder{

		public FooterViewHolder(View itemView) {
			super(itemView);
		}
	}

	public interface SelectorBoundsProvider{
		public void getSelectorBounds(View view, Rect outRect);
	}

	private class ItemTapGestureListener implements GestureDetector.OnGestureListener{

		@Override
		public boolean onDown(MotionEvent e){
			View view;
			if(includeMarginsInItemHitbox)
				view=findChildViewUnderWithMargins(e.getX(), e.getY());
			else
				view=findChildViewUnder(e.getX(), e.getY());
			if(view!=null){
				ViewHolder holder=getChildViewHolder(view);
				if(holder instanceof Clickable){
					boolean enabled=true;
					if(holder instanceof DisableableClickable)
						enabled=((DisableableClickable) holder).isEnabled();
					if(enabled){
						clickingViewHolder=holder;
						highlightedView=holder.itemView;
						return true;
					}
				}
			}
			return false;
		}

		@Override
		public boolean onSingleTapUp(MotionEvent e){
			if(clickingViewHolder!=null){
				didClick=true;
				playSoundEffect(SoundEffectConstants.CLICK);
				((Clickable)clickingViewHolder).onClick();
			}
			return true;
		}

		@Override
		public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY){
			return false;
		}

		@Override
		public void onShowPress(MotionEvent e){
			showHighlight();
		}

		@Override
		public void onLongPress(MotionEvent e){
			if(clickingViewHolder instanceof LongClickable && ((LongClickable) clickingViewHolder).onLongClick()){
				performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
				endClick();
			}
		}

		@Override
		public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY){
			return false;
		}
	}
}