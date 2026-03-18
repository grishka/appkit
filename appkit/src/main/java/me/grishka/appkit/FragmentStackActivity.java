package me.grishka.appkit;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentContainer;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.util.FloatProperty;
import android.util.Property;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.RoundedCorner;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.ViewTreeObserver;
import android.view.WindowInsets;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.PathInterpolator;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;
import android.window.BackEvent;
import android.window.OnBackAnimationCallback;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import me.grishka.appkit.fragments.AppKitFragment;
import me.grishka.appkit.fragments.CustomTransitionsFragment;
import me.grishka.appkit.fragments.WindowInsetsAwareFragment;
import me.grishka.appkit.utils.CubicBezierInterpolator;
import me.grishka.appkit.utils.V;

public class FragmentStackActivity extends Activity{
	private static final String TAG="FragmentStackActivity";

	protected FragmentStackContainer content;
	protected ArrayList<FrameLayout> fragmentContainers=new ArrayList<>();
	protected WindowInsets lastInsets;
	protected ArrayList<Animator> runningAnimators=new ArrayList<>();
	protected boolean blockInputEvents; // during fragment transitions
	protected boolean instanceStateSaved;
	private ArrayList<Integer> pendingFragmentRemovals=new ArrayList<>();
	private ArrayList<Fragment> pendingFragmentAdditions=new ArrayList<>();
	private int nextViewID=1;
	private Object stackOnBackInvokedCallback;
	private ArrayList<BackCallbackRecord> fragmentBackCallbacks=new ArrayList<>();
	private View predictiveAnimCurrentFragmentView, predictiveAnimPrevFragmentView;

	@Override
	protected void onCreate(@Nullable Bundle savedInstanceState){
		content=new FragmentStackContainer(this);
		content.setId(R.id.fragment_wrap);
		content.setFitsSystemWindows(true);
		getWindow().getDecorView().setSystemUiVisibility(getWindow().getDecorView().getSystemUiVisibility() | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
		setContentView(content);

		getWindow().setBackgroundDrawable(new ColorDrawable(0));
		getWindow().setStatusBarColor(0);
		getWindow().setNavigationBarColor(0);

		if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.UPSIDE_DOWN_CAKE){
			stackOnBackInvokedCallback=new PredictiveBackAnimationCallback();
		}else if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.TIRAMISU){
			stackOnBackInvokedCallback=(OnBackInvokedCallback) this::onPredictiveBackInvoked;
		}

		if(savedInstanceState!=null){
			nextViewID=savedInstanceState.getInt("appkit:nextGeneratedViewID", 1);
			int[] ids=savedInstanceState.getIntArray("appkit:fragmentContainerIDs");
			if(ids!=null && ids.length>0){
				int last=ids[ids.length-1];
				for(int id : ids){
					FrameLayout wrap=new FragmentContainer(this);
					wrap.setId(id);
					if(id!=last)
						wrap.setVisibility(View.GONE);
					content.addView(wrap, 0);
					fragmentContainers.add(wrap);
				}
				if(ids.length>1)
					addPredictiveBackCallback();
			}
		}

