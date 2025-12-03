package com.tiktok.ic.camera.activity;

import android.Manifest;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.tiktok.ic.camera.R;
import com.tiktok.ic.camera.utils.PermissionUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 图片拼图Activity
 * 支持横向、纵向、网格三种拼图模式，最多支持4张图片
 */
public class ImageCollageActivity extends BaseActivity {

    private static final int MIN_IMAGES = 2;
    private static final int MAX_IMAGES = 4;
    private static final int REQUEST_STORAGE_PERMISSION = 200;

    private ImageView previewImage;
    private LinearLayout selectedImagesContainer;
    private RadioGroup modeRadioGroup;
    private RadioButton radioHorizontal;
    private RadioButton radioVertical;
    private RadioButton radioGrid;
    private Button btnSelectImages;
    private Button btnSave;
    private Button btnBack;

    private List<String> selectedImagePaths = new ArrayList<>();
    private CollageMode currentMode = CollageMode.HORIZONTAL;

    private enum CollageMode {
        HORIZONTAL,  // 横向拼接
        VERTICAL,    // 纵向拼接
        GRID         // 网格拼接（2×2）
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_collage);

        initViews();
        setupListeners();

        // 获取已选择的图片路径
        ArrayList<String> paths = getIntent().getStringArrayListExtra("selected_paths");
        if (paths != null && paths.size() >= MIN_IMAGES && paths.size() <= MAX_IMAGES) {
            selectedImagePaths = paths;
            updateSelectedImagesPreview();
            updatePreview();
            // 如果已经有图片，隐藏选择按钮或改变其文本
            btnSelectImages.setText("重新选择图片");
        }

