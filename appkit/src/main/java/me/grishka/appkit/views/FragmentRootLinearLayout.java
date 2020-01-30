package me.grishka.appkit.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.WindowInsets;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;

public class FragmentRootLinearLayout extends LinearLayout{

	private Paint paint=new Paint();
	private int statusBarColor=0xFF000000, navigationBarColorBottom=0xFF000000, navigationBarColorSide=0xFF000000;

	public FragmentRootLinearLayout(Context context){
		super(context);
	}

	public FragmentRootLinearLayout(Context context, @Nullable AttributeSet attrs){
		super(context, attrs);
	}

	public FragmentRootLinearLayout(Context context, @Nullable AttributeSet attrs, int defStyleAttr){
		super(context, attrs, defStyleAttr);
	}

	@Override
	public WindowInsets onApplyWindowInsets(WindowInsets insets){
		setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(), insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
		return insets.consumeSystemWindowInsets();
	}

	public int getStatusBarColor(){
		return statusBarColor;
	}

	public void setStatusBarColor(int statusBarColor){
		this.statusBarColor=statusBarColor;
		invalidate();
	}

	public int getNavigationBarColor(){
		return navigationBarColorBottom;
	}

	public void setNavigationBarColor(int navigationBarColor){
		this.navigationBarColorBottom=navigationBarColorSide=navigationBarColor;
		invalidate();
	}

	public void setLandscapeNavigationBarColor(int color){
		navigationBarColorSide=color;
	}

	@Override
	protected void dispatchDraw(Canvas canvas){
		super.dispatchDraw(canvas);
		if(getPaddingTop()>0){
			paint.setColor(statusBarColor);
			canvas.drawRect(getPaddingLeft(), 0, getWidth()-getPaddingRight(), getPaddingTop(), paint);
		}
		paint.setColor(navigationBarColorBottom);
		if(getPaddingBottom()>0){
			canvas.drawRect(getPaddingLeft(), getHeight()-getPaddingBottom(), getWidth()-getPaddingRight(), getHeight(), paint);
		}
		paint.setColor(navigationBarColorSide);
		if(getPaddingLeft()>0){
			canvas.drawRect(0, 0, getPaddingLeft(), getHeight(), paint);
		}
		if(getPaddingRight()>0){
			canvas.drawRect(getWidth()-getPaddingRight(), 0, getWidth(), getHeight(), paint);
		}
	}
}
