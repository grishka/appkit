package me.grishka.appkit.views;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

public class RoundedDrawable extends Drawable {

	private final Matrix mShaderMatrix = new Matrix();
	private final RectF mBitmapRect = new RectF();
	private Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
	private RectF mBorderRect = new RectF();
	private int mRadius = -1;
	private int mInnerRadius = -1;
	private final BitmapShader mShader;
	private boolean mOnBoundsChangeCalled = false;
	private Bitmap bitmap;

	public RoundedDrawable(Bitmap bitmap) {
		mBitmapRect.set(0, 0, bitmap.getWidth(), bitmap.getHeight());
		mShader = new BitmapShader(bitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);

		mShader.setLocalMatrix(mShaderMatrix);
		mPaint.setShader(mShader);
		this.bitmap=bitmap;
	}

	@Override
	protected void onBoundsChange(Rect bounds) {
		super.onBoundsChange(bounds);
		mBorderRect.set(bounds.left, bounds.top, bounds.right, bounds.bottom);
		mInnerRadius = mRadius == -1 ? ((int) mBorderRect.width()) >> 1 : mRadius;

		mShaderMatrix.reset();
		mShaderMatrix.setRectToRect(mBitmapRect, mBorderRect, Matrix.ScaleToFit.CENTER);
		mShader.setLocalMatrix(mShaderMatrix);
		invalidateSelf();
		mOnBoundsChangeCalled = true;
	}

	@Override
	public void setBounds(Rect bounds) {
		super.setBounds(bounds);
		if(!mOnBoundsChangeCalled) {
			onBoundsChange(bounds);
			mOnBoundsChangeCalled = false;
		}
	}

	@Override
	public int getIntrinsicWidth() {
		return (int) mBitmapRect.width();
	}

	@Override
	public int getIntrinsicHeight() {
		return (int) mBitmapRect.height();
	}

	@Override
	public void draw(Canvas canvas) {
		canvas.drawRoundRect(mBorderRect, mInnerRadius, mInnerRadius, mPaint);
	}

	@Override
	public void setAlpha(int alpha) {
		mPaint.setAlpha(alpha);
	}

	@Override
	public void setColorFilter(ColorFilter cf) {
		mPaint.setColorFilter(cf);
	}

	@Override
	public int getOpacity() {
		return mPaint.getAlpha();
	}

	public RoundedDrawable setRadius(int radius) {
		if(radius >= 0) {
			mRadius = radius;
			mInnerRadius = radius;
			invalidateSelf();
		}
		return this;
	}

	public Bitmap getBitmap(){
		return bitmap;
	}
}