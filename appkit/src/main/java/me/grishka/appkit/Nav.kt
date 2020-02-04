package me.grishka.appkit

import android.app.Activity
import android.app.Fragment
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import me.grishka.appkit.fragments.AppKitFragment

object Nav {

    fun go(activity: Activity, fragmentClass: Class<out Fragment>, extras: Bundle?) {
        var extras = extras
        try {
            val fragment = fragmentClass.newInstance()
            if (extras == null)
                extras = Bundle()
            extras.putBoolean("_can_go_back", true)
            fragment.arguments = extras
            if (activity is FragmentStackActivity) {
                activity.showFragment(fragment)
            }
        } catch (x: Exception) {
            Log.w("Nav", x)
            Toast.makeText(activity, "Error navigating to " + fragmentClass.name, Toast.LENGTH_LONG).show()
        }

    }

    fun goClearingStack(activity: Activity, fragmentClass: Class<out Fragment>, extras: Bundle?) {
        var extras = extras
        try {
            val fragment = fragmentClass.newInstance()
            if (extras == null)
                extras = Bundle()
            fragment.arguments = extras
            if (activity is FragmentStackActivity) {
                activity.showFragmentClearingBackStack(fragment)
            }
        } catch (x: Exception) {
            Log.w("Nav", x)
            Toast.makeText(activity, "Error navigating to " + fragmentClass.name, Toast.LENGTH_LONG).show()
        }

    }

    fun goForResult(activity: Activity, fragmentClass: Class<out AppKitFragment>, extras: Bundle?, reqCode: Int, receiver: AppKitFragment) {
        var extras = extras
        try {
            val fragment = fragmentClass.newInstance()
            if (extras == null)
                extras = Bundle()
            extras.putBoolean("_can_go_back", true)
            fragment.arguments = extras
            fragment.setResultCallback { success, result -> receiver.onFragmentResult(reqCode, success, result) }
            if (activity is FragmentStackActivity) {
                activity.showFragment(fragment)
            }
        } catch (x: Exception) {
            Log.w("Nav", x)
            Toast.makeText(activity, "Error navigating to " + fragmentClass.name, Toast.LENGTH_LONG).show()
        }

    }

    fun finish(fragment: Fragment) {
        fragment.activity.onBackPressed()
    }
}
