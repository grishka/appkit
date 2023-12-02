package me.grishka.appkit.views;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Property;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import me.grishka.appkit.utils.CubicBezierInterpolator;
import me.grishka.appkit.utils.V;

public class BottomSheet extends Dialog{

	protected ContainerView container;
	protected View content;
	private DisplayMetrics displayMetrics;
	private boolean dismissed;
	private Drawable navigationBarBackground;
	private static final Property<View, Float> DUMMY_INVALIDATOR_PROPERTY=new Property<View, Float>(Float.class, "dummy"){
		@Override
		public Float get(View object){
			return 0f;
		}

		@Override
		public void set(View object, Float value){
			object.invalidate();
		}
	};
	protected float dimAmount=0.5f;

	public BottomSheet(@NonNull Context context){
		super(context);
		displayMetrics=context.getResources().getDisplayMetrics();
	}

	@Override
	protected void onCreate(Bundle savedInstanceState){
		super.onCreate(savedInstanceState);
		Window window=getWindow();
		window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
		window.setBackgroundDrawable(null);
		window.setWindowAnimations(0);
		window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS | WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN);
		window.setDimAmount(0);
		window.getDecorView().setSystemUiVisibility(window.getDecorView().getSystemUiVisibility() | View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
	}

	@Override
	public void setContentView(@NonNull View view){
		view.setNestedScrollingEnabled(true);
		container=new ContainerView(getContext());
		container.addView(view, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL));
		container.setClipToPadding(false);
		content=view;
		super.setContentView(container);
	}

	@Override
	public void dismiss(){
		if(dismissed)
			return;
		dismissed=true;
		int height=content.getHeight();
		getWindow().setDimAmount(0);
		AnimatorSet set=new AnimatorSet();
		set.playTogether(
				ObjectAnimator.ofFloat(content, View.TRANSLATION_Y, height),
				ObjectAnimator.ofFloat(getWindow(), "dimAmount", dimAmount, 0),
				ObjectAnimator.ofFloat(container, DUMMY_INVALIDATOR_PROPERTY, 1f, 0f)
		);
		set.setDuration(Math.max(60, (int) (180 * (height - content.getTranslationY()) / (float) height)));
		set.setInterpolator(CubicBezierInterpolator.EASE_OUT);
		set.addListener(new AnimatorListenerAdapter(){
			@Override
			public void onAnimationEnd(Animator animation){
				BottomSheet.super.dismiss();
			}
		});
		set.start();
	}

	@Override
	public void show(){
		super.show();
		content.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener(){
			@Override
			public boolean onPreDraw(){
				updateMargins();
				content.getViewTreeObserver().removeOnPreDrawListener(this);
				content.setTranslationY(content.getHeight());
				AnimatorSet set=new AnimatorSet();
				set.playTogether(
						ObjectAnimator.ofFloat(content, View.TRANSLATION_Y, 0),
						ObjectAnimator.ofFloat(getWindow(), "dimAmount", 0, dimAmount),
						ObjectAnimator.ofFloat(container, DUMMY_INVALIDATOR_PROPERTY, 1f, 0f)
				);
				set.setDuration(300);
				set.setInterpolator(CubicBezierInterpolator.DEFAULT);
				set.start();
				return true;
			}
		});
	}

	public void setNavigationBarBackground(Drawable drawable, boolean useLightNavbar){
		navigationBarBackground=drawable;
		if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.O && useLightNavbar){
			getWindow().getDecorView().setSystemUiVisibility(getWindow().getDecorView().getSystemUiVisibility() | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
		}
	}

	protected void updateMargins(){
		int width=Math.round(getWindow().getDecorView().getWidth()/displayMetrics.density);
		FrameLayout.LayoutParams lp=(FrameLayout.LayoutParams) content.getLayoutParams();
		if(width>640+56+56){
			lp.leftMargin=lp.rightMargin=V.dp(56);
			lp.topMargin=V.dp(56);
			lp.width=V.dp(640);
		}else{
			lp.leftMargin=lp.rightMargin=0;
			lp.topMargin=V.dp(72);
			lp.width=ViewGroup.LayoutParams.MATCH_PARENT;
		}
		container.requestLayout();
	}

	protected void onWindowInsetsUpdated(WindowInsets insets){}

	private class ContainerView extends FrameLayout{

		private float currentTranslationY=0;
		private VelocityTracker velocityTracker;
		private boolean isGestureNavigation;
		private Rect tmpRect=new Rect();

		public ContainerView(Context context) {
			super(context);
			setWillNotDraw(false);
		}

		@Override
		public boolean onStartNestedScroll(View child, View target, int nestedScrollAxes){
			if(velocityTracker==null)
				velocityTracker=VelocityTracker.obtain();
			return true;
		}

		@Override
		public void onStopNestedScroll(View child){
			super.onStopNestedScroll(child);
			if(velocityTracker!=null && !dismissed){
				if(currentTranslationY>0){
					velocityTracker.computeCurrentVelocity(1000);
					maybeDismiss(velocityTracker.getXVelocity(), velocityTracker.getYVelocity());
				}
				velocityTracker.recycle();
				velocityTracker=null;
			}
		}

		@Override
		public void onNestedScroll(View target, int dxConsumed, int dyConsumed, int dxUnconsumed, int dyUnconsumed){
			currentTranslationY-=dyUnconsumed;
			content.setTranslationY(Math.max(0, currentTranslationY));
			invalidate();
		}

		@Override
		public void onNestedPreScroll(View target, int dx, int dy, int[] consumed){
			if(currentTranslationY>0 && dy>0){
				if(dy<=currentTranslationY){
					currentTranslationY-=dy;
					consumed[1]=dy;
				}else{
					consumed[1]=(int)currentTranslationY;
					currentTranslationY=0;
				}
				content.setTranslationY(Math.max(0, currentTranslationY));
				invalidate();
			}
		}

		@Override
		public boolean onNestedFling(View target, float velocityX, float velocityY, boolean consumed){
			return false;
		}

		@Override
		public boolean onNestedPreFling(View target, float velocityX, float velocityY){
			return false;
		}

		@Override
		public boolean dispatchTouchEvent(MotionEvent ev){
			if(velocityTracker!=null)
				velocityTracker.addMovement(ev);
			if(dismissed)
				return true;
			return super.dispatchTouchEvent(ev);
		}

		@Override
		public boolean dispatchKeyEvent(KeyEvent event){
			if(dismissed)
				return true;
			return super.dispatchKeyEvent(event);
		}

		@Override
		public WindowInsets onApplyWindowInsets(WindowInsets insets){
			setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(), insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
			onWindowInsetsUpdated(insets);
			isGestureNavigation=Build.VERSION.SDK_INT>=29 && insets.getTappableElementInsets().bottom==0 && insets.getSystemWindowInsetBottom()>0;
			return insets;
		}

		private void maybeDismiss(float velX, float velY){
			boolean backAnimation = currentTranslationY < getPixelsInCM(0.8f, false) && (velY < 3500 || Math.abs(velY) < Math.abs(velX)) || velY < 0 && Math.abs(velY) >= 3500;
			if (!backAnimation) {
				dismiss();
			} else {
				final AnimatorSet currentAnimation = new AnimatorSet();
				currentAnimation.playTogether(ObjectAnimator.ofFloat(content, View.TRANSLATION_Y, 0), ObjectAnimator.ofFloat(this, DUMMY_INVALIDATOR_PROPERTY, 1f, 0f));
				currentAnimation.setDuration((int) (150 * (currentTranslationY / getPixelsInCM(0.8f, false))));
				currentAnimation.setInterpolator(CubicBezierInterpolator.EASE_OUT);
				currentTranslationY=0;
				currentAnimation.start();
			}
		}

		@Override
		protected void dispatchDraw(Canvas canvas){
			super.dispatchDraw(canvas);
			if(navigationBarBackground!=null && getPaddingBottom()>0){
				if(isGestureNavigation){
					canvas.save();
					canvas.translate(0, content.getTranslationY());
				}
				navigationBarBackground.setBounds(content.getLeft(), getHeight()-getPaddingBottom(), content.getRight(), getHeight());
				navigationBarBackground.draw(canvas);
				if(isGestureNavigation)
					canvas.restore();
			}
		}

		@Override
		public boolean onTouchEvent(MotionEvent event){
			if(event.getAction()==MotionEvent.ACTION_DOWN){
				content.getHitRect(tmpRect);
				if(!tmpRect.contains((int)event.getX(), (int)event.getY())){
					dismiss();
				}
				return true;
			}
			return super.onTouchEvent(event);
		}

		@Override
		protected void onSizeChanged(int w, int h, int oldw, int oldh){
			super.onSizeChanged(w, h, oldw, oldh);
			updateMargins();
		}
	}

	private float getPixelsInCM(float cm, boolean isX) {
		return (cm / 2.54f) * (isX ? displayMetrics.xdpi : displayMetrics.ydpi);
	}
}
