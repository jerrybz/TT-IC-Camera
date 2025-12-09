package com.tiktok.ic.camera.activity;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;

import com.tiktok.ic.camera.utils.FilterUtils;
import com.tiktok.ic.camera.R;
import com.tiktok.ic.camera.utils.StickerUtils;
import com.tiktok.ic.camera.utils.ImageCoordinateUtils;
import com.tiktok.ic.camera.utils.ImageProcessUtils;
import com.tiktok.ic.camera.utils.ImageSaveUtils;
import com.tiktok.ic.camera.utils.TextDrawUtils;
import com.tiktok.ic.camera.utils.StickerDrawUtils;
import com.tiktok.ic.camera.widget.CropOverlayView;
import com.tiktok.ic.camera.widget.EditableTextView;
import com.tiktok.ic.camera.widget.StickerView;
import com.tiktok.ic.camera.widget.ZoomableImageView;

import android.os.Handler;
import android.os.Looper;
import android.content.res.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 图片编辑Activity
 * 提供图片裁剪、旋转、文字添加、滤镜、贴纸、亮度对比度调节等功能
 */
public class ImageEditActivity extends BaseActivity {
    
    private static final String EXTRA_IMAGE_PATH = "image_path";
    
    // UI组件
    private ZoomableImageView imageView;
    private FrameLayout editContainer;
    private CropOverlayView cropOverlay;
    
    // 保存按钮
    private Button btnSave;
    
    // 二级功能栏
    private LinearLayout secondaryToolbar;
    private Button btnCrop;
    private Button btnRotate;
    private Button btnText;
    private Button btnAdjust;
    private Button btnFilter;
    private Button btnSticker;
    private Button btnCancel;
    private Button btnConfirm;
    
    // 功能选项面板
    private LinearLayout optionsPanel;
    private LinearLayout optionsContainer;
    
    // 图片相关
    private Bitmap originalBitmap; // 原始图片
    private Bitmap baseBitmap; // 基础图片
    private Bitmap currentBitmap; // 当前显示的图片
    private Bitmap rotateModeBaseBitmap; // 进入旋转模式前保存的baseBitmap状态
    private Bitmap cropModeBaseBitmap; // 进入裁剪模式前保存的baseBitmap状态
    private String imagePath;
    
    // 当前状态
    private EditMode currentMode = EditMode.NONE;
    private float currentBrightness = 0;
    private float currentContrast = 0;
    private float cropRatio = 0; // 0表示自由裁剪
    private FilterUtils.FilterType currentFilter = FilterUtils.FilterType.ORIGINAL; // 当前应用的滤镜
    
    // 文字编辑相关
    private java.util.List<EditableTextView> textViews = new java.util.ArrayList<>();
    private EditableTextView selectedTextView;
    
    // 贴纸相关
    private java.util.List<com.tiktok.ic.camera.widget.StickerView> stickerViews = new java.util.ArrayList<>();
    private com.tiktok.ic.camera.widget.StickerView selectedSticker;
    
    // 文字样式设置
    private android.graphics.Typeface currentTypeface = android.graphics.Typeface.DEFAULT;
    private float currentTextSize = 24;
    private int currentTextColor = Color.WHITE;
    private int currentTextAlpha = 255; // 50%-100%对应128-255
    
    private ActivityResultLauncher<String> requestPermissionLauncher;
    
    // 滤镜处理相关
    private ExecutorService filterExecutor = Executors.newSingleThreadExecutor();
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private FilterUtils.FilterType pendingFilter = null; // 待处理的滤镜
    
