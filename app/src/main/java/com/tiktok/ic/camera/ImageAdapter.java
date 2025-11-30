package com.tiktok.ic.camera;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        ImageViewHolder holder;

        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(R.layout.grid_item_image, parent, false);
            holder = new ImageViewHolder();
            holder.imageView = convertView.findViewById(R.id.grid_item_image_view);
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

        if (thumbnailCache.containsKey(imagePath)) {
            holder.imageView.setImageBitmap(thumbnailCache.get(imagePath));
        } else {
            loadThumbnailAsync(holder.imageView, imagePath);
        }

        return convertView;
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

    private static class ImageViewHolder {
        ImageView imageView;
    }
}
