package me.grishka.appkit.imageloader;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Movie;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class MovieDrawable extends Drawable implements Animatable{
	private final Movie movie;
	private boolean running;
	private long startTime;

	// Movie doesn't support drawing to a hardware-accelerated canvas, so we have to do this...
	private Bitmap bitmap;
	private Canvas bitmapCanvas;
	private Paint paint=new Paint(Paint.FILTER_BITMAP_FLAG);

	public MovieDrawable(Movie movie){
		this.movie=movie;
		bitmap=Bitmap.createBitmap(movie.width(), movie.height(), Bitmap.Config.ARGB_8888);
		bitmapCanvas=new Canvas(bitmap);
	}

	@Override
	public void start(){
		if(running)
			return;
		startTime=SystemClock.uptimeMillis();
		running=true;
		invalidateSelf();
	}

	@Override
	public void stop(){
		running=false;
	}

	@Override
	public boolean isRunning(){
		return running;
	}

	@Override
	public void draw(@NonNull Canvas canvas){
		if(running){
			int duration=movie.duration();
			if(duration>0)
				movie.setTime((int)(SystemClock.uptimeMillis()-startTime)%duration);
			bitmapCanvas.drawColor(0xFFFFFFFF, PorterDuff.Mode.CLEAR); // needed to support transparent gifs
			movie.draw(bitmapCanvas, 0, 0);
			invalidateSelf();
		}
		canvas.drawBitmap(bitmap, null, getBounds(), paint);
	}

	@Override
	public void setAlpha(int alpha){

	}

	@Override
	public void setColorFilter(@Nullable ColorFilter colorFilter){

	}

	@Override
	public int getOpacity(){
		return PixelFormat.TRANSPARENT;
	}

	@Override
	public int getIntrinsicWidth(){
		return movie.width();
	}

	@Override
	public int getIntrinsicHeight(){
		return movie.height();
	}
}
