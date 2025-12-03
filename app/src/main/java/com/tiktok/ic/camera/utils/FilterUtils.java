package com.tiktok.ic.camera.utils;

import android.graphics.Bitmap;

/**
 * 滤镜工具类，实现各种图片滤镜效果
 */
public class FilterUtils {
    
    /**
     * 滤镜类型枚举
     */
    public enum FilterType {
        ORIGINAL,      // 原图
        BLACK_WHITE,   // 黑白
        VINTAGE,       // 复古
        FRESH,         // 清新
        WARM,          // 暖色调
        COOL           // 冷色调
    }
    
    /**
     * 应用滤镜到图片
     * @param bitmap 原始图片
     * @param filterType 滤镜类型
     * @return 应用滤镜后的新图片
     */
    public static Bitmap applyFilter(Bitmap bitmap, FilterType filterType) {
        if (bitmap == null || filterType == FilterType.ORIGINAL) {
            return bitmap;
        }
        
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
        
        // 创建可修改的图片副本
        Bitmap result = sourceBitmap.copy(Bitmap.Config.RGB_565, true);
        int width = result.getWidth();
        int height = result.getHeight();
        int[] pixels = new int[width * height];
        result.getPixels(pixels, 0, width, 0, 0, width, height);

        if (needScale && sourceBitmap != bitmap) {
            sourceBitmap.recycle();
        }
        
        switch (filterType) {
            case BLACK_WHITE:
                applyBlackWhiteFilter(pixels);
                break;
            case VINTAGE:
                applyVintageFilter(pixels);
                break;
            case FRESH:
                applyFreshFilter(pixels);
                break;
            case WARM:
                applyWarmFilter(pixels);
                break;
            case COOL:
                applyCoolFilter(pixels);
                break;
            default:
                return bitmap;
        }
        
        result.setPixels(pixels, 0, width, 0, 0, width, height);
        return result;
    }
    
    /**
     * 黑白滤镜：将图片转换为灰度图
     */
    private static void applyBlackWhiteFilter(int[] pixels) {
        for (int i = 0; i < pixels.length; i++) {
            int pixel = pixels[i];
            int a = (pixel >> 24) & 0xFF;
            int r = (pixel >> 16) & 0xFF;
            int g = (pixel >> 8) & 0xFF;
            int b = pixel & 0xFF;
            
            // 使用加权平均计算灰度值（人眼对不同颜色的敏感度不同）
            int gray = (int) (0.299 * r + 0.587 * g + 0.114 * b);
            
            pixels[i] = (a << 24) | (gray << 16) | (gray << 8) | gray;
        }
    }
    
    /**
     * 复古滤镜：增加暖色调，降低饱和度，增加对比度
     */
    private static void applyVintageFilter(int[] pixels) {
        for (int i = 0; i < pixels.length; i++) {
            int pixel = pixels[i];
            int a = (pixel >> 24) & 0xFF;
            int r = (pixel >> 16) & 0xFF;
            int g = (pixel >> 8) & 0xFF;
            int b = pixel & 0xFF;
            
            // 增加红色和黄色（暖色调）
            r = Math.min(255, (int) (r * 1.1f + 10));
            g = Math.min(255, (int) (g * 1.05f + 5));
            b = Math.min(255, (int) (b * 0.95f));
            
            // 降低饱和度
            int gray = (int) (0.299 * r + 0.587 * g + 0.114 * b);
            float saturation = 0.6f; // 饱和度降低到60%
            r = (int) (gray + (r - gray) * saturation);
            g = (int) (gray + (g - gray) * saturation);
            b = (int) (gray + (b - gray) * saturation);
            
            // 增加对比度
            float contrast = 1.2f;
            r = (int) (((r / 255.0f - 0.5f) * contrast + 0.5f) * 255);
            g = (int) (((g / 255.0f - 0.5f) * contrast + 0.5f) * 255);
            b = (int) (((b / 255.0f - 0.5f) * contrast + 0.5f) * 255);
            
            // 限制范围
            r = Math.max(0, Math.min(255, r));
            g = Math.max(0, Math.min(255, g));
            b = Math.max(0, Math.min(255, b));
            
            pixels[i] = (a << 24) | (r << 16) | (g << 8) | b;
        }
    }
    
