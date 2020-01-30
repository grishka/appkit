package me.grishka.appkit;

import android.app.Activity;
import android.app.Fragment;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import me.grishka.appkit.fragments.AppKitFragment;
import me.grishka.appkit.fragments.FragmentResultCallback;

public class Nav{

	public static void go(Activity activity, Class<? extends Fragment> fragmentClass, Bundle extras){
		try{
			Fragment fragment=fragmentClass.newInstance();
			if(extras==null)
				extras=new Bundle();
			extras.putBoolean("_can_go_back", true);
			fragment.setArguments(extras);
			if(activity instanceof FragmentStackActivity){
				((FragmentStackActivity) activity).showFragment(fragment);
			}
		}catch(Exception x){
			Log.w("Nav", x);
			Toast.makeText(activity, "Error navigating to "+fragmentClass.getName(), Toast.LENGTH_LONG).show();
		}
	}

	public static void goClearingStack(Activity activity, Class<? extends Fragment> fragmentClass, Bundle extras){
		try{
			Fragment fragment=fragmentClass.newInstance();
			if(extras==null)
				extras=new Bundle();
			fragment.setArguments(extras);
			if(activity instanceof FragmentStackActivity){
				((FragmentStackActivity) activity).showFragmentClearingBackStack(fragment);
			}
		}catch(Exception x){
			Log.w("Nav", x);
			Toast.makeText(activity, "Error navigating to "+fragmentClass.getName(), Toast.LENGTH_LONG).show();
		}
	}

	public static void goForResult(Activity activity, Class<? extends AppKitFragment> fragmentClass, Bundle extras, final int reqCode, final AppKitFragment receiver){
		try{
			AppKitFragment fragment=fragmentClass.newInstance();
			if(extras==null)
				extras=new Bundle();
			extras.putBoolean("_can_go_back", true);
			fragment.setArguments(extras);
			fragment.setResultCallback(new FragmentResultCallback(){
				@Override
				public void onFragmentResult(boolean success, Bundle result){
					receiver.onFragmentResult(reqCode, success, result);
				}
			});
			if(activity instanceof FragmentStackActivity){
				((FragmentStackActivity) activity).showFragment(fragment);
			}
		}catch(Exception x){
			Log.w("Nav", x);
			Toast.makeText(activity, "Error navigating to "+fragmentClass.getName(), Toast.LENGTH_LONG).show();
		}
	}

	public static void finish(Fragment fragment){
		fragment.getActivity().onBackPressed();
	}
}
