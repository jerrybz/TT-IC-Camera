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
        
        // 限制最大分辨率，避免加载过大的图片
        final int MAX_DIMENSION = 1200;
        int targetWidth = reqWidth > 0 ? Math.min(reqWidth, MAX_DIMENSION) : MAX_DIMENSION;
        int targetHeight = reqHeight > 0 ? Math.min(reqHeight, MAX_DIMENSION) : MAX_DIMENSION;
        
        if (height > targetHeight || width > targetWidth) {
            final int halfHeight = height / 2;
            final int halfWidth = width / 2;
            while ((halfHeight / inSampleSize) >= targetHeight && (halfWidth / inSampleSize) >= targetWidth) {
                inSampleSize *= 2;
            }
        }
        return inSampleSize;
    }
    
    /**
     * 计算图片采样大小（内存优化版本），限制最大内存占用
     * 用于编辑场景，确保内存占用在合理范围内
     * 
     * @param options BitmapFactory.Options
     * @param reqWidth 请求的宽度
     * @param reqHeight 请求的高度
     * @param maxMemoryMB 最大内存占用（MB），默认100MB
     * @return 采样大小
     */
    public static int calculateInSampleSizeForMemory(
            android.graphics.BitmapFactory.Options options, 
            int reqWidth, int reqHeight, int maxMemoryMB) {
        
        final int height = options.outHeight;
        final int width = options.outWidth;
        
        // 计算RGB_565格式下的内存占用（每个像素2字节）
        long pixelCount = (long) width * height;
        long memoryBytes = pixelCount * 2;
        long maxMemoryBytes = maxMemoryMB * 1024L * 1024L;
        
        // 如果内存占用超过限制，计算需要的采样率
        int inSampleSize = 1;
        if (memoryBytes > maxMemoryBytes) {
            double ratio = (double) memoryBytes / maxMemoryBytes;
            inSampleSize = (int) Math.ceil(Math.sqrt(ratio));
            inSampleSize = (int) Math.pow(2, Math.ceil(Math.log(inSampleSize) / Math.log(2)));
        }

        int displaySampleSize = calculateInSampleSize(options, reqWidth, reqHeight);

        return Math.max(inSampleSize, displaySampleSize);
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
        
        // 如果亮度和对比度都为0，直接返回原图引用，不创建副本
        if (brightness == 0 && contrast == 0) {
            return bitmap;
        }
        
        // 如果图片太大，先进行缩放以减少内存占用
        int maxDimension = 1200;
        Bitmap sourceBitmap = bitmap;
        boolean needScale = false;
        if (bitmap.getWidth() > maxDimension || bitmap.getHeight() > maxDimension) {
            float scale = Math.min(
                (float) maxDimension / bitmap.getWidth(),
                (float) maxDimension / bitmap.getHeight()
            );
            int newWidth = (int) (bitmap.getWidth() * scale);
            int newHeight = (int) (bitmap.getHeight() * scale);
            sourceBitmap = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true);
            needScale = true;
        }
        
        Bitmap result = sourceBitmap.copy(Bitmap.Config.RGB_565, true);
        int width = result.getWidth();
        int height = result.getHeight();
        int[] pixels = new int[width * height];
        result.getPixels(pixels, 0, width, 0, 0, width, height);
        
        // 如果创建了临时缩放Bitmap，释放它
        if (needScale && sourceBitmap != bitmap) {
            sourceBitmap.recycle();
        }
        
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
        float textSize = Math.max(bitmap.getWidth(), bitmap.getHeight()) * 0.03f;
        textSize = Math.max(textSize, 60);
        textSize = Math.min(textSize, 120);
        paint.setTextSize(textSize);
        paint.setAlpha(220);
        
        float x = watermarked.getWidth() - paint.measureText(watermark) - 30;
        float y = watermarked.getHeight() - 30;
        
        canvas.drawText(watermark, x, y, paint);
        
        return watermarked;
    }
}
