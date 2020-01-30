package me.grishka.appkit.fragments;

import android.app.ActionBar;
import android.app.Activity;
import android.app.DialogFragment;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.Adapter;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;
import android.widget.TextView;
import android.widget.Toolbar;

import java.util.List;

import me.grishka.appkit.R;
import me.grishka.appkit.utils.StubListAdapter;
import me.grishka.appkit.utils.V;
import me.grishka.appkit.views.FragmentRootLinearLayout;

/**
 * Base class for all your fragments.
 */
public class AppKitFragment extends DialogFragment implements WindowInsetsAwareFragment {
	private boolean viewCreated;
	private CharSequence title, subtitle;
	private Toolbar toolbar;
	protected boolean hasOptionsMenu;
	private Spinner navigationSpinner;
	protected boolean isTablet;
	protected int scrW;
	private boolean titleMarquee=true, subtitleMarquee=true;
	private FragmentResultCallback resultCallback;
	private View rootView;
	private TextView toolbarTitleView, toolbarSubtitleView;
	private boolean ignoreSpinnerSelection;

	/**
	 * If your fragment is used as a child in TabbedFragment, this will be in the arguments.
	 * Toolbar, if present, will be removed automatically.
	 */
	public static final String EXTRA_IS_TAB="__is_tab";

	public boolean hasNavigationDrawer(){
		return false;
	}

	public void onToolbarNavigationClick(){
		getActivity().onBackPressed();
	}

	protected boolean canGoBack(){
		return getArguments()!=null && getArguments().getBoolean("_can_go_back");
	}

	public void setTitleMarqueeEnabled(boolean enabled){
		titleMarquee=enabled;
		updateToolbarMarquee();
	}

	public void setSubtitleMarqueeEnabled(boolean enabled){
		subtitleMarquee=enabled;
		updateToolbarMarquee();
	}

	public boolean isTitleMarqueeEnabled(boolean enabled){
		return titleMarquee;
	}

	public boolean isSubitleMarqueeEnabled(boolean enabled){
		return subtitleMarquee;
	}

	private void updateToolbarMarquee(){
		if(toolbar==null){
			return;
		}
		if(toolbarTitleView!=null){
			toolbarTitleView.setFadingEdgeLength(V.dp(10));
			toolbarTitleView.setHorizontalFadingEdgeEnabled(true);
			toolbarTitleView.setMarqueeRepeatLimit(2);
			if(titleMarquee){
				toolbarTitleView.setEllipsize(TextUtils.TruncateAt.MARQUEE);
				toolbarTitleView.setSelected(true);
			}else{
				toolbarTitleView.setSelected(false);
				toolbarTitleView.setEllipsize(TextUtils.TruncateAt.END);
			}
		}
		if(toolbarSubtitleView!=null){
			toolbarSubtitleView.setFadingEdgeLength(V.dp(10));
			toolbarSubtitleView.setHorizontalFadingEdgeEnabled(true);
			toolbarSubtitleView.setMarqueeRepeatLimit(2);
			if(subtitleMarquee){
				toolbarSubtitleView.setEllipsize(TextUtils.TruncateAt.MARQUEE);
				toolbarSubtitleView.setSelected(true);
			}else{
				toolbarSubtitleView.setSelected(false);
				toolbarSubtitleView.setEllipsize(TextUtils.TruncateAt.END);
			}
		}
	}

