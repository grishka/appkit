package me.grishka.appkit.example.fragments;

import android.app.Activity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

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

		Button stack=view.findViewById(R.id.stack);
		Button loader=view.findViewById(R.id.loader);
		Button spinner=view.findViewById(R.id.spinner);

		stack.setOnClickListener(new View.OnClickListener(){
			@Override
			public void onClick(View view){
				Bundle args=new Bundle();
				args.putInt("index", 1);
				Nav.INSTANCE.go(getActivity(), StackTestFragment.class, args);
			}
		});

		loader.setOnClickListener(new View.OnClickListener(){
			@Override
			public void onClick(View view){
				Nav.INSTANCE.go(getActivity(), ExampleLoaderFragment.class, new Bundle());
			}
		});

		spinner.setOnClickListener(new View.OnClickListener(){
			@Override
			public void onClick(View view){
				Nav.INSTANCE.go(getActivity(), SpinnerNavigationFragment.class, new Bundle());
			}
		});

		return view;
	}
}
