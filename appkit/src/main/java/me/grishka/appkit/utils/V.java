package me.grishka.appkit.utils;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.app.Application;
import android.content.Context;
import android.graphics.Point;
import android.view.View;
import android.view.ViewGroup;

import java.util.HashMap;

import me.grishka.appkit.R;

/**
 * View utilities
 */
public class V{

	private static Context appContext;
	private static HashMap<View, ObjectAnimator> visibilityAnims=new HashMap<View, ObjectAnimator>();

	/**
	 * This must be called before calling any other methods from this class.
	 * If you're using AppKit fragments, this has already been called for you upon fragment initialization.
	 * @param context
	 */
	public static void setApplicationContext(Context context){
		if(appContext==null)
			appContext=context.getApplicationContext();
	}

	/**
	 * Scale the input value according to the device's display density
	 * @param dp Input value in density-independent pixels (dp)
	 * @return Scaled value in physical pixels (px)
	 */
	public static int dp(float dp){
		if(appContext==null)
			throw new IllegalStateException("Application context is not set, call V.setApplicationContext() before using these methods");
		return Math.round(dp*appContext.getResources().getDisplayMetrics().density);
	}

	/**
	 * Change a View's visibility with a fade-in/-out animation. If that doesn't change the actual visibility, (INVISIBLE -> GONE or VISIBLE -> VISIBLE) does nothing.
	 * @param view The target view
	 * @param visibility The new visibility constant, either View.VISIBLE, View.INVISIBLE, or View.GONE
	 */
	public static void setVisibilityAnimated(final View view, final int visibility){
		if (view == null) {
			return;
		}
		boolean vis=visibility==View.VISIBLE;
		boolean viewVis=view.getVisibility()==View.VISIBLE && view.getTag(R.id.tag_visibility_anim)==null;
		if(vis==viewVis) return;
		if(visibilityAnims.containsKey(view)){
			visibilityAnims.get(view).cancel();
			visibilityAnims.remove(view);
		}
		if(vis){
			ObjectAnimator anim=ObjectAnimator.ofFloat(view, "alpha", view.getAlpha()<1 ? view.getAlpha() : 0, 1);
			anim.addListener(new AnimatorListenerAdapter(){
				public void onAnimationStart(Animator anim){
					view.setVisibility(visibility);
					//Log.i("appkit", "Anim start "+anim);
				}
				public void onAnimationEnd(Animator anim){
					view.setVisibility(visibility);
					//Log.i("appkit", "Anim end "+anim);
					visibilityAnims.remove(view);
				}
				public void onAnimationCancel(Animator anim){
					view.setVisibility(visibility);
					//Log.i("appkit", "Anim cancel "+anim);
				}
			});
			anim.setDuration(300);
			visibilityAnims.put(view, anim);
			anim.start();
		}else{
			ObjectAnimator anim=ObjectAnimator.ofFloat(view, "alpha", 0);
			anim.addListener(new AnimatorListenerAdapter(){
				boolean canceled=false;
				public void onAnimationStart(Animator anim){
					//Log.i("appkit", "Anim start "+anim);
				}
				public void onAnimationEnd(Animator anim){
					//Log.i("appkit", "Anim end "+anim);
					view.setTag(R.id.tag_visibility_anim, null);
					visibilityAnims.remove(view);
					if(canceled) return;
					view.setVisibility(visibility);
					view.setAlpha(1);
				}
				public void onAnimationCancel(Animator anim){
					//Log.i("appkit", "Anim cancel "+anim);
					canceled=true;
				}
			});
			view.setTag(R.id.tag_visibility_anim, true);
			anim.setDuration(300);
			visibilityAnims.put(view, anim);
			anim.start();
		}
	}

	public static void cancelVisibilityAnimation(final View view){
		//if(view.getTag(R.id.tag_visibility_anim)==null) return;
		if(!visibilityAnims.containsKey(view)) return;
		visibilityAnims.get(view).cancel();
		view.setAlpha(1);
	}

	public static Point getViewOffset(View v1, View v2){
		int[] p1={0,0}, p2={0,0};
		v1.getLocationOnScreen(p1);
		v2.getLocationOnScreen(p2);
		//Log.i("appkit", "view 1: "+p1[0]+","+p1[1]+"; view 2: "+p2[0]+","+p2[1]);
		return new Point(p1[0]-p2[0], p1[1]-p2[1]);
	}

	public static View findClickableChild(ViewGroup viewGroup, int x, int y){
		for(int i=0;i<viewGroup.getChildCount();i++){
			View c=viewGroup.getChildAt(i);
			if(c.getLeft()<x && c.getRight()>x && c.getTop()<y && c.getBottom()>y){
				if(c.isClickable())
					return c;
				if(c instanceof ViewGroup) {
					View r=findClickableChild((ViewGroup) c, x - c.getLeft(), y - c.getTop());
					if(r!=null)
						return r;
				}
			}
		}
		return null;
	}
}
