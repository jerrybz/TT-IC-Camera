package com.tiktok.ic.camera.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

/**
 * 裁剪覆盖层View
 * 显示裁剪框，支持拖动和调整大小，支持固定宽高比裁剪
 */
public class CropOverlayView extends View {
    
    private RectF cropRect = new RectF();
    private float cropRatio = 0; // 0表示自由裁剪
    private Paint overlayPaint;
    private Paint borderPaint;
    private Paint cornerPaint;
    
    // 图片实际尺寸和显示区域
    private float imageWidth = 0;
    private float imageHeight = 0;
    private RectF imageDisplayRect = new RectF(); // 图片在视图中的显示区域
    
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
        // 如果图片信息已设置，基于图片显示区域初始化裁剪框
        if (imageWidth > 0 && imageHeight > 0 && imageDisplayRect.width() > 0 && imageDisplayRect.height() > 0) {
            float displayWidth = imageDisplayRect.width();
            float displayHeight = imageDisplayRect.height();
            
            if (cropRatio > 0) {
                // 固定比例裁剪，完全贴合图片边缘，不留边距
                // 基于图片的实际尺寸计算裁剪框大小
                float imageAspect = imageWidth / imageHeight;
                float targetAspect = cropRatio;
                
                float cropWidth, cropHeight;
                
                if (imageAspect > targetAspect) {
                    // 图片更宽，以高度为准
                    cropHeight = imageHeight;
                    cropWidth = cropHeight * targetAspect;
                } else {
                    // 图片更高，以宽度为准
                    cropWidth = imageWidth;
                    cropHeight = cropWidth / targetAspect;
                }
                
                // 将图片尺寸转换为视图坐标
                float scaleX = displayWidth / imageWidth;
                float scaleY = displayHeight / imageHeight;
                
                float viewCropWidth = cropWidth * scaleX;
                float viewCropHeight = cropHeight * scaleY;
                
                // 将裁剪框居中显示在图片显示区域内
                float centerX = imageDisplayRect.centerX();
                float centerY = imageDisplayRect.centerY();
                
                cropRect.left = centerX - viewCropWidth / 2;
                cropRect.right = centerX + viewCropWidth / 2;
                cropRect.top = centerY - viewCropHeight / 2;
                cropRect.bottom = centerY + viewCropHeight / 2;
            } else {
                // 自由裁剪，留出边距，不完全贴合图片边缘（留出15%的边距）
                float marginRatio = 0.15f;
                float marginX = displayWidth * marginRatio;
                float marginY = displayHeight * marginRatio;
                
                cropRect.left = imageDisplayRect.left + marginX;
                cropRect.right = imageDisplayRect.right - marginX;
                cropRect.top = imageDisplayRect.top + marginY;
                cropRect.bottom = imageDisplayRect.bottom - marginY;
            }
        } else {
            // 如果没有图片信息，使用原来的逻辑
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
        }
        
        invalidate();
    }
    
    /**
     * 设置图片的实际尺寸和显示区域
     * @param imageWidth 图片实际宽度
     * @param imageHeight 图片实际高度
     * @param displayRect 图片在视图中的显示区域
     */
    public void setImageInfo(float imageWidth, float imageHeight, RectF displayRect) {
        this.imageWidth = imageWidth;
        this.imageHeight = imageHeight;
        this.imageDisplayRect.set(displayRect);
        
        // 重新初始化裁剪框（无论是否有比例限制）
        if (getWidth() > 0 && getHeight() > 0) {
            initCropRect();
        }
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
        
        // 如果图片信息已设置，基于图片尺寸计算裁剪框大小
        if (imageWidth > 0 && imageHeight > 0 && imageDisplayRect.width() > 0 && imageDisplayRect.height() > 0) {
            // 固定比例裁剪，完全贴合图片边缘，不留边距
            // 基于图片的实际尺寸计算裁剪框大小
            float imageAspect = imageWidth / imageHeight;
            float targetAspect = cropRatio;
            
            float cropWidth, cropHeight;
            
            if (imageAspect > targetAspect) {
                // 图片更宽，以高度为准
                cropHeight = imageHeight;
                cropWidth = cropHeight * targetAspect;
            } else {
                // 图片更高，以宽度为准
                cropWidth = imageWidth;
                cropHeight = cropWidth / targetAspect;
            }
            
            // 将图片尺寸转换为视图坐标
            float scaleX = imageDisplayRect.width() / imageWidth;
            float scaleY = imageDisplayRect.height() / imageHeight;
            
            float viewCropWidth = cropWidth * scaleX;
            float viewCropHeight = cropHeight * scaleY;
            
            // 将裁剪框居中显示在图片显示区域内
            float centerX = imageDisplayRect.centerX();
            float centerY = imageDisplayRect.centerY();
            
            cropRect.left = centerX - viewCropWidth / 2;
            cropRect.right = centerX + viewCropWidth / 2;
            cropRect.top = centerY - viewCropHeight / 2;
            cropRect.bottom = centerY + viewCropHeight / 2;
        } else {
            // 如果没有图片信息，使用原来的逻辑（基于当前裁剪框大小）
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
