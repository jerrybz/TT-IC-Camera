package com.tiktok.ic.camera;

import android.app.Application;

import com.tiktok.ic.camera.utils.ThemeUtils;

/**
 * 应用程序类，用于初始化全局设置
 */
public class ICCameraApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        // 初始化主题模式
        ThemeUtils.initTheme(this);
    }
}
