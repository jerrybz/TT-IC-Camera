package com.tiktok.ic.camera;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AlbumAdapter extends ArrayAdapter<String> {

    private Context context;
    private List<String> folderNames;
    private Map<String, List<String>> imageFolders;
    private Map<String, Bitmap> thumbnailCache;
    private final int THUMBNAIL_SIZE = 100;

    public AlbumAdapter(@NonNull Context context, List<String> folderNames, Map<String, List<String>> imageFolders) {
        super(context, 0, folderNames);
        this.context = context;
        this.folderNames = new ArrayList<>(folderNames);
        this.imageFolders = imageFolders;
        this.thumbnailCache = new HashMap<>();
    }

    public void setFolders(List<String> folderNames, Map<String, List<String>> imageFolders) {
        this.folderNames = new ArrayList<>(folderNames);
        this.imageFolders = imageFolders;
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return folderNames == null ? 0 : folderNames.size();
    }

    @Nullable
    @Override
    public String getItem(int position) {
        if (folderNames == null || position < 0 || position >= folderNames.size()) {
            return null;
        }
        return folderNames.get(position);
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        AlbumViewHolder holder;

        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(R.layout.list_item_album, parent, false);
            holder = new AlbumViewHolder();
            holder.thumbnailView = convertView.findViewById(R.id.album_thumbnail_view);
            holder.folderNameView = convertView.findViewById(R.id.folder_name_text_view);
            holder.imageCountView = convertView.findViewById(R.id.image_count_text_view);
            convertView.setTag(holder);
        } else {
            holder = (AlbumViewHolder) convertView.getTag();
        }

        String folderName = getItem(position);
        holder.folderNameView.setText(folderName == null ? "" : folderName);

        if (folderName == null || !imageFolders.containsKey(folderName)) {
            holder.imageCountView.setText("0 张图片");
            holder.thumbnailView.setImageResource(R.drawable.camera_logo);
            return convertView;
        }

            List<String> folderImages = imageFolders.get(folderName);
        int count = folderImages == null ? 0 : folderImages.size();
        holder.imageCountView.setText(count + " 张图片");

        if (folderImages == null || folderImages.isEmpty()) {
            holder.thumbnailView.setImageResource(R.drawable.camera_logo);
            return convertView;
        }

                String thumbnailPath = folderImages.get(0);
                if (thumbnailCache.containsKey(thumbnailPath)) {
                    holder.thumbnailView.setImageBitmap(thumbnailCache.get(thumbnailPath));
                } else {
                    Bitmap thumbnail = loadThumbnail(thumbnailPath);
                    if (thumbnail != null) {
                        thumbnailCache.put(thumbnailPath, thumbnail);
                        holder.thumbnailView.setImageBitmap(thumbnail);
            } else {
                holder.thumbnailView.setImageResource(R.drawable.camera_logo);
            }
        }

        return convertView;
    }

    private static class AlbumViewHolder {
        ImageView thumbnailView;
        TextView folderNameView;
        TextView imageCountView;
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
            return cropToSquare(bitmap);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private Bitmap cropToSquare(Bitmap bitmap) {
        if (bitmap == null) {
            return null;
        }
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int size = Math.min(width, height);
        int x = (width - size) / 2;
        int y = (height - size) / 2;
        return Bitmap.createBitmap(bitmap, x, y, size, size);
    }
}