package com.tiktok.ic.camera.Adapter;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.tiktok.ic.camera.R;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 图片列表适配器
 * 用于在GridView中显示图片缩略图，支持异步加载和缓存
 */
public class ImageAdapter extends ArrayAdapter<String> {

    private final Context context;
    private List<String> imagePaths;
    private final Map<String, Bitmap> thumbnailCache;
    private static final int THUMBNAIL_SIZE = 300; // 缩略图大小
    private final ExecutorService executorService = Executors.newFixedThreadPool(4);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public ImageAdapter(@NonNull Context context, List<String> imagePaths) {
        super(context, 0, imagePaths);
        this.context = context;
        this.imagePaths = new ArrayList<>(imagePaths);
        this.thumbnailCache = new HashMap<>();
    }

    public void setImages(List<String> imagePaths) {
        this.imagePaths = new ArrayList<>(imagePaths);
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return imagePaths == null ? 0 : imagePaths.size();
    }

    @Nullable
    @Override
    public String getItem(int position) {
        if (imagePaths == null || position < 0 || position >= imagePaths.size()) {
            return null;
        }
        return imagePaths.get(position);
    }

    private void loadThumbnailAsync(ImageView imageView, String imagePath) {
        WeakReference<ImageView> imageViewRef = new WeakReference<>(imageView);
        executorService.execute(() -> {
            Bitmap bitmap = loadThumbnail(imagePath);
            if (bitmap != null) {
                mainHandler.post(() -> {
                    ImageView view = imageViewRef.get();
                    if (view != null && imagePath.equals(view.getTag())) {
                        thumbnailCache.put(imagePath, bitmap);
                        view.setImageBitmap(bitmap);
                    } else {
                        bitmap.recycle();
                    }
                });
            }
        });
    }

    private Bitmap loadThumbnail(String imagePath) {
        try {
            // 计算缩略图的尺寸
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(imagePath, options);

            // 计算缩放比例
            int scale = 1;
            if (options.outHeight > THUMBNAIL_SIZE || options.outWidth > THUMBNAIL_SIZE) {
                int halfHeight = options.outHeight / 2;
                int halfWidth = options.outWidth / 2;
                while ((halfHeight / scale) >= THUMBNAIL_SIZE && (halfWidth / scale) >= THUMBNAIL_SIZE) {
                    scale *= 2;
                }
            }

            // 加载缩放后的图片
            options.inSampleSize = scale;
            options.inJustDecodeBounds = false;
            Bitmap bitmap = BitmapFactory.decodeFile(imagePath, options);

            // 裁剪为正方形
            return cropToSquare(bitmap);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private Bitmap cropToSquare(Bitmap bitmap) {
        if (bitmap == null) return null;
        
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int size = Math.min(width, height);
        
        // 计算裁剪区域
        int x = (width - size) / 2;
        int y = (height - size) / 2;
        
        return Bitmap.createBitmap(bitmap, x, y, size, size);
    }

    private OnPreviewButtonClickListener previewButtonClickListener;
    private OnItemClickListener itemClickListener;

    public interface OnPreviewButtonClickListener {
        void onPreviewButtonClick(String imagePath, ImageView imageView);
    }

    public interface OnItemClickListener {
        void onItemClick(String imagePath);
    }

    public void setOnPreviewButtonClickListener(OnPreviewButtonClickListener listener) {
        this.previewButtonClickListener = listener;
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.itemClickListener = listener;
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        ImageViewHolder holder;

        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(R.layout.grid_item_image, parent, false);
            holder = new ImageViewHolder();
            holder.imageView = convertView.findViewById(R.id.grid_item_image_view);
            holder.previewButton = convertView.findViewById(R.id.preview_button);
            convertView.setTag(holder);
        } else {
            holder = (ImageViewHolder) convertView.getTag();
        }

        String imagePath = getItem(position);
        holder.imageView.setImageDrawable(null);

        if (imagePath == null) {
            return convertView;
        }

        holder.imageView.setTag(imagePath);
        // 为每个ImageView设置唯一的transitionName，用于共享元素过渡动画
        holder.imageView.setTransitionName("image_" + imagePath.hashCode());
        
        // 确保ImageView不拦截点击事件，让父容器处理
        holder.imageView.setClickable(false);
        holder.imageView.setFocusable(false);

        // 确保convertView可点击
        convertView.setClickable(true);
        convertView.setFocusable(true);

        // 设置item点击事件（点击图片区域时触发）
        convertView.setOnClickListener(v -> {
            if (itemClickListener != null) {
                itemClickListener.onItemClick(imagePath);
            }
        });

        // 设置预览按钮点击事件
        if (holder.previewButton != null) {
            holder.previewButton.setOnClickListener(v -> {
                if (previewButtonClickListener != null) {
                    previewButtonClickListener.onPreviewButtonClick(imagePath, holder.imageView);
                }
            });
        }

        if (thumbnailCache.containsKey(imagePath)) {
            holder.imageView.setImageBitmap(thumbnailCache.get(imagePath));
        } else {
            loadThumbnailAsync(holder.imageView, imagePath);
        }

        return convertView;
    }

    private static class ImageViewHolder {
        ImageView imageView;
        ImageButton previewButton;
    }
}
