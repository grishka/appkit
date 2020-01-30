package me.grishka.appkit.example;

import android.os.Bundle;

import me.grishka.appkit.FragmentStackActivity;
import me.grishka.appkit.example.api.PlaceholderAPIController;
import me.grishka.appkit.example.fragments.TestSimpleFragment;

public class MainActivity extends FragmentStackActivity{

	static{
		PlaceholderAPIController.getInstance();
	}

	@Override
	protected void onCreate(Bundle savedInstanceState){
		super.onCreate(savedInstanceState);
		if(savedInstanceState==null){
			showFragmentClearingBackStack(new TestSimpleFragment());
		}
	}
}
