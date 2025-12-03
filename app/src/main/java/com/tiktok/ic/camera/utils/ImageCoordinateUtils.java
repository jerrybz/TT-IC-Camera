package com.tiktok.ic.camera.utils;

import android.graphics.RectF;

/**
 * 图片坐标转换工具类
 * 用于处理图片在视图中的显示区域计算和坐标转换
 */
public class ImageCoordinateUtils {
    
    /**
     * 计算图片在视图中的实际显示区域（保持宽高比，居中显示）
     * 
     * @param imageWidth 图片实际宽度
     * @param imageHeight 图片实际高度
     * @param viewWidth 视图宽度
     * @param viewHeight 视图高度
     * @return 包含显示区域信息的对象
     */
    public static ImageDisplayInfo calculateImageDisplayInfo(
            float imageWidth, float imageHeight, 
            float viewWidth, float viewHeight) {
        
        if (viewWidth == 0 || viewHeight == 0) {
            return null;
        }
        
        float imageAspect = imageWidth / imageHeight;
        float viewAspect = viewWidth / viewHeight;
        
        float displayWidth, displayHeight;
        float offsetX = 0, offsetY = 0;
        
        if (imageAspect > viewAspect) {
            // 图片更宽，以宽度为准
            displayWidth = viewWidth;
            displayHeight = viewWidth / imageAspect;
            offsetY = (viewHeight - displayHeight) / 2;
        } else {
            // 图片更高，以高度为准
            displayHeight = viewHeight;
            displayWidth = viewHeight * imageAspect;
            offsetX = (viewWidth - displayWidth) / 2;
        }
        
        // 计算坐标转换比例：从视图坐标转换为图片坐标
        float scaleX = imageWidth / displayWidth;
        float scaleY = imageHeight / displayHeight;
        
        RectF displayRect = new RectF(
            offsetX,
            offsetY,
            offsetX + displayWidth,
            offsetY + displayHeight
        );
        
        return new ImageDisplayInfo(displayRect, scaleX, scaleY, offsetX, offsetY);
    }
    
    /**
     * 将视图坐标转换为图片坐标
     * 
     * @param viewX 视图X坐标
     * @param viewY 视图Y坐标
     * @param displayInfo 图片显示信息
     * @return 图片坐标 [x, y]
     */
    public static float[] viewToImageCoordinates(float viewX, float viewY, ImageDisplayInfo displayInfo) {
        if (displayInfo == null) {
            return new float[]{0, 0};
        }
        
        float imageX = (viewX - displayInfo.offsetX) * displayInfo.scaleX;
        float imageY = (viewY - displayInfo.offsetY) * displayInfo.scaleY;
        
        return new float[]{imageX, imageY};
    }
    
    /**
     * 图片显示信息类
     */
    public static class ImageDisplayInfo {
        public final RectF displayRect;
        public final float scaleX;
        public final float scaleY;
        public final float offsetX;
        public final float offsetY;
        
        public ImageDisplayInfo(RectF displayRect, float scaleX, float scaleY, float offsetX, float offsetY) {
            this.displayRect = displayRect;
            this.scaleX = scaleX;
            this.scaleY = scaleY;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
        }
    }
}
