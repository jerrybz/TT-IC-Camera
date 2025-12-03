package com.tiktok.ic.camera.utils;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;

import com.tiktok.ic.camera.widget.EditableTextView;

import java.util.List;

/**
 * 文字绘制工具类
 * 提供将文字绘制到图片上的功能
 */
public class TextDrawUtils {
    
    /**
     * 将文字列表绘制到图片上
     * 
     * @param bitmap 目标图片
     * @param textViews 文字视图列表
     * @param imageViewWidth 图片视图宽度
     * @param imageViewHeight 图片视图高度
     * @param resources Resources对象（用于单位转换）
     * @return 绘制后的图片
     */
    public static Bitmap drawTextsOnBitmap(
            Bitmap bitmap,
            List<EditableTextView> textViews,
            float imageViewWidth,
            float imageViewHeight,
            Resources resources) {
        
        if (bitmap == null || textViews == null || textViews.isEmpty()) {
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
        
        for (EditableTextView textView : textViews) {
            if (textView.getText().isEmpty()) {
                continue;
            }
            
            // 获取文字在容器中的位置
            float textX = textView.getX();
            float textY = textView.getY();
            float textWidth = textView.getWidth();
            float textHeight = textView.getHeight();
            
            // 计算文字中心点在容器中的位置
            float centerX = textX + textWidth / 2f;
            float centerY = textY + textHeight / 2f;
            
            // 转换为图片坐标
            float[] imageCoords = ImageCoordinateUtils.viewToImageCoordinates(
                centerX, centerY, displayInfo);
            float imageX = imageCoords[0];
            float imageY = imageCoords[1];
            
            // 获取文字样式信息
            float baseScale = Math.min(displayInfo.scaleX, displayInfo.scaleY);
            float textSizeSp = textView.getTextSize(); // sp单位
            float textSizePx = textSizeSp * resources.getDisplayMetrics().scaledDensity; // 转换为px
            float textSize = textSizePx * textView.getScaleFactor() * baseScale;
            
            int textColor = textView.getTextColor();
            float rotation = textView.getRotationAngle();
            Typeface typeface = textView.getTypeface();
            
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setColor(textColor);
            paint.setTextSize(textSize);
            paint.setTypeface(typeface);
            
            // 保存画布状态
            canvas.save();
            
            // 应用旋转
            canvas.rotate(rotation, imageX, imageY);
            
            // 绘制文字（支持多行）
            String text = textView.getText();
            String[] lines = text.split("\n");
            float lineHeight = paint.getTextSize() * 1.2f;
            
            // 计算文字起始位置（多行文字需要从第一行开始向上偏移）
            float startY = imageY - (lines.length - 1) * lineHeight / 2;
            
            for (String line : lines) {
                if (!line.isEmpty()) {
                    // 计算文字宽度，用于居中对齐
                    float textWidthPx = paint.measureText(line);
                    float drawX = imageX - textWidthPx / 2;
                    canvas.drawText(line, drawX, startY, paint);
                }
                startY += lineHeight;
            }
            
            // 恢复画布状态
            canvas.restore();
        }
        
        return result;
    }
}
