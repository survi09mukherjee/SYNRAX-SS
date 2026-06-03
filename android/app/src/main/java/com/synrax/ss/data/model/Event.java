package com.synrax.ss.data.model;

import com.google.gson.annotations.SerializedName;

public class Event {
    @SerializedName("id")
    private String id;

    @SerializedName("name")
    private String name;

    @SerializedName("description")
    private String description;

    @SerializedName("passcode")
    private String passcode;

    @SerializedName("qr_payload")
    private String qrPayload;

    @SerializedName("creator_id")
    private int creatorId;

    @SerializedName("starts_at")
    private String startsAt;

    @SerializedName("expires_at")
    private String expiresAt;

    @SerializedName("created_at")
    private String createdAt;

    // Constructors
    public Event() {}

    public Event(String id, String name, String description, String passcode, String qrPayload, int creatorId, String startsAt, String expiresAt, String createdAt) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.passcode = passcode;
        this.qrPayload = qrPayload;
        this.creatorId = creatorId;
        this.startsAt = startsAt;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getPasscode() { return passcode; }
    public void setPasscode(String passcode) { this.passcode = passcode; }

    public String getQrPayload() { return qrPayload; }
    public void setQrPayload(String qrPayload) { this.qrPayload = qrPayload; }

    public int getCreatorId() { return creatorId; }
    public void setCreatorId(int creatorId) { this.creatorId = creatorId; }

    public String getStartsAt() { return startsAt; }
    public void setStartsAt(String startsAt) { this.startsAt = startsAt; }

    public String getExpiresAt() { return expiresAt; }
    public void setExpiresAt(String expiresAt) { this.expiresAt = expiresAt; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
