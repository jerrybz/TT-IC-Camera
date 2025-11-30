package com.tiktok.ic.camera.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

public class CropOverlayView extends View {
    
    private RectF cropRect = new RectF();
    private float cropRatio = 0; // 0表示自由裁剪
    private Paint overlayPaint;
    private Paint borderPaint;
    private Paint cornerPaint;
    
    private static final float MIN_CROP_SIZE = 100;
    private static final float CORNER_SIZE = 30;
    
    private int touchMode = NONE;
    private static final int NONE = 0;
    private static final int MOVE = 1;
    private static final int RESIZE_TOP_LEFT = 2;
    private static final int RESIZE_TOP_RIGHT = 3;
    private static final int RESIZE_BOTTOM_LEFT = 4;
    private static final int RESIZE_BOTTOM_RIGHT = 5;
    private static final int RESIZE_TOP = 6;
    private static final int RESIZE_BOTTOM = 7;
    private static final int RESIZE_LEFT = 8;
    private static final int RESIZE_RIGHT = 9;
    
    private float lastTouchX, lastTouchY;
    
    public CropOverlayView(Context context) {
        super(context);
        init();
    }
    
    public CropOverlayView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }
    
    public CropOverlayView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }
    
    private void init() {
        overlayPaint = new Paint();
        overlayPaint.setColor(0x80000000);
        overlayPaint.setStyle(Paint.Style.FILL);
        
        borderPaint = new Paint();
        borderPaint.setColor(0xFFFFFFFF);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(3);
        
        cornerPaint = new Paint();
        cornerPaint.setColor(0xFFFFFFFF);
        cornerPaint.setStyle(Paint.Style.STROKE);
        cornerPaint.setStrokeWidth(5);
    }
    
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        initCropRect();
    }
    
    private void initCropRect() {
        float padding = 50;
        float width = getWidth() - padding * 2;
        float height = getHeight() - padding * 2;
        
        if (cropRatio > 0) {
            if (width / height > cropRatio) {
                height = width / cropRatio;
            } else {
                width = height * cropRatio;
            }
        }
        
        cropRect.set(padding, padding, padding + width, padding + height);
        invalidate();
    }
    
    public void setCropRatio(float ratio) {
        this.cropRatio = ratio;
        if (getWidth() > 0 && getHeight() > 0) {
            adjustCropRectToRatio();
            invalidate();
        }
    }
    
    private void adjustCropRectToRatio() {
        if (cropRatio <= 0) return;
        
        float currentWidth = cropRect.width();
        float currentHeight = cropRect.height();
        float currentRatio = currentWidth / currentHeight;
        
        if (currentRatio > cropRatio) {
            float newWidth = currentHeight * cropRatio;
            float centerX = cropRect.centerX();
            cropRect.left = centerX - newWidth / 2;
            cropRect.right = centerX + newWidth / 2;
        } else {
            float newHeight = currentWidth / cropRatio;
            float centerY = cropRect.centerY();
            cropRect.top = centerY - newHeight / 2;
            cropRect.bottom = centerY + newHeight / 2;
        }
        
        constrainCropRect();
    }
    
    private void constrainCropRect() {
        float minX = 0;
        float minY = 0;
        float maxX = getWidth();
        float maxY = getHeight();
        
        if (cropRect.left < minX) {
            float offset = minX - cropRect.left;
            cropRect.left = minX;
            cropRect.right += offset;
        }
        if (cropRect.top < minY) {
            float offset = minY - cropRect.top;
            cropRect.top = minY;
            cropRect.bottom += offset;
        }
        if (cropRect.right > maxX) {
            float offset = cropRect.right - maxX;
            cropRect.right = maxX;
            cropRect.left -= offset;
        }
        if (cropRect.bottom > maxY) {
            float offset = cropRect.bottom - maxY;
            cropRect.bottom = maxY;
            cropRect.top -= offset;
        }
    }
    
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        
        // 绘制遮罩
        canvas.drawRect(0, 0, getWidth(), cropRect.top, overlayPaint);
        canvas.drawRect(0, cropRect.top, cropRect.left, cropRect.bottom, overlayPaint);
        canvas.drawRect(cropRect.right, cropRect.top, getWidth(), cropRect.bottom, overlayPaint);
        canvas.drawRect(0, cropRect.bottom, getWidth(), getHeight(), overlayPaint);
        
        // 绘制边框
        canvas.drawRect(cropRect, borderPaint);
        
        // 绘制四个角的控制点
        drawCorner(canvas, cropRect.left, cropRect.top);
        drawCorner(canvas, cropRect.right, cropRect.top);
        drawCorner(canvas, cropRect.left, cropRect.bottom);
        drawCorner(canvas, cropRect.right, cropRect.bottom);
    }
    
    private void drawCorner(Canvas canvas, float x, float y) {
        float halfSize = CORNER_SIZE / 2;
        canvas.drawLine(x, y - halfSize, x, y + halfSize, cornerPaint);
        canvas.drawLine(x - halfSize, y, x + halfSize, y, cornerPaint);
    }
    
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();
        
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                touchMode = getTouchMode(x, y);
                lastTouchX = x;
                lastTouchY = y;
                return touchMode != NONE;
                
            case MotionEvent.ACTION_MOVE:
                if (touchMode != NONE) {
                    handleTouchMove(x, y);
                    invalidate();
                }
                return true;
                
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                touchMode = NONE;
                return true;
        }
        
        return false;
    }
    
    private int getTouchMode(float x, float y) {
        float touchRadius = 50;
        
        if (distance(x, y, cropRect.left, cropRect.top) < touchRadius) {
            return RESIZE_TOP_LEFT;
        }
        if (distance(x, y, cropRect.right, cropRect.top) < touchRadius) {
            return RESIZE_TOP_RIGHT;
        }
        if (distance(x, y, cropRect.left, cropRect.bottom) < touchRadius) {
            return RESIZE_BOTTOM_LEFT;
        }
        if (distance(x, y, cropRect.right, cropRect.bottom) < touchRadius) {
            return RESIZE_BOTTOM_RIGHT;
        }
        
        if (Math.abs(x - cropRect.left) < touchRadius && y >= cropRect.top && y <= cropRect.bottom) {
            return RESIZE_LEFT;
        }
        if (Math.abs(x - cropRect.right) < touchRadius && y >= cropRect.top && y <= cropRect.bottom) {
            return RESIZE_RIGHT;
        }
        if (Math.abs(y - cropRect.top) < touchRadius && x >= cropRect.left && x <= cropRect.right) {
            return RESIZE_TOP;
        }
        if (Math.abs(y - cropRect.bottom) < touchRadius && x >= cropRect.left && x <= cropRect.right) {
            return RESIZE_BOTTOM;
        }
        
        if (cropRect.contains(x, y)) {
            return MOVE;
        }
        
        return NONE;
    }
    
    private float distance(float x1, float y1, float x2, float y2) {
        float dx = x1 - x2;
        float dy = y1 - y2;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }
    
    private void handleTouchMove(float x, float y) {
        float dx = x - lastTouchX;
        float dy = y - lastTouchY;
        
        switch (touchMode) {
            case MOVE:
                cropRect.offset(dx, dy);
                constrainCropRect();
                break;
                
            case RESIZE_TOP_LEFT:
                if (cropRatio > 0) {
                    resizeWithRatio(dx, dy, true, true);
                } else {
                    cropRect.left += dx;
                    cropRect.top += dy;
                    if (cropRect.width() < MIN_CROP_SIZE) {
                        cropRect.left = cropRect.right - MIN_CROP_SIZE;
                    }
                    if (cropRect.height() < MIN_CROP_SIZE) {
                        cropRect.top = cropRect.bottom - MIN_CROP_SIZE;
                    }
                }
                constrainCropRect();
                break;
                
            case RESIZE_TOP_RIGHT:
                if (cropRatio > 0) {
                    resizeWithRatio(dx, -dy, false, true);
                } else {
                    cropRect.right += dx;
                    cropRect.top += dy;
                    if (cropRect.width() < MIN_CROP_SIZE) {
                        cropRect.right = cropRect.left + MIN_CROP_SIZE;
                    }
                    if (cropRect.height() < MIN_CROP_SIZE) {
                        cropRect.top = cropRect.bottom - MIN_CROP_SIZE;
                    }
                }
                constrainCropRect();
                break;
                
            case RESIZE_BOTTOM_LEFT:
                if (cropRatio > 0) {
                    resizeWithRatio(-dx, dy, true, false);
                } else {
                    cropRect.left += dx;
                    cropRect.bottom += dy;
                    if (cropRect.width() < MIN_CROP_SIZE) {
                        cropRect.left = cropRect.right - MIN_CROP_SIZE;
                    }
                    if (cropRect.height() < MIN_CROP_SIZE) {
                        cropRect.bottom = cropRect.top + MIN_CROP_SIZE;
                    }
                }
                constrainCropRect();
                break;
                
            case RESIZE_BOTTOM_RIGHT:
                if (cropRatio > 0) {
                    resizeWithRatio(dx, dy, false, false);
                } else {
                    cropRect.right += dx;
                    cropRect.bottom += dy;
                    if (cropRect.width() < MIN_CROP_SIZE) {
                        cropRect.right = cropRect.left + MIN_CROP_SIZE;
                    }
                    if (cropRect.height() < MIN_CROP_SIZE) {
                        cropRect.bottom = cropRect.top + MIN_CROP_SIZE;
                    }
                }
                constrainCropRect();
                break;
                
            case RESIZE_LEFT:
                cropRect.left += dx;
                if (cropRatio > 0) {
                    float newHeight = cropRect.width() / cropRatio;
                    float centerY = cropRect.centerY();
                    cropRect.top = centerY - newHeight / 2;
                    cropRect.bottom = centerY + newHeight / 2;
                }
                if (cropRect.width() < MIN_CROP_SIZE) {
                    cropRect.left = cropRect.right - MIN_CROP_SIZE;
                }
                constrainCropRect();
                break;
                
            case RESIZE_RIGHT:
                cropRect.right += dx;
                if (cropRatio > 0) {
                    float newHeight = cropRect.width() / cropRatio;
                    float centerY = cropRect.centerY();
                    cropRect.top = centerY - newHeight / 2;
                    cropRect.bottom = centerY + newHeight / 2;
                }
                if (cropRect.width() < MIN_CROP_SIZE) {
                    cropRect.right = cropRect.left + MIN_CROP_SIZE;
                }
                constrainCropRect();
                break;
                
            case RESIZE_TOP:
                cropRect.top += dy;
                if (cropRatio > 0) {
                    float newWidth = cropRect.height() * cropRatio;
                    float centerX = cropRect.centerX();
                    cropRect.left = centerX - newWidth / 2;
                    cropRect.right = centerX + newWidth / 2;
                }
                if (cropRect.height() < MIN_CROP_SIZE) {
                    cropRect.top = cropRect.bottom - MIN_CROP_SIZE;
                }
                constrainCropRect();
                break;
                
            case RESIZE_BOTTOM:
                cropRect.bottom += dy;
                if (cropRatio > 0) {
                    float newWidth = cropRect.height() * cropRatio;
                    float centerX = cropRect.centerX();
                    cropRect.left = centerX - newWidth / 2;
                    cropRect.right = centerX + newWidth / 2;
                }
                if (cropRect.height() < MIN_CROP_SIZE) {
                    cropRect.bottom = cropRect.top + MIN_CROP_SIZE;
                }
                constrainCropRect();
                break;
        }
        
        lastTouchX = x;
        lastTouchY = y;
    }
    
    private void resizeWithRatio(float dx, float dy, boolean left, boolean top) {
        float currentWidth = cropRect.width();
        float currentHeight = cropRect.height();
        
        if (Math.abs(dx) > Math.abs(dy)) {
            float newWidth = currentWidth + (left ? -dx : dx);
            float newHeight = newWidth / cropRatio;
            float centerX = cropRect.centerX();
            float centerY = cropRect.centerY();
            
            cropRect.left = centerX - newWidth / 2;
            cropRect.right = centerX + newWidth / 2;
            cropRect.top = centerY - newHeight / 2;
            cropRect.bottom = centerY + newHeight / 2;
        } else {
            float newHeight = currentHeight + (top ? -dy : dy);
            float newWidth = newHeight * cropRatio;
            float centerX = cropRect.centerX();
            float centerY = cropRect.centerY();
            
            cropRect.left = centerX - newWidth / 2;
            cropRect.right = centerX + newWidth / 2;
            cropRect.top = centerY - newHeight / 2;
            cropRect.bottom = centerY + newHeight / 2;
        }
        
        if (cropRect.width() < MIN_CROP_SIZE || cropRect.height() < MIN_CROP_SIZE) {
            cropRect.set(cropRect.left, cropRect.top, cropRect.right, cropRect.bottom);
        }
    }
    
    public RectF getCropRect() {
        return new RectF(cropRect);
    }
}