		super.onCreate(savedInstanceState);
	}

	private void applySystemBarColors(boolean lightStatus, boolean lightNav){
		if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.M){
			int flags=getWindow().getDecorView().getSystemUiVisibility();
			int origFlags=flags;
			if(lightStatus)
				flags|=View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
			else
				flags&=~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
			if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.O_MR1){
				if(lightNav)
					flags|=View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
				else
					flags&=~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
			}
			if(flags!=origFlags){
				getWindow().getDecorView().setSystemUiVisibility(flags);
			}
		}
	}

	private void applySystemBarColorsForFragment(Fragment fragment){
		if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.M){
			if(fragment instanceof WindowInsetsAwareFragment waf){
				applySystemBarColors(waf.wantsLightStatusBar(), waf.wantsLightNavigationBar());
			}else{
				TypedArray ta=obtainStyledAttributes(new int[]{android.R.attr.windowLightStatusBar, android.R.attr.windowLightNavigationBar});
				applySystemBarColors(ta.getBoolean(0, false), ta.getBoolean(1, false));
				ta.recycle();
			}
		}
	}

	public void showFragment(final Fragment fragment){
		if(instanceStateSaved){
			pendingFragmentAdditions.add(fragment);
			return;
		}
		final FrameLayout wrap=new FragmentContainer(this);
		wrap.setId(generateViewId());
		if(!fragmentContainers.isEmpty())
			addPredictiveBackCallback();
		content.addView(wrap, 0);
		fragmentContainers.add(wrap);
		getFragmentManager().beginTransaction().add(wrap.getId(), fragment, "stackedFragment_"+wrap.getId()).commit();
		getFragmentManager().executePendingTransactions();
		if(fragment instanceof WindowInsetsAwareFragment waf){
			if(lastInsets!=null)
				waf.onApplyWindowInsets(new WindowInsets(lastInsets));
		}
		if(fragmentContainers.size()>1){
			wrap.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener(){
				@Override
				public boolean onPreDraw(){
					wrap.getViewTreeObserver().removeOnPreDrawListener(this);

					FrameLayout prevWrap=fragmentContainers.get(fragmentContainers.size()-2);
					Animator anim=null;
					if(fragment instanceof CustomTransitionsFragment ctf)
						anim=ctf.onCreateEnterTransition(prevWrap, wrap);
					if(anim==null)
						anim=createFragmentEnterTransition(prevWrap, wrap);
					Runnable onEnd=()->{
						for(int i=0; i<fragmentContainers.size()-1; i++){
							View container=fragmentContainers.get(i);
							if(container.getVisibility()==View.VISIBLE){
								Fragment otherFragment=getFragmentManager().findFragmentById(container.getId());
								getFragmentManager().beginTransaction().hide(otherFragment).commit();
								getFragmentManager().executePendingTransactions();
								container.setVisibility(View.GONE);
							}
						}
						if(fragment instanceof AppKitFragment akf)
							akf.onTransitionFinished();
					};
					if(anim!=null){
						anim.addListener(new AnimatorListenerAdapter(){
							@Override
							public void onAnimationEnd(Animator animation){
								onEnd.run();
								runningAnimators.remove(animation);
								if(runningAnimators.isEmpty())
									onAllFragmentTransitionsDone();
							}
						});
						if(runningAnimators.isEmpty())
							onFragmentTransitionStart();
						runningAnimators.add(anim);
						anim.start();
						wrap.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener(){
							private float prevAlpha=wrap.getAlpha();
							@Override
							public boolean onPreDraw(){
								float alpha=wrap.getAlpha();
								if(prevAlpha>alpha){
									wrap.getViewTreeObserver().removeOnPreDrawListener(this);
									return true;
								}
								if(alpha>=0.5f){
									wrap.getViewTreeObserver().removeOnPreDrawListener(this);
									applySystemBarColorsForFragment(fragment);
								}
								prevAlpha=alpha;
								return true;
							}
						});
					}else{
						onEnd.run();
						applySystemBarColorsForFragment(fragment);
					}
					return true;
				}
			});
		}else{
			applySystemBarColorsForFragment(fragment);
		}
		setTitle(getTitleForFragment(fragment));
	}

	public void showFragmentClearingBackStack(Fragment fragment){
		FragmentTransaction transaction=getFragmentManager().beginTransaction();
		for(FrameLayout fl:fragmentContainers){
			transaction.remove(getFragmentManager().findFragmentById(fl.getId()));
		}
		transaction.commit();
		getFragmentManager().executePendingTransactions();
		fragmentContainers.clear();
		content.removeAllViews();
		removePredictiveBackCallback();
		showFragment(fragment);
	}

	public void removeFragment(Fragment target, boolean hideKeyboard){
		if(instanceStateSaved){
			pendingFragmentRemovals.add(target.getId());
			return;
		}
		Fragment currentFragment=getFragmentManager().findFragmentById(fragmentContainers.get(fragmentContainers.size()-1).getId());
		if(target==currentFragment){ // top-most, remove with animation and show whatever is underneath
			final FrameLayout wrap=fragmentContainers.remove(fragmentContainers.size()-1);
			if(fragmentContainers.isEmpty()){ // There's nothing underneath. Finish the entire activity then.
				finish();
				return;
			}
			if(fragmentContainers.size()==1)
				removePredictiveBackCallback();
			final Fragment fragment=getFragmentManager().findFragmentById(wrap.getId());
			FrameLayout prevWrap=fragmentContainers.get(fragmentContainers.size()-1);
			Fragment prevFragment=getFragmentManager().findFragmentById(prevWrap.getId());
			getFragmentManager().beginTransaction().show(prevFragment).commit();
			getFragmentManager().executePendingTransactions();
			prevWrap.setVisibility(View.VISIBLE);
			final boolean lightStatus, lightNav;
			if(prevFragment instanceof WindowInsetsAwareFragment){
				((WindowInsetsAwareFragment) prevFragment).onApplyWindowInsets(new WindowInsets(lastInsets));
				lightStatus=((WindowInsetsAwareFragment) prevFragment).wantsLightStatusBar();
				lightNav=((WindowInsetsAwareFragment) prevFragment).wantsLightNavigationBar();
			}else{
				lightStatus=lightNav=false;
			}
			Animator anim=null;
			if(fragment instanceof CustomTransitionsFragment ctf)
				anim=ctf.onCreateExitTransition(prevWrap, wrap);
			if(anim==null)
				anim=createFragmentExitTransition(prevWrap, wrap);
			Runnable onEnd=()->{
				getFragmentManager().beginTransaction().remove(fragment).commit();
				getFragmentManager().executePendingTransactions();
				content.removeView(wrap);
			};
			if(anim!=null){
				anim.addListener(new AnimatorListenerAdapter(){
					@Override
					public void onAnimationEnd(Animator animation){
						onEnd.run();
						runningAnimators.remove(animation);
						if(runningAnimators.isEmpty())
							onAllFragmentTransitionsDone();
					}
				});
				if(runningAnimators.isEmpty())
					onFragmentTransitionStart();
				runningAnimators.add(anim);
				anim.start();
				wrap.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener(){
					private float prevAlpha=wrap.getAlpha();
					@Override
					public boolean onPreDraw(){
						float alpha=wrap.getAlpha();
						if(prevAlpha<alpha){
							wrap.getViewTreeObserver().removeOnPreDrawListener(this);
							return true;
						}
						if(alpha<=0.5f){
							wrap.getViewTreeObserver().removeOnPreDrawListener(this);
							applySystemBarColors(lightStatus, lightNav);
						}
						prevAlpha=alpha;
						return true;
					}
				});
			}else{
				onEnd.run();
				applySystemBarColors(lightStatus, lightNav);
			}
			if(hideKeyboard){
				InputMethodManager imm=(InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
				imm.hideSoftInputFromWindow(getWindow().getDecorView().getWindowToken(), 0);
			}
			setTitle(getTitleForFragment(prevFragment));
		}else{
			int id=target.getId();
			for(FrameLayout wrap:fragmentContainers){
				if(wrap.getId()==id){
					getFragmentManager().beginTransaction().remove(target).commit();
					getFragmentManager().executePendingTransactions();
					content.removeView(wrap);
					fragmentContainers.remove(wrap);
					return;
				}
			}
			throw new IllegalArgumentException("Fragment "+target+" is not from this activity");
		}
	}

	@Override
	public void onBackPressed(){
		if(!fragmentBackCallbacks.isEmpty()){
			BackCallbackRecord topCallback=fragmentBackCallbacks.get(fragmentBackCallbacks.size()-1);
			if(topCallback.fragment.isVisible()){
				topCallback.callback.run();
				return;
			}
		}
		if(fragmentContainers.size()>1){
			Fragment currentFragment=getFragmentManager().findFragmentById(fragmentContainers.get(fragmentContainers.size()-1).getId());
			removeFragment(currentFragment, true);
			return;
		}
		super.onBackPressed();
	}

	private Fragment getTopParent(Fragment fragment){
		Fragment parent=fragment.getParentFragment();
		return parent!=null ? getTopParent(parent) : fragment;
	}

	private void onPredictiveBackInvoked(){
		if(instanceStateSaved)
			return;
		if(fragmentContainers.size()>1){
			Fragment currentFragment=getFragmentManager().findFragmentById(fragmentContainers.get(fragmentContainers.size()-1).getId());
			removeFragment(currentFragment, true);
		}
	}

	@Override
	public boolean dispatchKeyEvent(KeyEvent event){
		if(blockInputEvents && event.getKeyCode()!=KeyEvent.KEYCODE_BACK){
			return true;
		}
		return super.dispatchKeyEvent(event);
	}

	@Override
	public boolean dispatchTouchEvent(MotionEvent ev){
		if(blockInputEvents)
			return true;
		return super.dispatchTouchEvent(ev);
	}

	@Override
	public boolean dispatchTrackballEvent(MotionEvent ev){
		if(blockInputEvents)
			return true;
		return super.dispatchTrackballEvent(ev);
	}

	@Override
	public boolean dispatchGenericMotionEvent(MotionEvent ev){
		if(blockInputEvents)
			return true;
		return super.dispatchGenericMotionEvent(ev);
	}

	protected void reapplyWindowInsets(){
		FragmentManager mgr=getFragmentManager();
		for(int i=0;i<content.getChildCount();i++){
			View child=content.getChildAt(i);
			Fragment fragment=mgr.findFragmentById(child.getId());
			if(fragment instanceof WindowInsetsAwareFragment){
				((WindowInsetsAwareFragment) fragment).onApplyWindowInsets(new WindowInsets(lastInsets));
			}
		}
	}

	public void invalidateSystemBarColors(final WindowInsetsAwareFragment fragment){
		if(!fragmentContainers.isEmpty() && getFragmentManager().findFragmentById(fragmentContainers.get(fragmentContainers.size()-1).getId())==fragment){
			content.post(()->applySystemBarColors(fragment.wantsLightStatusBar(), fragment.wantsLightNavigationBar()));
		}
	}

	@Override
	protected void onSaveInstanceState(@NonNull Bundle outState){
		if(!runningAnimators.isEmpty()){
			for(Animator anim:runningAnimators){
				anim.end();
			}
			runningAnimators.clear();
		}
		super.onSaveInstanceState(outState);
		int[] ids=new int[fragmentContainers.size()];
		for(int i=0;i<fragmentContainers.size();i++){
			ids[i]=fragmentContainers.get(i).getId();
		}
		outState.putIntArray("appkit:fragmentContainerIDs", ids);
		outState.putInt("appkit:nextGeneratedViewID", nextViewID);
		instanceStateSaved=true;
	}

	@Override
	protected void onResume(){
		super.onResume();
		instanceStateSaved=false;
		if(!pendingFragmentRemovals.isEmpty()){
			for(int id:pendingFragmentRemovals){
				Fragment f=getFragmentManager().findFragmentById(id);
				if(f!=null){
					removeFragment(f, true);
				}
			}
			pendingFragmentRemovals.clear();
		}
		if(!pendingFragmentAdditions.isEmpty()){
			for(Fragment f:pendingFragmentAdditions){
				showFragment(f);
			}
			pendingFragmentAdditions.clear();
		}
	}

	protected Animator createFragmentEnterTransition(View prev, View container){
		AnimatorSet anim=new AnimatorSet();
		anim.playTogether(
				ObjectAnimator.ofFloat(container, View.ALPHA, 0f, 1f),
				ObjectAnimator.ofFloat(container, View.TRANSLATION_X, V.dp(100), 0)
		);
		anim.setDuration(300);
		anim.setInterpolator(CubicBezierInterpolator.DEFAULT);
		return anim;
	}

	protected Animator createFragmentExitTransition(View prev, View container){
		AnimatorSet anim=new AnimatorSet();
		List<Animator> anims=new ArrayList<>();
		anims.add(ObjectAnimator.ofFloat(container, View.TRANSLATION_X, container.getTranslationX()+V.dp(100)));
		anims.add(ObjectAnimator.ofFloat(container, View.ALPHA, 0));
		if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.UPSIDE_DOWN_CAKE && predictiveAnimPrevFragmentView!=null){
			anims.add(ObjectAnimator.ofFloat(prev, View.TRANSLATION_X, 0));
			anims.add(ObjectAnimator.ofFloat(prev, View.TRANSLATION_Y, 0));
			anims.add(ObjectAnimator.ofFloat(prev, View.SCALE_X, 1));
			anims.add(ObjectAnimator.ofFloat(prev, View.SCALE_Y, 1));
			anims.add(ObjectAnimator.ofFloat(content, new FloatProperty<View>("fdafdsa"){
				@Override
				public Float get(View object){
					return content.predictiveBackOverlayPaint.getAlpha()/255f;
				}

				@Override
				public void setValue(View object, float value){
					content.predictiveBackOverlayPaint.setAlpha(Math.round(255*value));
					content.invalidate();
				}
			}, 0));
			anim.addListener(new AnimatorListenerAdapter(){
				@Override
				public void onAnimationEnd(Animator animation){
					predictiveAnimPrevFragmentView.setOutlineProvider(null);
					predictiveAnimPrevFragmentView.setClipToOutline(false);
					predictiveAnimPrevFragmentView=null;
					content.setBackground(null);
				}
			});
		}
		anim.playTogether(anims);
		anim.setDuration(200);
		anim.setInterpolator(CubicBezierInterpolator.DEFAULT);
		return anim;
	}

	protected CharSequence getTitleForFragment(Fragment fragment){
		if(fragment instanceof AppKitFragment akf){
			return akf.getTitle();
		}
		try{
			int label=getPackageManager().getActivityInfo(getComponentName(), 0).labelRes;
			if(label!=0)
				return getString(label);
			return getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(getPackageName(), 0));
		}catch(PackageManager.NameNotFoundException ignored){}
		return null;
	}

	protected void onFragmentTransitionStart(){
		blockInputEvents=true;
	}

	protected void onAllFragmentTransitionsDone(){
		blockInputEvents=false;
	}

	private void addPredictiveBackCallback(){
		if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.TIRAMISU){
			getOnBackInvokedDispatcher().registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, (OnBackInvokedCallback) stackOnBackInvokedCallback);
		}
	}

	private void removePredictiveBackCallback(){
		if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.TIRAMISU){
			getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback((OnBackInvokedCallback) stackOnBackInvokedCallback);
		}
	}

	public void addBackCallback(Fragment fragment, Runnable callback){
		for(BackCallbackRecord bcr:fragmentBackCallbacks){
			if(bcr.callback==callback){
				fragmentBackCallbacks.remove(bcr);
				if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.TIRAMISU){
					getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback((OnBackInvokedCallback) bcr.nativeCallback);
				}
				break;
			}
		}
		BackCallbackRecord bcr=new BackCallbackRecord(getTopParent(fragment), callback);
		if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.TIRAMISU){
			bcr.nativeCallback=(OnBackInvokedCallback) callback::run;
			getOnBackInvokedDispatcher().registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, (OnBackInvokedCallback) bcr.nativeCallback);
		}
		fragmentBackCallbacks.add(bcr);
	}

	public void removeBackCallback(Runnable callback){
		for(BackCallbackRecord bcr:fragmentBackCallbacks){
			if(bcr.callback==callback){
				fragmentBackCallbacks.remove(bcr);
				if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.TIRAMISU){
					getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback((OnBackInvokedCallback) bcr.nativeCallback);
				}
				break;
			}
		}
	}

	public int generateViewId(){
		int r=nextViewID;
		nextViewID++;
		if(nextViewID>0x00FFFFFF){
			nextViewID=1;
		}
		return r;
	}

	protected int getPredictiveBackBackgroundColor(){
		TypedArray ta=obtainStyledAttributes(new int[]{android.R.attr.statusBarColor});
		int color=ta.getColor(0, 0xff00ff00);
		ta.recycle();
		return color;
	}

	protected int getPredictiveBackOverlayColor(){
		return 0x80000000;
	}

	private class FragmentContainer extends FrameLayout{
		public Fragment fragment;

		public FragmentContainer(@NonNull Context context){
			super(context);
		}

		@Override
		public void addView(View child, int index, ViewGroup.LayoutParams params){
			super.addView(child, index, params);
			if(fragment==null)
				fragment=getFragmentManager().findFragmentById(getId());

			if(fragment instanceof WindowInsetsAwareFragment waf){
				post(()->invalidateSystemBarColors(waf));
			}
		}
	}

	protected class FragmentStackContainer extends FrameLayout{
		public Paint predictiveBackOverlayPaint=new Paint();

		public FragmentStackContainer(@NonNull Context context){
			super(context);
			setChildrenDrawingOrderEnabled(true);
		}

		@Override
		public WindowInsets onApplyWindowInsets(WindowInsets insets){
			lastInsets=new WindowInsets(insets);
			FragmentManager mgr=getFragmentManager();
			for(int i=0;i<getChildCount();i++){
				View child=getChildAt(i);
				Fragment fragment=mgr.findFragmentById(child.getId());
				if(fragment instanceof WindowInsetsAwareFragment wif){
					wif.onApplyWindowInsets(new WindowInsets(insets));
				}
			}
			return insets.consumeSystemWindowInsets();
		}

		@Override
		protected int getChildDrawingOrder(int childCount, int drawingPosition){
			return childCount-drawingPosition-1;
		}

		@Override
		protected boolean drawChild(@NonNull Canvas canvas, View child, long drawingTime){
			boolean res=super.drawChild(canvas, child, drawingTime);
			if(child==predictiveAnimPrevFragmentView){
				canvas.drawRect(0, 0, getWidth(), getHeight(), predictiveBackOverlayPaint);
			}
			return res;
		}
	}

	private static class BackCallbackRecord{
		public Runnable callback;
		public Object nativeCallback;
		public Fragment fragment;

		public BackCallbackRecord(Fragment fragment, Runnable callback){
			this.callback=callback;
			this.fragment=fragment;
		}
	}

	@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
	private class PredictiveBackAnimationCallback implements OnBackAnimationCallback{
		private PathInterpolator progressInterpolator=new PathInterpolator(0, 0, 0, 1);
		private DecelerateInterpolator yOffsetInterpolator=new DecelerateInterpolator();
		private float initialTouchY;
		private Fragment prevFragment;

		@Override
		public void onBackInvoked(){
			predictiveAnimCurrentFragmentView=null;
			prevFragment=null;
			onPredictiveBackInvoked();
		}

		@Override
		public void onBackStarted(@NonNull BackEvent backEvent){
			predictiveAnimCurrentFragmentView=fragmentContainers.get(fragmentContainers.size()-1);
			Fragment fragment=getFragmentManager().findFragmentById(predictiveAnimCurrentFragmentView.getId());
			if(fragment instanceof CustomTransitionsFragment ctf && !ctf.wantsPredictiveBackExitTransition()){
				// Don't do predictive back for fragments that customize transitions
				predictiveAnimCurrentFragmentView=null;
				return;
			}
			predictiveAnimPrevFragmentView=fragmentContainers.get(fragmentContainers.size()-2);
			prevFragment=getFragmentManager().findFragmentById(predictiveAnimPrevFragmentView.getId());
			getFragmentManager().beginTransaction().show(prevFragment).commit();
			getFragmentManager().executePendingTransactions();
			predictiveAnimPrevFragmentView.setVisibility(View.VISIBLE);
			initialTouchY=backEvent.getTouchY();
			float screenRadius=Math.min(
					Math.min(getScreenCornerRadius(lastInsets, RoundedCorner.POSITION_TOP_LEFT), getScreenCornerRadius(lastInsets, RoundedCorner.POSITION_TOP_RIGHT)),
					Math.min(getScreenCornerRadius(lastInsets, RoundedCorner.POSITION_BOTTOM_LEFT), getScreenCornerRadius(lastInsets, RoundedCorner.POSITION_BOTTOM_RIGHT))
			);
			ViewOutlineProvider clip=new ViewOutlineProvider(){
				@Override
				public void getOutline(View view, Outline outline){
					outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), screenRadius);
				}
			};
			predictiveAnimCurrentFragmentView.setOutlineProvider(clip);
			predictiveAnimCurrentFragmentView.setClipToOutline(true);
			predictiveAnimPrevFragmentView.setOutlineProvider(clip);
			predictiveAnimPrevFragmentView.setClipToOutline(true);
			content.setBackgroundColor(getPredictiveBackBackgroundColor());
			content.predictiveBackOverlayPaint.setColor(getPredictiveBackOverlayColor());
			update(backEvent);
		}

		@Override
		public void onBackProgressed(@NonNull BackEvent backEvent){
			if(predictiveAnimCurrentFragmentView==null)
				return;
			update(backEvent);
		}

		@Override
		public void onBackCancelled(){
			if(predictiveAnimCurrentFragmentView==null)
				return;
			resetView(predictiveAnimCurrentFragmentView);
			predictiveAnimCurrentFragmentView=null;
			predictiveAnimPrevFragmentView.setVisibility(View.GONE);
			getFragmentManager().beginTransaction().hide(prevFragment).commit();
			getFragmentManager().executePendingTransactions();
			resetView(predictiveAnimPrevFragmentView);
			predictiveAnimPrevFragmentView=null;
			prevFragment=null;
			content.setBackground(null);
		}

		private void resetView(View v){
			v.setScaleX(1);
			v.setScaleY(1);
			v.setTranslationX(0);
			v.setTranslationY(0);
			v.setOutlineProvider(null);
			v.setClipToOutline(false);
		}

		private void update(@NonNull BackEvent ev){
			float progress=progressInterpolator.getInterpolation(ev.getProgress());
			float scale=1f-progress*0.1f;
			predictiveAnimCurrentFragmentView.setScaleX(scale);
			predictiveAnimCurrentFragmentView.setScaleY(scale);
			predictiveAnimPrevFragmentView.setScaleX(scale);
			predictiveAnimPrevFragmentView.setScaleY(scale);
			float transX=(content.getWidth()/20f-V.dp(8))*(ev.getSwipeEdge()==BackEvent.EDGE_RIGHT ? -1 : 1);
			predictiveAnimCurrentFragmentView.setTranslationX(transX*progress);
			predictiveAnimPrevFragmentView.setTranslationX(-content.getWidth()/5f);
			float touchY=ev.getTouchY();
			if(Float.isFinite(touchY)){
				float touchOffset=Math.max(Math.min((touchY-initialTouchY)/(content.getHeight()/2f), 1f), -1f);
				float interpolatedOffset=Math.copySign(yOffsetInterpolator.getInterpolation(Math.abs(touchOffset)), touchOffset);
				float transY=(content.getHeight()/20f-V.dp(8))*interpolatedOffset*progress;
				predictiveAnimCurrentFragmentView.setTranslationY(transY);
				predictiveAnimPrevFragmentView.setTranslationY(transY);
			}
		}

		private float getScreenCornerRadius(WindowInsets insets, int pos){
			RoundedCorner corner=insets.getRoundedCorner(pos);
			if(corner==null)
				return 0;
			return corner.getRadius();
		}
	}
}
