package com.tiktok.ic.camera.activity;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.tiktok.ic.camera.utils.FilterUtils;
import com.tiktok.ic.camera.R;
import com.tiktok.ic.camera.utils.StickerUtils;
import com.tiktok.ic.camera.widget.CropOverlayView;
import com.tiktok.ic.camera.widget.EditableTextView;
import com.tiktok.ic.camera.widget.StickerView;
import com.tiktok.ic.camera.widget.ZoomableImageView;

import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 图片编辑Activity
 * 提供图片裁剪、旋转、文字添加、滤镜、贴纸、亮度对比度调节等功能
 */
public class ImageEditActivity extends AppCompatActivity {
    
    private static final String EXTRA_IMAGE_PATH = "image_path";
    
    // UI组件
    private ZoomableImageView imageView;
    private FrameLayout editContainer;
    private CropOverlayView cropOverlay;
    
    // 一级功能栏
    private LinearLayout primaryToolbar;
    private Button btnEdit;
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
    private HorizontalScrollView optionsPanel;
    private LinearLayout optionsContainer;
    
    // 图片相关
    private Bitmap originalBitmap; // 原始图片（用于恢复）
    private Bitmap baseBitmap; // 基础图片（应用了旋转、翻转、裁剪后的基础）
    private Bitmap currentBitmap; // 当前显示的图片（应用了亮度对比度）
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
        loadImage();
        setupToolbars();
    }
    
    private void initViews() {
        imageView = findViewById(R.id.image_view);
        editContainer = findViewById(R.id.edit_container);
        // 允许子视图超出边界显示（这样 StickerView 的按钮可以显示在边框外）
        editContainer.setClipChildren(false);
        editContainer.setClipToPadding(false);
        cropOverlay = findViewById(R.id.crop_overlay);
        
        primaryToolbar = findViewById(R.id.primary_toolbar);
        btnEdit = findViewById(R.id.btn_edit);
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
            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);
            
            options.inJustDecodeBounds = false;
            originalBitmap = BitmapFactory.decodeFile(imagePath, options);
            
            if (originalBitmap != null) {
                baseBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true);
                currentBitmap = baseBitmap.copy(Bitmap.Config.ARGB_8888, true);
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
    
    private int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;
        
        if (height > reqHeight || width > reqWidth) {
            final int halfHeight = height / 2;
            final int halfWidth = width / 2;
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }
        return inSampleSize;
    }
    
    private void setupToolbars() {
        // 一级功能栏 - 编辑按钮
        btnEdit.setOnClickListener(v -> {
            showSecondaryToolbar();
            enterEditMode(EditMode.CROP);
        });
        
        // 一级功能栏 - 保存按钮
        btnSave.setOnClickListener(v -> {
            if (checkStoragePermission()) {
                saveImage();
            } else {
                requestStoragePermission();
            }
        });
        
        // 二级功能栏 - 裁剪
        btnCrop.setOnClickListener(v -> enterEditMode(EditMode.CROP));
        
        // 二级功能栏 - 旋转
        btnRotate.setOnClickListener(v -> enterEditMode(EditMode.ROTATE));
        
        // 二级功能栏 - 文字
        btnText.setOnClickListener(v -> enterEditMode(EditMode.TEXT));
        
        // 二级功能栏 - 调节
        btnAdjust.setOnClickListener(v -> enterEditMode(EditMode.ADJUST));
        
        // 二级功能栏 - 滤镜
        btnFilter.setOnClickListener(v -> enterEditMode(EditMode.FILTER));
        
        // 二级功能栏 - 贴纸
        btnSticker.setOnClickListener(v -> enterEditMode(EditMode.STICKER));
        
        // 二级功能栏 - 取消
        btnCancel.setOnClickListener(v -> {
            if (currentMode == EditMode.TEXT) {
                // 文字模式下，清除所有文字编辑模块
                clearAllTextViews();
            } else if (currentMode == EditMode.FILTER) {
                // 滤镜模式下，恢复到原始图片（不应用滤镜）
                currentFilter = FilterUtils.FilterType.ORIGINAL;
                if (baseBitmap != null) {
                    currentBitmap = baseBitmap.copy(Bitmap.Config.ARGB_8888, true);
                    if (currentBrightness != 0 || currentContrast != 0) {
                        applyBrightnessContrast();
                    } else {
                        imageView.setImageBitmap(currentBitmap);
                        imageView.resetTransform();
                    }
                }
            } else if (currentMode == EditMode.STICKER) {
                // 贴纸模式下，清除所有贴纸
                clearAllStickers();
            } else {
                restoreToOriginal();
            }
            exitEditMode();
        });
        
        // 二级功能栏 - 确认
        btnConfirm.setOnClickListener(v -> {
            applyCurrentEdit();
            exitEditMode();
        });
    }
    
    private void showSecondaryToolbar() {
        primaryToolbar.setVisibility(View.GONE);
        secondaryToolbar.setVisibility(View.VISIBLE);
        optionsPanel.setVisibility(View.VISIBLE);
    }
    
    private void hideSecondaryToolbar() {
        primaryToolbar.setVisibility(View.VISIBLE);
        secondaryToolbar.setVisibility(View.GONE);
        optionsPanel.setVisibility(View.GONE);
        cropOverlay.setVisibility(View.GONE);
    }
    
    private void enterEditMode(EditMode mode) {
        currentMode = mode;
        updateSecondaryToolbarSelection();
        showOptionsForMode(mode);
        
        // 在调节模式和滤镜模式下禁用图片触摸，让滑动条和按钮能正常工作
        if (mode == EditMode.ADJUST || mode == EditMode.FILTER) {
            imageView.setTouchEnabled(false);
        } else {
            imageView.setTouchEnabled(true);
        }
        
        if (mode == EditMode.CROP) {
            cropOverlay.setVisibility(View.VISIBLE);
            cropOverlay.setCropRatio(cropRatio);
        } else {
            cropOverlay.setVisibility(View.GONE);
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
        hideSecondaryToolbar();
        cropOverlay.setVisibility(View.GONE);
    }
    
    private void restoreToOriginal() {
        // 恢复图片到原始状态
        if (originalBitmap != null) {
            baseBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true);
            currentBitmap = baseBitmap.copy(Bitmap.Config.ARGB_8888, true);
            
            // 重置所有状态
            currentBrightness = 0;
            currentContrast = 0;
            cropRatio = 0;
            currentFilter = FilterUtils.FilterType.ORIGINAL;
            
            // 更新显示
            imageView.setImageBitmap(currentBitmap);
            imageView.resetTransform();
            
            // 如果当前在调节模式，需要更新滑动条显示
            if (currentMode == EditMode.ADJUST) {
                // 重新显示调节选项以更新滑动条
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
        button.setTextColor(0xFFFF0000); // 红色文字
        button.setBackgroundColor(0x1AFF0000); // 浅红色背景
    }
    
    private void resetButtonStyle(Button button) {
        button.setTextColor(0xFF666666); // 灰色文字
        button.setBackgroundColor(Color.TRANSPARENT);
    }
    
    private void showOptionsForMode(EditMode mode) {
        optionsContainer.removeAllViews();
        
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
                // 检查点击位置是否在文字框上
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
                    // 取消所有文字框的选中状态
                    if (selectedTextView != null) {
                        selectedTextView.setSelected(false);
                        selectedTextView = null;
                    }
                    // 让容器获得焦点，这样EditText会失去焦点
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
            // 更新样式选项以反映当前选中文字的样式
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
        
        // 至少3种字体
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
        
        // 返回按钮
        addOptionButton("返回", v -> showTextOptions());
    }
    
    private void showTextSizeOptions() {
        optionsContainer.removeAllViews();
        
        LinearLayout sizeContainer = new LinearLayout(this);

        sizeContainer.setOrientation(LinearLayout.VERTICAL);
        sizeContainer.setPadding(16, 16, 16, 16);
        int screenWidth = (int) (getResources().getDisplayMetrics().widthPixels*0.7);
        sizeContainer.setLayoutParams(new LinearLayout.LayoutParams(
                screenWidth,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        TextView label = new TextView(this);
        label.setText("字号: " + (int)currentTextSize);
        label.setTextColor(Color.WHITE);
        label.setTextSize(14);
        sizeContainer.addView(label);
        
        SeekBar sizeSeekBar = new SeekBar(this);
        sizeSeekBar.setMax(24); // 12-36号，所以是24个级别
        sizeSeekBar.setProgress((int)(currentTextSize - 12));
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
        
        addOptionButton("返回", v -> showTextOptions());
    }
    
    private void showColorOptions() {
        optionsContainer.removeAllViews();
        
        // 10种预设颜色
        int[] presetColors = {
            Color.WHITE, Color.BLACK, Color.RED, Color.GREEN, Color.BLUE,
            Color.YELLOW, Color.CYAN, Color.MAGENTA, 0xFFFFA500, 0xFF800080
        };
        
        String[] colorNames = {"白色", "黑色", "红色", "绿色", "蓝色", 
                              "黄色", "青色", "紫色", "橙色", "紫色"};
        
        for (int i = 0; i < presetColors.length; i++) {
            final int color = presetColors[i];
            Button colorBtn = new Button(this);
            colorBtn.setText(colorNames[i]);
            colorBtn.setTextColor(Color.WHITE);
            colorBtn.setBackgroundColor(color);
            colorBtn.setPadding(24, 12, 24, 12);
            colorBtn.setTextSize(14);
            colorBtn.setOnClickListener(v -> {
                currentTextColor = color;
                applyTextStyleToSelected();
                showTextOptions();
            });
            
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            );
            params.setMargins(8, 0, 8, 0);
            optionsContainer.addView(colorBtn, params);
        }
        
        // RGB调色按钮
        addOptionButton("RGB调色", v -> showRGBColorPicker());
        
        // 返回按钮
        addOptionButton("返回", v -> showTextOptions());
    }
    
    private void showRGBColorPicker() {
        optionsContainer.removeAllViews();
        
        LinearLayout rgbContainer = new LinearLayout(this);
        rgbContainer.setOrientation(LinearLayout.VERTICAL);
        rgbContainer.setPadding(16, 16, 16, 16);
        int screenWidth = (int) (getResources().getDisplayMetrics().widthPixels*0.7);
        rgbContainer.setLayoutParams(new LinearLayout.LayoutParams(
                screenWidth,
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
        addOptionButton("返回", v -> showColorOptions());
    }
    
    private LinearLayout createRGBSeekBar(String label, int value, int index, java.util.function.IntConsumer onValueChanged) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setPadding(0, 8, 0, 8);

        TextView labelView = new TextView(this);
        labelView.setText(label + ": " + value);
        labelView.setTextColor(Color.WHITE);
        labelView.setTextSize(14);
        labelView.setMinWidth(60);
        layout.addView(labelView);

        SeekBar seekBar = new SeekBar(this);
        seekBar.setMax(255);
        seekBar.setProgress(value);
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
    
    private void showAlphaOptions() {
        optionsContainer.removeAllViews();
        
        LinearLayout alphaContainer = new LinearLayout(this);
        alphaContainer.setOrientation(LinearLayout.VERTICAL);
        alphaContainer.setPadding(16, 16, 16, 16);
        int screenWidth = (int) (getResources().getDisplayMetrics().widthPixels*0.7);
        alphaContainer.setLayoutParams(new LinearLayout.LayoutParams(
                screenWidth,
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
        
        addOptionButton("返回", v -> showTextOptions());
    }
    
    private void applyTextStyleToSelected() {
        if (selectedTextView != null) {
            selectedTextView.setTypeface(currentTypeface);
            selectedTextView.setTextSize(currentTextSize);
            selectedTextView.setTextColor(currentTextColor);
            selectedTextView.setTextAlpha(currentTextAlpha);
        }
    }
    
    private void showAdjustOptions() {
        LinearLayout adjustContainer = new LinearLayout(this);
        adjustContainer.setOrientation(LinearLayout.VERTICAL);
        adjustContainer.setPadding(16, 16, 16, 16);
        adjustContainer.setClickable(true);
        adjustContainer.setFocusable(false);

        int screenWidth = (int) (getResources().getDisplayMetrics().widthPixels*0.95);
        adjustContainer.setLayoutParams(new LinearLayout.LayoutParams(
            screenWidth,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        
        // 亮度调节
        LinearLayout brightnessLayout = new LinearLayout(this);
        brightnessLayout.setOrientation(LinearLayout.VERTICAL);
        brightnessLayout.setPadding(0, 0, 0, 24);
        
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
        
        SeekBar brightnessSeekBar = new SeekBar(this);
        brightnessSeekBar.setMax(200);
        brightnessSeekBar.setProgress((int)(currentBrightness + 100)); // 根据当前亮度值设置进度
        brightnessSeekBar.setClickable(true);
        brightnessSeekBar.setFocusable(true);
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
        brightnessControl.addView(brightnessValue, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        
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
                    // 使用分段线性映射：progress=0->-50, progress=100->0, progress=200->150
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
            String stickerName = StickerUtils.getStickerName(stickerType);
            int stickerResId = StickerUtils.getStickerResourceId(stickerType);
            
            Button stickerButton = new Button(this);
            stickerButton.setText(stickerName);
            stickerButton.setTextColor(Color.WHITE);
            stickerButton.setTextSize(14);
            stickerButton.setBackgroundColor(0x33FFFFFF);
            stickerButton.setPadding(24, 16, 24, 16);
            stickerButton.setMinHeight(48);
            
            // 在按钮上显示贴纸预览（可选，这里只显示文字）
            stickerButton.setOnClickListener(v -> {
                addSticker(stickerResId);
            });
            
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            );
            params.setMargins(8, 0, 8, 0);
            optionsContainer.addView(stickerButton, params);
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
        baseBitmap = drawStickersOnBitmap(baseBitmap);
        currentBitmap = baseBitmap.copy(Bitmap.Config.ARGB_8888, true);
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
    
    private Bitmap drawStickersOnBitmap(Bitmap bitmap) {
        if (bitmap == null || stickerViews.isEmpty()) {
            return bitmap;
        }
        
        Bitmap result = bitmap.copy(Bitmap.Config.ARGB_8888, true);
        Canvas canvas = new Canvas(result);
        
        // 计算图片和容器的缩放比例
        float scaleX = (float) bitmap.getWidth() / editContainer.getWidth();
        float scaleY = (float) bitmap.getHeight() / editContainer.getHeight();
        
        // 按顺序绘制所有贴纸（保持层级关系）
        for (StickerView stickerView : stickerViews) {
            // 获取贴纸的位置、大小、旋转角度
            float x = stickerView.getX() * scaleX;
            float y = stickerView.getY() * scaleY;
            float stickerWidth = stickerView.getWidth() * scaleX;
            float stickerHeight = stickerView.getHeight() * scaleY;
            float rotation = stickerView.getRotationAngle();
            float scale = stickerView.getScaleFactor();
            
            // 获取贴纸drawable的原始尺寸
            android.graphics.drawable.Drawable drawable = stickerView.getStickerDrawable();
            if (drawable == null) continue;
            
            int drawableWidth = drawable.getIntrinsicWidth();
            int drawableHeight = drawable.getIntrinsicHeight();
            if (drawableWidth <= 0) drawableWidth = 120; // 默认值
            if (drawableHeight <= 0) drawableHeight = 120; // 默认值
            
            // 计算绘制尺寸（考虑缩放）
            float drawWidth = drawableWidth * scaleX * scale;
            float drawHeight = drawableHeight * scaleY * scale;
            
            // 保存画布状态
            canvas.save();
            
            // 移动到贴纸中心位置
            canvas.translate(x + stickerWidth / 2, y + stickerHeight / 2);
            
            // 旋转
            canvas.rotate(rotation);
            
            // 设置drawable的bounds并绘制
            drawable.setBounds(
                (int)(-drawWidth / 2),
                (int)(-drawHeight / 2),
                (int)(drawWidth / 2),
                (int)(drawHeight / 2)
            );
            drawable.draw(canvas);
            
            // 恢复画布状态
            canvas.restore();
        }
        
        return result;
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
        
        // 应用滤镜到baseBitmap，生成新的currentBitmap
        Bitmap filteredBitmap = FilterUtils.applyFilter(baseBitmap, currentFilter);
        
        // 如果之前有亮度对比度调整，需要重新应用
        if (currentBrightness != 0 || currentContrast != 0) {
            // 先应用滤镜，再应用亮度对比度
            currentBitmap = filteredBitmap.copy(Bitmap.Config.ARGB_8888, true);
            applyBrightnessContrast();
        } else {
            currentBitmap = filteredBitmap;
            imageView.setImageBitmap(currentBitmap);
            imageView.resetTransform();
        }
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
        button.setPadding(24, 12, 24, 12);
        button.setTextSize(14);
        button.setOnClickListener(listener);
        
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(8, 0, 8, 0);
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
        Matrix matrix = new Matrix();
        matrix.postRotate(angle, baseBitmap.getWidth() / 2f, baseBitmap.getHeight() / 2f);
        baseBitmap = Bitmap.createBitmap(baseBitmap, 0, 0, 
            baseBitmap.getWidth(), baseBitmap.getHeight(), matrix, true);
        currentBitmap = baseBitmap.copy(Bitmap.Config.ARGB_8888, true);
        // 重新应用滤镜和亮度对比度
        if (currentFilter != FilterUtils.FilterType.ORIGINAL) {
            applyFilter();
        } else {
            applyBrightnessContrast();
        }
        imageView.setImageBitmap(currentBitmap);
        imageView.resetTransform();
    }
    
    private void flipHorizontal() {
        Matrix matrix = new Matrix();
        matrix.postScale(-1, 1, baseBitmap.getWidth() / 2f, baseBitmap.getHeight() / 2f);
        baseBitmap = Bitmap.createBitmap(baseBitmap, 0, 0, 
            baseBitmap.getWidth(), baseBitmap.getHeight(), matrix, true);
        currentBitmap = baseBitmap.copy(Bitmap.Config.ARGB_8888, true);
        // 重新应用滤镜和亮度对比度
        if (currentFilter != FilterUtils.FilterType.ORIGINAL) {
            applyFilter();
        } else {
            applyBrightnessContrast();
        }
        imageView.setImageBitmap(currentBitmap);
        imageView.resetTransform();
    }
    
    private void flipVertical() {
        Matrix matrix = new Matrix();
        matrix.postScale(1, -1, baseBitmap.getWidth() / 2f, baseBitmap.getHeight() / 2f);
        baseBitmap = Bitmap.createBitmap(baseBitmap, 0, 0, 
            baseBitmap.getWidth(), baseBitmap.getHeight(), matrix, true);
        currentBitmap = baseBitmap.copy(Bitmap.Config.ARGB_8888, true);
        // 重新应用滤镜和亮度对比度
        if (currentFilter != FilterUtils.FilterType.ORIGINAL) {
            applyFilter();
        } else {
            applyBrightnessContrast();
        }
        imageView.setImageBitmap(currentBitmap);
        imageView.resetTransform();
    }
    
    private void applyBrightnessContrast() {
        if (baseBitmap == null) return;
        
        Bitmap bitmap = baseBitmap.copy(Bitmap.Config.ARGB_8888, true);
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int[] pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
        
        float brightness = currentBrightness / 100.0f;
        float contrast = (currentContrast + 50) / 100.0f;
        
        for (int i = 0; i < pixels.length; i++) {
            int pixel = pixels[i];
            int a = (pixel >> 24) & 0xFF;
            int r = (pixel >> 16) & 0xFF;
            int g = (pixel >> 8) & 0xFF;
            int b = pixel & 0xFF;
            
            // 应用亮度
            r = (int) (r + brightness * 255);
            g = (int) (g + brightness * 255);
            b = (int) (b + brightness * 255);
            
            // 应用对比度
            r = (int) (((r / 255.0f - 0.5f) * contrast + 0.5f) * 255);
            g = (int) (((g / 255.0f - 0.5f) * contrast + 0.5f) * 255);
            b = (int) (((b / 255.0f - 0.5f) * contrast + 0.5f) * 255);
            
            // 限制范围
            r = Math.max(0, Math.min(255, r));
            g = Math.max(0, Math.min(255, g));
            b = Math.max(0, Math.min(255, b));
            
            pixels[i] = (a << 24) | (r << 16) | (g << 8) | b;
        }
        
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
        currentBitmap = bitmap;
        imageView.setImageBitmap(currentBitmap);
        imageView.resetTransform();
    }
    
    /**
     * 应用当前编辑模式的操作
     * 根据不同的编辑模式执行相应的应用操作
     */
    private void applyCurrentEdit() {
        switch (currentMode) {
            case CROP:
                applyCrop();
                break;
            case ADJUST:
                // 亮度对比度已经实时应用，这里只需要更新baseBitmap
                baseBitmap = currentBitmap.copy(Bitmap.Config.ARGB_8888, true);
                break;
            case TEXT:
                // 文字模式下，将文字绘制到图片上
                applyTexts();
                break;
            case FILTER:
                // 将滤镜应用到baseBitmap（永久应用）
                applyFilterToBase();
                // 重新应用亮度对比度（如果有）
                if (currentBrightness != 0 || currentContrast != 0) {
                    currentBitmap = baseBitmap.copy(Bitmap.Config.ARGB_8888, true);
                    applyBrightnessContrast();
                } else {
                    currentBitmap = baseBitmap.copy(Bitmap.Config.ARGB_8888, true);
                    imageView.setImageBitmap(currentBitmap);
                    imageView.resetTransform();
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
        baseBitmap = drawTextsOnBitmap(baseBitmap);
        currentBitmap = baseBitmap.copy(Bitmap.Config.ARGB_8888, true);
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
        
        // 将裁剪框坐标转换为图片坐标（基于baseBitmap）
        float scaleX = (float) baseBitmap.getWidth() / imageView.getWidth();
        float scaleY = (float) baseBitmap.getHeight() / imageView.getHeight();
        
        int x = (int) (cropRect.left * scaleX);
        int y = (int) (cropRect.top * scaleY);
        int width = (int) (cropRect.width() * scaleX);
        int height = (int) (cropRect.height() * scaleY);
        
        if (x < 0) x = 0;
        if (y < 0) y = 0;
        if (x + width > baseBitmap.getWidth()) width = baseBitmap.getWidth() - x;
        if (y + height > baseBitmap.getHeight()) height = baseBitmap.getHeight() - y;
        
        if (width > 0 && height > 0) {
            baseBitmap = Bitmap.createBitmap(baseBitmap, x, y, width, height);
            currentBitmap = baseBitmap.copy(Bitmap.Config.ARGB_8888, true);
            // 重新应用滤镜和亮度对比度
            if (currentFilter != FilterUtils.FilterType.ORIGINAL) {
                applyFilter();
            } else {
                applyBrightnessContrast();
            }
            imageView.setImageBitmap(currentBitmap);
            imageView.resetTransform();
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
                    finalBitmap = drawTextsOnBitmap(currentBitmap);
                }
                
                // 添加水印
                Bitmap watermarkedBitmap = addWatermark(finalBitmap, "训练营");
                
                // 保存到相册
                String savedPath = saveToGallery(watermarkedBitmap);
                
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
    
    private Bitmap drawTextsOnBitmap(Bitmap bitmap) {
        if (textViews.isEmpty()) {
            return bitmap;
        }
        
        Bitmap result = bitmap.copy(Bitmap.Config.ARGB_8888, true);
        Canvas canvas = new Canvas(result);
        
        float imageWidth = bitmap.getWidth();
        float imageHeight = bitmap.getHeight();
        float viewWidth = editContainer.getWidth();
        float viewHeight = editContainer.getHeight();
        
        if (viewWidth == 0 || viewHeight == 0) {
            return result;
        }
        
        // 计算图片在ImageView中的实际显示尺寸和位置
        // 需要考虑图片的缩放和居中显示
        float imageViewWidth = imageView.getWidth();
        float imageViewHeight = imageView.getHeight();
        
        // 计算图片的缩放比例（保持宽高比）
        float imageAspect = imageWidth / imageHeight;
        float viewAspect = imageViewWidth / imageViewHeight;
        
        float displayWidth, displayHeight;
        float offsetX = 0, offsetY = 0;
        
        if (imageAspect > viewAspect) {
            // 图片更宽，以宽度为准
            displayWidth = imageViewWidth;
            displayHeight = imageViewWidth / imageAspect;
            offsetY = (imageViewHeight - displayHeight) / 2;
        } else {
            // 图片更高，以高度为准
            displayHeight = imageViewHeight;
            displayWidth = imageViewHeight * imageAspect;
            offsetX = (imageViewWidth - displayWidth) / 2;
        }
        
        // 计算坐标转换比例
        float scaleX = imageWidth / displayWidth;
        float scaleY = imageHeight / displayHeight;
        
        for (EditableTextView textView : textViews) {
            if (textView.getText().isEmpty()) {
                continue;
            }
            
            // 获取文字在容器中的位置（相对于editContainer）
            float textX = textView.getX();
            float textY = textView.getY();
            float textWidth = textView.getWidth();
            float textHeight = textView.getHeight();
            
            // 计算文字中心点在容器中的位置
            float centerX = textX + textWidth / 2f;
            float centerY = textY + textHeight / 2f;
            
            // 转换为图片坐标（考虑图片在ImageView中的显示位置）
            float imageX = (centerX - offsetX) * scaleX;
            float imageY = (centerY - offsetY) * scaleY;
            
            // 获取文字样式信息
            float textSize = textView.getTextSize() * textView.getScaleFactor() * scaleX;
            int textColor = textView.getTextColor();
            float rotation = textView.getRotationAngle();
            android.graphics.Typeface typeface = textView.getTypeface();
            
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setColor(textColor);
            paint.setTextSize(textSize);
            paint.setTypeface(typeface);
            
            // 保存画布状态
            canvas.save();
            
            // 应用旋转
            canvas.rotate(rotation, imageX, imageY);
            
            // 绘制文字（支持多行）
            String text = textView.getText();
            String[] lines = text.split("\n");
            float lineHeight = paint.getTextSize() * 1.2f;
            
            // 计算文字起始位置（多行文字需要从第一行开始向上偏移）
            float startY = imageY - (lines.length - 1) * lineHeight / 2;
            
            for (String line : lines) {
                if (!line.isEmpty()) {
                    // 计算文字宽度，用于居中对齐
                    float textWidthPx = paint.measureText(line);
                    textX = imageX - textWidthPx / 2;
                    canvas.drawText(line, textX, startY, paint);
                }
                startY += lineHeight;
            }
            
            // 恢复画布状态
            canvas.restore();
        }
        
        return result;
    }
    
    private Bitmap addWatermark(Bitmap bitmap, String watermark) {
        Bitmap watermarked = bitmap.copy(Bitmap.Config.ARGB_8888, true);
        Canvas canvas = new Canvas(watermarked);
        
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.WHITE);
        // 根据图片大小动态调整水印字体大小
        float textSize = Math.max(bitmap.getWidth(), bitmap.getHeight()) * 0.03f; // 图片尺寸的3%
        textSize = Math.max(textSize, 60); // 最小60
        textSize = Math.min(textSize, 120); // 最大120
        paint.setTextSize(textSize);
        paint.setAlpha(220); // 提高不透明度，更明显
        
        float x = watermarked.getWidth() - paint.measureText(watermark) - 30;
        float y = watermarked.getHeight() - 30;
        
        canvas.drawText(watermark, x, y, paint);
        
        return watermarked;
    }
    
    private String saveToGallery(Bitmap bitmap) {
        try {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, 
                "IMG_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date()) + ".jpg");
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES);
            }
            
            Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (uri != null) {
                OutputStream outputStream = getContentResolver().openOutputStream(uri);
                if (outputStream != null) {
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream);
                    outputStream.close();
                    return uri.toString();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
    
    public static void start(Context context, String imagePath) {
        Intent intent = new Intent(context, ImageEditActivity.class);
        intent.putExtra(EXTRA_IMAGE_PATH, imagePath);
        context.startActivity(intent);
    }
}
