package me.grishka.appkit.example.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.Arrays;

import me.grishka.appkit.example.R;
import me.grishka.appkit.fragments.ToolbarFragment;

public class SpinnerNavigationFragment extends ToolbarFragment{

	private TextView text;

	@Override
	public void onCreate(Bundle savedInstanceState){
		super.onCreate(savedInstanceState);
	}

	@Override
	public View onCreateContentView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState){
		View view=inflater.inflate(R.layout.big_text_view, container, false);
		text=view.findViewById(R.id.text);
		return view;
	}

	@Override
	public void onViewCreated(View view, Bundle savedInstanceState){
		super.onViewCreated(view, savedInstanceState);
		setSpinnerItems(Arrays.asList("Spinner", "Navigation", "Items", "Wow"));
	}

	@Override
	protected boolean onSpinnerItemSelected(int position){
		text.setText("Item: "+position);
		return true;
	}
}
