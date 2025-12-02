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
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.ActivityOptionsCompat;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.tiktok.ic.camera.Adapter.MultiSelectImageAdapter;
import com.tiktok.ic.camera.R;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 拼图图片选择器Activity
 * 支持多选图片（2-4张），用于拼图功能
 */
public class ImageCollageSelectorActivity extends AppCompatActivity {

    private static final int MAX_SELECTION = 4;
    private static final int REQUEST_STORAGE_PERMISSION = 300;

    private GridView imageGridView;
    private TextView selectedCountText;
    private Button backButton;
    private Button confirmButton;
    private TextView emptyStateTextView;

    private MultiSelectImageAdapter imageAdapter;
    private List<String> allImagePaths;
    private Set<String> selectedPaths = new HashSet<>();
    private ActivityResultLauncher<Intent> previewLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_collage_selector);

        initViews();
        initActivityResultLaunchers();
        setupListeners();

        // 获取已选中的图片路径
        ArrayList<String> previousSelected = getIntent().getStringArrayListExtra("selected_paths");
        if (previousSelected != null) {
            selectedPaths.addAll(previousSelected);
        }

        // 检查权限
        if (!checkStoragePermission()) {
            requestStoragePermission();
        } else {
            loadImages();
        }
    }

    private void initViews() {
        imageGridView = findViewById(R.id.image_grid_view);
        selectedCountText = findViewById(R.id.selected_count_text);
        backButton = findViewById(R.id.back_button);
        confirmButton = findViewById(R.id.confirm_button);
        emptyStateTextView = findViewById(R.id.empty_state_text_view);

        allImagePaths = new ArrayList<>();
        imageAdapter = new MultiSelectImageAdapter(this, allImagePaths);
        imageAdapter.setSelectedPaths(selectedPaths);
        // 设置item点击监听：直接点击图片时切换选中状态
        imageAdapter.setOnItemClickListener(imagePath -> {
            if (imagePath == null) {
                return;
            }
            // 切换选择状态
            if (selectedPaths.contains(imagePath)) {
                selectedPaths.remove(imagePath);
            } else {
                if (selectedPaths.size() >= MAX_SELECTION) {
                    Toast.makeText(this, "最多只能选择" + MAX_SELECTION + "张图片", Toast.LENGTH_SHORT).show();
                    return;
                }
                selectedPaths.add(imagePath);
            }
            // 更新UI
            imageAdapter.setSelectedPaths(selectedPaths);
            updateSelectedCount();
        });
        // 设置预览按钮点击监听器
        imageAdapter.setOnPreviewButtonClickListener((imagePath, imageView) -> {
            // 预览按钮点击：跳转到预览界面
            String transitionName = imageView.getTransitionName();
            if (transitionName == null) {
                transitionName = "image_" + imagePath.hashCode();
                imageView.setTransitionName(transitionName);
            }
            
            boolean isSelected = selectedPaths.contains(imagePath);
            Intent previewIntent = ImagePreviewActivity.createIntentForMultiSelect(
                    ImageCollageSelectorActivity.this, 
                    imagePath, 
                    transitionName,
                    isSelected
            );
            ActivityOptionsCompat options = ActivityOptionsCompat.makeSceneTransitionAnimation(
                    ImageCollageSelectorActivity.this,
                    imageView,
                    transitionName
            );
            previewLauncher.launch(previewIntent, options);
        });
        imageGridView.setAdapter(imageAdapter);

        updateSelectedCount();
    }

    private void setupListeners() {
        backButton.setOnClickListener(v -> finish());

        confirmButton.setOnClickListener(v -> {
            if (selectedPaths.size() < 2) {
                Toast.makeText(this, "至少需要选择2张图片", Toast.LENGTH_SHORT).show();
                return;
            }
            if (selectedPaths.size() > MAX_SELECTION) {
                Toast.makeText(this, "最多只能选择" + MAX_SELECTION + "张图片", Toast.LENGTH_SHORT).show();
                return;
            }

            Intent resultIntent = new Intent();
            resultIntent.putStringArrayListExtra("selected_paths", new ArrayList<>(selectedPaths));
            setResult(RESULT_OK, resultIntent);
            finish();
        });
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
                        String imagePath = data.getStringExtra("image_path");
                        boolean isSelected = data.getBooleanExtra("is_selected", false);
                        
                        if (imagePath != null && isSelected) {
                            // 从预览页面返回，如果选择了图片，则添加到选择列表
                            if (selectedPaths.size() >= MAX_SELECTION && !selectedPaths.contains(imagePath)) {
                                Toast.makeText(this, "最多只能选择" + MAX_SELECTION + "张图片", Toast.LENGTH_SHORT).show();
                            } else {
                                selectedPaths.add(imagePath);
                            }
                            
                            // 更新UI
                            imageAdapter.setSelectedPaths(selectedPaths);
                            updateSelectedCount();
                        }
                    }
                }
        );
    }

    private void updateSelectedCount() {
        int count = selectedPaths.size();
        selectedCountText.setText("已选择 " + count + "/" + MAX_SELECTION);
        confirmButton.setEnabled(count >= 2);
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
            loadImagesFromMediaStore();
            
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
                MediaStore.Images.Media.DATA
        };
        String sortOrder = MediaStore.Images.Media.DATE_ADDED + " DESC";

        try (Cursor cursor = getContentResolver().query(uri, projection, null, null, sortOrder)) {
            allImagePaths.clear();

            if (cursor != null) {
                int dataColumnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);

                while (cursor.moveToNext()) {
                    String path = cursor.getString(dataColumnIndex);
                    allImagePaths.add(path);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void updateUI() {
        imageAdapter.setImages(allImagePaths);
        imageAdapter.setSelectedPaths(selectedPaths);
        emptyStateTextView.setVisibility(View.GONE);
        imageGridView.setVisibility(View.VISIBLE);
    }

    private void showEmptyState() {
        emptyStateTextView.setText("相册中没有图片");
        emptyStateTextView.setVisibility(View.VISIBLE);
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
                finish();
            }
        }
    }
}
