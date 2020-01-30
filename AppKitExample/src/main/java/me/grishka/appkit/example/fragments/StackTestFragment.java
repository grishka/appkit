package me.grishka.appkit.example.fragments;

import android.app.Activity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import me.grishka.appkit.Nav;
import me.grishka.appkit.example.R;
import me.grishka.appkit.fragments.ToolbarFragment;

public class StackTestFragment extends ToolbarFragment{

	private int index;

	@Override
	public void onAttach(Activity activity){
		super.onAttach(activity);
		setTitle("Fragment stack");
		index=getArguments().getInt("index");
	}

	@Override
	public View onCreateContentView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState){
		View view=inflater.inflate(R.layout.stack_test_fragment, container, false);

		TextView text=view.findViewById(R.id.text);
		Button btn=view.findViewById(R.id.start_fragment);

		text.setText("Fragment "+index);
		btn.setOnClickListener(new View.OnClickListener(){
			@Override
			public void onClick(View view){
				Bundle args=new Bundle();
				args.putInt("index", index+1);
				Nav.go(getActivity(), StackTestFragment.class, args);
			}
		});

		return view;
	}
}
