package me.grishka.appkit.example.fragments;

import android.app.Activity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import me.grishka.appkit.Nav;
import me.grishka.appkit.example.R;
import me.grishka.appkit.fragments.ToolbarFragment;

public class TestSimpleFragment extends ToolbarFragment{

	@Override
	public void onAttach(Activity activity){
		super.onAttach(activity);
		setTitle("Toolbar fragment");
	}

	@Override
	public View onCreateContentView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState){
		View view=inflater.inflate(R.layout.simple_fragment, container, false);

		view.findViewById(R.id.stack).setOnClickListener(this::onClick);
		view.findViewById(R.id.loader).setOnClickListener(this::onClick);
		view.findViewById(R.id.spinner).setOnClickListener(this::onClick);
		view.findViewById(R.id.gif).setOnClickListener(this::onClick);
		view.findViewById(R.id.img_loader).setOnClickListener(this::onClick);

		return view;
	}

	private void onClick(View v){
		switch(v.getId()){
			case R.id.stack -> {
				Bundle args=new Bundle();
				args.putInt("index", 1);
				Nav.go(getActivity(), StackTestFragment.class, args);
			}
			case R.id.loader -> Nav.go(getActivity(), ExampleLoaderFragment.class, new Bundle());
			case R.id.spinner -> Nav.go(getActivity(), SpinnerNavigationFragment.class, new Bundle());
			case R.id.gif -> Nav.go(getActivity(), AnimatedGifExampleFragment.class, new Bundle());
			case R.id.img_loader -> Nav.go(getActivity(), ListImageLoaderUpdateTestFragment.class, new Bundle());
		}
	}
}