        // 检查权限
        if (!PermissionUtils.hasPermission(this, PermissionUtils.PermissionType.STORAGE)) {
            PermissionUtils.requestPermissionWithRationale(
                    this,
                    PermissionUtils.PermissionType.STORAGE,
                    REQUEST_STORAGE_PERMISSION,
                    "需要存储权限才能访问相册进行拼图，请允许访问相册权限。"
            );
        }
    }

    private void initViews() {
        previewImage = findViewById(R.id.preview_image);
        selectedImagesContainer = findViewById(R.id.selected_images_container);
        modeRadioGroup = findViewById(R.id.mode_radio_group);
        radioHorizontal = findViewById(R.id.radio_horizontal);
        radioVertical = findViewById(R.id.radio_vertical);
        radioGrid = findViewById(R.id.radio_grid);
        btnSelectImages = findViewById(R.id.btn_select_images);
        btnSave = findViewById(R.id.btn_save);
        btnBack = findViewById(R.id.btn_back);
    }

    private void setupListeners() {
        btnBack.setOnClickListener(v -> finish());

        btnSelectImages.setOnClickListener(v -> {
            if (!PermissionUtils.hasPermission(this, PermissionUtils.PermissionType.STORAGE)) {
                PermissionUtils.requestPermissionWithRationale(
                        this,
                        PermissionUtils.PermissionType.STORAGE,
                        REQUEST_STORAGE_PERMISSION,
                        "需要存储权限才能访问相册进行拼图，请允许访问相册权限。"
                );
                return;
            }
            openImageSelector();
        });

        btnSave.setOnClickListener(v -> {
            if (selectedImagePaths.size() < MIN_IMAGES) {
                Toast.makeText(this, "请至少选择" + MIN_IMAGES + "张图片", Toast.LENGTH_SHORT).show();
                return;
            }
            saveCollage();
        });

        modeRadioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.radio_horizontal) {
                currentMode = CollageMode.HORIZONTAL;
            } else if (checkedId == R.id.radio_vertical) {
                currentMode = CollageMode.VERTICAL;
            } else if (checkedId == R.id.radio_grid) {
                currentMode = CollageMode.GRID;
            }
            updatePreview();
        });
    }


    private void openImageSelector() {
        // 跳转到自定义的图片选择器，支持多选
        Intent intent = new Intent(this, ImageCollageSelectorActivity.class);
        intent.putStringArrayListExtra("selected_paths", new ArrayList<>(selectedImagePaths));
        startActivityForResult(intent, 100);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 100 && resultCode == RESULT_OK && data != null) {
            ArrayList<String> paths = data.getStringArrayListExtra("selected_paths");
            if (paths != null && paths.size() >= MIN_IMAGES && paths.size() <= MAX_IMAGES) {
                selectedImagePaths = paths;
                updateSelectedImagesPreview();
                updatePreview();
            } else if (paths != null && paths.size() < MIN_IMAGES) {
                Toast.makeText(this, "至少需要选择" + MIN_IMAGES + "张图片", Toast.LENGTH_SHORT).show();
            } else if (paths != null && paths.size() > MAX_IMAGES) {
                Toast.makeText(this, "最多只能选择" + MAX_IMAGES + "张图片", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_STORAGE_PERMISSION) {
            PermissionUtils.handlePermissionResult(
                    this,
                    PermissionUtils.PermissionType.STORAGE,
                    permissions,
                    grantResults,
                    () -> {
                        // 权限已授予，可以继续操作
                    },
                    () -> Toast.makeText(this, "需要存储权限才能访问相册", Toast.LENGTH_SHORT).show(),
                    () -> {
                        PermissionUtils.showPermissionRationale(
                                this,
                                PermissionUtils.PermissionType.STORAGE,
                                "需要存储权限才能访问相册进行拼图，请在设置中开启存储权限。",
                                () -> PermissionUtils.openAppSettings(this),
                                null
                        );
                    }
            );
        }
    }

    private void updateSelectedImagesPreview() {
        selectedImagesContainer.removeAllViews();
        for (String path : selectedImagePaths) {
            ImageView imageView = new ImageView(this);
            int size = (int) (120 * getResources().getDisplayMetrics().density);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, size);
            params.setMargins(0, 0, (int) (8 * getResources().getDisplayMetrics().density), 0);
            imageView.setLayoutParams(params);
            imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
            
            try {
                Bitmap bitmap = BitmapFactory.decodeFile(path);
                if (bitmap != null) {
                    imageView.setImageBitmap(bitmap);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            
            selectedImagesContainer.addView(imageView);
        }
    }

    private void updatePreview() {
        if (selectedImagePaths.size() < MIN_IMAGES) {
            previewImage.setImageBitmap(null);
            return;
        }

        new Thread(() -> {
            Bitmap collage = createCollage();
            runOnUiThread(() -> {
                if (collage != null) {
                    previewImage.setImageBitmap(collage);
                }
            });
        }).start();
    }

    private Bitmap createCollage() {
        if (selectedImagePaths.isEmpty()) {
            return null;
        }

        List<Bitmap> bitmaps = new ArrayList<>();
        for (String path : selectedImagePaths) {
            Bitmap bitmap = BitmapFactory.decodeFile(path);
            if (bitmap != null) {
                bitmaps.add(bitmap);
            }
        }

        if (bitmaps.isEmpty()) {
            return null;
        }

        Bitmap result;
        switch (currentMode) {
            case HORIZONTAL:
                result = createHorizontalCollage(bitmaps);
                break;
            case VERTICAL:
                result = createVerticalCollage(bitmaps);
                break;
            case GRID:
                result = createGridCollage(bitmaps);
                break;
            default:
                result = createHorizontalCollage(bitmaps);
        }

        return result;
    }

    private Bitmap createHorizontalCollage(List<Bitmap> bitmaps) {
        // 计算统一高度（使用最小高度）
        int minHeight = Integer.MAX_VALUE;
        for (Bitmap bitmap : bitmaps) {
            if (bitmap.getHeight() < minHeight) {
                minHeight = bitmap.getHeight();
            }
        }

        // 计算每张图片的宽度（保持宽高比）
        int totalWidth = 0;
        List<Bitmap> scaledBitmaps = new ArrayList<>();
        for (Bitmap bitmap : bitmaps) {
            float scale = (float) minHeight / bitmap.getHeight();
            int scaledWidth = (int) (bitmap.getWidth() * scale);
            Bitmap scaled = Bitmap.createScaledBitmap(bitmap, scaledWidth, minHeight, true);
            scaledBitmaps.add(scaled);
            totalWidth += scaledWidth;
        }

        // 创建结果Bitmap
        Bitmap result = Bitmap.createBitmap(totalWidth, minHeight, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(result);
        canvas.drawColor(Color.WHITE);

        // 绘制每张图片
        int x = 0;
        for (Bitmap bitmap : scaledBitmaps) {
            canvas.drawBitmap(bitmap, x, 0, null);
            x += bitmap.getWidth();
        }

        // 回收临时bitmap（只回收新创建的缩放bitmap）
        for (int i = 0; i < scaledBitmaps.size(); i++) {
            Bitmap scaled = scaledBitmaps.get(i);
            Bitmap original = bitmaps.get(i);
            if (scaled != original) {
                scaled.recycle();
            }
        }

        return result;
    }

    private Bitmap createVerticalCollage(List<Bitmap> bitmaps) {
        // 计算统一宽度（使用最小宽度）
        int minWidth = Integer.MAX_VALUE;
        for (Bitmap bitmap : bitmaps) {
            if (bitmap.getWidth() < minWidth) {
                minWidth = bitmap.getWidth();
            }
        }

        // 计算每张图片的高度（保持宽高比）
        int totalHeight = 0;
        List<Bitmap> scaledBitmaps = new ArrayList<>();
        for (Bitmap bitmap : bitmaps) {
            float scale = (float) minWidth / bitmap.getWidth();
            int scaledHeight = (int) (bitmap.getHeight() * scale);
            Bitmap scaled = Bitmap.createScaledBitmap(bitmap, minWidth, scaledHeight, true);
            scaledBitmaps.add(scaled);
            totalHeight += scaledHeight;
        }

        // 创建结果Bitmap
        Bitmap result = Bitmap.createBitmap(minWidth, totalHeight, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(result);
        canvas.drawColor(Color.WHITE);

        // 绘制每张图片
        int y = 0;
        for (Bitmap bitmap : scaledBitmaps) {
            canvas.drawBitmap(bitmap, 0, y, null);
            y += bitmap.getHeight();
        }

        // 回收临时bitmap（只回收新创建的缩放bitmap）
        for (int i = 0; i < scaledBitmaps.size(); i++) {
            Bitmap scaled = scaledBitmaps.get(i);
            Bitmap original = bitmaps.get(i);
            if (scaled != original) {
                scaled.recycle();
            }
        }

        return result;
    }

    private Bitmap createGridCollage(List<Bitmap> bitmaps) {
        // 网格拼接：2×2布局
        int count = bitmaps.size();
        if (count < 2) {
            return createHorizontalCollage(bitmaps);
        }

        // 计算统一的单元格尺寸（使用所有图片的平均尺寸）
        int totalWidth = 0;
        int totalHeight = 0;
        for (Bitmap bitmap : bitmaps) {
            totalWidth += bitmap.getWidth();
            totalHeight += bitmap.getHeight();
        }
        int avgWidth = totalWidth / count;
        int avgHeight = totalHeight / count;

        // 使用平均尺寸作为单元格尺寸，确保边缘整齐
        int cellWidth = avgWidth;
        int cellHeight = avgHeight;

        // 创建2×2网格
        int cols = 2;
        int rows = (count + 1) / 2; // 向上取整
        int resultWidth = cellWidth * cols;
        int resultHeight = cellHeight * rows;

        Bitmap result = Bitmap.createBitmap(resultWidth, resultHeight, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(result);
        canvas.drawColor(Color.WHITE);

        // 绘制每张图片到网格中，填充整个单元格（保持宽高比，居中显示）
        for (int i = 0; i < count; i++) {
            Bitmap bitmap = bitmaps.get(i);
            int row = i / cols;
            int col = i % cols;

            // 计算缩放比例，使图片填充单元格（保持宽高比）
            float scaleX = (float) cellWidth / bitmap.getWidth();
            float scaleY = (float) cellHeight / bitmap.getHeight();
            float scale = Math.min(scaleX, scaleY); // 保持宽高比，选择较小的缩放比例

            int scaledWidth = (int) (bitmap.getWidth() * scale);
            int scaledHeight = (int) (bitmap.getHeight() * scale);
            Bitmap scaled = Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true);

            // 计算居中位置，确保边缘整齐对齐
            int x = col * cellWidth + (cellWidth - scaledWidth) / 2;
            int y = row * cellHeight + (cellHeight - scaledHeight) / 2;

            canvas.drawBitmap(scaled, x, y, null);

            if (scaled != bitmap) {
                scaled.recycle();
            }
        }

        return result;
    }

    private void saveCollage() {
        new Thread(() -> {
            try {
                Bitmap collage = createCollage();
                if (collage == null) {
                    runOnUiThread(() -> Toast.makeText(this, "生成拼图失败", Toast.LENGTH_SHORT).show());
                    return;
                }

                // 添加水印
                Bitmap watermarked = addWatermark(collage, "训练营");

                // 保存到相册
                String savedPath = saveToGallery(watermarked);

                runOnUiThread(() -> {
                    if (savedPath != null) {
                        // 跳转到保存成功界面
                        SaveSuccessActivity.start(this, savedPath);
                        finish();
                    } else {
                        Toast.makeText(this, "保存失败，请检查存储空间", Toast.LENGTH_SHORT).show();
                    }
                });

                // 回收bitmap
                if (watermarked != collage) {
                    watermarked.recycle();
                }
                collage.recycle();
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> Toast.makeText(this, "保存失败: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
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
                    "COLLAGE_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date()) + ".jpg");
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
}