    /**
     * 清新滤镜：提高亮度，增加绿色和蓝色，降低对比度
     */
    private static void applyFreshFilter(int[] pixels) {
        for (int i = 0; i < pixels.length; i++) {
            int pixel = pixels[i];
            int a = (pixel >> 24) & 0xFF;
            int r = (pixel >> 16) & 0xFF;
            int g = (pixel >> 8) & 0xFF;
            int b = pixel & 0xFF;
            
            // 增加绿色和蓝色（清新感）
            r = Math.min(255, (int) (r * 0.95f));
            g = Math.min(255, (int) (g * 1.1f + 10));
            b = Math.min(255, (int) (b * 1.15f + 15));
            
            // 提高亮度
            float brightness = 1.15f;
            r = (int) (r * brightness);
            g = (int) (g * brightness);
            b = (int) (b * brightness);
            
            // 降低对比度（柔和效果）
            float contrast = 0.9f;
            r = (int) (((r / 255.0f - 0.5f) * contrast + 0.5f) * 255);
            g = (int) (((g / 255.0f - 0.5f) * contrast + 0.5f) * 255);
            b = (int) (((b / 255.0f - 0.5f) * contrast + 0.5f) * 255);
            
            // 限制范围
            r = Math.max(0, Math.min(255, r));
            g = Math.max(0, Math.min(255, g));
            b = Math.max(0, Math.min(255, b));
            
            pixels[i] = (a << 24) | (r << 16) | (g << 8) | b;
        }
    }
    
    /**
     * 暖色调滤镜：增加红色和黄色，降低蓝色
     */
    private static void applyWarmFilter(int[] pixels) {
        for (int i = 0; i < pixels.length; i++) {
            int pixel = pixels[i];
            int a = (pixel >> 24) & 0xFF;
            int r = (pixel >> 16) & 0xFF;
            int g = (pixel >> 8) & 0xFF;
            int b = pixel & 0xFF;
            
            // 增加红色和黄色（暖色调）
            r = Math.min(255, (int) (r * 1.2f + 20));
            g = Math.min(255, (int) (g * 1.1f + 10));
            b = Math.min(255, (int) (b * 0.9f - 10));
            
            // 限制范围
            r = Math.max(0, Math.min(255, r));
            g = Math.max(0, Math.min(255, g));
            b = Math.max(0, Math.min(255, b));
            
            pixels[i] = (a << 24) | (r << 16) | (g << 8) | b;
        }
    }
    
    /**
     * 冷色调滤镜：增加蓝色和青色，降低红色和黄色
     */
    private static void applyCoolFilter(int[] pixels) {
        for (int i = 0; i < pixels.length; i++) {
            int pixel = pixels[i];
            int a = (pixel >> 24) & 0xFF;
            int r = (pixel >> 16) & 0xFF;
            int g = (pixel >> 8) & 0xFF;
            int b = pixel & 0xFF;
            
            // 增加蓝色和青色（冷色调）
            r = Math.min(255, (int) (r * 0.9f - 10));
            g = Math.min(255, (int) (g * 1.05f));
            b = Math.min(255, (int) (b * 1.2f + 20));
            
            // 限制范围
            r = Math.max(0, Math.min(255, r));
            g = Math.max(0, Math.min(255, g));
            b = Math.max(0, Math.min(255, b));
            
            pixels[i] = (a << 24) | (r << 16) | (g << 8) | b;
        }
    }
    
    /**
     * 获取滤镜名称
     */
    public static String getFilterName(FilterType filterType) {
        switch (filterType) {
            case ORIGINAL:
                return "原图";
            case BLACK_WHITE:
                return "黑白";
            case VINTAGE:
                return "复古";
            case FRESH:
                return "清新";
            case WARM:
                return "暖色调";
            case COOL:
                return "冷色调";
            default:
                return "原图";
        }
    }
}
