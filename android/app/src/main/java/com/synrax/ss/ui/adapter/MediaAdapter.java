package com.synrax.ss.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.synrax.ss.R;
import com.synrax.ss.data.model.Media;
import com.synrax.ss.network.RetrofitClient;
import java.util.List;

/**
 * MediaAdapter - Binds the photos grid in EventGalleryActivity.
 */
public class MediaAdapter extends RecyclerView.Adapter<MediaAdapter.MediaViewHolder> {

    private final List<Media> mediaList;
    private final OnMediaClickListener listener;

    public interface OnMediaClickListener {
        void onMediaClick(Media media);
    }

    public MediaAdapter(List<Media> mediaList, OnMediaClickListener listener) {
        this.mediaList = mediaList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public MediaViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_media, parent, false);
        return new MediaViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MediaViewHolder holder, int position) {
        Media media = mediaList.get(position);
        holder.bind(media, listener);
    }

    @Override
    public int getItemCount() {
        return mediaList.size();
    }

    static class MediaViewHolder extends RecyclerView.ViewHolder {
        private final ImageView imgThumbnail;

        public MediaViewHolder(@NonNull View itemView) {
            super(itemView);
            imgThumbnail = itemView.findViewById(R.id.img_thumbnail);
        }

        public void bind(final Media media, final OnMediaClickListener listener) {
            // Resolve image url dynamically from RetrofitClient BASE_URL
            String imageUrl = "";
            String storagePath = media.getStoragePath();
            if (storagePath != null) {
                if (storagePath.startsWith("http://") || storagePath.startsWith("https://")) {
                    imageUrl = storagePath;
                } else {
                    String baseUrl = RetrofitClient.BASE_URL;
                    if (baseUrl.contains("/api/v1/")) {
                        String host = baseUrl.substring(0, baseUrl.indexOf("/api/v1/"));
                        imageUrl = host + "/uploads/" + storagePath;
                    } else {
                        imageUrl = "http://10.0.2.2:8000/uploads/" + storagePath;
                    }
                }
            }

            Glide.with(itemView.getContext())
                    .load(imageUrl)
                    .placeholder(android.R.drawable.ic_menu_gallery)
                    .error(android.R.drawable.stat_notify_error)
                    .centerCrop()
                    .into(imgThumbnail);

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onMediaClick(media);
                }
            });
        }
    }
}
