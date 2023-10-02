package me.grishka.appkit;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowInsets;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;

import java.util.ArrayList;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import me.grishka.appkit.fragments.AppKitFragment;
import me.grishka.appkit.fragments.CustomTransitionsFragment;
import me.grishka.appkit.fragments.OnBackPressedListener;
import me.grishka.appkit.fragments.WindowInsetsAwareFragment;
import me.grishka.appkit.utils.CubicBezierInterpolator;
import me.grishka.appkit.utils.V;

public class FragmentStackActivity extends Activity{
	protected FrameLayout content;
	protected ArrayList<FrameLayout> fragmentContainers=new ArrayList<>();
	protected WindowInsets lastInsets;
	protected ArrayList<Animator> runningAnimators=new ArrayList<>();
	protected boolean blockInputEvents; // during fragment transitions
	protected boolean instanceStateSaved;
	private ArrayList<Integer> pendingFragmentRemovals=new ArrayList<>();
	private ArrayList<Fragment> pendingFragmentAdditions=new ArrayList<>();
	private int nextViewID=1;

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

		if(savedInstanceState!=null){
			nextViewID=savedInstanceState.getInt("appkit:nextGeneratedViewID", 1);
			int[] ids=savedInstanceState.getIntArray("appkit:fragmentContainerIDs");
			if(ids.length>0){
				int last=ids[ids.length-1];
				for(int id : ids){
					FrameLayout wrap=new FragmentContainer(this);
					wrap.setId(id);
					if(id!=last)
						wrap.setVisibility(View.GONE);
					content.addView(wrap, 0);
					fragmentContainers.add(wrap);
				}
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

	public void showFragment(final Fragment fragment){
		if(instanceStateSaved){
			pendingFragmentAdditions.add(fragment);
			return;
		}
		final FrameLayout wrap=new FragmentContainer(this);
		wrap.setId(generateViewId());
		content.addView(wrap, 0);
		fragmentContainers.add(wrap);
		getFragmentManager().beginTransaction().add(wrap.getId(), fragment, "stackedFragment_"+wrap.getId()).commit();
		getFragmentManager().executePendingTransactions();
		final boolean lightStatus, lightNav;
		if(fragment instanceof WindowInsetsAwareFragment){
			if(lastInsets!=null)
				((WindowInsetsAwareFragment) fragment).onApplyWindowInsets(new WindowInsets(lastInsets));
			lightStatus=((WindowInsetsAwareFragment) fragment).wantsLightStatusBar();
			lightNav=((WindowInsetsAwareFragment) fragment).wantsLightNavigationBar();
		}else{
			lightStatus=lightNav=false;
		}
		if(fragmentContainers.size()>1){
			wrap.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener(){
				@Override
				public boolean onPreDraw(){
					wrap.getViewTreeObserver().removeOnPreDrawListener(this);

					FrameLayout prevWrap=fragmentContainers.get(fragmentContainers.size()-2);
					Animator anim;
					if(fragment instanceof CustomTransitionsFragment ctf)
						anim=ctf.onCreateEnterTransition(prevWrap, wrap);
					else
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
					return true;
				}
			});
		}else{
			applySystemBarColors(lightStatus, lightNav);
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
			Animator anim;
			if(fragment instanceof CustomTransitionsFragment ctf)
				anim=ctf.onCreateExitTransition(prevWrap, wrap);
			else
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
		if(!fragmentContainers.isEmpty()){
			Fragment currentFragment=getFragmentManager().findFragmentById(fragmentContainers.get(fragmentContainers.size()-1).getId());
			if(currentFragment instanceof OnBackPressedListener && ((OnBackPressedListener) currentFragment).onBackPressed())
				return;
			if(fragmentContainers.size()>1){
				removeFragment(currentFragment, true);
				return;
			}
		}
		super.onBackPressed();
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
		anim.playTogether(
				ObjectAnimator.ofFloat(container, View.TRANSLATION_X, V.dp(100)),
				ObjectAnimator.ofFloat(container, View.ALPHA, 0)
		);
		anim.setDuration(200);
		anim.setInterpolator(CubicBezierInterpolator.DEFAULT);
		return anim;
	}

	protected CharSequence getTitleForFragment(Fragment fragment){
		if(fragment instanceof AppKitFragment){
			return ((AppKitFragment) fragment).getTitle();
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

	public int generateViewId(){
		int r=nextViewID;
		nextViewID++;
		if(nextViewID>0x00FFFFFF){
			nextViewID=1;
		}
		return r;
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

	private class FragmentStackContainer extends FrameLayout{

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
	}
}
