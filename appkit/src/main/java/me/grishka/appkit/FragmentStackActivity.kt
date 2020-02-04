package me.grishka.appkit

import android.app.Activity
import android.app.Fragment
import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowInsets
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import me.grishka.appkit.fragments.AppKitFragment
import me.grishka.appkit.fragments.WindowInsetsAwareFragment
import me.grishka.appkit.utils.CubicBezierInterpolator
import me.grishka.appkit.utils.V
import java.util.*

open class FragmentStackActivity : Activity() {
    protected lateinit var content: FrameLayout
    protected var fragmentContainers = ArrayList<FrameLayout>()
    protected var lastInsets: WindowInsets? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        content = object : FrameLayout(this) {
            override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
                lastInsets = WindowInsets(insets)
                val mgr = fragmentManager
                for (i in 0 until childCount) {
                    val child = getChildAt(i)
                    val fragment = mgr.findFragmentById(child.id)
                    if (fragment is WindowInsetsAwareFragment) {
                        (fragment as WindowInsetsAwareFragment).onApplyWindowInsets(WindowInsets(insets))
                    }
                }
                return insets.consumeSystemWindowInsets()
            }
        }
        content.id = R.id.fragment_wrap
        content.fitsSystemWindows = true
        window.decorView.systemUiVisibility = window.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        setContentView(content)

        window.setBackgroundDrawable(ColorDrawable(0))
        window.statusBarColor = 0
        window.navigationBarColor = 0

        if (savedInstanceState != null) {
            val ids = savedInstanceState.getIntArray("appkit:fragmentContainerIDs")
            if (ids!!.size > 0) {
                val last = ids[ids.size - 1]
                for (id in ids) {
                    val wrap = FrameLayout(this)
                    wrap.id = id
                    if (id != last)
                        wrap.visibility = View.GONE
                    content.addView(wrap)
                    fragmentContainers.add(wrap)
                }
            }
        }

        super.onCreate(savedInstanceState)
    }

    private fun applySystemBarColors(lightStatus: Boolean, lightNav: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            var flags = window.decorView.systemUiVisibility
            val origFlags = flags
            if (lightStatus)
                flags = flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            else
                flags = flags and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                if (lightNav)
                    flags = flags or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
                else
                    flags = flags and View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()
            }
            if (flags != origFlags) {
                window.decorView.systemUiVisibility = flags
            }
        }
    }

    fun showFragment(fragment: Fragment) {
        val wrap = FrameLayout(this)
        wrap.id = View.generateViewId()
        content.addView(wrap)
        fragmentContainers.add(wrap)
        fragmentManager.beginTransaction().add(wrap.id, fragment, "stackedFragment_" + wrap.id).commit()
        fragmentManager.executePendingTransactions()
        val lightStatus: Boolean
        val lightNav: Boolean
        if (fragment is WindowInsetsAwareFragment) {
            if (lastInsets != null)
                (fragment as WindowInsetsAwareFragment).onApplyWindowInsets(WindowInsets(lastInsets))
            lightStatus = (fragment as WindowInsetsAwareFragment).wantsLightStatusBar()
            lightNav = (fragment as WindowInsetsAwareFragment).wantsLightNavigationBar()
        } else {
            lightNav = false
            lightStatus = lightNav
        }
        if (fragmentContainers.size > 1) {
            wrap.alpha = 0f
            wrap.translationX = V.dp(100f).toFloat()
            wrap.animate().translationX(0f).alpha(1f).setDuration(300).setInterpolator(CubicBezierInterpolator.DEFAULT).withEndAction {
                for (i in 0 until fragmentContainers.size - 1) {
                    fragmentContainers[i].visibility = View.GONE
                }
                if (fragment is AppKitFragment)
                    fragment.onTransitionFinished()
            }.start()
            wrap.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
                private var prevAlpha = wrap.alpha
                override fun onPreDraw(): Boolean {
                    val alpha = wrap.alpha
                    if (prevAlpha > alpha) {
                        wrap.viewTreeObserver.removeOnPreDrawListener(this)
                        return true
                    }
                    if (alpha >= 0.5f) {
                        wrap.viewTreeObserver.removeOnPreDrawListener(this)
                        applySystemBarColors(lightStatus, lightNav)
                    }
                    prevAlpha = alpha
                    return true
                }
            })
        } else {
            applySystemBarColors(lightStatus, lightNav)
        }
    }

    fun showFragmentClearingBackStack(fragment: Fragment) {
        val transaction = fragmentManager.beginTransaction()
        for (fl in fragmentContainers) {
            transaction.remove(fragmentManager.findFragmentById(fl.id))
        }
        transaction.commit()
        fragmentManager.executePendingTransactions()
        fragmentContainers.clear()
        content.removeAllViews()
        showFragment(fragment)
    }

    override fun onBackPressed() {
        if (fragmentContainers.size > 1) {
            val wrap = fragmentContainers.removeAt(fragmentContainers.size - 1)
            val fragment = fragmentManager.findFragmentById(wrap.id)
            val prevWrap = fragmentContainers[fragmentContainers.size - 1]
            prevWrap.visibility = View.VISIBLE
            val prevFragment = fragmentManager.findFragmentById(prevWrap.id)
            val lightStatus: Boolean
            val lightNav: Boolean
            if (prevFragment is WindowInsetsAwareFragment) {
                (prevFragment as WindowInsetsAwareFragment).onApplyWindowInsets(WindowInsets(lastInsets))
                lightStatus = (prevFragment as WindowInsetsAwareFragment).wantsLightStatusBar()
                lightNav = (prevFragment as WindowInsetsAwareFragment).wantsLightNavigationBar()
            } else {
                lightNav = false
                lightStatus = lightNav
            }
            wrap.animate().translationX(V.dp(100f).toFloat()).alpha(0f).setDuration(200).setInterpolator(CubicBezierInterpolator.DEFAULT).withEndAction {
                fragmentManager.beginTransaction().remove(fragment).commit()
                fragmentManager.executePendingTransactions()
                content.removeView(wrap)
            }.start()
            wrap.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
                private var prevAlpha = wrap.alpha
                override fun onPreDraw(): Boolean {
                    val alpha = wrap.alpha
                    if (prevAlpha < alpha) {
                        wrap.viewTreeObserver.removeOnPreDrawListener(this)
                        return true
                    }
                    if (alpha <= 0.5f) {
                        wrap.viewTreeObserver.removeOnPreDrawListener(this)
                        applySystemBarColors(lightStatus, lightNav)
                    }
                    prevAlpha = alpha
                    return true
                }
            })
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(window.decorView.windowToken, 0)
            return
        }
        super.onBackPressed()
    }

    protected fun reapplyWindowInsets() {
        val mgr = fragmentManager
        for (i in 0 until content.childCount) {
            val child = content.getChildAt(i)
            val fragment = mgr.findFragmentById(child.id)
            if (fragment is WindowInsetsAwareFragment) {
                (fragment as WindowInsetsAwareFragment).onApplyWindowInsets(WindowInsets(lastInsets))
            }
        }
    }

    fun invalidateSystemBarColors(fragment: WindowInsetsAwareFragment) {
        if (fragmentManager.findFragmentById(fragmentContainers[fragmentContainers.size - 1].id) === fragment) {
            content.post { applySystemBarColors(fragment.wantsLightStatusBar(), fragment.wantsLightNavigationBar()) }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val ids = IntArray(fragmentContainers.size)
        for (i in fragmentContainers.indices) {
            ids[i] = fragmentContainers[i].id
        }
        outState.putIntArray("appkit:fragmentContainerIDs", ids)
    }
}
