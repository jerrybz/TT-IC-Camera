package com.tiktok.ic.camera.utils;

import com.tiktok.ic.camera.R;

/**
 * 贴纸工具类，管理贴纸资源
 */
public class StickerUtils {
    
    /**
     * 贴纸类型枚举
     * 要添加新贴纸，请在此枚举中添加新类型
     */
    public enum StickerType {
        STICKER_02,
        STICKER_03,
        STICKER_04,
        STICKER_05,
        STICKER_06,
        STICKER_07,
        STICKER_08,
        STICKER_09,
        STICKER_10,
        STICKER_11,
        STICKER_12,
        STICKER_13
    }
    
    /**
     * 获取贴纸的资源ID
     * @param stickerType 贴纸类型
     * @return 资源ID
     * 
     * 添加新贴纸时，需要在此方法中添加对应的case分支
     */
    public static int getStickerResourceId(StickerType stickerType) {
        switch (stickerType) {
            case STICKER_02:
                return R.drawable.sticker_02;
            case STICKER_03:
                return R.drawable.sticker_03;
            case STICKER_04:
                return R.drawable.sticker_04;
            case STICKER_05:
                return R.drawable.sticker_05;
            case STICKER_06:
                return R.drawable.sticker_06;
            case STICKER_07:
                return R.drawable.sticker_07;
            case STICKER_08:
                return R.drawable.sticker_08;
            case STICKER_09:
                return R.drawable.sticker_09;
            case STICKER_10:
                return R.drawable.sticker_10;
            case STICKER_11:
                return R.drawable.sticker_11;
            case STICKER_12:
                return R.drawable.sticker_12;
            case STICKER_13:
                return R.drawable.sticker_13;
            default:
                return R.drawable.sticker_02;
        }
    }

    /**
     * 获取所有贴纸类型
     */
    public static StickerType[] getAllStickers() {
        return StickerType.values();
    }
}
