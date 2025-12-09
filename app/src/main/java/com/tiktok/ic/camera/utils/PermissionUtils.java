package com.tiktok.ic.camera.utils;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.tiktok.ic.camera.R;

/**
 * 权限工具类
 * 统一处理权限请求、检查和引导用户前往设置页面
 */
public class PermissionUtils {

    /**
     * 权限类型枚举
     */
    public enum PermissionType {
        CAMERA,              // 相机权限
        STORAGE,             // 存储权限
        WRITE_STORAGE        // 写入存储权限
    }

    /**
     * 检查权限是否已授予
     * 
     * @param context 上下文
     * @param permissionType 权限类型
     * @return 是否已授予权限
     */
    public static boolean hasPermission(Context context, PermissionType permissionType) {
        String permission = getPermissionString(permissionType);
        if (permission == null) {
            return true; // Android 13+ 不需要 WRITE_STORAGE 权限
        }
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * 根据权限类型和Android版本获取权限字符串
     * 
     * @param permissionType 权限类型
     * @return 权限字符串，如果不需要权限则返回null
     */
    public static String getPermissionString(PermissionType permissionType) {
        switch (permissionType) {
            case CAMERA:
                return Manifest.permission.CAMERA;
            case STORAGE:
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    return Manifest.permission.READ_MEDIA_IMAGES;
                } else {
                    return Manifest.permission.READ_EXTERNAL_STORAGE;
                }
            case WRITE_STORAGE:
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    return null; // Android 13+ 不需要此权限
                } else {
                    return Manifest.permission.WRITE_EXTERNAL_STORAGE;
                }
            default:
                return null;
        }
    }

    /**
     * 获取权限的显示名称
     * 
     * @param permissionType 权限类型
     * @return 权限显示名称
     */
    public static String getPermissionName(PermissionType permissionType) {
        switch (permissionType) {
            case CAMERA:
                return "相机权限";
            case STORAGE:
                return "存储权限";
            case WRITE_STORAGE:
                return "存储权限";
            default:
                return "权限";
        }
    }

    /**
     * 检查是否应该显示权限说明
     * 如果用户之前拒绝过权限，应该显示说明
     * 
     * @param activity Activity
     * @param permissionType 权限类型
     * @return 是否应该显示说明
     */
    public static boolean shouldShowRationale(Activity activity, PermissionType permissionType) {
        String permission = getPermissionString(permissionType);
        if (permission == null) {
            return false;
        }
        return ActivityCompat.shouldShowRequestPermissionRationale(activity, permission);
    }

    /**
     * 检查用户是否勾选了"不再询问"
     * 如果返回true，说明用户已经永久拒绝权限，需要引导前往设置
     * 
     * @param activity Activity
     * @param permissionType 权限类型
     * @return 是否勾选了"不再询问"
     */
    public static boolean isPermissionPermanentlyDenied(Activity activity, PermissionType permissionType) {
        String permission = getPermissionString(permissionType);
        if (permission == null) {
            return false;
        }
        // 如果权限未授予，且不应该显示说明，说明用户勾选了"不再询问"
        return !hasPermission(activity, permissionType) 
                && !ActivityCompat.shouldShowRequestPermissionRationale(activity, permission);
    }

    /**
     * 显示权限说明对话框
     * 
     * @param activity Activity
     * @param permissionType 权限类型
     * @param message 自定义说明消息，如果为null则使用默认消息
     * @param onConfirm 确认回调
     * @param onCancel 取消回调
     */
    public static void showPermissionRationale(
            Activity activity,
            PermissionType permissionType,
            String message,
            Runnable onConfirm,
            Runnable onCancel) {
        
        String permissionName = getPermissionName(permissionType);
        String defaultMessage = "需要" + permissionName + "才能使用此功能，请在设置中开启" + permissionName + "。";
        String finalMessage = message != null ? message : defaultMessage;

        AlertDialog.Builder builder = new AlertDialog.Builder(activity)
                .setTitle("需要" + permissionName)
                .setMessage(finalMessage)
                .setCancelable(false);
        
        // 根据是否永久拒绝来决定按钮文本
        if (isPermissionPermanentlyDenied(activity, permissionType)) {
            // 永久拒绝，显示"前往设置"和"取消"
            builder.setPositiveButton("前往设置", (dialog, which) -> {
                if (onConfirm != null) {
                    onConfirm.run();
                }
            });
        } else {
            // 可以再次请求，显示"允许"和"取消"
            builder.setPositiveButton("允许", (dialog, which) -> {
                if (onConfirm != null) {
                    onConfirm.run();
                }
            });
        }
        
        builder.setNegativeButton("取消", (dialog, which) -> {
            if (onCancel != null) {
                onCancel.run();
            }
        }).show();
    }

    /**
     * 请求权限
     * 
     * @param activity Activity
     * @param permissionType 权限类型
     * @param requestCode 请求码
     */
    public static void requestPermission(Activity activity, PermissionType permissionType, int requestCode) {
        String permission = getPermissionString(permissionType);
        if (permission == null) {
            return; // 不需要权限
        }
        ActivityCompat.requestPermissions(activity, new String[]{permission}, requestCode);
    }

    /**
     * 处理权限请求结果
     * 
     * @param activity Activity
     * @param permissionType 权限类型
     * @param permissions 权限数组
     * @param grantResults 授权结果数组
     * @param onGranted 授权成功回调
     * @param onDenied 授权失败回调
     * @param onPermanentlyDenied 永久拒绝回调（勾选了"不再询问"）
     */
    public static void handlePermissionResult(
            Activity activity,
            PermissionType permissionType,
            String[] permissions,
            int[] grantResults,
            Runnable onGranted,
            Runnable onDenied,
            Runnable onPermanentlyDenied) {
        
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            // 权限已授予
            if (onGranted != null) {
                onGranted.run();
            }
        } else {
            // 权限被拒绝
            String permission = getPermissionString(permissionType);
            if (permission != null && permissions.length > 0 && permissions[0].equals(permission)) {
                if (isPermissionPermanentlyDenied(activity, permissionType)) {
                    // 用户勾选了"不再询问"，引导前往设置
                    if (onPermanentlyDenied != null) {
                        onPermanentlyDenied.run();
                    } else {
                        // 默认行为：显示引导对话框
                        showPermissionRationale(
                                activity,
                                permissionType,
                                null,
                                () -> openAppSettings(activity),
                                null
                        );
                    }
                } else {
                    // 用户只是拒绝了，可以再次请求
                    if (onDenied != null) {
                        onDenied.run();
                    } else {
                        String permissionName = getPermissionName(permissionType);
                        Toast.makeText(activity, "需要" + permissionName + "才能使用此功能", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        }
    }

    /**
     * 打开应用设置页面
     * 
     * @param activity Activity
     */
    public static void openAppSettings(Activity activity) {
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            Uri uri = Uri.fromParts("package", activity.getPackageName(), null);
            intent.setData(uri);
            activity.startActivity(intent);
        } catch (Exception e) {
            e.printStackTrace();
            // 如果无法打开应用设置，尝试打开系统设置
            try {
                Intent intent = new Intent(Settings.ACTION_SETTINGS);
                activity.startActivity(intent);
            } catch (Exception ex) {
                ex.printStackTrace();
                Toast.makeText(activity, "无法打开设置页面", Toast.LENGTH_SHORT).show();
            }
        }
    }

    /**
     * 智能请求权限（带引导功能）
     * 如果用户之前拒绝过，会先显示说明对话框
     * 
     * @param activity Activity
     * @param permissionType 权限类型
     * @param requestCode 请求码
     * @param rationaleMessage 说明消息（可选）
     */
    public static void requestPermissionWithRationale(
            Activity activity,
            PermissionType permissionType,
            int requestCode,
            String rationaleMessage) {
        
        // 如果已有权限，直接返回
        if (hasPermission(activity, permissionType)) {
            return;
        }

        // 检查是否已经永久拒绝
        if (isPermissionPermanentlyDenied(activity, permissionType)) {
            // 如果已经永久拒绝，直接显示引导对话框，引导用户前往设置
            showPermissionRationale(
                    activity,
                    permissionType,
                    rationaleMessage,
                    () -> openAppSettings(activity),
                    null
            );
        } else if (shouldShowRationale(activity, permissionType)) {
            // 如果应该显示说明（用户之前拒绝过），先显示说明对话框
            String permissionName = getPermissionName(permissionType);
            String message = rationaleMessage != null 
                    ? rationaleMessage 
                    : "需要" + permissionName + "才能使用此功能，请允许" + permissionName + "。";
            
            showPermissionRationale(
                    activity,
                    permissionType,
                    message,
                    () -> {
                        // 用户点击"允许"，请求权限
                        requestPermission(activity, permissionType, requestCode);
                    },
                    null
            );
        } else {
            // 第一次请求，直接请求权限
            requestPermission(activity, permissionType, requestCode);
        }
    }
}
