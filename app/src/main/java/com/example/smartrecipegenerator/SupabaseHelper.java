package com.example.smartrecipegenerator;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class SupabaseHelper {
    private static final String TAG = "SupabaseHelper";
    
    // Supabase 配置
    private static final String SUPABASE_URL = "https://zudkfhhtyrwtsrsvtxlf.supabase.co";
    private static final String SUPABASE_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Inp1ZGtmaGh0eXJ3dHNyc3Z0eGxmIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NDIyNzc3NjcsImV4cCI6MjA1Nzg1Mzc2N30.pKTCsMU5TQuMjrp7D7lUWStasj9WiyBWOswBb9vFvsI";
    
    // 表名
    private static final String TABLE_USERS = "users";
    private static final String TABLE_PANTRY = "pantry";
    private static final String TABLE_RECIPES = "recipes";
    
    // JSON MediaType
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    
    // OkHttpClient 實例
    private final OkHttpClient client = new OkHttpClient();
    
    // Context
    private final Context context;
    
    public SupabaseHelper(Context context) {
        this.context = context;
    }
    
    /**
     * 生成臨時用戶名
     */
    private String generateTempUsername() {
        String CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder sb = new StringBuilder();
        Random random = new Random();
        for (int i = 0; i < 8; i++) {
            sb.append(CHARS.charAt(random.nextInt(CHARS.length())));
        }
        return "User_" + sb.toString();
    }
    
    /**
     * 註冊新用戶
     * @param email 用戶郵箱
     * @param password 用戶密碼
     * @param callback 回調函數
     */
    public void signUp(String email, String password, final SupabaseCallback<Boolean> callback) {
        try {
            // 生成臨時用戶名
            String tempUsername = generateTempUsername();
            
            // 創建請求體
            JSONObject requestBody = new JSONObject();
            requestBody.put("name", tempUsername);
            requestBody.put("email", email);
            requestBody.put("password", password);
            
            // 創建請求
            Request request = new Request.Builder()
                .url(SUPABASE_URL + "/rest/v1/" + TABLE_USERS)
                .addHeader("apikey", SUPABASE_KEY)
                .addHeader("Authorization", "Bearer " + SUPABASE_KEY)
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "return=representation")
                .post(RequestBody.create(requestBody.toString(), JSON))
                .build();
            
            // 發送請求
            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "Sign up failed", e);
                    callback.onError(e.getMessage());
                }
                
                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (response.isSuccessful()) {
                        callback.onSuccess(true);
                    } else {
                        String errorBody = response.body().string();
                        Log.e(TAG, "Sign up failed: " + errorBody);
                        callback.onError("Sign up failed: " + errorBody);
                    }
                }
            });
        } catch (JSONException e) {
            Log.e(TAG, "Error creating JSON", e);
            callback.onError(e.getMessage());
        }
    }
    
    /**
     * 用戶登錄
     * @param email 用戶郵箱
     * @param password 用戶密碼
     * @param callback 回調函數
     */
    public void signIn(String email, String password, final SupabaseCallback<Boolean> callback) {
        try {
            // 創建請求
            Request request = new Request.Builder()
                .url(SUPABASE_URL + "/rest/v1/" + TABLE_USERS + "?email=eq." + email + "&password=eq." + password + "&select=id,email")
                .addHeader("apikey", SUPABASE_KEY)
                .addHeader("Authorization", "Bearer " + SUPABASE_KEY)
                .get()
                .build();
            
            // 發送請求
            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "Sign in failed", e);
                    callback.onError(e.getMessage());
                }
                
                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (response.isSuccessful()) {
                        String responseBody = response.body().string();
                        try {
                            JSONArray jsonArray = new JSONArray(responseBody);
                            if (jsonArray.length() > 0) {
                                JSONObject user = jsonArray.getJSONObject(0);
                                String userEmail = user.getString("email");
                                int userId = user.getInt("id");
                                
                                // 保存用戶信息到 SharedPreferences
                                SharedPreferences prefs = context.getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
                                prefs.edit()
                                    .putString("email", userEmail)
                                    .putInt("userId", userId)
                                    .apply();
                                
                                callback.onSuccess(true);
                            } else {
                                callback.onSuccess(false);
                            }
                        } catch (JSONException e) {
                            Log.e(TAG, "Error parsing JSON", e);
                            callback.onError(e.getMessage());
                        }
                    } else {
                        String errorBody = response.body().string();
                        Log.e(TAG, "Sign in failed: " + errorBody);
                        callback.onError("Sign in failed: " + errorBody);
                    }
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error signing in", e);
            callback.onError(e.getMessage());
        }
    }
    
    /**
     * 獲取用戶信息
     * @param email 用戶郵箱
     * @param callback 回調函數
     */
    public void getUserInfo(String email, final SupabaseCallback<UserInfo> callback) {
        try {
            // 創建請求
            Request request = new Request.Builder()
                .url(SUPABASE_URL + "/rest/v1/" + TABLE_USERS + "?email=eq." + email + "&select=name,email")
                .addHeader("apikey", SUPABASE_KEY)
                .addHeader("Authorization", "Bearer " + SUPABASE_KEY)
                .get()
                .build();
            
            // 發送請求
            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "Get user info failed", e);
                    callback.onError(e.getMessage());
                }
                
                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (response.isSuccessful()) {
                        String responseBody = response.body().string();
                        try {
                            JSONArray jsonArray = new JSONArray(responseBody);
                            if (jsonArray.length() > 0) {
                                JSONObject user = jsonArray.getJSONObject(0);
                                String name = user.getString("name");
                                String userEmail = user.getString("email");
                                
                                UserInfo userInfo = new UserInfo(name, userEmail);
                                callback.onSuccess(userInfo);
                            } else {
                                callback.onError("User not found");
                            }
                        } catch (JSONException e) {
                            Log.e(TAG, "Error parsing JSON", e);
                            callback.onError(e.getMessage());
                        }
                    } else {
                        String errorBody = response.body().string();
                        Log.e(TAG, "Get user info failed: " + errorBody);
                        callback.onError("Get user info failed: " + errorBody);
                    }
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error getting user info", e);
            callback.onError(e.getMessage());
        }
    }
    
    /**
     * 更新用戶名
     * @param email 用戶郵箱
     * @param newName 新用戶名
     * @param callback 回調函數
     */
    public void updateUsername(String email, String newName, final SupabaseCallback<Boolean> callback) {
        try {
            // 創建請求體
            JSONObject requestBody = new JSONObject();
            requestBody.put("name", newName);
            
            // 創建請求
            Request request = new Request.Builder()
                .url(SUPABASE_URL + "/rest/v1/" + TABLE_USERS + "?email=eq." + email)
                .addHeader("apikey", SUPABASE_KEY)
                .addHeader("Authorization", "Bearer " + SUPABASE_KEY)
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "return=representation")
                .patch(RequestBody.create(requestBody.toString(), JSON))
                .build();
            
            // 發送請求
            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "Update username failed", e);
                    callback.onError(e.getMessage());
                }
                
                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (response.isSuccessful()) {
                        callback.onSuccess(true);
                    } else {
                        String errorBody = response.body().string();
                        Log.e(TAG, "Update username failed: " + errorBody);
                        callback.onError("Update username failed: " + errorBody);
                    }
                }
            });
        } catch (JSONException e) {
            Log.e(TAG, "Error creating JSON", e);
            callback.onError(e.getMessage());
        }
    }
    
    /**
     * 獲取用戶 ID
     * @param email 用戶郵箱
     * @param callback 回調函數
     */
    public void getUserId(String email, final SupabaseCallback<Integer> callback) {
        try {
            // 創建請求
            Request request = new Request.Builder()
                .url(SUPABASE_URL + "/rest/v1/" + TABLE_USERS + "?email=eq." + email + "&select=id")
                .addHeader("apikey", SUPABASE_KEY)
                .addHeader("Authorization", "Bearer " + SUPABASE_KEY)
                .get()
                .build();
            
            // 發送請求
            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "Get user ID failed", e);
                    callback.onError(e.getMessage());
                }
                
                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (response.isSuccessful()) {
                        try {
                            String responseBody = response.body().string();
                            JSONArray jsonArray = new JSONArray(responseBody);
                            
                            if (jsonArray.length() > 0) {
                                JSONObject userObject = jsonArray.getJSONObject(0);
                                int userId = userObject.getInt("id");
                                callback.onSuccess(userId);
                            } else {
                                callback.onSuccess(-1); // 用戶不存在
                            }
                        } catch (JSONException e) {
                            Log.e(TAG, "Error parsing JSON", e);
                            callback.onError(e.getMessage());
                        }
                    } else {
                        String errorBody = response.body().string();
                        Log.e(TAG, "Get user ID failed: " + errorBody);
                        callback.onError("Get user ID failed: " + errorBody);
                    }
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error getting user ID", e);
            callback.onError(e.getMessage());
        }
    }
    
    /**
     * 添加物品到 Pantry
     * @param userId 用戶 ID
     * @param itemName 物品名稱
     * @param callback 回調函數
     */
    public void addItemToPantry(int userId, String itemName, final SupabaseCallback<Boolean> callback) {
        try {
            // 創建請求體
            JSONObject requestBody = new JSONObject();
            requestBody.put("user_id", userId);
            requestBody.put("item_name", itemName);
            
            // 創建請求
            Request request = new Request.Builder()
                .url(SUPABASE_URL + "/rest/v1/" + TABLE_PANTRY)
                .addHeader("apikey", SUPABASE_KEY)
                .addHeader("Authorization", "Bearer " + SUPABASE_KEY)
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "return=representation")
                .post(RequestBody.create(requestBody.toString(), JSON))
                .build();
            
            // 發送請求
            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "Add item to pantry failed", e);
                    callback.onError(e.getMessage());
                }
                
                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (response.isSuccessful()) {
                        callback.onSuccess(true);
                    } else {
                        String errorBody = response.body().string();
                        Log.e(TAG, "Add item to pantry failed: " + errorBody);
                        callback.onError("Add item to pantry failed: " + errorBody);
                    }
                }
            });
        } catch (JSONException e) {
            Log.e(TAG, "Error creating JSON", e);
            callback.onError(e.getMessage());
        }
    }
    
    /**
     * 獲取用戶的 Pantry 物品
     * @param userId 用戶 ID
     * @param callback 回調函數
     */
    public void getUserPantryItems(int userId, final SupabaseCallback<List<PantryItem>> callback) {
        try {
            Log.d(TAG, "Getting pantry items for user ID: " + userId);
            
            // 創建請求
            Request request = new Request.Builder()
                .url(SUPABASE_URL + "/rest/v1/" + TABLE_PANTRY + "?user_id=eq." + userId + "&order=created_at.desc")
                .addHeader("apikey", SUPABASE_KEY)
                .addHeader("Authorization", "Bearer " + SUPABASE_KEY)
                .get()
                .build();
            
            // 發送請求
            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "Get pantry items failed", e);
                    callback.onError(e.getMessage());
                }
                
                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    String responseBody = response.body().string();
                    Log.d(TAG, "Pantry items response: " + responseBody);
                    
                    if (response.isSuccessful()) {
                        try {
                            JSONArray jsonArray = new JSONArray(responseBody);
                            List<PantryItem> pantryItems = new ArrayList<>();
                            
                            for (int i = 0; i < jsonArray.length(); i++) {
                                JSONObject itemObject = jsonArray.getJSONObject(i);
                                int id = itemObject.getInt("id");
                                String itemName = itemObject.getString("item_name");
                                String createdAt = itemObject.getString("created_at");
                                
                                PantryItem item = new PantryItem(id, itemName, createdAt);
                                pantryItems.add(item);
                            }
                            
                            Log.d(TAG, "Parsed " + pantryItems.size() + " pantry items");
                            callback.onSuccess(pantryItems);
                        } catch (JSONException e) {
                            Log.e(TAG, "Error parsing JSON", e);
                            callback.onError(e.getMessage());
                        }
                    } else {
                        Log.e(TAG, "Get pantry items failed: " + responseBody);
                        callback.onError("Get pantry items failed: " + responseBody);
                    }
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error getting pantry items", e);
            callback.onError(e.getMessage());
        }
    }
    
    /**
     * 從 Pantry 刪除物品
     * @param itemId 物品 ID
     * @param callback 回調函數
     */
    public void deleteItemFromPantry(int itemId, final SupabaseCallback<Boolean> callback) {
        try {
            // 創建請求
            Request request = new Request.Builder()
                .url(SUPABASE_URL + "/rest/v1/" + TABLE_PANTRY + "?id=eq." + itemId)
                .addHeader("apikey", SUPABASE_KEY)
                .addHeader("Authorization", "Bearer " + SUPABASE_KEY)
                .delete()
                .build();
            
            // 發送請求
            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "Delete item from pantry failed", e);
                    callback.onError(e.getMessage());
                }
                
                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (response.isSuccessful()) {
                        callback.onSuccess(true);
                    } else {
                        String errorBody = response.body().string();
                        Log.e(TAG, "Delete item from pantry failed: " + errorBody);
                        callback.onError("Delete item from pantry failed: " + errorBody);
                    }
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error deleting item from pantry", e);
            callback.onError(e.getMessage());
        }
    }
    
    /**
     * 刪除用戶
     * @param email 用戶郵箱
     * @param callback 回調函數
     */
    public void deleteUser(String email, final SupabaseCallback<Boolean> callback) {
        try {
            // 創建請求
            Request request = new Request.Builder()
                .url(SUPABASE_URL + "/rest/v1/" + TABLE_USERS + "?email=eq." + email)
                .addHeader("apikey", SUPABASE_KEY)
                .addHeader("Authorization", "Bearer " + SUPABASE_KEY)
                .delete()
                .build();
            
            // 發送請求
            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "Delete user failed", e);
                    callback.onError(e.getMessage());
                }
                
                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (response.isSuccessful()) {
                        callback.onSuccess(true);
                    } else {
                        String errorBody = response.body().string();
                        Log.e(TAG, "Delete user failed: " + errorBody);
                        callback.onError("Delete user failed: " + errorBody);
                    }
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error deleting user", e);
            callback.onError(e.getMessage());
        }
    }
    
    /**
     * 保存食譜到數據庫
     * @param userId 用戶 ID
     * @param title 食譜標題
     * @param content 食譜內容
     * @param imageUrl 食譜圖片 URL
     * @param callback 回調函數
     */
    public void saveRecipe(int userId, String title, String content, String imageUrl, final SupabaseCallback<Boolean> callback) {
        try {
            // 創建請求體
            JSONObject requestBody = new JSONObject();
            requestBody.put("user_id", userId);
            requestBody.put("title", title);
            requestBody.put("content", content);
            requestBody.put("image_url", imageUrl);
            
            // 創建請求
            Request request = new Request.Builder()
                .url(SUPABASE_URL + "/rest/v1/" + TABLE_RECIPES)
                .addHeader("apikey", SUPABASE_KEY)
                .addHeader("Authorization", "Bearer " + SUPABASE_KEY)
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "return=representation")
                .post(RequestBody.create(requestBody.toString(), JSON))
                .build();
            
            // 發送請求
            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "Save recipe failed", e);
                    callback.onError(e.getMessage());
                }
                
                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (response.isSuccessful()) {
                        callback.onSuccess(true);
                    } else {
                        String errorBody = response.body().string();
                        Log.e(TAG, "Save recipe failed: " + errorBody);
                        callback.onError("Save recipe failed: " + errorBody);
                    }
                }
            });
        } catch (JSONException e) {
            Log.e(TAG, "Error creating JSON", e);
            callback.onError(e.getMessage());
        }
    }
    
    /**
     * 回調接口
     */
    public interface SupabaseCallback<T> {
        void onSuccess(T result);
        void onError(String errorMessage);
    }
} 