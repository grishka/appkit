package me.grishka.appkit.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.LayoutRes;
import me.grishka.appkit.R;

/**
 * A fragment with a toolbar on top of its view (and nothing else).
 */
public abstract class ToolbarFragment extends AppKitFragment {

	public abstract View onCreateContentView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState);
	protected int layoutID;
	protected View content;

	public ToolbarFragment(){
		this(R.layout.appkit_toolbar_fragment);
	}

	protected ToolbarFragment(@LayoutRes int layout){
		layoutID=layout;
	}

	@LayoutRes
	protected int getLayout() {
		return layoutID;
	}

	protected void setLayout(int id){
		if(content!=null)
			throw new IllegalStateException("Can't set layout when view is already created");
		layoutID=id;
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		content=inflater.inflate(layoutID, null);

		ViewGroup contentWrap= (ViewGroup) content.findViewById(R.id.appkit_content);
		contentWrap.addView(onCreateContentView(inflater, container, savedInstanceState));

		return content;
	}

	@Override
	public void onDestroyView() {
		super.onDestroyView();
		content=null;
	}
}
