package me.grishka.appkit.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Build;
import android.util.AttributeSet;
import android.view.WindowInsets;
import android.view.WindowInsetsAnimation;
import android.widget.LinearLayout;

import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

public class FragmentRootLinearLayout extends LinearLayout{

	private Paint paint=new Paint();
	private int statusBarColor=0xFF000000, navigationBarColorBottom=0xFF000000, navigationBarColorSide=0xFF000000;
	private int bottomInset;

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
		setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(), insets.getSystemWindowInsetRight(), bottomInset=insets.getSystemWindowInsetBottom());
		return insets.consumeSystemWindowInsets();
	}

	@RequiresApi(Build.VERSION_CODES.R)
	@Override
	public void setWindowInsetsAnimationCallback(@Nullable WindowInsetsAnimation.Callback callback){
		super.setWindowInsetsAnimationCallback(callback==null ? null : new DelegatingWindowInsetsAnimationCallback(callback));
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
		if(bottomInset>0){
			canvas.drawRect(getPaddingLeft(), getHeight()-bottomInset, getWidth()-getPaddingRight(), getHeight(), paint);
		}
		paint.setColor(navigationBarColorSide);
		if(getPaddingLeft()>0){
			canvas.drawRect(0, 0, getPaddingLeft(), getHeight(), paint);
		}
		if(getPaddingRight()>0){
			canvas.drawRect(getWidth()-getPaddingRight(), 0, getWidth(), getHeight(), paint);
		}
	}

	@RequiresApi(Build.VERSION_CODES.R)
	private class DelegatingWindowInsetsAnimationCallback extends WindowInsetsAnimation.Callback{
		private final WindowInsetsAnimation.Callback delegate;

		public DelegatingWindowInsetsAnimationCallback(WindowInsetsAnimation.Callback delegate){
			super(WindowInsetsAnimation.Callback.DISPATCH_MODE_CONTINUE_ON_SUBTREE);
			this.delegate=delegate;
		}

		@Override
		public void onPrepare(@NonNull WindowInsetsAnimation animation){
			delegate.onPrepare(animation);
		}

		@NonNull
		@Override
		public WindowInsetsAnimation.Bounds onStart(@NonNull WindowInsetsAnimation animation, @NonNull WindowInsetsAnimation.Bounds bounds){
			return delegate.onStart(animation, bounds);
		}

		@Override
		public void onEnd(@NonNull WindowInsetsAnimation animation){
			delegate.onEnd(animation);
		}

		@NonNull
		@Override
		public WindowInsets onProgress(@NonNull WindowInsets insets, @NonNull List<WindowInsetsAnimation> runningAnimations){
			bottomInset=insets.getSystemWindowInsetBottom();
			invalidate();
			return delegate.onProgress(insets, runningAnimations);
		}
	}
}
