# ICCamera

一个功能丰富的图片处理应用，提供拍照、图片编辑、拼图等功能。

## 项目简介

ICCamera 是一款专为 Android 平台开发的相机应用，集成了多种图片处理功能，帮助用户轻松拍摄、编辑和分享照片。

## 主要功能

### 相机拍照
- 支持使用设备相机进行拍照
- 自动保存到相册

### 图片编辑
提供丰富的图片编辑功能：
- **裁剪**：支持自由裁剪和多种比例裁剪
- **旋转**：支持图片旋转和翻转
- **滤镜**：提供 6 种滤镜效果（原图、黑白、复古、清新、暖色调、冷色调）
- **调节**：支持亮度和对比度调节
- **文字**：支持添加可编辑文字，自定义字体、大小、颜色和透明度
- **贴纸**：支持添加贴纸装饰
- **缩放**：支持图片缩放查看

### 图片拼图
- 支持横向、纵向、网格三种拼图模式
- 最多支持 4 张图片拼接
- 实时预览拼图效果

### 图库浏览
- 浏览设备中的图片
- 按相册分类查看
- 单张图片预览
- 支持多选图片（用于拼图功能）

### 主题切换
- 支持日间/夜间主题切换
- 一键切换，实时生效

### 分享功能
- 支持跳转抖音平台个人主页
- 将编辑后的图片进行分享

## 技术栈

- **开发语言**：Java
- **最低支持版本**：Android 10 (API 29)
- **目标 SDK 版本**：Android 36
- **编译 SDK 版本**：Android 36
- **主要依赖**：
  - AndroidX AppCompat 1.6.1
  - Material Design Components 1.10.0
  - AndroidX Activity 1.8.0
  - ConstraintLayout 2.1.4

## 项目结构

```
app/src/main/java/com/tiktok/ic/camera/
├── activity/              # Activity 类
│   ├── BaseActivity.java                   # 基础 Activity（统一主题管理）
│   ├── MainActivity.java                   # 主界面
│   ├── ImageEditActivity.java              # 图片编辑
│   ├── ImagePreviewActivity.java           # 图片预览
│   ├── ImageGalleryActivity.java           # 图库浏览
│   ├── ImageCollageActivity.java           # 图片拼图
│   ├── ImageCollageSelectorActivity.java    # 拼图选择器
│   └── SaveSuccessActivity.java            # 保存成功页面
├── Adapter/              # 适配器类
│   ├── AlbumAdapter.java                   # 相册列表适配器
│   ├── ImageAdapter.java                   # 图片列表适配器
│   └── MultiSelectImageAdapter.java        # 多选图片适配器
├── utils/                # 工具类
│   ├── FilterUtils.java                    # 滤镜工具（6种滤镜效果）
│   ├── ShareUtils.java                     # 分享工具（跳转抖音）
│   ├── StickerUtils.java                   # 贴纸工具
│   ├── ThemeUtils.java                     # 主题工具（日夜间切换）
│   ├── PermissionUtils.java                # 权限管理工具
│   ├── ImageSaveUtils.java                 # 图片保存工具
│   ├── ImageProcessUtils.java              # 图片处理工具
│   ├── ImageCoordinateUtils.java           # 图片坐标工具
│   ├── StickerDrawUtils.java               # 贴纸绘制工具
│   └── TextDrawUtils.java                  # 文字绘制工具
├── widget/               # 自定义控件
│   ├── CropOverlayView.java                # 裁剪覆盖层
│   ├── StickerView.java                    # 贴纸视图
│   ├── ZoomableImageView.java              # 可缩放图片视图
│   ├── EditableTextView.java               # 可编辑文字视图
│   └── SquareImageView.java                # 正方形图片视图
└── ICCameraApplication.java                # 应用入口类
```

## 权限说明

应用需要以下权限：

- **相机权限** (`CAMERA`)：用于拍照功能
- **存储权限** (`READ_EXTERNAL_STORAGE`, `WRITE_EXTERNAL_STORAGE`)：用于读取和保存图片（Android 12 及以下，maxSdkVersion=32）
- **媒体文件权限** (`READ_MEDIA_IMAGES`)：用于访问图片文件（Android 13 及以上）

应用会在运行时动态请求所需权限，并提供友好的权限说明。

## 构建说明

### 环境要求
- Android Studio（推荐最新版本）
- JDK 11 或更高版本
- Android SDK（API 29 及以上）

### 构建步骤

1. 克隆或下载项目到本地
2. 使用 Android Studio 打开项目
3. 等待 Gradle 同步完成（首次打开会自动下载依赖）
4. 连接 Android 设备（API 29+）或启动模拟器
5. 点击运行按钮或使用快捷键运行应用
