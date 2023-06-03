package me.grishka.appkit.fragments;

import android.animation.Animator;
import android.view.View;

public interface CustomTransitionsFragment{
	Animator onCreateEnterTransition(View prev, View container);
	Animator onCreateExitTransition(View prev, View container);
}
