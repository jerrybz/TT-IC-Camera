package com.tiktok.ic.camera.activity;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.tiktok.ic.camera.R;
import com.tiktok.ic.camera.utils.ShareUtils;

import java.io.InputStream;

/**
 * 保存成功Activity
 * 显示保存成功的提示和缩略图，支持查看大图和返回主页
 */
public class SaveSuccessActivity extends BaseActivity {

    private static final String EXTRA_IMAGE_URI = "image_uri";
    
    private ImageView thumbnailImageView;
    private TextView successText;
    private Button backButton;
    private android.widget.ImageButton tiktokButton;
    private String imageUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_save_success);

        // 初始化UI组件
        thumbnailImageView = findViewById(R.id.thumbnail_image_view);
        successText = findViewById(R.id.success_text);
        backButton = findViewById(R.id.back_button);
        tiktokButton = findViewById(R.id.tiktok_button);

        // 获取传递过来的图片URI
        Intent intent = getIntent();
        if (intent != null && intent.hasExtra(EXTRA_IMAGE_URI)) {
            imageUri = intent.getStringExtra(EXTRA_IMAGE_URI);
            loadThumbnail();
        } else {
            Toast.makeText(this, "图片信息丢失", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // 设置缩略图点击事件，查看大图
        thumbnailImageView.setOnClickListener(v -> {
            if (imageUri != null) {
                // 跳转到预览界面查看大图（仅查看模式，不显示编辑按钮）
                Intent previewIntent = ImagePreviewActivity.createIntentForViewOnly(this, imageUri);
                startActivity(previewIntent);
            }
        });

        // 设置返回按钮，返回主页
        backButton.setOnClickListener(v -> {
            // 返回主页
            Intent mainIntent = new Intent(this, MainActivity.class);
            mainIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(mainIntent);
            finish();
        });

        // 抖音一键分享按钮
        tiktokButton.setOnClickListener(v -> {
            // 使用ShareUtils的安全方法启动抖音应用
            ShareUtils.launchTikTokApp(this);
        });
    }

    private void loadThumbnail() {
        try {
            // 从URI加载图片
            Uri uri = Uri.parse(imageUri);
            
            // 先只解码边界，获取图片尺寸
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            
            InputStream inputStream = getContentResolver().openInputStream(uri);
            if (inputStream != null) {
                BitmapFactory.decodeStream(inputStream, null, options);
                inputStream.close();
            } else {
                Toast.makeText(this, "无法打开图片", Toast.LENGTH_SHORT).show();
                return;
            }

            // 计算缩略图尺寸（缩略图显示区域为300dp，转换为px约为900x900）
            int thumbnailSize = 900;
            int inSampleSize = calculateInSampleSize(options, thumbnailSize, thumbnailSize);

            // 重新打开流并解码缩略图
            options.inJustDecodeBounds = false;
            options.inSampleSize = inSampleSize;
            
            inputStream = getContentResolver().openInputStream(uri);
            if (inputStream != null) {
                Bitmap thumbnail = BitmapFactory.decodeStream(inputStream, null, options);
                inputStream.close();

                if (thumbnail != null) {
                    thumbnailImageView.setImageBitmap(thumbnail);
                } else {
                    Toast.makeText(this, "无法加载缩略图", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "无法打开图片", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "加载图片出错: " + e.getMessage(), Toast.LENGTH_SHORT).show();
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

    public static void start(android.content.Context context, String imageUri) {
        Intent intent = new Intent(context, SaveSuccessActivity.class);
        intent.putExtra(EXTRA_IMAGE_URI, imageUri);
        context.startActivity(intent);
    }
}
