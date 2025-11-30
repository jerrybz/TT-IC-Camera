package com.tiktok.ic.camera;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class ImagePreviewActivity extends AppCompatActivity {

    private static final String EXTRA_IMAGE_PATH = "image_path";
    private static final String EXTRA_IMAGE_LIST = "image_list";
    private static final String EXTRA_CURRENT_POSITION = "current_position";
    
    private ImageView previewImageView;
    private Button selectButton;
    private String imagePath;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_preview);

        // 初始化UI组件
        previewImageView = findViewById(R.id.preview_image_view);
        selectButton = findViewById(R.id.select_button);
        Button backButton = findViewById(R.id.back_button);

        // 获取传递过来的图片路径
        Intent intent = getIntent();
        if (intent != null && intent.hasExtra(EXTRA_IMAGE_PATH)) {
            imagePath = intent.getStringExtra(EXTRA_IMAGE_PATH);
            
            // 加载并显示图片
            loadAndDisplayImage();
        }

        // 设置按钮点击事件
        backButton.setOnClickListener(v -> finish());
        
        selectButton.setOnClickListener(v -> {
            // 选择图片，返回上一页
            Intent resultIntent = new Intent();
            resultIntent.putExtra("selected_image", imagePath);
            setResult(RESULT_OK, resultIntent);
            finish();
        });
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

    private Bitmap decodeSampledBitmap(String path) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, options);

        options.inSampleSize = calculateInSampleSize(options, previewImageView.getWidth(), previewImageView.getHeight());
        options.inJustDecodeBounds = false;

        return BitmapFactory.decodeFile(path, options);
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
}