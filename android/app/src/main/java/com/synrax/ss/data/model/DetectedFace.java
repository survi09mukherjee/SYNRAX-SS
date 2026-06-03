package com.synrax.ss.data.model;

import com.google.gson.annotations.SerializedName;
import java.util.Map;

public class DetectedFace {
    @SerializedName("id")
    private int id;

    @SerializedName("media_id")
    private int mediaId;

    @SerializedName("face_cluster_id")
    private Integer faceClusterId;

    @SerializedName("bounding_box")
    private Map<String, Object> boundingBox; // Holds x, y, w, h parameters

    // Constructors
    public DetectedFace() {}

    public DetectedFace(int id, int mediaId, Integer faceClusterId, Map<String, Object> boundingBox) {
        this.id = id;
        this.mediaId = mediaId;
        this.faceClusterId = faceClusterId;
        this.boundingBox = boundingBox;
    }

    // Getters and Setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getMediaId() { return mediaId; }
    public void setMediaId(int mediaId) { this.mediaId = mediaId; }

    public Integer getFaceClusterId() { return faceClusterId; }
    public void setFaceClusterId(Integer faceClusterId) { this.faceClusterId = faceClusterId; }

    public Map<String, Object> getBoundingBox() { return boundingBox; }
    public void setBoundingBox(Map<String, Object> boundingBox) { this.boundingBox = boundingBox; }
}
