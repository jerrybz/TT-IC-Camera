package com.tiktok.ic.camera.activity;

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
import android.widget.ImageButton;

import com.tiktok.ic.camera.R;
import com.tiktok.ic.camera.utils.ThemeUtils;
import com.tiktok.ic.camera.utils.PermissionUtils;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

/**
 * 主界面Activity
 * 提供图片编辑、拍照、拼图等功能的入口
 */
public class MainActivity extends BaseActivity {

    private static final int REQUEST_CAMERA_PERMISSION = 100;
    private static final int REQUEST_STORAGE_PERMISSION = 101;

    private ActivityResultLauncher<Intent> galleryLauncher;
    private ActivityResultLauncher<Intent> cameraLauncher;

    private Uri photoUri;
    private String currentPhotoPath;
    private ImageButton btnThemeToggle;

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
        Button btnCollage = findViewById(R.id.btn_collage);
        btnThemeToggle = findViewById(R.id.btn_theme_toggle);

        initActivityLaunchers();
        initThemeToggle();

        // 设置点击事件
        btnImageEdit.setOnClickListener(v -> {
            // 检查并请求访问相册所需的权限
            requestGalleryPermissions();
        });

        btnCamera.setOnClickListener(v -> {
            // 调用权限请求方法
            requestCameraPermissions();
        });

