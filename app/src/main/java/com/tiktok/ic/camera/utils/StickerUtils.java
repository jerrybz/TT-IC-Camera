package com.tiktok.ic.camera.utils;

import com.tiktok.ic.camera.R;

/**
 * 贴纸工具类，管理贴纸资源
 * 
 * 如何添加新贴纸：
 * 1. 在 res/drawable/ 目录下创建贴纸资源文件（如 sticker_flower.xml 或 sticker_flower.png）
 * 2. 在 StickerType 枚举中添加新的贴纸类型（如 FLOWER）
 * 3. 在 getStickerResourceId() 方法中添加对应的资源ID映射
 * 4. 在 getStickerName() 方法中添加对应的名称
 * 
 * 示例：
 * 1. 创建 res/drawable/sticker_flower.xml
 * 2. 在枚举中添加：FLOWER,  // 花朵
 * 3. 在 getStickerResourceId() 中添加：
 *    case FLOWER:
 *        return R.drawable.sticker_flower;
 * 4. 在 getStickerName() 中添加：
 *    case FLOWER:
 *        return "花朵";
 */
public class StickerUtils {
    
    /**
     * 贴纸类型枚举
     * 要添加新贴纸，请在此枚举中添加新类型
     */
    public enum StickerType {
        HEART,          // 爱心
        STAR,           // 星星
        SMILE,          // 笑脸
        // 后续可以添加更多贴纸类型，例如：
        // FLOWER,       // 花朵
        // CROWN,        // 皇冠
        // SUN,          // 太阳
        // MOON,         // 月亮
        // CLOUD,        // 云朵
        // RAINBOW,      // 彩虹
        // BUTTERFLY,    // 蝴蝶
        // BALLOON,      // 气球
        // DIAMOND,      // 钻石
        // MUSIC,        // 音乐符号
        // ARROW,        // 箭头
        // CHECK,        // 对勾
        // FIRE,         // 火焰
        // SNOWFLAKE     // 雪花
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
            case HEART:
                return R.drawable.sticker_heart;
            case STAR:
                return R.drawable.sticker_star;
            case SMILE:
                return R.drawable.sticker_smile;
            // 添加新贴纸时，在这里添加case，例如：
            // case FLOWER:
            //     return R.drawable.sticker_flower;
            default:
                return R.drawable.sticker_heart;
        }
    }
    
    /**
     * 获取贴纸名称
     * 
     * 添加新贴纸时，需要在此方法中添加对应的case分支
     */
    public static String getStickerName(StickerType stickerType) {
        switch (stickerType) {
            case HEART:
                return "爱心";
            case STAR:
                return "星星";
            case SMILE:
                return "笑脸";
            // 添加新贴纸时，在这里添加case，例如：
            // case FLOWER:
            //     return "花朵";
            default:
                return "爱心";
        }
    }
    
    /**
     * 获取所有贴纸类型
     */
    public static StickerType[] getAllStickers() {
        return StickerType.values();
    }
}
