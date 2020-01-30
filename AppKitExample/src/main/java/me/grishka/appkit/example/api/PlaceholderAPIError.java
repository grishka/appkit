package me.grishka.appkit.example.api;

import android.content.Context;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import me.grishka.appkit.api.ErrorResponse;
import me.grishka.appkit.example.R;

public class PlaceholderAPIError extends ErrorResponse{

	private String description;

	public PlaceholderAPIError(String description){
		this.description=description;
	}

	@Override
	public void bindErrorView(View view){
		TextView text=view.findViewById(R.id.error_text);
		text.setText(description);
	}

	@Override
	public void showToast(Context context){
		Toast.makeText(context, description, Toast.LENGTH_SHORT).show();
	}
}