	private void initToolbar(){
		toolbar.setTitle("[title]");
		toolbar.setSubtitle("[subtitle]");
		for(int i=0;i<toolbar.getChildCount();i++){
			View child=toolbar.getChildAt(i);
			if(child instanceof TextView){
				TextView textView=(TextView) child;
				String val=textView.getText().toString();
				if("[title]".equals(val)){
					toolbarTitleView=textView;
				}else if("[subtitle]".equals(val)){
					toolbarSubtitleView=textView;
				}
			}
		}

		if(title!=null)
			toolbar.setTitle(title);
		else
			toolbar.setTitle(null);
		if(subtitle!=null)
			toolbar.setSubtitle(subtitle);
		else
			toolbar.setSubtitle(null);
		if(hasOptionsMenu){
			invalidateToolbarMenu();
			toolbar.setOnMenuItemClickListener(new Toolbar.OnMenuItemClickListener() {
				@Override
				public boolean onMenuItemClick(MenuItem item) {
					return onOptionsItemSelected(item);
				}
			});
		}
		if(canGoBack()){
			int[] attrs={R.attr.appkitBackDrawable, android.R.attr.textColorSecondary};
			TypedArray ta=toolbar.getContext().obtainStyledAttributes(attrs);
			Drawable d=ta.getDrawable(0);
			int tintColor=ta.getColor(1, 0xFF000000);
			ta.recycle();
			if(d==null)
				d=getResources().getDrawable(R.drawable.ic_arrow_back);
			d=d.mutate();
			d.setTint(tintColor);
			toolbar.setNavigationIcon(d);
		}else if(hasNavigationDrawer()){
			Drawable d=getResources().getDrawable(R.drawable.ic_menu).mutate();
			TypedArray ta=toolbar.getContext().obtainStyledAttributes(new int[]{android.R.attr.textColorSecondary});
			d.setTint(ta.getColor(0, 0xFF000000));
			ta.recycle();
			toolbar.setNavigationIcon(d);
		}
		toolbar.setNavigationOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				onToolbarNavigationClick();
			}
		});
		if(navigationSpinner!=null){
			toolbar.addView(navigationSpinner, new Toolbar.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
			toolbar.setTitle(null);
			toolbar.setSubtitle(null);
		}
	}

	@Override
	public void onViewCreated(View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		rootView=view;

		TypedArray ta=rootView.getContext().obtainStyledAttributes(new int[]{android.R.attr.statusBarColor, android.R.attr.navigationBarColor});
		setStatusBarColor(ta.getColor(0, 0xFF000000));
		setNavigationBarColor(ta.getColor(1, 0xFF000000));
		ta.recycle();

		toolbar=(Toolbar)view.findViewById(R.id.toolbar);
		if(toolbar!=null && getArguments()!=null && getArguments().getBoolean(EXTRA_IS_TAB)){
			((ViewGroup)toolbar.getParent()).removeView(toolbar);
			toolbar=null;
		}
		viewCreated=true;
		if(toolbar!=null){
			initToolbar();
		}else{
			if(title!=null){
				if(getArguments()==null || !getArguments().getBoolean("_dialog"))
					getActivity().setTitle(title);
			}
			if(getActivity().getActionBar()!=null && (getArguments()==null || !getArguments().getBoolean("_dialog"))) {
				if(title!=null){
					if(getActivity().getActionBar().getNavigationMode()!=ActionBar.NAVIGATION_MODE_STANDARD) {
						getActivity().getActionBar().setListNavigationCallbacks(StubListAdapter.getInstance(), null);
						getActivity().getActionBar().setDisplayShowTitleEnabled(true);
					}
					getActivity().getActionBar().setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
				}
				if (subtitle != null) {
					getActivity().getActionBar().setSubtitle(subtitle);
				}
			}
		}
		updateToolbarMarquee();
	}

	@Override
	public void onDestroyView(){
		super.onDestroyView();
		navigationSpinner=null;
		toolbar=null;
	}

	@Override
	public void setHasOptionsMenu(boolean hasMenu) {
		super.setHasOptionsMenu(hasMenu);
		hasOptionsMenu=hasMenu;
		invalidateOptionsMenu();
	}

	public boolean hasOptionsMenu(){
		return hasOptionsMenu;
	}

	public void invalidateOptionsMenu(){
		if(toolbar!=null){
			invalidateToolbarMenu();
		}else if(getActivity()!=null){
			getActivity().invalidateOptionsMenu();
		}
	}

	private void invalidateToolbarMenu(){
		toolbar.getMenu().clear();
		if(hasOptionsMenu){
			onCreateOptionsMenu(toolbar.getMenu(), new MenuInflater(getActivity()));
		}
	}

	protected Toolbar getToolbar(){
		return toolbar;
	}

	@Override
	public void onAttach(Activity activity){
		super.onAttach(activity);
		V.setApplicationContext(activity);
		updateConfiguration();
	}


	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		updateConfiguration();
		if(toolbar!=null){
			ViewGroup parent=(ViewGroup) toolbar.getParent();
			int index=parent.indexOfChild(toolbar);
			parent.removeView(toolbar);
			if(navigationSpinner!=null){
				toolbar.removeView(navigationSpinner);
				ignoreSpinnerSelection=true;
				int selectedItem=navigationSpinner.getSelectedItemPosition();
				SpinnerAdapter adapter=navigationSpinner.getAdapter();
				navigationSpinner.setAdapter(null);
				navigationSpinner.setAdapter(adapter);
				navigationSpinner.setSelection(selectedItem);
				ignoreSpinnerSelection=false;
			}
			toolbar=(Toolbar)LayoutInflater.from(getActivity()).inflate(R.layout.appkit_toolbar, parent, false);
			parent.addView(toolbar, index);
			initToolbar();
			updateToolbarMarquee();
		}
	}


	private void updateConfiguration() {
		scrW = getResources().getConfiguration().screenWidthDp;
		isTablet = scrW >= 924;
	}


	protected void setTitle(CharSequence title){
		this.title=title;
		if(navigationSpinner!=null)
			return;
		if(toolbar!=null) {
			toolbar.setTitle(title);
			updateToolbarMarquee();
		}else if(getActivity()!=null && viewCreated){
			if(getArguments()==null || !getArguments().getBoolean("_dialog"))
				getActivity().setTitle(title);
		}
	}

	protected void setTitle(int res){
		setTitle(getString(res));
	}

	protected void setSubtitle(CharSequence subtitle){
		this.subtitle=subtitle;
		if(navigationSpinner!=null)
			return;
		if(toolbar!=null){
			toolbar.setSubtitle(subtitle);
			updateToolbarMarquee();
		}else if(viewCreated && getActivity().getActionBar()!=null){
			getActivity().getActionBar().setSubtitle(subtitle);
		}
	}

	protected void setSubtitle(int res){
		setSubtitle(getString(res));
	}

	protected ArrayAdapter onCreateNavigationSpinnerAdapter(){
		return new NavigationSpinnerAdapter(getActivity());
	}

	protected void setSpinnerItems(List<?> items){
		if(items==null){
			setSpinnerAdapter(null);
			return;
		}
		ArrayAdapter adapter = onCreateNavigationSpinnerAdapter();
		adapter.addAll(items);
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		setSpinnerAdapter(adapter);
	}

	protected Spinner onCreateNavigationSpinner(LayoutInflater inflater){
		return (Spinner) inflater.inflate(R.layout.appkit_navigation_spinner, null);
	}

	protected void setSpinnerAdapter(SpinnerAdapter adapter){
		if(adapter==null){
			if(navigationSpinner!=null){
				toolbar.removeView(navigationSpinner);
				navigationSpinner=null;
			}
			return;
		}
		if(navigationSpinner==null){
			navigationSpinner=onCreateNavigationSpinner(getActivity().getLayoutInflater());
			navigationSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
				@Override
				public void onItemSelected(AdapterView<?> adapterView, View view, int pos, long id) {
					if(!ignoreSpinnerSelection)
						onSpinnerItemSelected(pos);
				}

				@Override
				public void onNothingSelected(AdapterView<?> adapterView) {

				}
			});
			if(toolbar!=null){
				toolbar.addView(navigationSpinner, new Toolbar.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
				toolbar.setTitle(null);
				toolbar.setSubtitle(null);
			}
		}
		navigationSpinner.setAdapter(adapter);
	}

	protected Context getToolbarContext(){
		TypedArray ta=getActivity().getTheme().obtainStyledAttributes(new int[]{android.R.attr.actionBarTheme});
		int themeID=ta.getResourceId(0, 0);
		ta.recycle();
		if(themeID==0){
			return getActivity();
		}
		return new ContextThemeWrapper(getActivity(), themeID);
	}

	protected Context getToolbarPopupContext(){
		TypedArray ta=getActivity().getTheme().obtainStyledAttributes(new int[]{android.R.attr.actionBarPopupTheme});
		int themeID=ta.getResourceId(0, 0);
		ta.recycle();
		if(themeID==0){
			return getActivity();
		}
		return new ContextThemeWrapper(getActivity(), themeID);
	}

	/**
	 * Override this to get notified when the user selects an item in the toolbar spinner.
	 * @param position The position of the selected item
	 * @return True if the event is handled and the item should remain selected, false otherwise
	 */
	protected boolean onSpinnerItemSelected(int position){
		return false;
	}

	protected void setSelectedNavigationItem(int position){
		navigationSpinner.setSelection(position);
	}

	protected int getSelectedNavigationItem(){
		if(navigationSpinner==null)
			return -1;
		return navigationSpinner.getSelectedItemPosition();
	}

	public void setResultCallback(FragmentResultCallback resultCallback){
		this.resultCallback=resultCallback;
	}

	protected void setResult(boolean success, Bundle result){
		if(resultCallback!=null)
			resultCallback.onFragmentResult(success, result);
	}

	public void onFragmentResult(int reqCode, boolean success, Bundle result){

	}

	public void onTransitionFinished(){}

	@Override
	public void onApplyWindowInsets(WindowInsets insets){
		if(rootView!=null)
			rootView.dispatchApplyWindowInsets(insets);
	}

	@Override
	public boolean wantsLightStatusBar(){
		if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.M && rootView!=null){
			TypedArray ta=rootView.getContext().obtainStyledAttributes(new int[]{android.R.attr.windowLightStatusBar});
			boolean light=ta.getBoolean(0, false);
			ta.recycle();
			return light;
		}
		return false;
	}

	@Override
	public boolean wantsLightNavigationBar(){
		if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.O_MR1 && rootView!=null){
			TypedArray ta=rootView.getContext().obtainStyledAttributes(new int[]{android.R.attr.windowLightNavigationBar});
			boolean light=ta.getBoolean(0, false);
			ta.recycle();
			return light;
		}
		return false;
	}

	protected void setStatusBarColor(int color){
		if(rootView instanceof FragmentRootLinearLayout)
			((FragmentRootLinearLayout) rootView).setStatusBarColor(color);
	}

	protected void setNavigationBarColor(int color){
		if(rootView instanceof FragmentRootLinearLayout)
			((FragmentRootLinearLayout) rootView).setNavigationBarColor(color);
	}

	protected class NavigationSpinnerAdapter extends ArrayAdapter {

		public NavigationSpinnerAdapter(Context context) {
			super(context, R.layout.appkit_spinner_view, android.R.id.text1);
			setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		}

		@Override
		public View getDropDownView(int position, View convertView, ViewGroup parent) {
			View view=super.getDropDownView(position, convertView, parent);
			if(convertView==null){
				// WTF doesn't it work via XML?
				TypedArray ta=getActivity().getTheme().obtainStyledAttributes(new int[]{android.R.attr.colorAccent, android.R.attr.colorForeground});
				int colorAccent=ta.getColor(0, 0xFF000000);
				int colorForeground=ta.getColor(1, 0xFF000000);
				ta.recycle();
				ColorStateList csl=new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}}, new int[]{colorAccent, colorForeground});
				((TextView) view).setTextColor(csl);
			}
			return view;
		}
	}
}
