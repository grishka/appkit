package me.grishka.appkit.views;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;

import androidx.viewpager.widget.ViewPager;

/**
 * Created by grishka on 15/07/15.
 */
public class DisableableViewPager extends ViewPager{
    private boolean swipeEnabled = true;

    public DisableableViewPager(Context context) {
        super(context);
    }

    public DisableableViewPager(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        return swipeEnabled && super.onInterceptTouchEvent(ev);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        return swipeEnabled && super.onTouchEvent(ev);
    }

    public boolean isSwipeEnabled() {
        return swipeEnabled;
    }

    public void setSwipeEnabled(boolean swipeEnabled) {
        this.swipeEnabled = swipeEnabled;
    }
}
