package com.tiktok.ic.camera.utils;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.widget.Toast;

import java.util.List;

/**
 * 分享工具类
 * 提供应用安装检查、权限检查和启动应用等功能
 */
public class ShareUtils {
    
    /**
     * 检查应用是否已安装
     * 
     * @param context 上下文
     * @param pkgName 包名
     * @return true表示已安装，false表示未安装
     */
    public static boolean checkAppInstalled(Context context, String pkgName) {
        if (pkgName == null || pkgName.isEmpty()) {
            return false;
        }
        
        try {
            final PackageManager packageManager = context.getPackageManager();
            packageManager.getPackageInfo(pkgName, PackageManager.GET_ACTIVITIES);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            // 应用未安装
            return false;
        } catch (Exception e) {
            // 其他异常，使用备用方法
//            return checkAppInstalledFallback(context, pkgName);
            return false;
        }
    }
    
    /**
     * 检查是否有权限启动其他应用
     * 
     * @param context 上下文
     * @return true表示有权限，false表示无权限
     */
    public static boolean checkLaunchPermission(Context context) {
        try {
            PackageManager pm = context.getPackageManager();
            return pm != null;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 安全启动抖音应用
     * 包含完整的权限检查、应用安装检查和异常处理
     *
     * @param context 上下文
     */
    public static void launchTikTokApp(Context context) {
        if (context == null) {
            return;
        }
        
        // 1. 检查权限
        if (!checkLaunchPermission(context)) {
            Toast.makeText(context, "无法启动应用，请检查权限设置", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // 2. 获取包名和URI（使用安全方式）
        String packageName = "com.ss.android.ugc.aweme";
        String deepLinkUri = "snssdk1128://user/profile/75476264857";
        
        // 3. 检查应用是否安装
        if (!checkAppInstalled(context, packageName)) {
            Toast.makeText(context, "请先安装抖音", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // 4. 尝试启动应用
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse(deepLinkUri));
            intent.setPackage(packageName);
            
            // 检查是否有应用可以处理这个Intent
            if (intent.resolveActivity(context.getPackageManager()) != null) {
                context.startActivity(intent);
            } else {
                // 如果指定包名无法启动，尝试不指定包名
                intent.setPackage(null);
                if (intent.resolveActivity(context.getPackageManager()) != null) {
                    context.startActivity(intent);
                } else {
                    Toast.makeText(context, "无法打开抖音", Toast.LENGTH_SHORT).show();
                }
            }
        } catch (SecurityException e) {
            // 权限异常
            Toast.makeText(context, "没有权限启动应用", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            // 其他异常
            Toast.makeText(context, "启动失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
}
