package com.example.smartrecipegenerator;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.webkit.WebView;
import android.webkit.JavascriptInterface;
import android.widget.Toast;

import androidx.annotation.OptIn;
import androidx.camera.core.ExperimentalGetImage;

import es.dmoral.toasty.Toasty;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

public class WebAppInterface {
    private Context context;
    private WebView webView;
    private DatabaseHelper dbHelper;

    WebAppInterface(Context context, WebView webView) {
        this.context = context;
        this.webView = webView;
        this.dbHelper = new DatabaseHelper(context);
    }

    @JavascriptInterface
    public void signIn(String email, String password) {
        if (dbHelper.checkUser(email, password)) {
            SharedPreferences prefs = context.getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
            prefs.edit().putString("email", email).apply();
            
            Toasty.success(context, "Login Successful", Toast.LENGTH_SHORT, true).show();
            ((Activity)context).runOnUiThread(() -> {
                ((MainActivity)context).getWebView().loadUrl("file:///android_asset/home.html");
            });
        } else {
            Toasty.error(context, "Invalid Credentials", Toast.LENGTH_SHORT, true).show();
        }
    }

    @JavascriptInterface
    public void signUp(String email, String password) {
        if (dbHelper.insertUser(email, password)) {
            Toasty.success(context, "Registration Successful", Toast.LENGTH_SHORT, true).show();
            ((Activity)context).runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    ((MainActivity)context).getWebView().loadUrl("file:///android_asset/login.html");
                }
            });
        } else {
            Toasty.error(context, "Registration Failed", Toast.LENGTH_SHORT, true).show();
        }
    }

    @OptIn(markerClass = ExperimentalGetImage.class)
    @JavascriptInterface
    public void snapPhoto() {
        try {
            Intent intent = new Intent(context, CameraActivity.class);
            context.startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(context, "Fail to Execute XCam: " + e.getMessage(),
                Toast.LENGTH_SHORT).show();
        }
    }

    @JavascriptInterface
    public void getUserInfo() {
        SharedPreferences prefs = context.getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
        String currentUserEmail = prefs.getString("email", "");

        UserInfo userInfo = dbHelper.getUserInfo(currentUserEmail);
        if (userInfo != null) {
            ((Activity) context).runOnUiThread(() -> {
                webView.evaluateJavascript(
                    String.format("updateUserInfo('%s', '%s')", 
                    userInfo.getName(), 
                    userInfo.getEmail()),
                    null
                );
            });
        }
    }

    @JavascriptInterface
    public void editProfile() {
        Toast.makeText(context, "編輯資料功能即將推出", Toast.LENGTH_SHORT).show();
    }

    @JavascriptInterface
    public void logout() {
        SharedPreferences prefs = context.getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
        prefs.edit().clear().apply();
        
        ((Activity)context).runOnUiThread(() -> {
            ((MainActivity)context).getWebView().loadUrl("file:///android_asset/login.html");
        });
    }

    @JavascriptInterface
    public void deleteAccount() {
        // 從 SharedPreferences 獲取當前登錄用戶的郵箱
        SharedPreferences prefs = context.getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
        String currentUserEmail = prefs.getString("email", "");
        
        if (currentUserEmail.isEmpty()) {
            Toast.makeText(context, "無法刪除賬號：未找到用戶信息", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // 從數據庫刪除用戶
        boolean isDeleted = dbHelper.deleteUser(currentUserEmail);
        
        if (isDeleted) {
            // 清除 SharedPreferences 中的用戶信息
            prefs.edit().clear().apply();
            
            // 顯示成功消息
            Toast.makeText(context, "Account deleted successfully", Toast.LENGTH_SHORT).show();
            
            // 返回登錄頁面
            ((Activity)context).runOnUiThread(() -> {
                ((MainActivity)context).getWebView().loadUrl("file:///android_asset/login.html");
            });
        } else {
            // 顯示錯誤消息
            Toast.makeText(context, "刪除賬號失敗，請稍後再試", Toast.LENGTH_SHORT).show();
        }
    }

    @JavascriptInterface
    public void getPantryItems() {
        SharedPreferences prefs = context.getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
        String email = prefs.getString("email", "");
        
        DatabaseHelper dbHelper = new DatabaseHelper(context);
        int userId = dbHelper.getUserId(email);
        
        if (userId != -1) {
            List<PantryItem> items = dbHelper.getUserPantryItems(userId);
            
            // 將物品列表轉換為 JSON
            JSONArray jsonArray = new JSONArray();
            for (PantryItem item : items) {
                try {
                    JSONObject jsonItem = new JSONObject();
                    jsonItem.put("id", item.getId());
                    jsonItem.put("name", item.getName());
                    jsonItem.put("dateAdded", item.getDateAdded());
                    jsonArray.put(jsonItem);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
            
            final String itemsJson = jsonArray.toString();
            
            ((Activity) context).runOnUiThread(() -> {
                webView.evaluateJavascript(
                    "updatePantryItems('" + itemsJson + "')",
                    null
                );
            });
        }
    }

    @JavascriptInterface
    public void addPantryItem(String itemName) {
        SharedPreferences prefs = context.getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
        String email = prefs.getString("email", "");
        
        DatabaseHelper dbHelper = new DatabaseHelper(context);
        int userId = dbHelper.getUserId(email);
        
        if (userId != -1) {
            boolean added = dbHelper.addItemToPantry(userId, itemName);
            if (added) {
                Toast.makeText(context, itemName + " added to pantry", Toast.LENGTH_SHORT).show();
                // 刷新列表
                getPantryItems();
            } else {
                Toast.makeText(context, "Failed to add item", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @JavascriptInterface
    public void deletePantryItem(int itemId) {
        DatabaseHelper dbHelper = new DatabaseHelper(context);
        boolean deleted = dbHelper.deleteItemFromPantry(itemId);
        
        if (deleted) {
            Toast.makeText(context, "Item deleted", Toast.LENGTH_SHORT).show();
            // 刷新列表
            getPantryItems();
        } else {
            Toast.makeText(context, "Failed to delete item", Toast.LENGTH_SHORT).show();
        }
    }

    @JavascriptInterface
    public void viewPantry() {
        ((Activity)context).runOnUiThread(() -> {
            webView.loadUrl("file:///android_asset/pantry.html");
        });
    }
}
