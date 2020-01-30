package me.grishka.appkit;

import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.res.Configuration;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;

import java.util.ArrayList;
import java.util.Arrays;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import me.grishka.appkit.fragments.AppKitFragment;
import me.grishka.appkit.fragments.WindowInsetsAwareFragment;
import me.grishka.appkit.utils.CubicBezierInterpolator;
import me.grishka.appkit.utils.V;

public class FragmentStackActivity extends Activity{
	protected FrameLayout content;
	protected ArrayList<FrameLayout> fragmentContainers=new ArrayList<FrameLayout>();
	protected WindowInsets lastInsets;

	@Override
	protected void onCreate(@Nullable Bundle savedInstanceState){
		content=new FrameLayout(this){
			@Override
			public WindowInsets onApplyWindowInsets(WindowInsets insets){
				lastInsets=new WindowInsets(insets);
				FragmentManager mgr=getFragmentManager();
				for(int i=0;i<getChildCount();i++){
					View child=getChildAt(i);
					Fragment fragment=mgr.findFragmentById(child.getId());
					if(fragment instanceof WindowInsetsAwareFragment){
						((WindowInsetsAwareFragment) fragment).onApplyWindowInsets(new WindowInsets(insets));
					}
				}
				return insets.consumeSystemWindowInsets();
			}
		};
		content.setId(R.id.fragment_wrap);
		content.setFitsSystemWindows(true);
		getWindow().getDecorView().setSystemUiVisibility(getWindow().getDecorView().getSystemUiVisibility() | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
		setContentView(content);

		getWindow().setBackgroundDrawable(new ColorDrawable(0));
		getWindow().setStatusBarColor(0);
		getWindow().setNavigationBarColor(0);

		if(savedInstanceState!=null){
			int[] ids=savedInstanceState.getIntArray("appkit:fragmentContainerIDs");
			if(ids.length>0){
				int last=ids[ids.length-1];
				for(int id : ids){
					FrameLayout wrap=new FrameLayout(this);
					wrap.setId(id);
					if(id!=last)
						wrap.setVisibility(View.GONE);
					content.addView(wrap);
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
		final FrameLayout wrap=new FrameLayout(this);
		wrap.setId(View.generateViewId());
		content.addView(wrap);
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
			wrap.setAlpha(0);
			wrap.setTranslationX(V.dp(100));
			wrap.animate().translationX(0).alpha(1).setDuration(300).setInterpolator(CubicBezierInterpolator.DEFAULT).withEndAction(new Runnable(){
				@Override
				public void run(){
					for(int i=0; i<fragmentContainers.size()-1; i++){
						fragmentContainers.get(i).setVisibility(View.GONE);
					}
					if(fragment instanceof AppKitFragment)
						((AppKitFragment) fragment).onTransitionFinished();
				}
			}).start();
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
			applySystemBarColors(lightStatus, lightNav);
		}
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

	@Override
	public void onBackPressed(){
		if(fragmentContainers.size()>1){
			final FrameLayout wrap=fragmentContainers.remove(fragmentContainers.size()-1);
			final Fragment fragment=getFragmentManager().findFragmentById(wrap.getId());
			FrameLayout prevWrap=fragmentContainers.get(fragmentContainers.size()-1);
			prevWrap.setVisibility(View.VISIBLE);
			Fragment prevFragment=getFragmentManager().findFragmentById(prevWrap.getId());
			final boolean lightStatus, lightNav;
			if(prevFragment instanceof WindowInsetsAwareFragment){
				((WindowInsetsAwareFragment) prevFragment).onApplyWindowInsets(new WindowInsets(lastInsets));
				lightStatus=((WindowInsetsAwareFragment) prevFragment).wantsLightStatusBar();
				lightNav=((WindowInsetsAwareFragment) prevFragment).wantsLightNavigationBar();
			}else{
				lightStatus=lightNav=false;
			}
			wrap.animate().translationX(V.dp(100)).alpha(0).setDuration(200).setInterpolator(CubicBezierInterpolator.DEFAULT).withEndAction(new Runnable(){
				@Override
				public void run(){
					getFragmentManager().beginTransaction().remove(fragment).commit();
					getFragmentManager().executePendingTransactions();
					content.removeView(wrap);
				}
			}).start();
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
			InputMethodManager imm=(InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
			imm.hideSoftInputFromWindow(getWindow().getDecorView().getWindowToken(), 0);
			return;
		}
		super.onBackPressed();
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
		if(getFragmentManager().findFragmentById(fragmentContainers.get(fragmentContainers.size()-1).getId())==fragment){
			content.post(new Runnable(){
				@Override
				public void run(){
					applySystemBarColors(fragment.wantsLightStatusBar(), fragment.wantsLightNavigationBar());
				}
			});
		}
	}

	@Override
	protected void onSaveInstanceState(@NonNull Bundle outState){
		super.onSaveInstanceState(outState);
		int[] ids=new int[fragmentContainers.size()];
		for(int i=0;i<fragmentContainers.size();i++){
			ids[i]=fragmentContainers.get(i).getId();
		}
		outState.putIntArray("appkit:fragmentContainerIDs", ids);
	}
}
