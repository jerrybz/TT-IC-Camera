package com.tiktok.ic.camera.utils;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;

/**
 * 图片处理工具类
 * 提供图片旋转、翻转、亮度对比度调整、水印等功能
 */
public class ImageProcessUtils {
    
    /**
     * 计算图片采样大小，用于优化内存使用
     * 
     * @param options BitmapFactory.Options
     * @param reqWidth 请求的宽度
     * @param reqHeight 请求的高度
     * @return 采样大小
     */
    public static int calculateInSampleSize(
            android.graphics.BitmapFactory.Options options, 
            int reqWidth, int reqHeight) {
        
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;
        
        if (height > reqHeight || width > reqWidth) {
            final int halfHeight = height / 2;
            final int halfWidth = width / 2;
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }
        return inSampleSize;
    }
    
    /**
     * 旋转图片
     * 
     * @param bitmap 原始图片
     * @param angle 旋转角度（正数为顺时针）
     * @return 旋转后的图片
     */
    public static Bitmap rotateImage(Bitmap bitmap, int angle) {
        if (bitmap == null) return null;
        
        Matrix matrix = new Matrix();
        matrix.postRotate(angle, bitmap.getWidth() / 2f, bitmap.getHeight() / 2f);
        return Bitmap.createBitmap(bitmap, 0, 0, 
            bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }
    
    /**
     * 水平翻转图片
     * 
     * @param bitmap 原始图片
     * @return 翻转后的图片
     */
    public static Bitmap flipHorizontal(Bitmap bitmap) {
        if (bitmap == null) return null;
        
        Matrix matrix = new Matrix();
        matrix.postScale(-1, 1, bitmap.getWidth() / 2f, bitmap.getHeight() / 2f);
        return Bitmap.createBitmap(bitmap, 0, 0, 
            bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }
    
    /**
     * 垂直翻转图片
     * 
     * @param bitmap 原始图片
     * @return 翻转后的图片
     */
    public static Bitmap flipVertical(Bitmap bitmap) {
        if (bitmap == null) return null;
        
        Matrix matrix = new Matrix();
        matrix.postScale(1, -1, bitmap.getWidth() / 2f, bitmap.getHeight() / 2f);
        return Bitmap.createBitmap(bitmap, 0, 0, 
            bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }
    
    /**
     * 应用亮度和对比度调整
     * 
     * @param bitmap 原始图片
     * @param brightness 亮度值（-100到100）
     * @param contrast 对比度值（-50到150）
     * @return 处理后的图片
     */
    public static Bitmap applyBrightnessContrast(Bitmap bitmap, float brightness, float contrast) {
        if (bitmap == null) return null;
        
        // 如果亮度和对比度都为0，直接返回原图
        if (brightness == 0 && contrast == 0) {
            return bitmap.copy(Bitmap.Config.ARGB_8888, true);
        }
        
        Bitmap result = bitmap.copy(Bitmap.Config.ARGB_8888, true);
        int width = result.getWidth();
        int height = result.getHeight();
        int[] pixels = new int[width * height];
        result.getPixels(pixels, 0, width, 0, 0, width, height);
        
        // 亮度：-100到100，映射到-1.0到1.0
        float brightnessValue = brightness / 100.0f;
        // 对比度：-50到150，映射到0.5到2.0（1.0表示无变化）
        float contrastValue = 1.0f + (contrast / 100.0f);
        
        for (int i = 0; i < pixels.length; i++) {
            int pixel = pixels[i];
            int a = (pixel >> 24) & 0xFF;
            int r = (pixel >> 16) & 0xFF;
            int g = (pixel >> 8) & 0xFF;
            int b = pixel & 0xFF;
            
            // 先应用对比度（在0-255范围内）
            float rNorm = r / 255.0f;
            float gNorm = g / 255.0f;
            float bNorm = b / 255.0f;
            
            rNorm = ((rNorm - 0.5f) * contrastValue + 0.5f);
            gNorm = ((gNorm - 0.5f) * contrastValue + 0.5f);
            bNorm = ((bNorm - 0.5f) * contrastValue + 0.5f);
            
            r = (int) (rNorm * 255);
            g = (int) (gNorm * 255);
            b = (int) (bNorm * 255);
            
            // 再应用亮度
            r = (int) (r + brightnessValue * 255);
            g = (int) (g + brightnessValue * 255);
            b = (int) (b + brightnessValue * 255);
            
            // 限制范围
            r = Math.max(0, Math.min(255, r));
            g = Math.max(0, Math.min(255, g));
            b = Math.max(0, Math.min(255, b));
            
            pixels[i] = (a << 24) | (r << 16) | (g << 8) | b;
        }
        
        result.setPixels(pixels, 0, width, 0, 0, width, height);
        return result;
    }
    
    /**
     * 添加水印到图片
     * 
     * @param bitmap 原始图片
     * @param watermark 水印文字
     * @return 添加水印后的图片
     */
    public static Bitmap addWatermark(Bitmap bitmap, String watermark) {
        if (bitmap == null || watermark == null || watermark.isEmpty()) {
            return bitmap;
        }
        
        Bitmap watermarked = bitmap.copy(Bitmap.Config.ARGB_8888, true);
        Canvas canvas = new Canvas(watermarked);
        
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.WHITE);
        // 根据图片大小动态调整水印字体大小
        float textSize = Math.max(bitmap.getWidth(), bitmap.getHeight()) * 0.03f; // 图片尺寸的3%
        textSize = Math.max(textSize, 60); // 最小60
        textSize = Math.min(textSize, 120); // 最大120
        paint.setTextSize(textSize);
        paint.setAlpha(220); // 提高不透明度，更明显
        
        float x = watermarked.getWidth() - paint.measureText(watermark) - 30;
        float y = watermarked.getHeight() - 30;
        
        canvas.drawText(watermark, x, y, paint);
        
        return watermarked;
    }
}
