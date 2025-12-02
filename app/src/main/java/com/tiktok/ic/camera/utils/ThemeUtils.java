package com.tiktok.ic.camera.utils;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.appcompat.app.AppCompatDelegate;

/**
 * 主题工具类，用于管理日夜间模式切换
 */
public class ThemeUtils {
    private static final String PREF_NAME = "theme_prefs";
    private static final String KEY_NIGHT_MODE = "night_mode";
    
    /**
     * 初始化主题模式
     * 默认使用日间模式
     * @param context 上下文
     */
    public static void initTheme(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        boolean isNightMode = false;
        boolean hasStoredValue = false;
        
        // 兼容旧数据格式：可能存储的是 Integer 类型
        try {
            // 检查是否有存储的值
            if (prefs.contains(KEY_NIGHT_MODE)) {
                hasStoredValue = true;
                isNightMode = prefs.getBoolean(KEY_NIGHT_MODE, false);
            }
        } catch (ClassCastException e) {
            // 如果存储的是 Integer，尝试读取并转换
            try {
                if (prefs.contains(KEY_NIGHT_MODE)) {
                    hasStoredValue = true;
                    int nightModeInt = prefs.getInt(KEY_NIGHT_MODE, 0);
                    isNightMode = (nightModeInt != 0);
                    // 清除旧数据，重新存储为 Boolean
                    prefs.edit().remove(KEY_NIGHT_MODE).apply();
                }
            } catch (Exception ex) {
                // 如果读取失败，使用默认值 false
                isNightMode = false;
                hasStoredValue = false;
            }
        }
        
        // 如果是第一次启动（没有存储值），强制设置为日间模式
        if (!hasStoredValue) {
            isNightMode = false;
        }
        
        // 确保数据格式正确并应用主题
        // 强制设置为日间模式（MODE_NIGHT_NO），不跟随系统设置
        int mode = isNightMode ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO;
        AppCompatDelegate.setDefaultNightMode(mode);
        
        // 保存当前设置
        prefs.edit()
                .remove(KEY_NIGHT_MODE) // 先移除，确保清除旧格式
                .putBoolean(KEY_NIGHT_MODE, isNightMode)
                .apply();
    }
    
    /**
     * 设置夜间模式
     * @param context 上下文
     * @param isNightMode 是否为夜间模式
     */
    public static void setNightMode(Context context, boolean isNightMode) {
        setNightMode(context, isNightMode, true);
    }
    
    /**
     * 设置夜间模式（内部方法）
     * @param context 上下文
     * @param isNightMode 是否为夜间模式
     * @param applyTheme 是否应用主题
     */
    private static void setNightMode(Context context, boolean isNightMode, boolean applyTheme) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        // 清除可能存在的旧格式数据
        prefs.edit()
                .remove(KEY_NIGHT_MODE) // 先移除，确保清除旧格式
                .putBoolean(KEY_NIGHT_MODE, isNightMode)
                .apply();
        
        if (applyTheme) {
            int mode = isNightMode ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO;
            AppCompatDelegate.setDefaultNightMode(mode);
        }
    }
    
    /**
     * 切换夜间模式
     * @param context 上下文
     * @return 切换后的模式（true为夜间模式，false为日间模式）
     */
    public static boolean toggleNightMode(Context context) {
        boolean currentMode = isNightMode(context);
        boolean newMode = !currentMode;
        setNightMode(context, newMode);
        return newMode;
    }
    
    /**
     * 获取当前是否为夜间模式
     * @param context 上下文
     * @return true为夜间模式，false为日间模式
     */
    public static boolean isNightMode(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        boolean isNightMode = false;
        
        // 兼容旧数据格式
        try {
            isNightMode = prefs.getBoolean(KEY_NIGHT_MODE, false);
        } catch (ClassCastException e) {
            // 如果存储的是 Integer，尝试读取并转换
            try {
                int nightModeInt = prefs.getInt(KEY_NIGHT_MODE, 0);
                isNightMode = (nightModeInt != 0);
            } catch (Exception ex) {
                isNightMode = false;
            }
        }
        
        return isNightMode;
    }
}
