package me.grishka.appkit.views;

import android.content.Context;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;

/**
 * Created by grishka on 08.07.15.
 */
public class NavigationSpinner extends Spinner {

	// Only measure this many items to get a decent max width.
	private static final int MAX_ITEMS_MEASURED = 15;

	public NavigationSpinner(Context context) {
		super(context);
	}

	public NavigationSpinner(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public NavigationSpinner(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
	}

	public NavigationSpinner(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes, int mode) {
		super(context, attrs, defStyleAttr, defStyleRes, mode);
	}

	public NavigationSpinner(Context context, AttributeSet attrs, int defStyleAttr, int mode) {
		super(context, attrs, defStyleAttr, mode);
	}

	public NavigationSpinner(Context context, int mode) {
		super(context, mode);
	}

	public int measureContentWidth(SpinnerAdapter adapter, Drawable background) {
		if (adapter == null) {
			return 0;
		}

		int width = 0;
		View itemView = null;
		Rect tempRect=new Rect();
		int itemType = 0;
		final int widthMeasureSpec =
				MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
		final int heightMeasureSpec =
				MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);

		// Make sure the number of items we'll measure is capped. If it's a huge data set
		// with wildly varying sizes, oh well.
		int start = Math.max(0, getSelectedItemPosition());
		final int end = Math.min(adapter.getCount(), start + MAX_ITEMS_MEASURED);
		final int count = end - start;
		start = Math.max(0, start - (MAX_ITEMS_MEASURED - count));
		for (int i = start; i < end; i++) {
			final int positionType = adapter.getItemViewType(i);
			if (positionType != itemType) {
				itemType = positionType;
				itemView = null;
			}
			itemView = adapter.getView(i, itemView, this);
			if (itemView.getLayoutParams() == null) {
				itemView.setLayoutParams(new ViewGroup.LayoutParams(
						ViewGroup.LayoutParams.WRAP_CONTENT,
						ViewGroup.LayoutParams.WRAP_CONTENT));
			}
			itemView.measure(widthMeasureSpec, heightMeasureSpec);
			width = Math.max(width, itemView.getMeasuredWidth());
		}

		// Add background padding to measured width
		if (background != null) {
			background.getPadding(tempRect);
			width += tempRect.left + tempRect.right;
		}

		return width;
	}

	@Override
	public boolean performClick() {
		int w=Math.min(measureContentWidth(getAdapter(), null), getWidth());
		if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.JELLY_BEAN)
			setDropDownWidth(w);
		return super.performClick();
	}
}
