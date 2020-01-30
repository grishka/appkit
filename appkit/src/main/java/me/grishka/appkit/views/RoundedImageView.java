package me.grishka.appkit.views;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.widget.ImageView;


public class RoundedImageView extends ImageView {

    private int mRadius = -1;

    public RoundedImageView(Context context) {
        super(context);
    }

    public RoundedImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public RoundedImageView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public RoundedImageView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    public void setImageDrawable(Drawable drawable) {
        if (drawable instanceof BitmapDrawable) {
            super.setImageDrawable(new RoundedDrawable(((BitmapDrawable) drawable).getBitmap()).setRadius(mRadius));
        } else {
            super.setImageDrawable(drawable);
        }
    }

    @Override
    public void setImageResource(int resId) {
        setImageDrawable(getResources().getDrawable(resId));
    }

    @Override
    public void setImageBitmap(Bitmap bm) {
        super.setImageDrawable(new RoundedDrawable(bm).setRadius(mRadius));
    }
}
