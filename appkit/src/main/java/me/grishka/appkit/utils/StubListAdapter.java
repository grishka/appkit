package me.grishka.appkit.utils;

import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

/**
 * Created by grishka on 09.03.15.
 */
public class StubListAdapter extends BaseAdapter{
	private static StubListAdapter instance=new StubListAdapter();

	public static StubListAdapter getInstance(){
		return instance;
	}

	@Override
	public int getCount() {
		return 0;
	}

	@Override
	public Object getItem(int position) {
		return null;
	}

	@Override
	public long getItemId(int position) {
		return 0;
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		return null;
	}
}