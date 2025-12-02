package com.tiktok.ic.camera.activity;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.GridView;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.ActivityOptionsCompat;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.tiktok.ic.camera.Adapter.AlbumAdapter;
import com.tiktok.ic.camera.Adapter.ImageAdapter;
import com.tiktok.ic.camera.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 图片相册Activity
 * 显示所有图片和相册文件夹，支持图片预览和选择
 */
public class ImageGalleryActivity extends AppCompatActivity {

    private static final int REQUEST_STORAGE_PERMISSION = 100;

    private RadioGroup tabRadioGroup;
    private RadioButton allImagesRadioButton;
    private RadioButton foldersRadioButton;
    private GridView imageGridView;
    private ListView folderListView;
    private TextView emptyStateTextView;
    private Button backButton;
    private View folderDetailBar;
    private TextView folderDetailTitle;
    private TextView folderDetailAction;

    private ImageAdapter imageAdapter;
    private AlbumAdapter albumAdapter;
    private List<String> allImagePaths;
    private Map<String, List<String>> imageFolders;
    private List<String> folderNames;
    private boolean isAllImagesMode = true;
    private boolean inFolderDetailMode = false;
    private String currentFolderName = null;
    private ActivityResultLauncher<Intent> previewLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_gallery);

        // 初始化UI组件
        initViews();
        initActivityResultLaunchers();

        // 请求存储权限
        if (!checkStoragePermission()) {
            requestStoragePermission();
        } else {
            // 有权限，加载图片
            loadImages();
        }

        // 设置Tab切换监听
        tabRadioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.radio_all_images) {
                switchToAllImagesMode();
            } else if (checkedId == R.id.radio_folders) {
                switchToFoldersMode();
            }
        });

        // 设置返回按钮点击事件
        backButton.setOnClickListener(v -> finish());

        folderDetailAction.setOnClickListener(v -> exitFolderDetail());

        // 设置文件夹列表项点击事件
        folderListView.setOnItemClickListener((parent, view, position, id) -> {
            String folderName = folderNames.get(position);
            List<String> folderImages = imageFolders.get(folderName);
            showFolderImages(folderName, folderImages);
        });
    }

    private void initViews() {
        tabRadioGroup = findViewById(R.id.tab_radio_group);
        allImagesRadioButton = findViewById(R.id.radio_all_images);
        foldersRadioButton = findViewById(R.id.radio_folders);
        imageGridView = findViewById(R.id.image_grid_view);
        folderListView = findViewById(R.id.folder_list_view);
        emptyStateTextView = findViewById(R.id.empty_state_text_view);
        backButton = findViewById(R.id.back_button);
        folderDetailBar = findViewById(R.id.folder_detail_bar);
        folderDetailTitle = findViewById(R.id.folder_detail_title);
        folderDetailAction = findViewById(R.id.folder_detail_action);

        allImagePaths = new ArrayList<>();
        imageFolders = new HashMap<>();
        folderNames = new ArrayList<>();

        imageAdapter = new ImageAdapter(this, allImagePaths);
        // 设置item点击监听：直接点击图片时进入编辑界面
        imageAdapter.setOnItemClickListener(imagePath -> {
            if (imagePath != null) {
                ImageEditActivity.start(ImageGalleryActivity.this, imagePath);
                finish();
            }
        });
        imageAdapter.setOnPreviewButtonClickListener((imagePath, imageView) -> {
            // 预览按钮点击：跳转到预览界面
            String transitionName = imageView.getTransitionName();
            if (transitionName == null) {
                transitionName = "image_" + imagePath.hashCode();
                imageView.setTransitionName(transitionName);
            }
            
            Intent previewIntent = ImagePreviewActivity.createIntent(ImageGalleryActivity.this, imagePath, transitionName);
            ActivityOptionsCompat options = ActivityOptionsCompat.makeSceneTransitionAnimation(
                    ImageGalleryActivity.this,
                    imageView,
                    transitionName
            );
            previewLauncher.launch(previewIntent, options);
        });
        imageGridView.setAdapter(imageAdapter);

        albumAdapter = new AlbumAdapter(this, folderNames, imageFolders);
        folderListView.setAdapter(albumAdapter);
    }

    private void initActivityResultLaunchers() {
        previewLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() != RESULT_OK) {
                        return;
                    }
                    Intent data = result.getData();
                    if (data != null) {
                        String selectedImagePath = data.getStringExtra("selected_image");
                        if (selectedImagePath != null) {
                            Intent resultIntent = new Intent();
                            resultIntent.putExtra("selected_image_path", selectedImagePath);
                            setResult(RESULT_OK, resultIntent);
                            finish();
                        }
                    }
                }
        );
    }

    private boolean checkStoragePermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
                    == PackageManager.PERMISSION_GRANTED;
        } else {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void requestStoragePermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_MEDIA_IMAGES},
                    REQUEST_STORAGE_PERMISSION);
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                    REQUEST_STORAGE_PERMISSION);
        }
    }

    private void loadImages() {
        new Thread(() -> {
            // 从MediaStore加载图片
            loadImagesFromMediaStore();
            
            // 在主线程更新UI
            new Handler(Looper.getMainLooper()).post(() -> {
                if (allImagePaths.isEmpty()) {
                    showEmptyState();
                } else {
                    updateUI();
                }
            });
        }).start();
    }

    private void loadImagesFromMediaStore() {
        Uri uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        String[] projection = {
                MediaStore.Images.Media.DATA,
                MediaStore.Images.Media.BUCKET_DISPLAY_NAME
        };
        String sortOrder = MediaStore.Images.Media.DATE_ADDED + " DESC";

        try (Cursor cursor = getContentResolver().query(uri, projection, null, null, sortOrder)) {
            allImagePaths.clear();
            imageFolders.clear();
            folderNames.clear();

            if (cursor != null) {
                int dataColumnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
                int bucketColumnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME);

                while (cursor.moveToNext()) {
                    String path = cursor.getString(dataColumnIndex);
                    String folderName = cursor.getString(bucketColumnIndex);

                    // 添加到所有图片列表
                    allImagePaths.add(path);

                    // 按文件夹分类
                    List<String> folderList = imageFolders.get(folderName);
                    if (folderList == null) {
                        folderList = new ArrayList<>();
                        imageFolders.put(folderName, folderList);
                        folderNames.add(folderName);
                    }
                    folderList.add(path);
                }

                Collections.sort(folderNames);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void updateUI() {
        imageAdapter.setImages(allImagePaths);
        albumAdapter.setFolders(folderNames, imageFolders);
        emptyStateTextView.setVisibility(View.GONE);
        imageGridView.setVisibility(View.VISIBLE);
        folderListView.setVisibility(View.GONE);
        folderDetailBar.setVisibility(View.GONE);
        inFolderDetailMode = false;
        currentFolderName = null;
    }

    private void showEmptyState() {
        emptyStateTextView.setText("相册中没有图片");
        emptyStateTextView.setVisibility(View.VISIBLE);
        imageGridView.setVisibility(View.GONE);
        folderListView.setVisibility(View.GONE);
    }

    private void switchToAllImagesMode() {
        isAllImagesMode = true;
        inFolderDetailMode = false;
        currentFolderName = null;
        folderDetailBar.setVisibility(View.GONE);
        imageAdapter.setImages(allImagePaths);
        imageGridView.setVisibility(View.VISIBLE);
        folderListView.setVisibility(View.GONE);
    }

    private void switchToFoldersMode() {
        isAllImagesMode = false;
        if (inFolderDetailMode) {
            showFolderDetailUI();
        } else {
            imageGridView.setVisibility(View.GONE);
            folderListView.setVisibility(View.VISIBLE);
            folderDetailBar.setVisibility(View.GONE);
        }
    }

    private void showFolderImages(String folderName, List<String> folderImages) {
        if (folderImages == null || folderImages.isEmpty()) {
            Toast.makeText(this, "该文件夹暂无图片", Toast.LENGTH_SHORT).show();
            return;
        }
        currentFolderName = folderName;
        inFolderDetailMode = true;
        imageAdapter.setImages(folderImages);
        showFolderDetailUI();
        tabRadioGroup.check(R.id.radio_folders);
    }

    private void showFolderDetailUI() {
        folderDetailBar.setVisibility(View.VISIBLE);
        folderDetailTitle.setText(currentFolderName == null ? "" : currentFolderName);
        folderListView.setVisibility(View.GONE);
        imageGridView.setVisibility(View.VISIBLE);
    }

    private void exitFolderDetail() {
        if (!inFolderDetailMode) {
            return;
        }
        inFolderDetailMode = false;
        currentFolderName = null;
        folderDetailBar.setVisibility(View.GONE);
        folderListView.setVisibility(View.VISIBLE);
        imageGridView.setVisibility(View.GONE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_STORAGE_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                loadImages();
            } else {
                Toast.makeText(this, "需要存储权限才能访问相册", Toast.LENGTH_SHORT).show();
                // 检查是否用户勾选了"不再询问"
                if (!ActivityCompat.shouldShowRequestPermissionRationale(this, permissions[0])) {
                    Toast.makeText(this, "请在设置中开启存储权限以使用相册功能", Toast.LENGTH_LONG).show();
                }
                finish();
            }
        }
    }

}