package me.grishka.appkit.example.fragments;

import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

import me.grishka.appkit.fragments.ToolbarFragment;
import me.grishka.appkit.imageloader.ViewImageLoader;
import me.grishka.appkit.imageloader.requests.UrlImageLoaderRequest;
import me.grishka.appkit.utils.V;

public class AnimatedGifExampleFragment extends ToolbarFragment{
	@Override
	public View onCreateContentView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState){
		FrameLayout fl=new FrameLayout(getActivity());
		ImageView img=new ImageView(getActivity());
		fl.addView(img, new FrameLayout.LayoutParams(V.dp(250), V.dp(250), Gravity.CENTER));
		ViewImageLoader.load(img, new ColorDrawable(0xff808080), new UrlImageLoaderRequest("https://media.giphy.com/media/EBJQRG6M99zSNhnhsW/giphy.gif"));
		return fl;
	}
}