        btnCollage.setOnClickListener(v -> {
            // 检查并请求访问相册所需的权限（用于拼图）
            requestCollagePermissions();
        });
    }

    /**
     * 初始化主题切换按钮
     */
    private void initThemeToggle() {
        // 根据当前模式设置图标
        updateThemeToggleIcon();
        
        // 设置点击事件
        btnThemeToggle.setOnClickListener(v -> {
            boolean isNightMode = ThemeUtils.toggleNightMode(this);
            // 重新创建Activity以应用新主题
            recreate();
        });
    }

    /**
     * 更新主题切换按钮图标
     * 日间模式显示太阳图标，夜间模式显示月亮图标
     */
    private void updateThemeToggleIcon() {
        boolean isNightMode = ThemeUtils.isNightMode(this);
        // 日间模式显示太阳图标，夜间模式显示月亮图标
        btnThemeToggle.setImageResource(isNightMode ? R.drawable.ic_night_mode : R.drawable.ic_day_mode);
    }

    /**
     * 请求相册访问权限
     * 根据Android版本请求不同的权限（Android 13+使用READ_MEDIA_IMAGES，否则使用READ_EXTERNAL_STORAGE）
     */
    private void requestGalleryPermissions() {
        if (PermissionUtils.hasPermission(this, PermissionUtils.PermissionType.STORAGE)) {
            openGallery();
        } else {
            PermissionUtils.requestPermissionWithRationale(
                    this,
                    PermissionUtils.PermissionType.STORAGE,
                    REQUEST_STORAGE_PERMISSION,
                    "需要存储权限才能访问相册，请允许访问相册权限。"
            );
        }
    }

    /**
     * 请求拼图功能所需的存储权限
     * 根据Android版本请求不同的权限
     */
    private void requestCollagePermissions() {
        if (PermissionUtils.hasPermission(this, PermissionUtils.PermissionType.STORAGE)) {
            openCollage();
        } else {
            PermissionUtils.requestPermissionWithRationale(
                    this,
                    PermissionUtils.PermissionType.STORAGE,
                    REQUEST_STORAGE_PERMISSION + 10,
                    "需要存储权限才能访问相册进行拼图，请允许访问相册权限。"
            );
        }
    }

    private void openCollage() {
        // 先跳转到图片选择器，选择完图片后再进入拼图界面
        Intent intent = new Intent(MainActivity.this, ImageCollageSelectorActivity.class);
        startActivityForResult(intent, 200);
    }

    /**
     * 请求相机权限
     * 包括相机权限和存储权限（Android 11及以下需要）
     */
    private void requestCameraPermissions() {
        // 先检查相机权限
        if (!PermissionUtils.hasPermission(this, PermissionUtils.PermissionType.CAMERA)) {
            PermissionUtils.requestPermissionWithRationale(
                    this,
                    PermissionUtils.PermissionType.CAMERA,
                    REQUEST_CAMERA_PERMISSION,
                    "需要相机权限才能拍照，请允许相机权限。"
            );
            return;
        }
        
        // 检查是否还需要存储权限（用于保存照片，Android 11及以下需要）
        if (android.os.Build.VERSION.SDK_INT <= android.os.Build.VERSION_CODES.R) {
            if (!PermissionUtils.hasPermission(this, PermissionUtils.PermissionType.WRITE_STORAGE)) {
                PermissionUtils.requestPermissionWithRationale(
                        this,
                        PermissionUtils.PermissionType.WRITE_STORAGE,
                        REQUEST_STORAGE_PERMISSION,
                        "需要存储权限才能保存照片，请允许存储权限。"
                );
                return;
            }
        }
        
        // 权限都已授予，启动相机
        startCamera();
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
    
    /**
     * 打开相册界面
     */
    private void openGallery() {
        Toast.makeText(this, "打开相册", Toast.LENGTH_SHORT).show();
        galleryLauncher.launch(new Intent(MainActivity.this, ImageGalleryActivity.class));
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
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 200 && resultCode == RESULT_OK && data != null) {
            ArrayList<String> selectedPaths = data.getStringArrayListExtra("selected_paths");
            if (selectedPaths != null && selectedPaths.size() >= 2) {
                // 跳转到拼图Activity，传递已选择的图片
                Intent intent = new Intent(MainActivity.this, ImageCollageActivity.class);
                intent.putStringArrayListExtra("selected_paths", selectedPaths);
                startActivity(intent);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case REQUEST_STORAGE_PERMISSION:
                PermissionUtils.handlePermissionResult(
                        this,
                        PermissionUtils.PermissionType.STORAGE,
                        permissions,
                        grantResults,
                        () -> {
                            // 权限授予成功
                            // 判断是为了相机还是相册请求的权限
                            if (PermissionUtils.hasPermission(this, PermissionUtils.PermissionType.CAMERA)) {
                                startCamera();
                            } else {
                                openGallery();
                            }
                        },
                        () -> {
                            // 权限被拒绝（但可以再次请求）
                            Toast.makeText(this, "需要存储权限才能访问媒体文件或保存照片", Toast.LENGTH_SHORT).show();
                        },
                        () -> {
                            // 权限被永久拒绝，引导前往设置
                            PermissionUtils.showPermissionRationale(
                                    this,
                                    PermissionUtils.PermissionType.STORAGE,
                                    "需要存储权限才能访问媒体文件或保存照片，请在设置中开启存储权限。",
                                    () -> PermissionUtils.openAppSettings(this),
                                    null
                            );
                        }
                );
                break;
            case REQUEST_STORAGE_PERMISSION + 10:
                // 拼图功能的权限请求
                PermissionUtils.handlePermissionResult(
                        this,
                        PermissionUtils.PermissionType.STORAGE,
                        permissions,
                        grantResults,
                        () -> openCollage(),
                        () -> Toast.makeText(this, "需要存储权限才能访问相册进行拼图", Toast.LENGTH_SHORT).show(),
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
                break;
            case REQUEST_CAMERA_PERMISSION:
                PermissionUtils.handlePermissionResult(
                        this,
                        PermissionUtils.PermissionType.CAMERA,
                        permissions,
                        grantResults,
                        () -> {
                            // 相机权限已授予，检查是否需要存储权限
                            if (android.os.Build.VERSION.SDK_INT <= android.os.Build.VERSION_CODES.R) {
                                if (!PermissionUtils.hasPermission(this, PermissionUtils.PermissionType.WRITE_STORAGE)) {
                                    PermissionUtils.requestPermissionWithRationale(
                                            this,
                                            PermissionUtils.PermissionType.WRITE_STORAGE,
                                            REQUEST_STORAGE_PERMISSION,
                                            "需要存储权限才能保存照片，请允许存储权限。"
                                    );
                                    return;
                                }
                            }
                            startCamera();
                        },
                        () -> Toast.makeText(this, "需要相机权限才能拍照", Toast.LENGTH_SHORT).show(),
                        () -> {
                            PermissionUtils.showPermissionRationale(
                                    this,
                                    PermissionUtils.PermissionType.CAMERA,
                                    "需要相机权限才能拍照，请在设置中开启相机权限。",
                                    () -> PermissionUtils.openAppSettings(this),
                                    null
                            );
                        }
                );
                break;
        }
    }

    /**
     * 启动图片编辑界面
     * @param imagePath 图片路径
     */
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