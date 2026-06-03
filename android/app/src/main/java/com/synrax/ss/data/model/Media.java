package com.synrax.ss.data.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;
import java.util.Map;

public class Media {
    @SerializedName("id")
    private int id;

    @SerializedName("event_id")
    private String eventId;

    @SerializedName("uploader_id")
    private int uploaderId;

    @SerializedName("storage_path")
    private String storagePath;

    @SerializedName("file_name")
    private String fileName;

    @SerializedName("file_size")
    private long fileSize;

    @SerializedName("content_type")
    private String contentType;

    @SerializedName("phash")
    private String phash;

    @SerializedName("metadata_json")
    private Map<String, Object> metadataJson;

    @SerializedName("created_at")
    private String createdAt;

    @SerializedName("detected_faces")
    private List<DetectedFace> detectedFaces;

    // Constructors
    public Media() {}

    public Media(int id, String eventId, int uploaderId, String storagePath, String fileName, long fileSize, String contentType, String phash, Map<String, Object> metadataJson, String createdAt, List<DetectedFace> detectedFaces) {
        this.id = id;
        this.eventId = eventId;
        this.uploaderId = uploaderId;
        this.storagePath = storagePath;
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.contentType = contentType;
        this.phash = phash;
        this.metadataJson = metadataJson;
        this.createdAt = createdAt;
        this.detectedFaces = detectedFaces;
    }

    // Getters and Setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }

    public int getUploaderId() { return uploaderId; }
    public void setUploaderId(int uploaderId) { this.uploaderId = uploaderId; }

    public String getStoragePath() { return storagePath; }
    public void setStoragePath(String storagePath) { this.storagePath = storagePath; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public long getFileSize() { return fileSize; }
    public void setFileSize(long fileSize) { this.fileSize = fileSize; }

    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }

    public String getPhash() { return phash; }
    public void setPhash(String phash) { this.phash = phash; }

    public Map<String, Object> getMetadataJson() { return metadataJson; }
    public void setMetadataJson(Map<String, Object> metadataJson) { this.metadataJson = metadataJson; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public List<DetectedFace> getDetectedFaces() { return detectedFaces; }
    public void setDetectedFaces(List<DetectedFace> detectedFaces) { this.detectedFaces = detectedFaces; }
}
