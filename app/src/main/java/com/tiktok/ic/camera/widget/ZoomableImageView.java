package com.tiktok.ic.camera.widget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;

import androidx.appcompat.widget.AppCompatImageView;

public class ZoomableImageView extends AppCompatImageView {
    
    private static final float MIN_SCALE = 0.5f;
    private static final float MAX_SCALE = 2.0f;
    
    private Matrix matrix;
    private float currentScale = 1.0f;
    private PointF lastTouchPoint = new PointF();
    private int mode = NONE;
    private boolean touchEnabled = true;
    
    private static final int NONE = 0;
    private static final int DRAG = 1;
    private static final int ZOOM = 2;
    
    private ScaleGestureDetector scaleGestureDetector;
    
    public void setTouchEnabled(boolean enabled) {
        this.touchEnabled = enabled;
    }
    
    public ZoomableImageView(Context context) {
        super(context);
        init();
    }
    
    public ZoomableImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }
    
    public ZoomableImageView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }
    
    private void init() {
        setScaleType(ScaleType.MATRIX);
        matrix = new Matrix();
        scaleGestureDetector = new ScaleGestureDetector(getContext(), new ScaleListener());
    }
    
    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (changed && getDrawable() != null) {
            centerImage();
        }
    }
    
    private void centerImage() {
        if (getDrawable() == null) return;
        
        float drawableWidth = getDrawable().getIntrinsicWidth();
        float drawableHeight = getDrawable().getIntrinsicHeight();
        float viewWidth = getWidth();
        float viewHeight = getHeight();
        
        if (viewWidth == 0 || viewHeight == 0) return;
        
        float scale = Math.min(viewWidth / drawableWidth, viewHeight / drawableHeight);
        currentScale = scale;
        
        matrix.reset();
        matrix.postScale(scale, scale);
        matrix.postTranslate((viewWidth - drawableWidth * scale) / 2,
            (viewHeight - drawableHeight * scale) / 2);
        
        setImageMatrix(matrix);
    }
    
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (getDrawable() == null || !touchEnabled) {
            return super.onTouchEvent(event);
        }
        
        scaleGestureDetector.onTouchEvent(event);
        
        switch (event.getAction() & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_DOWN:
                lastTouchPoint.set(event.getX(), event.getY());
                mode = DRAG;
                break;
                
            case MotionEvent.ACTION_POINTER_DOWN:
                mode = ZOOM;
                break;
                
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
                mode = NONE;
                break;
                
            case MotionEvent.ACTION_MOVE:
                if (mode == DRAG && event.getPointerCount() == 1) {
                    float dx = event.getX() - lastTouchPoint.x;
                    float dy = event.getY() - lastTouchPoint.y;
                    
                    matrix.postTranslate(dx, dy);
                    setImageMatrix(matrix);
                    
                    lastTouchPoint.set(event.getX(), event.getY());
                }
                break;
        }
        
        return true;
    }
    
    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            float scaleFactor = detector.getScaleFactor();
            float newScale = currentScale * scaleFactor;
            
            if (newScale < MIN_SCALE) {
                scaleFactor = MIN_SCALE / currentScale;
                newScale = MIN_SCALE;
            } else if (newScale > MAX_SCALE) {
                scaleFactor = MAX_SCALE / currentScale;
                newScale = MAX_SCALE;
            }
            
            if (newScale != currentScale) {
                float focusX = detector.getFocusX();
                float focusY = detector.getFocusY();
                
                matrix.postScale(scaleFactor, scaleFactor, focusX, focusY);
                setImageMatrix(matrix);
                
                currentScale = newScale;
            }
            
            return true;
        }
    }
    
    public void resetTransform() {
        centerImage();
    }
}