    private enum EditMode {
        NONE, CROP, ROTATE, TEXT, ADJUST, FILTER, STICKER
    }
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_edit);
        
        imagePath = getIntent().getStringExtra(EXTRA_IMAGE_PATH);
        if (imagePath == null) {
            Toast.makeText(this, "图片路径无效", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        
        initViews();
        initPermissionLauncher();
        if (savedInstanceState == null) {
            loadImage();
        }
        setupToolbars();
    }
    
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        setContentView(R.layout.activity_image_edit);
        initViews();
        if (currentBitmap != null) {
            imageView.setImageBitmap(currentBitmap);
            imageView.resetTransform();
        }
        setupToolbars();
        
        if (currentMode == EditMode.CROP) {
            cropOverlay.setVisibility(View.VISIBLE);
            updateCropOverlayImageInfo();
            cropOverlay.setCropRatio(cropRatio);
        }
        
        if (currentMode == EditMode.TEXT) {
            imageView.setTouchEnabled(false);
            setupTextMode();
        } else if (currentMode == EditMode.STICKER) {
            imageView.setTouchEnabled(false);
            setupStickerMode();
        } else if (currentMode == EditMode.ADJUST || currentMode == EditMode.FILTER) {
            imageView.setTouchEnabled(false);
            editContainer.setOnTouchListener(null);
        } else {
            imageView.setTouchEnabled(true);
        }
        
        if (currentMode != EditMode.NONE) {
            showOptionsForMode(currentMode);
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (filterExecutor != null && !filterExecutor.isShutdown()) {
            filterExecutor.shutdown();
        }

        if (imageView != null) {
            imageView.setImageBitmap(null);
        }
        
        //清理所有Bitmap引用，释放内存
        if (originalBitmap != null && !originalBitmap.isRecycled()) {
            originalBitmap.recycle();
            originalBitmap = null;
        }
        if (baseBitmap != null && baseBitmap != originalBitmap && !baseBitmap.isRecycled()) {
            baseBitmap.recycle();
            baseBitmap = null;
        }
        if (currentBitmap != null && currentBitmap != originalBitmap && 
            currentBitmap != baseBitmap && !currentBitmap.isRecycled()) {
            currentBitmap.recycle();
            currentBitmap = null;
        }
        if (rotateModeBaseBitmap != null && rotateModeBaseBitmap != originalBitmap && 
            rotateModeBaseBitmap != baseBitmap && !rotateModeBaseBitmap.isRecycled()) {
            rotateModeBaseBitmap.recycle();
            rotateModeBaseBitmap = null;
        }
        if (cropModeBaseBitmap != null && cropModeBaseBitmap != originalBitmap && 
            cropModeBaseBitmap != baseBitmap && !cropModeBaseBitmap.isRecycled()) {
            cropModeBaseBitmap.recycle();
            cropModeBaseBitmap = null;
        }
    }
    
    private void initViews() {
        imageView = findViewById(R.id.image_view);
        editContainer = findViewById(R.id.edit_container);
        editContainer.setClipChildren(false);
        editContainer.setClipToPadding(false);
        cropOverlay = findViewById(R.id.crop_overlay);
        
        btnSave = findViewById(R.id.btn_save);
        
        secondaryToolbar = findViewById(R.id.secondary_toolbar);
        btnCrop = findViewById(R.id.btn_crop);
        btnRotate = findViewById(R.id.btn_rotate);
        btnText = findViewById(R.id.btn_text);
        btnAdjust = findViewById(R.id.btn_adjust);
        btnFilter = findViewById(R.id.btn_filter);
        btnSticker = findViewById(R.id.btn_sticker);
        btnCancel = findViewById(R.id.btn_cancel);
        btnConfirm = findViewById(R.id.btn_confirm);
        
        optionsPanel = findViewById(R.id.options_panel);
        optionsContainer = findViewById(R.id.options_container);
    }
    
    private void initPermissionLauncher() {
        requestPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            isGranted -> {
                if (isGranted) {
                    saveImage();
                } else {
                    Toast.makeText(this, "需要存储权限才能保存图片", Toast.LENGTH_SHORT).show();
                }
            }
        );
    }
    
    private void loadImage() {
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(imagePath, options);
            
            int reqWidth = getResources().getDisplayMetrics().widthPixels;
            int reqHeight = getResources().getDisplayMetrics().heightPixels;

            options.inSampleSize = ImageProcessUtils.calculateInSampleSizeForMemory(
                options, reqWidth, reqHeight, 50);

            options.inPreferredConfig = Bitmap.Config.RGB_565;
            options.inJustDecodeBounds = false;

            options.inScaled = false;
            
            originalBitmap = BitmapFactory.decodeFile(imagePath, options);
            
            if (originalBitmap != null) {
                int maxDimension = 1200;
                if (originalBitmap.getWidth() > maxDimension || originalBitmap.getHeight() > maxDimension) {
                    float scale = Math.min(
                        (float) maxDimension / originalBitmap.getWidth(),
                        (float) maxDimension / originalBitmap.getHeight()
                    );
                    int newWidth = (int) (originalBitmap.getWidth() * scale);
                    int newHeight = (int) (originalBitmap.getHeight() * scale);
                    Bitmap scaledBitmap = Bitmap.createScaledBitmap(originalBitmap, newWidth, newHeight, true);
                    if (scaledBitmap != originalBitmap) {
                        originalBitmap.recycle();
                        originalBitmap = scaledBitmap;
                    }
                }

                baseBitmap = originalBitmap.copy(Bitmap.Config.RGB_565, true);
                currentBitmap = baseBitmap; // 直接引用，不创建新副本
                imageView.setImageBitmap(currentBitmap);
            } else {
                Toast.makeText(this, "无法加载图片", Toast.LENGTH_SHORT).show();
                finish();
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "加载图片失败", Toast.LENGTH_SHORT).show();
            finish();
        }
    }
    
    private void setupToolbars() {
        // 保存按钮
        btnSave.setOnClickListener(v -> {
            if (ImageSaveUtils.checkStoragePermission(this)) {
                saveImage();
            } else {
                requestStoragePermission();
            }
        });

        showSecondaryToolbar();
        
        // 裁剪
        btnCrop.setOnClickListener(v -> enterEditMode(EditMode.CROP));
        
        // 旋转
        btnRotate.setOnClickListener(v -> enterEditMode(EditMode.ROTATE));
        
        // 文字
        btnText.setOnClickListener(v -> enterEditMode(EditMode.TEXT));
        
        // 调节
        btnAdjust.setOnClickListener(v -> enterEditMode(EditMode.ADJUST));
        
        // 滤镜
        btnFilter.setOnClickListener(v -> enterEditMode(EditMode.FILTER));
        
        // 贴纸
        btnSticker.setOnClickListener(v -> enterEditMode(EditMode.STICKER));
        
        // 取消
        btnCancel.setOnClickListener(v -> {
            if (currentMode == EditMode.TEXT) {
                clearAllTextViews();
            } else if (currentMode == EditMode.FILTER) {
                currentFilter = FilterUtils.FilterType.ORIGINAL;
                if (baseBitmap != null) {
                    // 如果不需要亮度对比度调整，直接使用baseBitmap引用
                    if (currentBrightness != 0 || currentContrast != 0) {
                        if (currentBitmap != null && currentBitmap != baseBitmap && 
                            currentBitmap != originalBitmap) {
                            currentBitmap.recycle();
                        }
                        currentBitmap = baseBitmap.copy(Bitmap.Config.RGB_565, true);
                        applyBrightnessContrast();
                    } else {
                        // 直接使用baseBitmap，不创建副本
                        if (currentBitmap != null && currentBitmap != baseBitmap && 
                            currentBitmap != originalBitmap) {
                            currentBitmap.recycle();
                        }
                        currentBitmap = baseBitmap;
                        imageView.setImageBitmap(currentBitmap);
                        imageView.resetTransform();
                    }
                }
            } else if (currentMode == EditMode.ADJUST) {
                if (baseBitmap != null) {
                    if (currentBitmap != null && currentBitmap != baseBitmap && 
                        currentBitmap != originalBitmap) {
                        currentBitmap.recycle();
                    }
                    currentBitmap = baseBitmap;
                    currentBrightness = 0;
                    currentContrast = 0;
                    imageView.setImageBitmap(currentBitmap);
                    imageView.resetTransform();
                }
            } else if (currentMode == EditMode.CROP) {
                if (cropModeBaseBitmap != null) {
                    if (baseBitmap != null && baseBitmap != originalBitmap && 
                        baseBitmap != cropModeBaseBitmap) {
                        baseBitmap.recycle();
                    }
                    Bitmap oldCurrentBitmap = currentBitmap;
                    baseBitmap = cropModeBaseBitmap.copy(Bitmap.Config.RGB_565, true);
                    
                    if (currentFilter == FilterUtils.FilterType.ORIGINAL && 
                        currentBrightness == 0 && currentContrast == 0) {
                        currentBitmap = baseBitmap;
                    } else {
                        currentBitmap = baseBitmap.copy(Bitmap.Config.ARGB_8888, true);
                    }
                    
                    if (oldCurrentBitmap != null && oldCurrentBitmap != originalBitmap && 
                        oldCurrentBitmap != baseBitmap && oldCurrentBitmap != currentBitmap) {
                        oldCurrentBitmap.recycle();
                    }
                    
                    if (currentFilter != FilterUtils.FilterType.ORIGINAL) {
                        applyFilter();
                    } else {
                        applyBrightnessContrast();
                    }
                    imageView.setImageBitmap(currentBitmap);
                    imageView.resetTransform();
                }
            } else if (currentMode == EditMode.ROTATE) {
                if (rotateModeBaseBitmap != null) {
                    if (baseBitmap != null && baseBitmap != originalBitmap && 
                        baseBitmap != rotateModeBaseBitmap) {
                        baseBitmap.recycle();
                    }
                    Bitmap oldCurrentBitmap = currentBitmap;
                    baseBitmap = rotateModeBaseBitmap.copy(Bitmap.Config.RGB_565, true);
                    
                    if (currentFilter == FilterUtils.FilterType.ORIGINAL && 
                        currentBrightness == 0 && currentContrast == 0) {
                        currentBitmap = baseBitmap;
                    } else {
                        currentBitmap = baseBitmap.copy(Bitmap.Config.ARGB_8888, true);
                    }
                    
                    if (oldCurrentBitmap != null && oldCurrentBitmap != originalBitmap && 
                        oldCurrentBitmap != baseBitmap && oldCurrentBitmap != currentBitmap) {
                        oldCurrentBitmap.recycle();
                    }
                    
                    if (currentFilter != FilterUtils.FilterType.ORIGINAL) {
                        applyFilter();
                    } else {
                        applyBrightnessContrast();
                    }
                    imageView.setImageBitmap(currentBitmap);
                    imageView.resetTransform();
                }
            } else if (currentMode == EditMode.STICKER) {
                clearAllStickers();
            } else {
                restoreToOriginal();
            }
            exitEditMode();
        });
        
        // 确认
        btnConfirm.setOnClickListener(v -> {
            applyCurrentEdit();
            exitEditMode();
        });
    }
    
    private void showSecondaryToolbar() {
        secondaryToolbar.setVisibility(View.VISIBLE);
    }

    private void enterEditMode(EditMode mode) {
        if (currentMode == EditMode.TEXT && mode != EditMode.TEXT) {
            clearAllTextViews();
        }
        if (currentMode == EditMode.STICKER && mode != EditMode.STICKER) {
            clearAllStickers();
        }
        if (currentMode == EditMode.ADJUST && mode != EditMode.ADJUST) {
            if (baseBitmap != null) {
                if (currentBitmap != null && currentBitmap != baseBitmap && 
                    currentBitmap != originalBitmap) {
                    currentBitmap.recycle();
                }
                currentBitmap = baseBitmap;
                currentBrightness = 0;
                currentContrast = 0;
                imageView.setImageBitmap(currentBitmap);
                imageView.resetTransform();
            }
        }
        if (currentMode == EditMode.FILTER && mode != EditMode.FILTER) {
            if (baseBitmap != null) {
                currentFilter = FilterUtils.FilterType.ORIGINAL;
                if (currentBrightness != 0 || currentContrast != 0) {
                    if (currentBitmap != null && currentBitmap != baseBitmap && 
                        currentBitmap != originalBitmap) {
                        currentBitmap.recycle();
                    }
                    currentBitmap = baseBitmap.copy(Bitmap.Config.RGB_565, true);
                    applyBrightnessContrast();
                } else {
                    if (currentBitmap != null && currentBitmap != baseBitmap && 
                        currentBitmap != originalBitmap) {
                        currentBitmap.recycle();
                    }
                    currentBitmap = baseBitmap;
                    imageView.setImageBitmap(currentBitmap);
                    imageView.resetTransform();
                }
            }
        }
        
        clearSavedModeBitmaps();
        
        currentMode = mode;
        updateSecondaryToolbarSelection();
        showOptionsForMode(mode);
        
        // 在调节模式和滤镜模式下禁用图片触摸，让滑动条和按钮能正常工作
        if (mode == EditMode.ADJUST || mode == EditMode.FILTER) {
            imageView.setTouchEnabled(false);
            editContainer.setOnTouchListener(null);
        } else {
            imageView.setTouchEnabled(true);
        }
        
        if (mode == EditMode.CROP) {
            cropOverlay.setVisibility(View.VISIBLE);
            updateCropOverlayImageInfo();
            cropOverlay.setCropRatio(cropRatio);
            if (baseBitmap != null) {
                cropModeBaseBitmap = baseBitmap.copy(Bitmap.Config.RGB_565, true);
            }
        } else {
            cropOverlay.setVisibility(View.GONE);
        }
        
        if (mode == EditMode.ROTATE) {
            if (baseBitmap != null) {
                rotateModeBaseBitmap = baseBitmap.copy(Bitmap.Config.RGB_565, true);
            }
        }
        
        // 文字模式下，允许在容器上添加文字
        if (mode == EditMode.TEXT) {
            imageView.setTouchEnabled(false);
            setupTextMode();
        }
        
        // 贴纸模式下，允许在容器上添加贴纸
        if (mode == EditMode.STICKER) {
            imageView.setTouchEnabled(false);
            setupStickerMode();
        }
    }
    
    private void exitEditMode() {
        currentMode = EditMode.NONE;
        imageView.setTouchEnabled(true);
        // 不再隐藏工具栏，只隐藏选项面板和裁剪覆盖层
        optionsPanel.setVisibility(View.GONE);
        cropOverlay.setVisibility(View.GONE);
        clearSavedModeBitmaps();
    }
    
    private void clearSavedModeBitmaps() {
        if (rotateModeBaseBitmap != null && rotateModeBaseBitmap != originalBitmap && 
            rotateModeBaseBitmap != baseBitmap && !rotateModeBaseBitmap.isRecycled()) {
            rotateModeBaseBitmap.recycle();
            rotateModeBaseBitmap = null;
        }
        if (cropModeBaseBitmap != null && cropModeBaseBitmap != originalBitmap && 
            cropModeBaseBitmap != baseBitmap && !cropModeBaseBitmap.isRecycled()) {
            cropModeBaseBitmap.recycle();
            cropModeBaseBitmap = null;
        }
    }
    
    /**
     * 更新裁剪覆盖层的图片信息
     * 计算图片在视图中的实际显示区域
     */
    private void updateCropOverlayImageInfo() {
        if (baseBitmap == null || imageView == null || cropOverlay == null) {
            return;
        }
        
        float imageWidth = baseBitmap.getWidth();
        float imageHeight = baseBitmap.getHeight();
        
        float imageViewWidth = imageView.getWidth();
        float imageViewHeight = imageView.getHeight();
        
        if (imageViewWidth == 0 || imageViewHeight == 0) {
            // 如果视图尺寸还未确定，延迟到下一帧
            imageView.post(() -> updateCropOverlayImageInfo());
            return;
        }
        
        // 使用工具类计算图片显示区域
        ImageCoordinateUtils.ImageDisplayInfo displayInfo = 
            ImageCoordinateUtils.calculateImageDisplayInfo(
                imageWidth, imageHeight, imageViewWidth, imageViewHeight);
        
        if (displayInfo != null) {
            cropOverlay.setImageInfo(imageWidth, imageHeight, displayInfo.displayRect);
        }
    }
    
    private void restoreToOriginal() {
        if (originalBitmap != null) {
            // 释放旧的baseBitmap和currentBitmap
            if (baseBitmap != null && baseBitmap != originalBitmap) {
                baseBitmap.recycle();
            }
            if (currentBitmap != null && currentBitmap != originalBitmap && 
                currentBitmap != baseBitmap) {
                currentBitmap.recycle();
            }
            
            baseBitmap = originalBitmap.copy(Bitmap.Config.RGB_565, true);
            currentBitmap = baseBitmap;

            currentBrightness = 0;
            currentContrast = 0;
            cropRatio = 0;
            currentFilter = FilterUtils.FilterType.ORIGINAL;

            imageView.setImageBitmap(currentBitmap);
            imageView.resetTransform();

            if (currentMode == EditMode.ADJUST) {
                showOptionsForMode(EditMode.ADJUST);
            }
        }
    }
    
    private void updateSecondaryToolbarSelection() {
        // 重置所有按钮样式
        resetButtonStyle(btnCrop);
        resetButtonStyle(btnRotate);
        resetButtonStyle(btnText);
        resetButtonStyle(btnAdjust);
        resetButtonStyle(btnFilter);
        resetButtonStyle(btnSticker);
        
        // 设置选中按钮样式
        switch (currentMode) {
            case CROP:
                setSelectedButtonStyle(btnCrop);
                break;
            case ROTATE:
                setSelectedButtonStyle(btnRotate);
                break;
            case TEXT:
                setSelectedButtonStyle(btnText);
                break;
            case ADJUST:
                setSelectedButtonStyle(btnAdjust);
                break;
            case FILTER:
                setSelectedButtonStyle(btnFilter);
                break;
            case STICKER:
                setSelectedButtonStyle(btnSticker);
                break;
        }
    }
    
    private void setSelectedButtonStyle(Button button) {
        button.setTextColor(0xFFFF0000);
        button.setBackgroundColor(0x1AFF0000);
    }
    
    private void resetButtonStyle(Button button) {
        button.setTextColor(0xFF666666);
        button.setBackgroundColor(Color.TRANSPARENT);
    }
    
    private void showOptionsForMode(EditMode mode) {
        optionsContainer.removeAllViews();

        optionsPanel.setVisibility(View.VISIBLE);

        if (mode == EditMode.ADJUST || mode == EditMode.TEXT) {
            optionsContainer.setClickable(false);
            optionsContainer.setFocusable(false);
        }
        
        switch (mode) {
            case CROP:
                showCropOptions();
                break;
            case ROTATE:
                showRotateOptions();
                break;
            case TEXT:
                showTextOptions();
                break;
            case ADJUST:
                showAdjustOptions();
                break;
            case FILTER:
                showFilterOptions();
                break;
            case STICKER:
                showStickerOptions();
                break;
        }
    }
    
    private void showCropOptions() {
        // 自由裁剪
        addOptionButton("自由", v -> {
            cropRatio = 0;
            cropOverlay.setCropRatio(0);
            updateCropButtonSelection(v);
        });
        
        // 1:1
        addOptionButton("1:1", v -> {
            cropRatio = 1.0f;
            cropOverlay.setCropRatio(1.0f);
            updateCropButtonSelection(v);
        });
        
        // 4:3
        addOptionButton("4:3", v -> {
            cropRatio = 4.0f / 3.0f;
            cropOverlay.setCropRatio(4.0f / 3.0f);
            updateCropButtonSelection(v);
        });
        
        // 16:9
        addOptionButton("16:9", v -> {
            cropRatio = 16.0f / 9.0f;
            cropOverlay.setCropRatio(16.0f / 9.0f);
            updateCropButtonSelection(v);
        });
        
        // 3:4
        addOptionButton("3:4", v -> {
            cropRatio = 3.0f / 4.0f;
            cropOverlay.setCropRatio(3.0f / 4.0f);
            updateCropButtonSelection(v);
        });
        
        // 9:16
        addOptionButton("9:16", v -> {
            cropRatio = 9.0f / 16.0f;
            cropOverlay.setCropRatio(9.0f / 16.0f);
            updateCropButtonSelection(v);
        });
    }
    
    private void showRotateOptions() {
        // 90°顺时针
        addOptionButton("90°顺时针", v -> rotateImage(90));
        
        // 90°逆时针
        addOptionButton("90°逆时针", v -> rotateImage(-90));
        
        // 180°
        addOptionButton("180°", v -> rotateImage(180));
        
        // 水平翻转
        addOptionButton("水平翻转", v -> flipHorizontal());
        
        // 垂直翻转
        addOptionButton("垂直翻转", v -> flipVertical());
    }
    
    private void showTextOptions() {
        optionsContainer.removeAllViews();
        
        // 添加文字按钮
        addOptionButton("添加文字", v -> addNewText());
        
        // 字体选项
        addOptionButton("字体", v -> showFontOptions());
        
        // 字号选项
        addOptionButton("字号", v -> showTextSizeOptions());
        
        // 颜色选项
        addOptionButton("颜色", v -> showColorOptions());
        
        // 透明度选项
        addOptionButton("透明度", v -> showAlphaOptions());
    }
    
    @SuppressLint("ClickableViewAccessibility")
    private void setupTextMode() {
        // 设置容器点击监听，点击空白处时让编辑框失去焦点
        editContainer.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                boolean clickedOnTextView = false;
                float x = event.getX();
                float y = event.getY();
                
                for (EditableTextView textView : textViews) {
                    float textX = textView.getX();
                    float textY = textView.getY();
                    float textWidth = textView.getWidth();
                    float textHeight = textView.getHeight();
                    
                    if (x >= textX && x <= textX + textWidth &&
                        y >= textY && y <= textY + textHeight) {
                        clickedOnTextView = true;
                        break;
                    }
                }
                
                // 如果点击在空白处，让所有编辑框失去焦点并隐藏键盘
                if (!clickedOnTextView) {
                    hideKeyboard();
                    if (selectedTextView != null) {
                        selectedTextView.setSelected(false);
                        selectedTextView = null;
                    }
                    editContainer.requestFocus();
                }
            }
            return false; // 返回false让事件继续传递
        });
    }
    
    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            View currentFocus = getCurrentFocus();
            if (currentFocus != null) {
                imm.hideSoftInputFromWindow(currentFocus.getWindowToken(), 0);
            }
        }
    }
    
    private void addNewText() {
        EditableTextView textView = new EditableTextView(this);
        textView.setText("");
        textView.setTypeface(currentTypeface);
        textView.setTextSize(currentTextSize);
        textView.setTextColor(currentTextColor);
        textView.setTextAlpha(currentTextAlpha);
        
        // 设置初始位置在容器中心
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        );
        params.leftMargin = editContainer.getWidth() / 2 - 100;
        params.topMargin = editContainer.getHeight() / 2 - 50;
        
        textView.setLayoutParams(params);
        
        // 设置删除监听
        textView.setOnTextDeleteListener(view -> {
            editContainer.removeView(view);
            textViews.remove(view);
            if (selectedTextView == view) {
                selectedTextView = null;
            }
        });
        
        // 设置选中监听
        textView.setOnTextSelectedListener(view -> {
            if (selectedTextView != null && selectedTextView != view) {
                selectedTextView.setSelected(false);
            }
            selectedTextView = view;
            updateTextStyleFromSelected();
        });
        
        editContainer.addView(textView);
        textViews.add(textView);
        textView.setSelected(true);
        selectedTextView = textView;
        
        // 自动弹出键盘
        textView.post(() -> {
            android.view.inputmethod.InputMethodManager imm = 
                (android.view.inputmethod.InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(textView.editText, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
            }
        });
    }
    
    private void updateTextStyleFromSelected() {
        if (selectedTextView != null) {
            currentTypeface = selectedTextView.getTypeface();
            currentTextSize = selectedTextView.getTextSize();
            currentTextColor = selectedTextView.getTextColor();
            currentTextAlpha = selectedTextView.getTextAlpha();
        }
    }
    
    private void showFontOptions() {
        optionsContainer.removeAllViews();
        
        // 4种字体
        addOptionButton("默认", v -> {
            currentTypeface = android.graphics.Typeface.DEFAULT;
            applyTextStyleToSelected();
            showTextOptions();
        });
        
        addOptionButton("粗体", v -> {
            currentTypeface = android.graphics.Typeface.DEFAULT_BOLD;
            applyTextStyleToSelected();
            showTextOptions();
        });
        
        addOptionButton("等宽", v -> {
            currentTypeface = android.graphics.Typeface.MONOSPACE;
            applyTextStyleToSelected();
            showTextOptions();
        });
        
        addOptionButton("无衬线", v -> {
            currentTypeface = android.graphics.Typeface.SANS_SERIF;
            applyTextStyleToSelected();
            showTextOptions();
        });
        
        // 返回按钮
        addOptionButton("返回", v -> showTextOptions());
    }
    
    private void showTextSizeOptions() {
        optionsContainer.removeAllViews();
        
        LinearLayout sizeContainer = new LinearLayout(this);

        sizeContainer.setOrientation(LinearLayout.VERTICAL);
        sizeContainer.setPadding(8, 8, 8, 8);
        sizeContainer.setClickable(false);
        sizeContainer.setFocusable(false);
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int buttonWidth = (int) (32 * getResources().getDisplayMetrics().density);
        int padding = (int) (64 * getResources().getDisplayMetrics().density);
        int availableWidth = screenWidth - buttonWidth * 4 - padding;
        sizeContainer.setLayoutParams(new LinearLayout.LayoutParams(
                availableWidth,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        TextView label = new TextView(this);
        label.setText("字号: " + (int)currentTextSize);
        label.setTextColor(Color.WHITE);
        label.setTextSize(14);
        sizeContainer.addView(label);
        
        SeekBar sizeSeekBar = new SeekBar(this);
        sizeSeekBar.setMax(24);
        sizeSeekBar.setProgress((int)(currentTextSize - 12));

        sizeSeekBar.setEnabled(true);

        sizeSeekBar.setOnTouchListener((v, event) -> {
            v.getParent().requestDisallowInterceptTouchEvent(true);
            return false;
        });
        sizeSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    currentTextSize = 12 + progress;
                    label.setText("字号: " + (int)currentTextSize);
                    applyTextStyleToSelected();
                }
            }
            
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        
        sizeContainer.addView(sizeSeekBar);
        optionsContainer.addView(sizeContainer);
        
        addSmallBackButton(v -> showTextOptions());
    }
    
    private void showColorOptions() {
        optionsContainer.removeAllViews();
        
        // 10种预设颜色
        int[] presetColors = {
            Color.WHITE, Color.BLACK, Color.RED, Color.GREEN, Color.BLUE,
            Color.YELLOW, Color.CYAN, Color.MAGENTA, 0xFFFFA500, 0xFF800080
        };

        int circleSize = (int) (getResources().getDisplayMetrics().density * 40);
        
        for (int i = 0; i < presetColors.length; i++) {
            final int color = presetColors[i];

            View colorView = new View(this);

            GradientDrawable circleDrawable = new GradientDrawable();
            circleDrawable.setShape(GradientDrawable.OVAL);
            circleDrawable.setColor(color);
            // 如果是白色，添加边框以便区分
            if (color == Color.WHITE) {
                circleDrawable.setStroke((int) (getResources().getDisplayMetrics().density * 2), Color.GRAY);
            }
            colorView.setBackground(circleDrawable);
            
            // 设置点击监听
            colorView.setOnClickListener(v -> {
                currentTextColor = color;
                applyTextStyleToSelected();
                showTextOptions();
            });

            colorView.setClickable(true);
            colorView.setFocusable(true);

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                circleSize,
                circleSize
            );
            params.setMargins(8, 0, 8, 0);
            optionsContainer.addView(colorView, params);
        }

        addOptionButton("RGB调色", v -> showRGBColorPicker());

        addOptionButton("返回", v -> showTextOptions());
    }
    
    private void showRGBColorPicker() {
        optionsContainer.removeAllViews();
        
        LinearLayout rgbContainer = new LinearLayout(this);
        rgbContainer.setOrientation(LinearLayout.VERTICAL);
        rgbContainer.setPadding(8, 8, 8, 8);
        rgbContainer.setClickable(false);
        rgbContainer.setFocusable(false);
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int buttonWidth = (int) (32 * getResources().getDisplayMetrics().density); // 按钮宽度约48dp
        int padding = (int) (32 * getResources().getDisplayMetrics().density); // 左右边距
        int availableWidth = screenWidth - buttonWidth * 4 - padding * 2;
        rgbContainer.setLayoutParams(new LinearLayout.LayoutParams(
                availableWidth,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        int r = Color.red(currentTextColor);
        int g = Color.green(currentTextColor);
        int b = Color.blue(currentTextColor);
        
        // R
        LinearLayout rLayout = createRGBSeekBar("R", r, 0, value -> {
            currentTextColor = Color.rgb(value, Color.green(currentTextColor), Color.blue(currentTextColor));
            applyTextStyleToSelected();
        });
        rgbContainer.addView(rLayout);
        
        // G
        LinearLayout gLayout = createRGBSeekBar("G", g, 1, value -> {
            currentTextColor = Color.rgb(Color.red(currentTextColor), value, Color.blue(currentTextColor));
            applyTextStyleToSelected();
        });
        rgbContainer.addView(gLayout);
        
        // B
        LinearLayout bLayout = createRGBSeekBar("B", b, 2, value -> {
            currentTextColor = Color.rgb(Color.red(currentTextColor), Color.green(currentTextColor), value);
            applyTextStyleToSelected();
        });
        rgbContainer.addView(bLayout);
        
        optionsContainer.addView(rgbContainer);
        addSmallBackButton(v -> showColorOptions());
    }
    
    @SuppressLint("ClickableViewAccessibility")
    private LinearLayout createRGBSeekBar(String label, int value, int index, java.util.function.IntConsumer onValueChanged) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setPadding(0, 8, 0, 8);
        // 确保不拦截触摸事件
        layout.setClickable(false);
        layout.setFocusable(false);
        // 设置layout的宽度为MATCH_PARENT，让SeekBar能够正确扩展
        layout.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        TextView labelView = new TextView(this);
        labelView.setText(label + ": " + value);
        labelView.setTextColor(Color.WHITE);
        labelView.setTextSize(12);
        labelView.setMinWidth(50);
        layout.addView(labelView);

        SeekBar seekBar = new SeekBar(this);
        seekBar.setMax(255);
        seekBar.setProgress(value);
        // 确保SeekBar可以接收触摸事件
        seekBar.setEnabled(true);
        // 设置触摸事件处理，确保事件不被父容器拦截
        seekBar.setOnTouchListener((v, event) -> {
            // 让 SeekBar 自己处理触摸事件，阻止父容器拦截
            v.getParent().requestDisallowInterceptTouchEvent(true);
            return false; // 返回 false 让 SeekBar 继续处理事件
        });
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    labelView.setText(label + ": " + progress);
                    onValueChanged.accept(progress);
                }
            }
            
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f
        );
        layout.addView(seekBar, params);
        
        return layout;
    }
    
    @SuppressLint("ClickableViewAccessibility")
    private void showAlphaOptions() {
        optionsContainer.removeAllViews();
        
        LinearLayout alphaContainer = new LinearLayout(this);
        alphaContainer.setOrientation(LinearLayout.VERTICAL);
        alphaContainer.setPadding(8, 8, 8, 8);
        // 确保不拦截触摸事件
        alphaContainer.setClickable(false);
        alphaContainer.setFocusable(false);
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int buttonWidth = (int) (32 * getResources().getDisplayMetrics().density);
        int padding = (int) (64 * getResources().getDisplayMetrics().density);
        int availableWidth = screenWidth - buttonWidth * 4 - padding;
        alphaContainer.setLayoutParams(new LinearLayout.LayoutParams(
                availableWidth,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        TextView label = new TextView(this);
        int alphaPercent = (int)((currentTextAlpha / 255.0f) * 100);
        label.setText("透明度: " + alphaPercent + "%");
        label.setTextColor(Color.WHITE);
        label.setTextSize(14);
        alphaContainer.addView(label);
        
        SeekBar alphaSeekBar = new SeekBar(this);
        alphaSeekBar.setMax(155); // 50%-100%对应128-255，所以是128-255共128个级别，但用155更简单
        // 将50%-100%映射到0-155
        int progress = (int)((currentTextAlpha - 128) / 127.0f * 155);
        alphaSeekBar.setProgress(Math.max(0, Math.min(155, progress)));
        // 确保SeekBar可以接收触摸事件
        alphaSeekBar.setEnabled(true);
        // 设置触摸事件处理，确保事件不被父容器拦截
        alphaSeekBar.setOnTouchListener((v, event) -> {
            // 在 ACTION_DOWN 时阻止父容器拦截触摸事件
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                v.getParent().requestDisallowInterceptTouchEvent(true);
            }
            return false; // 返回 false 让 SeekBar 继续处理事件
        });
        alphaSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    // 将0-155映射到128-255 (50%-100%)
                    currentTextAlpha = 128 + (int)(progress / 155.0f * 127);
                    int alphaPercent = (int)((currentTextAlpha / 255.0f) * 100);
                    label.setText("透明度: " + alphaPercent + "%");
                    applyTextStyleToSelected();
                }
            }
            
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        
        alphaContainer.addView(alphaSeekBar);
        optionsContainer.addView(alphaContainer);
        
        addSmallBackButton(v -> showTextOptions());
    }
    
    private void applyTextStyleToSelected() {
        if (selectedTextView != null) {
            selectedTextView.setTypeface(currentTypeface);
            selectedTextView.setTextSize(currentTextSize);
            selectedTextView.setTextColor(currentTextColor);
            selectedTextView.setTextAlpha(currentTextAlpha);
        }
    }
    
    @SuppressLint("ClickableViewAccessibility")
    private void showAdjustOptions() {
        LinearLayout adjustContainer = new LinearLayout(this);
        adjustContainer.setOrientation(LinearLayout.VERTICAL);
        adjustContainer.setPadding(8, 8, 8, 8);
        // 确保不拦截触摸事件
        adjustContainer.setClickable(false);
        adjustContainer.setFocusable(false);
        // 计算可用宽度：屏幕宽度减去左右按钮和边距
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int buttonWidth = (int) (32 * getResources().getDisplayMetrics().density); // 按钮宽度约32dp
        int padding = (int) (64 * getResources().getDisplayMetrics().density); // 左右边距
        int availableWidth = screenWidth - buttonWidth * 2 - padding;
        // 使用明确的宽度，确保SeekBar有足够的空间
        adjustContainer.setLayoutParams(new LinearLayout.LayoutParams(
            availableWidth,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        
        // 亮度调节
        LinearLayout brightnessLayout = new LinearLayout(this);
        brightnessLayout.setOrientation(LinearLayout.VERTICAL);
        brightnessLayout.setPadding(0, 0, 0, 24);
        // 确保不拦截触摸事件
        brightnessLayout.setClickable(false);
        brightnessLayout.setFocusable(false);
        
        TextView brightnessLabel = new TextView(this);
        brightnessLabel.setText("亮度");
        brightnessLabel.setTextColor(Color.WHITE);
        brightnessLabel.setTextSize(14);
        brightnessLayout.addView(brightnessLabel);
        
        LinearLayout brightnessControl = new LinearLayout(this);
        brightnessControl.setOrientation(LinearLayout.HORIZONTAL);
        brightnessControl.setPadding(0, 8, 0, 0);
        brightnessControl.setGravity(android.view.Gravity.CENTER_VERTICAL);
        brightnessControl.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        brightnessControl.setClickable(false);
        brightnessControl.setFocusable(false);
        
        SeekBar brightnessSeekBar = new SeekBar(this);
        brightnessSeekBar.setMax(200);
        brightnessSeekBar.setProgress((int)(currentBrightness + 100)); // 根据当前亮度值设置进度
        brightnessSeekBar.setClickable(true);
        brightnessSeekBar.setFocusable(true);
        brightnessSeekBar.setEnabled(true);
        brightnessSeekBar.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                v.getParent().requestDisallowInterceptTouchEvent(true);
            }
            return false;
        });
        LinearLayout.LayoutParams brightnessSeekBarParams = new LinearLayout.LayoutParams(
            0, 
            LinearLayout.LayoutParams.WRAP_CONTENT, 
            1.0f
        );
        brightnessSeekBarParams.setMargins(0, 0, 16, 0);
        brightnessControl.addView(brightnessSeekBar, brightnessSeekBarParams);
        
        TextView brightnessValue = new TextView(this);
        brightnessValue.setText(String.valueOf((int)currentBrightness));
        brightnessValue.setTextColor(Color.WHITE);
        brightnessValue.setTextSize(14);
        brightnessValue.setMinWidth(50);
        brightnessValue.setGravity(android.view.Gravity.CENTER);
        brightnessControl.addView(brightnessValue, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        
        brightnessLayout.addView(brightnessControl);
        adjustContainer.addView(brightnessLayout);
        
        brightnessSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    currentBrightness = progress - 100;
                    brightnessValue.setText(String.valueOf((int)currentBrightness));
                    applyBrightnessContrast();
                }
            }
            
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        
        // 对比度调节
        LinearLayout contrastLayout = new LinearLayout(this);
        contrastLayout.setOrientation(LinearLayout.VERTICAL);
        // 确保不拦截触摸事件
        contrastLayout.setClickable(false);
        contrastLayout.setFocusable(false);
        
        TextView contrastLabel = new TextView(this);
        contrastLabel.setText("对比度");
        contrastLabel.setTextColor(Color.WHITE);
        contrastLabel.setTextSize(14);
        contrastLayout.addView(contrastLabel);
        
        LinearLayout contrastControl = new LinearLayout(this);
        contrastControl.setOrientation(LinearLayout.HORIZONTAL);
        contrastControl.setPadding(0, 8, 0, 0);
        contrastControl.setGravity(android.view.Gravity.CENTER_VERTICAL);
        contrastControl.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        // 确保不拦截触摸事件
        contrastControl.setClickable(false);
        contrastControl.setFocusable(false);
        
        SeekBar contrastSeekBar = new SeekBar(this);
        contrastSeekBar.setMax(200);
        // 根据当前对比度值设置进度：-50->0, 0->100, 150->200
        int contrastProgress;
        if (currentContrast <= 0) {
            contrastProgress = (int)((currentContrast + 50) / 50.0f * 100);
        } else {
            contrastProgress = (int)(100 + (currentContrast / 150.0f * 100));
        }
        contrastSeekBar.setProgress(Math.max(0, Math.min(200, contrastProgress)));
        contrastSeekBar.setClickable(true);
        contrastSeekBar.setFocusable(true);
        contrastSeekBar.setEnabled(true);
        contrastSeekBar.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                v.getParent().requestDisallowInterceptTouchEvent(true);
            }
            return false;
        });
        LinearLayout.LayoutParams contrastSeekBarParams = new LinearLayout.LayoutParams(
            0, 
            LinearLayout.LayoutParams.WRAP_CONTENT, 
            1.0f
        );
        contrastSeekBarParams.setMargins(0, 0, 16, 0);
        contrastControl.addView(contrastSeekBar, contrastSeekBarParams);
        
        TextView contrastValue = new TextView(this);
        contrastValue.setText(String.valueOf((int)currentContrast));
        contrastValue.setTextColor(Color.WHITE);
        contrastValue.setTextSize(14);
        contrastValue.setMinWidth(50);
        contrastValue.setGravity(android.view.Gravity.CENTER);
        contrastControl.addView(contrastValue, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        
        contrastLayout.addView(contrastControl);
        adjustContainer.addView(contrastLayout);
        
        contrastSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    // SeekBar范围0-200，映射到对比度-50到150，progress=100时对应0
                    if (progress <= 100) {
                        currentContrast = (progress / 100.0f) * 50 - 50;
                    } else {
                        currentContrast = ((progress - 100) / 100.0f) * 150;
                    }
                    contrastValue.setText(String.valueOf((int)currentContrast));
                    applyBrightnessContrast();
                }
            }
            
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        
        optionsContainer.addView(adjustContainer);
    }
    
    private void showStickerOptions() {
        // 创建贴纸选择按钮
        StickerUtils.StickerType[] stickers = StickerUtils.getAllStickers();
        
        for (StickerUtils.StickerType stickerType : stickers) {
            int stickerResId = StickerUtils.getStickerResourceId(stickerType);
            
            // 使用ImageView显示贴纸图片
            android.widget.ImageView stickerImageView = new android.widget.ImageView(this);
            stickerImageView.setImageResource(stickerResId);
            stickerImageView.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
            stickerImageView.setAdjustViewBounds(true);
            
            // 设置点击监听
            stickerImageView.setOnClickListener(v -> {
                addSticker(stickerResId);
            });
            
            // 设置可点击和焦点
            stickerImageView.setClickable(true);
            stickerImageView.setFocusable(true);
            
            // 设置固定尺寸，使贴纸预览更统一（稍微小一点）
            int stickerPreviewSize = (int) (getResources().getDisplayMetrics().density * 32);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                stickerPreviewSize,
                stickerPreviewSize
            );
            params.setMargins(8, 0, 8, 0);
            optionsContainer.addView(stickerImageView, params);
        }
    }
    
    private void setupStickerMode() {
        // 设置容器点击监听，点击空白处取消选中
        editContainer.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                // 检查点击位置是否在贴纸上
                boolean clickedOnSticker = false;
                float x = event.getX();
                float y = event.getY();
                
                for (StickerView stickerView : stickerViews) {
                    float stickerX = stickerView.getX();
                    float stickerY = stickerView.getY();
                    float stickerWidth = stickerView.getWidth();
                    float stickerHeight = stickerView.getHeight();
                    
                    if (x >= stickerX && x <= stickerX + stickerWidth &&
                        y >= stickerY && y <= stickerY + stickerHeight) {
                        clickedOnSticker = true;
                        break;
                    }
                }
                
                // 如果点击在空白处，取消所有贴纸的选中状态
                if (!clickedOnSticker) {
                    if (selectedSticker != null) {
                        selectedSticker.setSelected(false);
                        selectedSticker = null;
                    }
                }
            }
            return false; // 返回false让事件继续传递
        });
    }
    
    private void addSticker(int stickerResId) {
        StickerView stickerView = new StickerView(this);
        stickerView.setStickerResource(stickerResId);
        
        // 设置初始位置（容器中心）
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.leftMargin = (int) (editContainer.getWidth() / 2f - 60);
        params.topMargin = (int) (editContainer.getHeight() / 2f - 60);
        
        stickerView.setLayoutParams(params);
        
        // 设置监听器
        stickerView.setOnStickerDeleteListener(view -> {
            removeSticker(view);
        });
        
        stickerView.setOnStickerSelectedListener(view -> {
            selectSticker(view);
        });
        
        stickerView.setOnStickerBringToFrontListener(view -> {
            bringStickerToFront(view);
        });
        
        // 添加到容器和列表
        editContainer.addView(stickerView);
        stickerViews.add(stickerView);
        
        // 自动选中新添加的贴纸
        selectSticker(stickerView);
    }
    
    private void selectSticker(StickerView stickerView) {
        // 取消之前选中的贴纸
        if (selectedSticker != null && selectedSticker != stickerView) {
            selectedSticker.setSelected(false);
        }
        
        // 选中新的贴纸
        selectedSticker = stickerView;
        stickerView.setSelected(true);
    }
    
    private void removeSticker(StickerView stickerView) {
        editContainer.removeView(stickerView);
        stickerViews.remove(stickerView);
        if (selectedSticker == stickerView) {
            selectedSticker = null;
        }
    }
    
    private void bringStickerToFront(StickerView stickerView) {
        // 将贴纸移到最上层
        editContainer.bringChildToFront(stickerView);
        // 更新列表顺序（将当前贴纸移到列表末尾）
        stickerViews.remove(stickerView);
        stickerViews.add(stickerView);
        // 确保按钮在最上层
        stickerView.bringToFront();
        // 刷新视图
        editContainer.invalidate();
    }
    
    private void clearAllStickers() {
        for (StickerView stickerView : stickerViews) {
            editContainer.removeView(stickerView);
        }
        stickerViews.clear();
        selectedSticker = null;
    }
    
    private void applyStickers() {
        if (stickerViews.isEmpty()) {
            return;
        }
        
        // 将贴纸绘制到baseBitmap上
        float imageViewWidth = imageView.getWidth();
        float imageViewHeight = imageView.getHeight();
        if (imageViewWidth == 0 || imageViewHeight == 0) {
            imageViewWidth = editContainer.getWidth();
            imageViewHeight = editContainer.getHeight();
        }
        
        Bitmap stickerBitmap = StickerDrawUtils.drawStickersOnBitmap(
            baseBitmap, stickerViews, imageViewWidth, imageViewHeight);
        // 释放旧的baseBitmap和currentBitmap
        Bitmap oldBaseBitmap = baseBitmap;
        Bitmap oldCurrentBitmap = currentBitmap;
        baseBitmap = stickerBitmap;
        
        // 优化：如果不需要滤镜和亮度对比度，直接使用baseBitmap
        if (currentFilter == FilterUtils.FilterType.ORIGINAL && 
            currentBrightness == 0 && currentContrast == 0) {
            currentBitmap = baseBitmap;
        } else {
            currentBitmap = baseBitmap.copy(Bitmap.Config.ARGB_8888, true);
        }
        
        // 释放旧的bitmap
        if (oldBaseBitmap != null && oldBaseBitmap != originalBitmap && 
            oldBaseBitmap != baseBitmap) {
            oldBaseBitmap.recycle();
        }
        if (oldCurrentBitmap != null && oldCurrentBitmap != originalBitmap && 
            oldCurrentBitmap != baseBitmap && oldCurrentBitmap != currentBitmap) {
            oldCurrentBitmap.recycle();
        }
        
        // 重新应用滤镜和亮度对比度
        if (currentFilter != FilterUtils.FilterType.ORIGINAL) {
            applyFilter();
        } else {
            applyBrightnessContrast();
        }
        imageView.setImageBitmap(currentBitmap);
        imageView.resetTransform();
        
        // 清除所有贴纸视图
        clearAllStickers();
    }
    
    
    private void showFilterOptions() {
        // 创建滤镜选择按钮
        FilterUtils.FilterType[] filters = {
            FilterUtils.FilterType.ORIGINAL,
            FilterUtils.FilterType.BLACK_WHITE,
            FilterUtils.FilterType.VINTAGE,
            FilterUtils.FilterType.FRESH,
            FilterUtils.FilterType.WARM,
            FilterUtils.FilterType.COOL
        };
        
        for (FilterUtils.FilterType filter : filters) {
            String filterName = FilterUtils.getFilterName(filter);
            Button filterButton = new Button(this);
            filterButton.setText(filterName);
            filterButton.setTextColor(Color.WHITE);
            filterButton.setTextSize(14);
            filterButton.setBackgroundColor(Color.TRANSPARENT);
            filterButton.setPadding(24, 16, 24, 16);
            filterButton.setMinHeight(48);
            
            // 如果当前滤镜已选中，设置选中样式
            if (filter == currentFilter) {
                filterButton.setTextColor(0xFFFF7A18);
                filterButton.setBackgroundColor(0x1AFF7A18);
            }
            
            filterButton.setOnClickListener(v -> {
                // 更新当前滤镜
                currentFilter = filter;
                
                // 应用滤镜
                applyFilter();
                
                // 更新所有按钮样式
                for (int i = 0; i < optionsContainer.getChildCount(); i++) {
                    View child = optionsContainer.getChildAt(i);
                    if (child instanceof Button) {
                        Button btn = (Button) child;
                        if (btn == v) {
                            btn.setTextColor(0xFFFF7A18);
                            btn.setBackgroundColor(0x1AFF7A18);
                        } else {
                            btn.setTextColor(Color.WHITE);
                            btn.setBackgroundColor(Color.TRANSPARENT);
                        }
                    }
                }
            });
            
            optionsContainer.addView(filterButton);
        }
    }
    
    private void applyFilter() {
        if (baseBitmap == null) return;
        
        // 记录当前要应用的滤镜
        FilterUtils.FilterType filterToApply = currentFilter;
        pendingFilter = filterToApply;
        
        // 在后台线程处理滤镜
        filterExecutor.execute(() -> {
            // 检查是否已被新的滤镜请求覆盖
            if (pendingFilter != filterToApply) {
                return;
            }
            
            // 应用滤镜到baseBitmap，生成新的currentBitmap
            Bitmap filteredBitmap = FilterUtils.applyFilter(baseBitmap, filterToApply);
            
            // 检查是否已被新的滤镜请求覆盖
            if (pendingFilter != filterToApply || filteredBitmap == null) {
                if (filteredBitmap != null && filteredBitmap != baseBitmap) {
                    filteredBitmap.recycle();
                }
                return;
            }
            
            // 在主线程更新UI
            final Bitmap finalFilteredBitmap = filteredBitmap;
            mainHandler.post(() -> {
                // 再次检查是否已被新的滤镜请求覆盖
                if (pendingFilter != filterToApply) {
                    if (finalFilteredBitmap != null && finalFilteredBitmap != baseBitmap) {
                        finalFilteredBitmap.recycle();
                    }
                    return;
                }
                
                // 如果之前有亮度对比度调整，需要重新应用
                if (currentBrightness != 0 || currentContrast != 0) {
                    // 先应用滤镜，再应用亮度对比度
                    // 释放旧的currentBitmap（如果不是baseBitmap或originalBitmap）
                    Bitmap oldCurrentBitmap = currentBitmap;
                    currentBitmap = finalFilteredBitmap.copy(Bitmap.Config.RGB_565, true);
                    
                    // 释放旧的currentBitmap
                    if (oldCurrentBitmap != null && oldCurrentBitmap != baseBitmap && 
                        oldCurrentBitmap != originalBitmap && oldCurrentBitmap != currentBitmap) {
                        oldCurrentBitmap.recycle();
                    }
                    
                    // 如果finalFilteredBitmap是新创建的（不是baseBitmap），需要释放它
                    if (finalFilteredBitmap != baseBitmap && finalFilteredBitmap != originalBitmap) {
                        finalFilteredBitmap.recycle();
                    }
                    applyBrightnessContrast();
                } else {
                    // 优化：如果不需要亮度对比度，直接使用滤镜结果
                    // 释放旧的currentBitmap（如果不是baseBitmap或originalBitmap，且不是刚创建的filteredBitmap）
                    Bitmap oldCurrentBitmap = currentBitmap;
                    currentBitmap = finalFilteredBitmap;
                    
                    if (oldCurrentBitmap != null && oldCurrentBitmap != baseBitmap && 
                        oldCurrentBitmap != originalBitmap && oldCurrentBitmap != currentBitmap) {
                        oldCurrentBitmap.recycle();
                    }
                    
                    imageView.setImageBitmap(currentBitmap);
                    imageView.resetTransform();
                    // 强制刷新视图
                    imageView.invalidate();
                }
            });
        });
    }
    
    /**
     * 将滤镜永久应用到baseBitmap
     * 在确认编辑时调用，将滤镜效果合并到基础图片中
     */
    private void applyFilterToBase() {
        if (baseBitmap == null || currentFilter == FilterUtils.FilterType.ORIGINAL) {
            return;
        }
        
        // 将滤镜应用到baseBitmap（永久应用）
        Bitmap filteredBitmap = FilterUtils.applyFilter(baseBitmap, currentFilter);
        if (filteredBitmap != null && filteredBitmap != baseBitmap) {
            // 释放旧的baseBitmap（如果不是原始图片）
            if (baseBitmap != originalBitmap) {
                baseBitmap.recycle();
            }
            baseBitmap = filteredBitmap;
            // 重置滤镜状态，因为已经应用到baseBitmap了
            currentFilter = FilterUtils.FilterType.ORIGINAL;
        }
    }
    
    private void addOptionButton(String text, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextColor(Color.WHITE);
        button.setBackgroundColor(0x33FFFFFF);
        button.setPadding(16, 8, 16, 8);
        button.setTextSize(12);
        button.setOnClickListener(listener);
        
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(4, 0, 4, 0);
        optionsContainer.addView(button, params);
    }
    
    /**
     * 添加小的返回按钮，用于SeekBar相关的界面
     */
    private void addSmallBackButton(View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText("返回");
        button.setTextColor(Color.WHITE);
        button.setBackgroundColor(0x33FFFFFF);
        button.setPadding(8, 4, 8, 4);
        button.setTextSize(16);
        button.setOnClickListener(listener);
        int minSize = (int) (64 * getResources().getDisplayMetrics().density);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                minSize,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0,32,0,32);
        optionsContainer.addView(button, params);
    }
    
    private void updateCropButtonSelection(View selectedView) {
        for (int i = 0; i < optionsContainer.getChildCount(); i++) {
            View child = optionsContainer.getChildAt(i);
            if (child instanceof Button) {
                if (child == selectedView) {
                    child.setBackgroundColor(0xFFFF0000);
                    ((Button) child).setTextColor(Color.WHITE);
                } else {
                    child.setBackgroundColor(0x33FFFFFF);
                    ((Button) child).setTextColor(Color.WHITE);
                }
            }
        }
    }
    
    private void rotateImage(int angle) {
        Bitmap rotatedBitmap = ImageProcessUtils.rotateImage(baseBitmap, angle);
        if (rotatedBitmap != null) {
            // 释放旧的baseBitmap和currentBitmap（如果不是原始图片）
            if (baseBitmap != originalBitmap) {
                baseBitmap.recycle();
            }
            Bitmap oldCurrentBitmap = currentBitmap;
            baseBitmap = rotatedBitmap;
            
            // 优化：如果不需要滤镜和亮度对比度，直接使用baseBitmap
            if (currentFilter == FilterUtils.FilterType.ORIGINAL && 
                currentBrightness == 0 && currentContrast == 0) {
                currentBitmap = baseBitmap;
            } else {
                currentBitmap = baseBitmap.copy(Bitmap.Config.ARGB_8888, true);
            }
            
            // 释放旧的currentBitmap
            if (oldCurrentBitmap != null && oldCurrentBitmap != originalBitmap && 
                oldCurrentBitmap != baseBitmap && oldCurrentBitmap != currentBitmap) {
                oldCurrentBitmap.recycle();
            }
            
            // 重新应用滤镜和亮度对比度
            if (currentFilter != FilterUtils.FilterType.ORIGINAL) {
                applyFilter();
            } else {
                applyBrightnessContrast();
            }
            imageView.setImageBitmap(currentBitmap);
            imageView.resetTransform();
            
            // 更新裁剪覆盖层的图片信息（因为图片尺寸可能已变化）
            if (currentMode == EditMode.CROP) {
                updateCropOverlayImageInfo();
            }
        }
    }
    
    private void flipHorizontal() {
        Bitmap flippedBitmap = ImageProcessUtils.flipHorizontal(baseBitmap);
        if (flippedBitmap != null) {
            // 释放旧的baseBitmap（如果不是原始图片）
            if (baseBitmap != originalBitmap) {
                baseBitmap.recycle();
            }
            Bitmap oldCurrentBitmap = currentBitmap;
            baseBitmap = flippedBitmap;
            
            // 优化：如果不需要滤镜和亮度对比度，直接使用baseBitmap
            if (currentFilter == FilterUtils.FilterType.ORIGINAL && 
                currentBrightness == 0 && currentContrast == 0) {
                currentBitmap = baseBitmap;
            } else {
                currentBitmap = baseBitmap.copy(Bitmap.Config.ARGB_8888, true);
            }
            
            // 释放旧的currentBitmap
            if (oldCurrentBitmap != null && oldCurrentBitmap != originalBitmap && 
                oldCurrentBitmap != baseBitmap && oldCurrentBitmap != currentBitmap) {
                oldCurrentBitmap.recycle();
            }
            
            // 重新应用滤镜和亮度对比度
            if (currentFilter != FilterUtils.FilterType.ORIGINAL) {
                applyFilter();
            } else {
                applyBrightnessContrast();
            }
            imageView.setImageBitmap(currentBitmap);
            imageView.resetTransform();
            
            // 更新裁剪覆盖层的图片信息
            if (currentMode == EditMode.CROP) {
                updateCropOverlayImageInfo();
            }
        }
    }
    
    private void flipVertical() {
        Bitmap flippedBitmap = ImageProcessUtils.flipVertical(baseBitmap);
        if (flippedBitmap != null) {
            // 释放旧的baseBitmap（如果不是原始图片）
            if (baseBitmap != originalBitmap) {
                baseBitmap.recycle();
            }
            Bitmap oldCurrentBitmap = currentBitmap;
            baseBitmap = flippedBitmap;
            
            // 优化：如果不需要滤镜和亮度对比度，直接使用baseBitmap
            if (currentFilter == FilterUtils.FilterType.ORIGINAL && 
                currentBrightness == 0 && currentContrast == 0) {
                currentBitmap = baseBitmap;
            } else {
                currentBitmap = baseBitmap.copy(Bitmap.Config.ARGB_8888, true);
            }
            
            // 释放旧的currentBitmap
            if (oldCurrentBitmap != null && oldCurrentBitmap != originalBitmap && 
                oldCurrentBitmap != baseBitmap && oldCurrentBitmap != currentBitmap) {
                oldCurrentBitmap.recycle();
            }
            
            // 重新应用滤镜和亮度对比度
            if (currentFilter != FilterUtils.FilterType.ORIGINAL) {
                applyFilter();
            } else {
                applyBrightnessContrast();
            }
            imageView.setImageBitmap(currentBitmap);
            imageView.resetTransform();
            
            // 更新裁剪覆盖层的图片信息
            if (currentMode == EditMode.CROP) {
                updateCropOverlayImageInfo();
            }
        }
    }
    
    private void applyBrightnessContrast() {
        if (baseBitmap == null) return;
        
        currentBitmap = ImageProcessUtils.applyBrightnessContrast(
            baseBitmap, currentBrightness, currentContrast);
        if (currentBitmap != null) {
            imageView.setImageBitmap(currentBitmap);
            imageView.resetTransform();
        }
    }
    
    /**
     * 应用当前编辑模式的操作
     * 根据不同的编辑模式执行相应的应用操作
     */
    private void applyCurrentEdit() {
        switch (currentMode) {
            case CROP:
                applyCrop();
                if (cropModeBaseBitmap != null && cropModeBaseBitmap != originalBitmap && 
                    cropModeBaseBitmap != baseBitmap) {
                    cropModeBaseBitmap.recycle();
                    cropModeBaseBitmap = null;
                }
                break;
            case ADJUST:
                if (currentBitmap != baseBitmap) {
                    if (baseBitmap != originalBitmap) {
                        baseBitmap.recycle();
                    }
                    baseBitmap = currentBitmap.copy(Bitmap.Config.RGB_565, true);
                }
                break;
            case ROTATE:
                // 旋转操作已经实时应用到baseBitmap，确认时清理保存的状态
                if (rotateModeBaseBitmap != null && rotateModeBaseBitmap != originalBitmap && 
                    rotateModeBaseBitmap != baseBitmap) {
                    rotateModeBaseBitmap.recycle();
                    rotateModeBaseBitmap = null;
                }
                break;
            case TEXT:
                // 文字模式下，将文字绘制到图片上
                applyTexts();
                break;
            case FILTER:
                // 将滤镜应用到baseBitmap（永久应用）
                applyFilterToBase();
                // 重新应用亮度对比度（如果有）
                Bitmap oldCurrentBitmap = currentBitmap;
                if (currentBrightness != 0 || currentContrast != 0) {
                    currentBitmap = baseBitmap.copy(Bitmap.Config.ARGB_8888, true);
                    applyBrightnessContrast();
                } else {
                    // 优化：如果不需要亮度对比度，直接使用baseBitmap
                    currentBitmap = baseBitmap;
                    imageView.setImageBitmap(currentBitmap);
                    imageView.resetTransform();
                }
                // 释放旧的currentBitmap
                if (oldCurrentBitmap != null && oldCurrentBitmap != baseBitmap && 
                    oldCurrentBitmap != originalBitmap && oldCurrentBitmap != currentBitmap) {
                    oldCurrentBitmap.recycle();
                }
                break;
            case STICKER:
                // 贴纸模式下，将贴纸绘制到图片上
                applyStickers();
                break;
        }
    }
    
    private void clearAllTextViews() {
        // 清除所有文字编辑模块
        for (EditableTextView textView : textViews) {
            editContainer.removeView(textView);
        }
        textViews.clear();
        selectedTextView = null;
    }
    
    private void applyTexts() {
        if (textViews.isEmpty()) {
            return;
        }
        
        // 将文字绘制到baseBitmap上
        float imageViewWidth = imageView.getWidth();
        float imageViewHeight = imageView.getHeight();
        if (imageViewWidth == 0 || imageViewHeight == 0) {
            imageViewWidth = editContainer.getWidth();
            imageViewHeight = editContainer.getHeight();
        }
        
        Bitmap textBitmap = TextDrawUtils.drawTextsOnBitmap(
            baseBitmap, textViews, imageViewWidth, imageViewHeight, getResources());
        // 释放旧的baseBitmap（如果不是原始图片）
        if (baseBitmap != originalBitmap && textBitmap != baseBitmap) {
            baseBitmap.recycle();
        }
        Bitmap oldBaseBitmap = baseBitmap;
        Bitmap oldCurrentBitmap = currentBitmap;
        baseBitmap = textBitmap;
        
        // 优化：如果不需要滤镜和亮度对比度，直接使用baseBitmap
        if (currentFilter == FilterUtils.FilterType.ORIGINAL && 
            currentBrightness == 0 && currentContrast == 0) {
            currentBitmap = baseBitmap;
        } else {
            currentBitmap = baseBitmap.copy(Bitmap.Config.ARGB_8888, true);
        }
        
        // 释放旧的bitmap
        if (oldBaseBitmap != null && oldBaseBitmap != originalBitmap && 
            oldBaseBitmap != baseBitmap) {
            oldBaseBitmap.recycle();
        }
        if (oldCurrentBitmap != null && oldCurrentBitmap != originalBitmap && 
            oldCurrentBitmap != baseBitmap && oldCurrentBitmap != currentBitmap) {
            oldCurrentBitmap.recycle();
        }
        
        // 重新应用滤镜和亮度对比度
        if (currentFilter != FilterUtils.FilterType.ORIGINAL) {
            applyFilter();
        } else {
            applyBrightnessContrast();
        }
        imageView.setImageBitmap(currentBitmap);
        imageView.resetTransform();
        
        // 清除所有文字编辑模块
        clearAllTextViews();
    }
    
    private void applyCrop() {
        android.graphics.RectF cropRect = cropOverlay.getCropRect();
        if (cropRect == null || cropRect.width() <= 0 || cropRect.height() <= 0) return;
        
        float imageWidth = baseBitmap.getWidth();
        float imageHeight = baseBitmap.getHeight();
        float imageViewWidth = imageView.getWidth();
        float imageViewHeight = imageView.getHeight();
        
        if (imageViewWidth == 0 || imageViewHeight == 0) {
            return;
        }
        
        // 使用工具类计算图片显示区域
        ImageCoordinateUtils.ImageDisplayInfo displayInfo = 
            ImageCoordinateUtils.calculateImageDisplayInfo(
                imageWidth, imageHeight, imageViewWidth, imageViewHeight);
        
        if (displayInfo == null) {
            return;
        }
        
        // 将裁剪框坐标转换为图片坐标
        float[] topLeft = ImageCoordinateUtils.viewToImageCoordinates(
            cropRect.left, cropRect.top, displayInfo);
        float[] bottomRight = ImageCoordinateUtils.viewToImageCoordinates(
            cropRect.right, cropRect.bottom, displayInfo);
        
        int x = (int) topLeft[0];
        int y = (int) topLeft[1];
        int width = (int) (bottomRight[0] - topLeft[0]);
        int height = (int) (bottomRight[1] - topLeft[1]);
        
        if (x < 0) x = 0;
        if (y < 0) y = 0;
        if (x + width > baseBitmap.getWidth()) width = baseBitmap.getWidth() - x;
        if (y + height > baseBitmap.getHeight()) height = baseBitmap.getHeight() - y;
        
        if (width > 0 && height > 0) {
            Bitmap croppedBitmap = Bitmap.createBitmap(baseBitmap, x, y, width, height);
            // 释放旧的baseBitmap和currentBitmap
            Bitmap oldBaseBitmap = baseBitmap;
            Bitmap oldCurrentBitmap = currentBitmap;
            baseBitmap = croppedBitmap;
            
            // 优化：如果不需要滤镜和亮度对比度，直接使用baseBitmap
            if (currentFilter == FilterUtils.FilterType.ORIGINAL && 
                currentBrightness == 0 && currentContrast == 0) {
                currentBitmap = baseBitmap;
            } else {
                currentBitmap = baseBitmap.copy(Bitmap.Config.ARGB_8888, true);
            }
            
            // 释放旧的bitmap
            if (oldBaseBitmap != null && oldBaseBitmap != originalBitmap && 
                oldBaseBitmap != baseBitmap) {
                oldBaseBitmap.recycle();
            }
            if (oldCurrentBitmap != null && oldCurrentBitmap != originalBitmap && 
                oldCurrentBitmap != baseBitmap && oldCurrentBitmap != currentBitmap) {
                oldCurrentBitmap.recycle();
            }
            
            // 重新应用滤镜和亮度对比度
            if (currentFilter != FilterUtils.FilterType.ORIGINAL) {
                applyFilter();
            } else {
                applyBrightnessContrast();
            }
            imageView.setImageBitmap(currentBitmap);
            imageView.resetTransform();
            
            // 更新裁剪覆盖层的图片信息（因为图片尺寸已变化）
            if (currentMode == EditMode.CROP) {
                updateCropOverlayImageInfo();
            }
        }
    }
    
    private boolean checkStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return true;
        } else {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED;
        }
    }
    
    private void requestStoragePermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        } else {
            saveImage();
        }
    }
    
    /**
     * 保存编辑后的图片到相册
     * 在后台线程中执行，添加水印后保存
     */
    private void saveImage() {
        new Thread(() -> {
            try {
                if (currentBitmap == null) {
                    runOnUiThread(() -> Toast.makeText(this, "生成图片失败", Toast.LENGTH_SHORT).show());
                    return;
                }
                
                // 如果还有未应用的文字，先绘制到图片上
                Bitmap finalBitmap = currentBitmap;
                if (!textViews.isEmpty()) {
                    float imageViewWidth = imageView.getWidth();
                    float imageViewHeight = imageView.getHeight();
                    if (imageViewWidth == 0 || imageViewHeight == 0) {
                        imageViewWidth = editContainer.getWidth();
                        imageViewHeight = editContainer.getHeight();
                    }
                    finalBitmap = TextDrawUtils.drawTextsOnBitmap(
                        currentBitmap, textViews, imageViewWidth, imageViewHeight, getResources());
                }
                
                // 添加水印
                Bitmap watermarkedBitmap = ImageProcessUtils.addWatermark(finalBitmap, "训练营");
                
                // 保存到相册
                String savedPath = ImageSaveUtils.saveToGallery(this, watermarkedBitmap);
                
                runOnUiThread(() -> {
                    if (savedPath != null) {
                        // 跳转到保存成功界面
                        SaveSuccessActivity.start(this, savedPath);
                        finish();
                    } else {
                        Toast.makeText(this, "保存失败，请检查存储空间", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> Toast.makeText(this, "保存失败: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }
    
    public static void start(Context context, String imagePath) {
        Intent intent = new Intent(context, ImageEditActivity.class);
        intent.putExtra(EXTRA_IMAGE_PATH, imagePath);
        context.startActivity(intent);
    }
}
