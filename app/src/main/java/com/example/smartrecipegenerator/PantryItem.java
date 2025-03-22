package com.example.smartrecipegenerator;

public class PantryItem {
    private int id;
    private String name;
    private String dateAdded;
    
    public PantryItem(int id, String name, String dateAdded) {
        this.id = id;
        this.name = name;
        this.dateAdded = dateAdded;
    }
    
    public int getId() {
        return id;
    }
    
    public String getName() {
        return name;
    }
    
    public String getDateAdded() {
        return dateAdded;
    }
} 