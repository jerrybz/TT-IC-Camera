package com.tiktok.ic.camera.activity;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.tiktok.ic.camera.R;

import java.io.InputStream;

/**
 * 图片预览Activity
 * 支持单张图片预览，支持共享元素过渡动画
 */
public class ImagePreviewActivity extends AppCompatActivity {

    private static final String EXTRA_IMAGE_PATH = "image_path";
    private static final String EXTRA_IMAGE_LIST = "image_list";
    private static final String EXTRA_CURRENT_POSITION = "current_position";
    private static final String EXTRA_TRANSITION_NAME = "transition_name";
    private static final String EXTRA_MULTI_SELECT_MODE = "multi_select_mode";
    private static final String EXTRA_IS_SELECTED = "is_selected";
    private static final String EXTRA_VIEW_ONLY_MODE = "view_only_mode";
    
    private ImageView previewImageView;
    private Button selectButton;
    private TextView previewBottomTip;
    private android.widget.LinearLayout previewBottomBar;
    private String imagePath;
    private String transitionName;
    private boolean isMultiSelectMode = false;
    private boolean isSelected = false;
    private boolean isViewOnlyMode = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_preview);

        // 初始化UI组件
        previewImageView = findViewById(R.id.preview_image_view);
        selectButton = findViewById(R.id.select_button);
        previewBottomTip = findViewById(R.id.preview_bottom_tip);
        previewBottomBar = findViewById(R.id.preview_bottom_bar);
        Button backButton = findViewById(R.id.back_button);

        // 获取传递过来的图片路径和transitionName
        Intent intent = getIntent();
        if (intent != null && intent.hasExtra(EXTRA_IMAGE_PATH)) {
            imagePath = intent.getStringExtra(EXTRA_IMAGE_PATH);
            transitionName = intent.getStringExtra(EXTRA_TRANSITION_NAME);
            isMultiSelectMode = intent.getBooleanExtra(EXTRA_MULTI_SELECT_MODE, false);
            isSelected = intent.getBooleanExtra(EXTRA_IS_SELECTED, false);
            isViewOnlyMode = intent.getBooleanExtra(EXTRA_VIEW_ONLY_MODE, false);
            
            // 设置transitionName以支持共享元素过渡动画
            if (transitionName != null && previewImageView != null) {
                previewImageView.setTransitionName(transitionName);
            }
            
            // 等待布局完成后再加载图片，确保过渡动画平滑
            previewImageView.post(() -> {
                // 在下一帧加载图片，让过渡动画先开始
                previewImageView.postOnAnimation(() -> {
                    loadAndDisplayImage();
                });
            });
        }
        
        // 根据模式更新UI
        updateUIForMode();
        
        // 如果是仅查看模式（从保存成功界面跳转），隐藏底部操作栏
        if (isViewOnlyMode && previewBottomBar != null) {
            previewBottomBar.setVisibility(View.GONE);
        }

        // 设置按钮点击事件，支持返回时的过渡动画
        backButton.setOnClickListener(v -> {
            if (isMultiSelectMode) {
                // 多选模式：返回选择状态
                Intent resultIntent = new Intent();
                resultIntent.putExtra("image_path", imagePath);
                resultIntent.putExtra("is_selected", isSelected);
                setResult(RESULT_OK, resultIntent);
            }
            // 支持返回时的共享元素过渡动画
            supportFinishAfterTransition();
        });
        
        selectButton.setOnClickListener(v -> {
            if (isMultiSelectMode) {
                // 多选模式：选择图片，直接返回
                Intent resultIntent = new Intent();
                resultIntent.putExtra("image_path", imagePath);
                resultIntent.putExtra("is_selected", true); // 选择
                setResult(RESULT_OK, resultIntent);
                supportFinishAfterTransition();
            } else {
                // 单选模式：选择图片，返回上一页，支持返回时的过渡动画
                Intent resultIntent = new Intent();
                resultIntent.putExtra("selected_image", imagePath);
                setResult(RESULT_OK, resultIntent);
                supportFinishAfterTransition();
            }
        });
    }
    
    @Override
    public void onBackPressed() {
        if (isMultiSelectMode) {
            // 多选模式：返回选择状态
            Intent resultIntent = new Intent();
            resultIntent.putExtra("image_path", imagePath);
            resultIntent.putExtra("is_selected", isSelected);
            setResult(RESULT_OK, resultIntent);
        }
        // 支持系统返回键的过渡动画
        supportFinishAfterTransition();
    }

    private void loadAndDisplayImage() {
        try {
            // 加载图片
            Bitmap bitmap = decodeSampledBitmap(imagePath);
            if (bitmap != null) {
                previewImageView.setImageBitmap(bitmap);
            } else {
                Toast.makeText(this, "无法加载图片", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "加载图片出错", Toast.LENGTH_SHORT).show();
        }
    }

    private Bitmap decodeSampledBitmap(String pathOrUri) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        
        // 判断是URI还是文件路径
        if (pathOrUri != null && pathOrUri.startsWith("content://")) {
            // 从URI加载
            try {
                Uri uri = Uri.parse(pathOrUri);
                InputStream inputStream = getContentResolver().openInputStream(uri);
                if (inputStream != null) {
                    BitmapFactory.decodeStream(inputStream, null, options);
                    inputStream.close();
                } else {
                    return null;
                }
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        } else {
            // 从文件路径加载
            BitmapFactory.decodeFile(pathOrUri, options);
        }

        options.inSampleSize = calculateInSampleSize(options, previewImageView.getWidth(), previewImageView.getHeight());
        options.inJustDecodeBounds = false;

        // 再次加载实际图片
        if (pathOrUri != null && pathOrUri.startsWith("content://")) {
            // 从URI加载
            try {
                Uri uri = Uri.parse(pathOrUri);
                InputStream inputStream = getContentResolver().openInputStream(uri);
                if (inputStream != null) {
                    Bitmap bitmap = BitmapFactory.decodeStream(inputStream, null, options);
                    inputStream.close();
                    return bitmap;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        } else {
            // 从文件路径加载
            return BitmapFactory.decodeFile(pathOrUri, options);
        }
    }

    private int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        int inSampleSize = 1;
        int width = options.outWidth;
        int height = options.outHeight;

        if (reqWidth == 0 || reqHeight == 0) {
            return Math.max(1, Math.min(width, height) / 1080);
        }

        if (height > reqHeight || width > reqWidth) {
            int halfHeight = height / 2;
            int halfWidth = width / 2;

            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }

        return Math.max(1, inSampleSize);
    }

    public static Intent createIntent(Context context, String imagePath) {
        Intent intent = new Intent(context, ImagePreviewActivity.class);
        intent.putExtra(EXTRA_IMAGE_PATH, imagePath);
        return intent;
    }
    
    public static Intent createIntent(Context context, String imagePath, String transitionName) {
        Intent intent = new Intent(context, ImagePreviewActivity.class);
        intent.putExtra(EXTRA_IMAGE_PATH, imagePath);
        intent.putExtra(EXTRA_TRANSITION_NAME, transitionName);
        return intent;
    }
    
    public static Intent createIntentForViewOnly(Context context, String imagePath) {
        Intent intent = new Intent(context, ImagePreviewActivity.class);
        intent.putExtra(EXTRA_IMAGE_PATH, imagePath);
        intent.putExtra(EXTRA_VIEW_ONLY_MODE, true);
        return intent;
    }
    
    public static Intent createIntentForMultiSelect(Context context, String imagePath, String transitionName, boolean isSelected) {
        Intent intent = new Intent(context, ImagePreviewActivity.class);
        intent.putExtra(EXTRA_IMAGE_PATH, imagePath);
        intent.putExtra(EXTRA_TRANSITION_NAME, transitionName);
        intent.putExtra(EXTRA_MULTI_SELECT_MODE, true);
        intent.putExtra(EXTRA_IS_SELECTED, isSelected);
        return intent;
    }
    
    private void updateUIForMode() {
        if (isMultiSelectMode) {
            // 多选模式下，按钮始终显示"选择"
            selectButton.setText("选择");
            // 更新提示文本
            if (previewBottomTip != null) {
                previewBottomTip.setText("点击按钮选择这张图片");
            }
        }
    }
}