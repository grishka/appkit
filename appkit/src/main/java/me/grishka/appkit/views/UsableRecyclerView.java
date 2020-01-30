package me.grishka.appkit.views;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;

import java.util.ArrayList;

import androidx.annotation.DrawableRes;
import androidx.recyclerview.widget.RecyclerView;
import me.grishka.appkit.imageloader.ImageLoaderViewHolder;
import me.grishka.appkit.imageloader.ListImageLoaderWrapper;
import me.grishka.appkit.imageloader.ObservableListImageLoaderAdapter;
import me.grishka.appkit.imageloader.ImageLoaderRecyclerAdapter;
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

	private int touchSlop, clickStartTimeout, longClickTimeout;
	private float touchDownX, touchDownY;
	private float lastTouchX, lastTouchY;
	private ViewHolder clickingViewHolder;
	private View highlightedView;
	private Rect highlightBounds=new Rect();
	private Drawable highlight;
	private Runnable postedClickStart, postedLongClick;
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
		touchSlop= ViewConfiguration.get(getContext()).getScaledTouchSlop();
		clickStartTimeout=ViewConfiguration.getTapTimeout();
		longClickTimeout=ViewConfiguration.getLongPressTimeout();
		TypedArray ta=getContext().obtainStyledAttributes(new int[]{android.R.attr.selectableItemBackground});
		setSelector(ta.getDrawable(0));
		ta.recycle();

		setRecycledViewPool(new AutoAssignMaxRecycledViewPool(25));
	}

	@Override
	public boolean onTouchEvent(MotionEvent e) {
		if(e.getAction()==MotionEvent.ACTION_DOWN && getScrollState()==SCROLL_STATE_IDLE){
			touchDownX=lastTouchX=e.getX();
			touchDownY=lastTouchY=e.getY();
			highlightedView=null;
			View view=findChildViewUnder(e.getX(), e.getY());
			if(view!=null){
				ViewHolder holder=getChildViewHolder(view);
				if(holder!=null){
					if(holder instanceof Clickable){
						if((holder instanceof DisableableClickable && ((DisableableClickable)holder).isEnabled()) ||!(holder instanceof DisableableClickable)) {
							clickingViewHolder = holder;
							highlightedView=view;
							if(postedClickStart!=null)
								removeCallbacks(postedClickStart);
							postDelayed(postedClickStart=new ClickStartRunnable(), clickStartTimeout);
						}
						if(holder instanceof LongClickable){
							postDelayed(postedLongClick=new LongClickRunnable(), longClickTimeout);
						}
					}
				}
			}
		}
		if(e.getAction()==MotionEvent.ACTION_CANCEL){
			clickingViewHolder=null;
			if(highlightedView!=null) {
				highlightedView.setPressed(false);
				highlight.setState(EMPTY_STATE_SET);
				if (postedClickStart != null) {
					removeCallbacks(postedClickStart);
					postedClickStart = null;
				}
				if (postedLongClick != null) {
					removeCallbacks(postedLongClick);
					postedLongClick = null;
				}
			}
		}
		if(e.getAction()==MotionEvent.ACTION_MOVE && clickingViewHolder!=null){
			lastTouchX=e.getX();
			lastTouchY=e.getY();
			if(Math.abs(e.getX()-touchDownX)>touchSlop || Math.abs(e.getY()-touchDownY)>touchSlop){
				clickingViewHolder=null;
				highlightedView.setPressed(false);
				highlight.setState(EMPTY_STATE_SET);
				if(postedClickStart!=null) {
					removeCallbacks(postedClickStart);
					postedClickStart=null;
				}
				if(postedLongClick!=null){
					removeCallbacks(postedLongClick);
					postedLongClick=null;
				}
			}
		}
		if(e.getAction()==MotionEvent.ACTION_UP){
			lastTouchX=e.getX();
			lastTouchY=e.getY();
			if(postedLongClick!=null){
				removeCallbacks(postedLongClick);
				postedLongClick=null;
			}
			if(clickingViewHolder!=null && (Math.abs(e.getX()-touchDownX)<touchSlop || Math.abs(e.getY()-touchDownY)<touchSlop)){
				((Clickable)clickingViewHolder).onClick();
				playSoundEffect(SoundEffectConstants.CLICK);
				if(postedClickStart!=null) {
					removeCallbacks(postedClickStart);
					postedClickStart.run();
					postedClickStart=null;
				}
				clickingViewHolder = null;
				postDelayed(new Runnable() { // allow the pressed state to be drawn for at least 1 frame
					@Override
					public void run() {
						if(highlightedView!=null)
							highlightedView.setPressed(false);
						highlight.setState(EMPTY_STATE_SET);
					}
				}, 50);
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
		/*if(!selector.isStateful()){
			highlight=
		}*/
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
			if (highlightedView != null) {
				if(highlightBoundsProvider!=null){
					highlightBoundsProvider.getSelectorBounds(highlightedView, highlightBounds);
				}else{
					int x=Math.round(highlightedView.getX());
					int y=Math.round(highlightedView.getY());
					highlightBounds.set(x, y, x+highlightedView.getWidth(), y+highlightedView.getHeight());
				}
			}
			highlight.setBounds(highlightBounds);
			if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.LOLLIPOP){
				highlight.setHotspot(lastTouchX, lastTouchY);
			}
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
		if(adapter!=null && adapter instanceof ImageLoaderRecyclerAdapter){
			return ((ImageLoaderRecyclerAdapter)adapter).getImageCountForItem(item);
		}
		return 0;
	}

	@Override
	public String getImageURL(int item, int image) {
		RecyclerView.Adapter adapter=getAdapter();
		if(adapter!=null && adapter instanceof ImageLoaderRecyclerAdapter){
			return ((ImageLoaderRecyclerAdapter)adapter).getImageURL(item, image);
		}
		return null;
	}

	@Override
	public void imageLoaded(int item, int image, Bitmap bitmap) {
		ViewHolder holder=findViewHolderForAdapterPosition(item);
		if(holder!=null && holder instanceof ImageLoaderViewHolder){
			((ImageLoaderViewHolder)holder).setImage(image, bitmap);
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

	public static abstract class Adapter<VH extends ViewHolder> extends RecyclerView.Adapter<VH> implements ImageLoaderRecyclerAdapter{

		private ListImageLoaderWrapper imgLoader;

		public Adapter(ListImageLoaderWrapper imgLoader) {
			this.imgLoader = imgLoader;
		}

		@Override
		public void onBindViewHolder(VH holder, int position) {
			if(holder instanceof ImageLoaderViewHolder){
				imgLoader.bindViewHolder(this, (ImageLoaderViewHolder)holder, position);
			}
		}

		@Override
		public int getImageCountForItem(int position) {
			return 0;
		}

		@Override
		public String getImageURL(int position, int image) {
			return null;
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

	private class ClickStartRunnable implements Runnable{
		@Override
		public void run() {
			if(clickingViewHolder==null) return; // click has been canceled
			postedClickStart=null;
			highlightedView.setPressed(true);
			highlight.setState(PRESSED_ENABLED_FOCUSED_STATE_SET);
			invalidate();
		}
	}

	private class LongClickRunnable implements Runnable{
		@Override
		public void run() {
			if(clickingViewHolder==null) return; // click has been canceled
			postedLongClick=null;
			highlightedView.setPressed(false);
			highlight.setState(EMPTY_STATE_SET);
			boolean result=((LongClickable)clickingViewHolder).onLongClick();
			if(result){
				performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
			}
			clickingViewHolder=null;
		}
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
}