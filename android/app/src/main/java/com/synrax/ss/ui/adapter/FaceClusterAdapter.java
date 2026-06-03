package com.synrax.ss.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.google.android.material.card.MaterialCardView;
import com.synrax.ss.R;
import com.synrax.ss.data.model.FaceCluster;
import com.synrax.ss.network.RetrofitClient;
import java.util.List;

/**
 * FaceClusterAdapter - Displays horizontal list of detected faces for gallery filtering.
 */
public class FaceClusterAdapter extends RecyclerView.Adapter<FaceClusterAdapter.FaceViewHolder> {

    private final List<FaceCluster> faceList;
    private final OnFaceClusterClickListener listener;
    private int selectedPosition = -1; // -1 means no filter active

    public interface OnFaceClusterClickListener {
        void onFaceClusterClick(FaceCluster cluster, boolean isSelected);
    }

    public FaceClusterAdapter(List<FaceCluster> faceList, OnFaceClusterClickListener listener) {
        this.faceList = faceList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public FaceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_face_cluster, parent, false);
        return new FaceViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull FaceViewHolder holder, int position) {
        FaceCluster cluster = faceList.get(position);
        holder.bind(cluster, position, selectedPosition == position, (clickedCluster, isSelected) -> {
            int prevSelected = selectedPosition;
            if (isSelected) {
                // Was already selected, so deselect it
                selectedPosition = -1;
                notifyItemChanged(position);
                listener.onFaceClusterClick(null, false);
            } else {
                // Select new item
                selectedPosition = position;
                if (prevSelected != -1) {
                    notifyItemChanged(prevSelected);
                }
                notifyItemChanged(selectedPosition);
                listener.onFaceClusterClick(clickedCluster, true);
            }
        });
    }

    @Override
    public int getItemCount() {
        return faceList.size();
    }

    /** Reset selected cluster filter style state */
    public void clearSelection() {
        int prevSelected = selectedPosition;
        selectedPosition = -1;
        if (prevSelected != -1) {
            notifyItemChanged(prevSelected);
        }
    }

    static class FaceViewHolder extends RecyclerView.ViewHolder {
        private final MaterialCardView cardFace;
        private final ImageView imgFace;
        private final TextView txtFaceLabel;

        public FaceViewHolder(@NonNull View itemView) {
            super(itemView);
            cardFace = itemView.findViewById(R.id.card_face);
            imgFace = itemView.findViewById(R.id.img_face);
            txtFaceLabel = itemView.findViewById(R.id.txt_face_label);
        }

        public void bind(final FaceCluster cluster, final int position, final boolean isSelected, final OnItemClickListener clickListener) {
            txtFaceLabel.setText(cluster.getLabel() != null ? cluster.getLabel() : "Person " + cluster.getId());

            // Build dynamic face thumbnail URL
            String imageUrl = "";
            String repPath = cluster.getRepresentativeFacePath();
            if (repPath != null) {
                if (repPath.startsWith("http://") || repPath.startsWith("https://")) {
                    imageUrl = repPath;
                } else {
                    String baseUrl = RetrofitClient.BASE_URL;
                    if (baseUrl.contains("/api/v1/")) {
                        String host = baseUrl.substring(0, baseUrl.indexOf("/api/v1/"));
                        imageUrl = host + "/uploads/" + repPath;
                    } else {
                        imageUrl = "http://10.0.2.2:8000/uploads/" + repPath;
                    }
                }
            }

            Glide.with(itemView.getContext())
                    .load(imageUrl)
                    .placeholder(android.R.drawable.ic_menu_gallery)
                    .error(android.R.drawable.stat_notify_error)
                    .centerCrop()
                    .into(imgFace);

            // Configure Selected Stroke highlights
            if (isSelected) {
                cardFace.setStrokeColor(itemView.getContext().getResources().getColor(R.color.accent));
                cardFace.setStrokeWidth(6);
            } else {
                cardFace.setStrokeColor(itemView.getContext().getResources().getColor(R.color.surface_card));
                cardFace.setStrokeWidth(2);
            }

            itemView.setOnClickListener(v -> clickListener.onItemClick(cluster, isSelected));
        }

        interface OnItemClickListener {
            void onItemClick(FaceCluster cluster, boolean isSelected);
        }
    }
}
