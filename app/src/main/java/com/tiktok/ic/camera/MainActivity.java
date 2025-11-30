package com.tiktok.ic.camera;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.widget.Button;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_CAMERA_PERMISSION = 100;
    private static final int REQUEST_STORAGE_PERMISSION = 101;

    private ActivityResultLauncher<Intent> galleryLauncher;
    private ActivityResultLauncher<Intent> cameraLauncher;

    private Uri photoUri;
    private String currentPhotoPath;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        
        // 设置系统栏边距 - 只设置左右和底部，允许轮播图覆盖顶部状态栏
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom); // 顶部padding设为0
            return insets;
        });

        // 初始化按钮
        Button btnImageEdit = findViewById(R.id.btn_image_edit);
        Button btnCamera = findViewById(R.id.btn_camera);

        initActivityLaunchers();

        // 设置点击事件
        btnImageEdit.setOnClickListener(v -> {
            // 检查并请求访问相册所需的权限
            requestGalleryPermissions();
        });

        btnCamera.setOnClickListener(v -> {
            // 调用权限请求方法
            requestCameraPermissions();
        });
    }

    private void requestGalleryPermissions() {
        // 根据Android版本请求不同的权限
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            // Android 13及以上版本
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) 
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, 
                        new String[]{Manifest.permission.READ_MEDIA_IMAGES}, 
                        REQUEST_STORAGE_PERMISSION);
            } else {
                openGallery();
            }
        } else {
            // Android 12及以下版本
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) 
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, 
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 
                        REQUEST_STORAGE_PERMISSION);
            } else {
                openGallery();
            }
        }
    }

    private void requestCameraPermissions() {
        // 相机权限是必须的
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) 
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, 
                    new String[]{Manifest.permission.CAMERA}, 
                    REQUEST_CAMERA_PERMISSION);
        } else {
            // 检查是否还需要存储权限（用于保存照片）
            if (android.os.Build.VERSION.SDK_INT <= android.os.Build.VERSION_CODES.R) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) 
                        != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this, 
                            new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 
                            REQUEST_STORAGE_PERMISSION);
                    return;
                }
            }
            // 权限都已授予，启动相机
            startCamera();
        }
    }

    private void initActivityLaunchers() {
        galleryLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() != RESULT_OK) {
                        return;
                    }
                    Intent data = result.getData();
                    if (data != null && data.hasExtra("selected_image_path")) {
                        String selectedImagePath = data.getStringExtra("selected_image_path");
                        Toast.makeText(this, "已选择图片", Toast.LENGTH_SHORT).show();
                        launchEditor(selectedImagePath);
                    }
                }
        );

        cameraLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK) {
                        Toast.makeText(this, "拍照成功", Toast.LENGTH_SHORT).show();
                        if (currentPhotoPath != null) {
                            launchEditor(currentPhotoPath);
                        }
                    } else {
                        cleanupCurrentPhoto();
                    }
                }
        );
    }
    
    private void openGallery() {
        // 跳转到我们自定义的相册Activity
        Toast.makeText(this, "打开相册", Toast.LENGTH_SHORT).show();
        galleryLauncher.launch(new Intent(MainActivity.this, ImageGalleryActivity.class));
        // 不再使用系统相册选择器
//        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
//        startActivityForResult(intent, REQUEST_GALLERY_ACCESS);
    }

    private void startCamera() {
        // 检查设备是否有相机
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) {
            Toast.makeText(this, "设备没有相机", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            File photoFile = null;
            try {
                photoFile = createImageFile();
            } catch (IOException e) {
                Toast.makeText(this, "无法创建照片文件", Toast.LENGTH_SHORT).show();
            }
            if (photoFile != null) {
                photoUri = FileProvider.getUriForFile(
                        this,
                        getPackageName() + ".fileprovider",
                        photoFile
                );
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
                takePictureIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                cameraLauncher.launch(takePictureIntent);
            }
        } else {
            Toast.makeText(this, "无法启动相机", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case REQUEST_STORAGE_PERMISSION:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // 判断是为了相机还是相册请求的权限
                    // 检查是否已经有相机权限，如果有则启动相机
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                        startCamera();
                    } else {
                        // 否则打开相册
                        openGallery();
                    }
                } else {
                    Toast.makeText(this, "需要存储权限才能访问媒体文件或保存照片", Toast.LENGTH_SHORT).show();
                    // 检查是否用户勾选了"不再询问"
                    if (!ActivityCompat.shouldShowRequestPermissionRationale(this, permissions[0])) {
                        // 用户拒绝并勾选了不再询问，可以引导用户去设置中开启权限
                        Toast.makeText(this, "请在设置中开启存储权限以使用完整功能", Toast.LENGTH_LONG).show();
                    }
                }
                break;
            case REQUEST_CAMERA_PERMISSION:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // 相机权限已授予，检查是否需要存储权限
                    if (android.os.Build.VERSION.SDK_INT <= android.os.Build.VERSION_CODES.R) {
                        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) 
                                != PackageManager.PERMISSION_GRANTED) {
                            ActivityCompat.requestPermissions(this, 
                                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 
                                    REQUEST_STORAGE_PERMISSION);
                            return;
                        }
                    }
                    startCamera();
                } else {
                    Toast.makeText(this, "需要相机权限才能拍照", Toast.LENGTH_SHORT).show();
                    // 检查是否用户勾选了"不再询问"
                    if (!ActivityCompat.shouldShowRequestPermissionRationale(this, permissions[0])) {
                        Toast.makeText(this, "请在设置中开启相机权限以使用拍照功能", Toast.LENGTH_LONG).show();
                    }
                }
                break;
        }
    }

    private void launchEditor(String imagePath) {
        if (imagePath == null) {
            Toast.makeText(this, "图片路径无效", Toast.LENGTH_SHORT).show();
            return;
        }
        ImageEditActivity.start(this, imagePath);
    }

    private File createImageFile() throws IOException {
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        if (storageDir == null) {
            storageDir = getFilesDir();
        }
        if (!storageDir.exists()) storageDir.mkdirs();
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        File image = File.createTempFile(
                "IMG_" + timeStamp + "_",
                ".jpg",
                storageDir
        );
        currentPhotoPath = image.getAbsolutePath();
        return image;
    }

    private void cleanupCurrentPhoto() {
        if (currentPhotoPath != null) {
            File file = new File(currentPhotoPath);
            if (file.exists()) {
                file.delete();
            }
        }
        currentPhotoPath = null;
        photoUri = null;
    }
}