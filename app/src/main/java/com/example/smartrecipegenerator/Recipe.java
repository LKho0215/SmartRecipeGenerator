package com.example.smartrecipegenerator;

public class Recipe {
    private int id;
    private int userId;
    private String title;
    private String content;
    private String imageUrl;
    private String createdAt;
    
    public Recipe(int id, int userId, String title, String content, String imageUrl, String createdAt) {
        this.id = id;
        this.userId = userId;
        this.title = title;
        this.content = content;
        this.imageUrl = imageUrl;
        this.createdAt = createdAt;
    }
    
    public int getId() {
        return id;
    }
    
    public int getUserId() {
        return userId;
    }
    
    public String getTitle() {
        return title;
    }
    
    public String getContent() {
        return content;
    }
    
    public String getImageUrl() {
        return imageUrl;
    }
    
    public String getCreatedAt() {
        return createdAt;
    }
}