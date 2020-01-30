package me.grishka.appkit.fragments;

import android.view.WindowInsets;

public interface WindowInsetsAwareFragment{
	public void onApplyWindowInsets(WindowInsets insets);
	public boolean wantsLightStatusBar();
	public boolean wantsLightNavigationBar();
}
