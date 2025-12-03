package com.tiktok.ic.camera.utils;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;

import com.tiktok.ic.camera.widget.StickerView;

import java.util.List;

/**
 * 贴纸绘制工具类
 * 提供将贴纸绘制到图片上的功能
 */
public class StickerDrawUtils {
    
    /**
     * 将贴纸列表绘制到图片上
     * 
     * @param bitmap 目标图片
     * @param stickerViews 贴纸视图列表
     * @param imageViewWidth 图片视图宽度
     * @param imageViewHeight 图片视图高度
     * @return 绘制后的图片
     */
    public static Bitmap drawStickersOnBitmap(
            Bitmap bitmap,
            List<StickerView> stickerViews,
            float imageViewWidth,
            float imageViewHeight) {
        
        if (bitmap == null || stickerViews == null || stickerViews.isEmpty()) {
            return bitmap;
        }
        
        Bitmap result = bitmap.copy(Bitmap.Config.ARGB_8888, true);
        Canvas canvas = new Canvas(result);
        
        float imageWidth = bitmap.getWidth();
        float imageHeight = bitmap.getHeight();
        
        if (imageViewWidth == 0 || imageViewHeight == 0) {
            return result;
        }
        
        // 计算图片在ImageView中的实际显示区域
        ImageCoordinateUtils.ImageDisplayInfo displayInfo = 
            ImageCoordinateUtils.calculateImageDisplayInfo(
                imageWidth, imageHeight, imageViewWidth, imageViewHeight);
        
        if (displayInfo == null) {
            return result;
        }
        
        // 按顺序绘制所有贴纸（保持层级关系）
        for (StickerView stickerView : stickerViews) {
            // 获取贴纸在容器中的位置
            float stickerX = stickerView.getX();
            float stickerY = stickerView.getY();
            float stickerWidth = stickerView.getWidth();
            float stickerHeight = stickerView.getHeight();
            
            // 计算贴纸中心点在容器中的位置
            float centerX = stickerX + stickerWidth / 2f;
            float centerY = stickerY + stickerHeight / 2f;
            
            // 转换为图片坐标
            float[] imageCoords = ImageCoordinateUtils.viewToImageCoordinates(
                centerX, centerY, displayInfo);
            float imageX = imageCoords[0];
            float imageY = imageCoords[1];
            
            float rotation = stickerView.getRotationAngle();
            float scale = stickerView.getScaleFactor();
            
            // 获取贴纸drawable的原始尺寸
            Drawable drawable = stickerView.getStickerDrawable();
            if (drawable == null) continue;
            
            int drawableWidth = drawable.getIntrinsicWidth();
            int drawableHeight = drawable.getIntrinsicHeight();
            if (drawableWidth <= 0) drawableWidth = 120; // 默认值
            if (drawableHeight <= 0) drawableHeight = 120; // 默认值
            
            // 计算绘制尺寸（考虑缩放，使用统一的缩放比例保持宽高比）
            float baseScale = Math.min(displayInfo.scaleX, displayInfo.scaleY);
            float drawWidth = drawableWidth * baseScale * scale;
            float drawHeight = drawableHeight * baseScale * scale;
            
            // 保存画布状态
            canvas.save();
            
            // 移动到贴纸中心位置
            canvas.translate(imageX, imageY);
            
            // 旋转
            canvas.rotate(rotation);
            
            // 设置drawable的bounds并绘制
            drawable.setBounds(
                (int)(-drawWidth / 2),
                (int)(-drawHeight / 2),
                (int)(drawWidth / 2),
                (int)(drawHeight / 2)
            );
            drawable.draw(canvas);
            
            // 恢复画布状态
            canvas.restore();
        }
        
        return result;
    }
}
