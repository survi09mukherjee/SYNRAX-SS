package com.synrax.ss.data.model;

import com.google.gson.annotations.SerializedName;

public class FaceCluster {
    @SerializedName("id")
    private int id;

    @SerializedName("event_id")
    private String eventId;

    @SerializedName("label")
    private String label;

    @SerializedName("representative_face_path")
    private String representativeFacePath;

    @SerializedName("created_at")
    private String createdAt;

    // Constructors
    public FaceCluster() {}

    public FaceCluster(int id, String eventId, String label, String representativeFacePath, String createdAt) {
        this.id = id;
        this.eventId = eventId;
        this.label = label;
        this.representativeFacePath = representativeFacePath;
        this.createdAt = createdAt;
    }

    // Getters and Setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public String getRepresentativeFacePath() { return representativeFacePath; }
    public void setRepresentativeFacePath(String representativeFacePath) { this.representativeFacePath = representativeFacePath; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
