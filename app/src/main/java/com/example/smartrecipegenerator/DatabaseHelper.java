package com.example.smartrecipegenerator;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "users.db";
    private static final int DATABASE_VERSION = 2;
    private static final String TABLE_USERS = "users";
    private static final String COLUMN_ID = "id";
    private static final String COLUMN_NAME = "name";
    private static final String COLUMN_EMAIL = "email";
    private static final String COLUMN_PASSWORD = "password";

    // Pantry 表
    private static final String TABLE_PANTRY = "pantry";
    private static final String COLUMN_PANTRY_ID = "id";
    private static final String COLUMN_USER_ID = "user_id";
    private static final String COLUMN_ITEM_NAME = "item_name";
    private static final String COLUMN_DATE_ADDED = "date_added";

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        // 創建用戶表
        String CREATE_USERS_TABLE = "CREATE TABLE " + TABLE_USERS + "("
                + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + COLUMN_NAME + " TEXT,"
                + COLUMN_EMAIL + " TEXT UNIQUE,"
                + COLUMN_PASSWORD + " TEXT" + ")";
        db.execSQL(CREATE_USERS_TABLE);
        
        // 創建 Pantry 表
        String CREATE_PANTRY_TABLE = "CREATE TABLE " + TABLE_PANTRY + "("
                + COLUMN_PANTRY_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + COLUMN_USER_ID + " INTEGER,"
                + COLUMN_ITEM_NAME + " TEXT,"
                + COLUMN_DATE_ADDED + " DATETIME DEFAULT CURRENT_TIMESTAMP,"
                + "FOREIGN KEY(" + COLUMN_USER_ID + ") REFERENCES " 
                + TABLE_USERS + "(" + COLUMN_ID + ")" + ")";
        db.execSQL(CREATE_PANTRY_TABLE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            // 創建 Pantry 表（如果是從版本1升級）
            String CREATE_PANTRY_TABLE = "CREATE TABLE " + TABLE_PANTRY + "("
                    + COLUMN_PANTRY_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + COLUMN_USER_ID + " INTEGER,"
                    + COLUMN_ITEM_NAME + " TEXT,"
                    + COLUMN_DATE_ADDED + " DATETIME DEFAULT CURRENT_TIMESTAMP,"
                    + "FOREIGN KEY(" + COLUMN_USER_ID + ") REFERENCES " 
                    + TABLE_USERS + "(" + COLUMN_ID + ")" + ")";
            db.execSQL(CREATE_PANTRY_TABLE);
        }
    }

    private String generateTempUsername() {
        String CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder sb = new StringBuilder();
        Random random = new Random();
        for (int i = 0; i < 8; i++) {
            sb.append(CHARS.charAt(random.nextInt(CHARS.length())));
        }
        return "User_" + sb.toString();
    }

    public boolean insertUser(String email, String password) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_NAME, generateTempUsername());
        values.put(COLUMN_EMAIL, email);
        values.put(COLUMN_PASSWORD, password);

        long result = db.insert(TABLE_USERS, null, values);
        db.close();
        return result != -1;
    }

    public UserInfo getUserInfo(String email) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] columns = {COLUMN_NAME, COLUMN_EMAIL};
        String selection = COLUMN_EMAIL + "=?";
        String[] selectionArgs = {email};
        
        Cursor cursor = db.query(TABLE_USERS, columns, selection, selectionArgs, null, null, null);
        UserInfo userInfo = null;
        
        if (cursor != null && cursor.moveToFirst()) {
            userInfo = new UserInfo(
                cursor.getString(cursor.getColumnIndex(COLUMN_NAME)),
                cursor.getString(cursor.getColumnIndex(COLUMN_EMAIL))
            );
            cursor.close();
        }
        
        db.close();
        return userInfo;
    }

    public boolean updateUsername(String email, String newName) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_NAME, newName);
        
        int result = db.update(TABLE_USERS, values, COLUMN_EMAIL + "=?", new String[]{email});
        db.close();
        return result > 0;
    }

    public boolean checkUser(String email, String password) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] columns = {COLUMN_ID};
        String selection = COLUMN_EMAIL + "=? AND " + COLUMN_PASSWORD + "=?";
        String[] selectionArgs = {email, password};
        Cursor cursor = db.query(TABLE_USERS, columns, selection, selectionArgs, null, null, null);
        int count = cursor.getCount();
        cursor.close();
        db.close();
        return count > 0;
    }

    /**
     * 從數據庫中刪除用戶
     * @param email 用戶郵箱
     * @return 是否成功刪除
     */
    public boolean deleteUser(String email) {
        SQLiteDatabase db = this.getWritableDatabase();
        int result = db.delete(TABLE_USERS, COLUMN_EMAIL + "=?", new String[]{email});
        db.close();
        return result > 0;
    }

    // 獲取用戶ID
    public int getUserId(String email) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] columns = {COLUMN_ID};
        String selection = COLUMN_EMAIL + "=?";
        String[] selectionArgs = {email};
        
        Cursor cursor = db.query(TABLE_USERS, columns, selection, selectionArgs, null, null, null);
        int userId = -1;
        
        if (cursor != null && cursor.moveToFirst()) {
            userId = cursor.getInt(cursor.getColumnIndex(COLUMN_ID));
            cursor.close();
        }
        
        db.close();
        return userId;
    }
    
    // 添加物品到 Pantry
    public boolean addItemToPantry(int userId, String itemName) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_USER_ID, userId);
        values.put(COLUMN_ITEM_NAME, itemName);
        
        long result = db.insert(TABLE_PANTRY, null, values);
        db.close();
        return result != -1;
    }
    
    // 獲取用戶的 Pantry 物品
    public List<PantryItem> getUserPantryItems(int userId) {
        List<PantryItem> pantryItems = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        
        String[] columns = {COLUMN_PANTRY_ID, COLUMN_ITEM_NAME, COLUMN_DATE_ADDED};
        String selection = COLUMN_USER_ID + "=?";
        String[] selectionArgs = {String.valueOf(userId)};
        
        Cursor cursor = db.query(TABLE_PANTRY, columns, selection, selectionArgs, null, null, COLUMN_DATE_ADDED + " DESC");
        
        if (cursor != null && cursor.moveToFirst()) {
            do {
                int id = cursor.getInt(cursor.getColumnIndex(COLUMN_PANTRY_ID));
                String itemName = cursor.getString(cursor.getColumnIndex(COLUMN_ITEM_NAME));
                String dateAdded = cursor.getString(cursor.getColumnIndex(COLUMN_DATE_ADDED));
                
                PantryItem item = new PantryItem(id, itemName, dateAdded);
                pantryItems.add(item);
            } while (cursor.moveToNext());
            
            cursor.close();
        }
        
        db.close();
        return pantryItems;
    }
    
    // 從 Pantry 刪除物品
    public boolean deleteItemFromPantry(int itemId) {
        SQLiteDatabase db = this.getWritableDatabase();
        int result = db.delete(TABLE_PANTRY, COLUMN_PANTRY_ID + "=?", new String[]{String.valueOf(itemId)});
        db.close();
        return result > 0;
    }
}
