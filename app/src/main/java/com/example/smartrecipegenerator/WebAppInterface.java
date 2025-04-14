package com.example.smartrecipegenerator;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.webkit.WebView;
import android.webkit.JavascriptInterface;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.annotation.OptIn;
import androidx.camera.core.ExperimentalGetImage;

import es.dmoral.toasty.Toasty;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.io.FileOutputStream;
import android.util.Log;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import android.text.Html;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;

public class WebAppInterface {
    private Context context;
    private WebView webView;
    private SupabaseHelper supabaseHelper;

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final String GEMINI_API_KEY = "AIzaSyB6cHgVlwh15xwLhgpRnpubCwV4AdUV0Q0"; // 替換為你的 Gemini API 密鑰
    private String generatedRecipe = "";
    private String currentImageUrl = ""; // 新增變量來存儲當前圖片URL

    WebAppInterface(Context context, WebView webView) {
        this.context = context;
        this.webView = webView;
        this.supabaseHelper = new SupabaseHelper(context);
    }

    @JavascriptInterface
    public void signIn(String email, String password) {
        // 對輸入的密碼進行哈希處理
        String hashedPassword = hashPassword(password);

        supabaseHelper.signIn(email, hashedPassword, new SupabaseHelper.SupabaseCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean result) {
                if (result) {
                    saveLoginState(email);
                    ((Activity) context).runOnUiThread(() -> {
                        webView.evaluateJavascript("handleLoginSuccess()", null);
                        Toasty.success(context, "Login Successful", Toast.LENGTH_SHORT, true).show();
                        new Handler().postDelayed(() -> {
                            webView.loadUrl("file:///android_asset/home.html");
                        }, 3000);
                    });
                } else {
                    ((Activity) context).runOnUiThread(() -> {
                        Toasty.error(context, "Invalid Credentials", Toast.LENGTH_SHORT, true).show();
                    });
                }
            }

            @Override
            public void onError(String errorMessage) {
                ((Activity) context).runOnUiThread(() -> {
                    Toasty.error(context, "Login Failed: " + errorMessage, Toast.LENGTH_SHORT, true).show();
                });
            }
        });
    }

    // 保存登錄狀態
    private void saveLoginState(String email) {
        SharedPreferences prefs = context.getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean("isLoggedIn", true);
        editor.putString("email", email);
        editor.apply();
    }

    @JavascriptInterface
    public void signUp(String email, String password) {
        // 後端再次驗證密碼長度
        if (password.length() < 6) {
            ((Activity) context).runOnUiThread(() -> {
                Toasty.error(context, "Password must be at least 6 characters long", Toast.LENGTH_SHORT, true).show();
            });
            return;
        }

        // 對密碼進行哈希處理
        String hashedPassword = hashPassword(password);

        supabaseHelper.signUp(email, hashedPassword, new SupabaseHelper.SupabaseCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean result) {
                if (result) {
                    ((Activity) context).runOnUiThread(() -> {
                        Toasty.success(context, "Registration Successful", Toast.LENGTH_SHORT, true).show();
                        webView.loadUrl("file:///android_asset/login.html");
                    });
                } else {
                    ((Activity) context).runOnUiThread(() -> {
                        Toasty.error(context, "Registration Failed", Toast.LENGTH_SHORT, true).show();
                    });
                }
            }

            @Override
            public void onError(String errorMessage) {
                ((Activity) context).runOnUiThread(() -> {
                    Toasty.error(context, "Email has been used", Toast.LENGTH_SHORT, true).show();
                });
            }
        });
    }

    // 添加哈希密碼的方法
    private String hashPassword(String password) {
        try {
            // 創建 MessageDigest 實例，使用 SHA-256 算法
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            // 將密碼轉換為字節數組並進行哈希
            byte[] hash = digest.digest(password.getBytes(StandardCharsets.UTF_8));

            // 將字節數組轉換為十六進制字符串
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }

            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            Log.e("PasswordHash", "Error hashing password", e);
            // 如果哈希失敗，返回原始密碼（不應該發生）
            return password;
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

        supabaseHelper.getUserInfo(currentUserEmail, new SupabaseHelper.SupabaseCallback<UserInfo>() {
            @Override
            public void onSuccess(UserInfo userInfo) {
                ((Activity) context).runOnUiThread(() -> {
                    webView.evaluateJavascript(
                            String.format("updateUserInfo('%s', '%s')",
                                    userInfo.getName(),
                                    userInfo.getEmail()),
                            null
                    );
                });
            }

            @Override
            public void onError(String errorMessage) {
                ((Activity) context).runOnUiThread(() -> {
                    Toast.makeText(context, "Failed to get user info: " + errorMessage, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    @JavascriptInterface
    public void updateUserName(String newName) {
        Log.d("ProfileDebug", "updateUserName called with: " + newName);

        SharedPreferences prefs = context.getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
        String email = prefs.getString("email", "");

        if (!email.isEmpty()) {
            // 首先獲取用戶ID
            supabaseHelper.getUserId(email, new SupabaseHelper.SupabaseCallback<Integer>() {
                @Override
                public void onSuccess(Integer userId) {
                    if (userId != -1) {
                        // 使用用戶ID更新用戶名
                        supabaseHelper.updateUserName(userId, newName, new SupabaseHelper.SupabaseCallback<Boolean>() {
                            @Override
                            public void onSuccess(Boolean result) {
                                ((Activity) context).runOnUiThread(() -> {
                                    if (result) {
                                        // 更新本地存儲的用戶名
                                        SharedPreferences.Editor editor = prefs.edit();
                                        editor.putString("name", newName);
                                        editor.apply();

                                        // 通知前端更新成功
                                        webView.evaluateJavascript(
                                            "handleUpdateSuccess('Name updated successfully')",
                                            null
                                        );
                                    } else {
                                        webView.evaluateJavascript(
                                            "handleUpdateError('Failed to update name')",
                                            null
                                        );
                                    }
                                });
                            }

                            @Override
                            public void onError(String errorMessage) {
                                Log.e("ProfileDebug", "Error updating name: " + errorMessage);
                                ((Activity) context).runOnUiThread(() -> {
                                    webView.evaluateJavascript(
                                        "handleUpdateError('" + errorMessage + "')",
                                        null
                                    );
                                });
                            }
                        });
                    } else {
                        ((Activity) context).runOnUiThread(() -> {
                            webView.evaluateJavascript(
                                "handleUpdateError('User not found')",
                                null
                            );
                        });
                    }
                }

                @Override
                public void onError(String errorMessage) {
                    Log.e("ProfileDebug", "Error getting user ID: " + errorMessage);
                    ((Activity) context).runOnUiThread(() -> {
                        webView.evaluateJavascript(
                            "handleUpdateError('" + errorMessage + "')",
                            null
                        );
                    });
                }
            });
        } else {
            ((Activity) context).runOnUiThread(() -> {
                webView.evaluateJavascript(
                    "handleUpdateError('User not logged in')",
                    null
                );
            });
        }
    }

    @JavascriptInterface
    public void addToPantry(String itemName) {
        SharedPreferences prefs = context.getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
        int userId = prefs.getInt("userId", -1);

        if (userId != -1) {
            supabaseHelper.addItemToPantry(userId, itemName, new SupabaseHelper.SupabaseCallback<Boolean>() {
                @Override
                public void onSuccess(Boolean result) {
                    ((Activity) context).runOnUiThread(() -> {
                        Toast.makeText(context, "Item added to pantry", Toast.LENGTH_SHORT).show();
                        webView.evaluateJavascript("loadPantryItems();", null);
                    });
                }

                @Override
                public void onError(String errorMessage) {
                    ((Activity) context).runOnUiThread(() -> {
                        Toast.makeText(context, "Failed to add item: " + errorMessage, Toast.LENGTH_SHORT).show();
                    });
                }
            });
        } else {
            ((Activity) context).runOnUiThread(() -> {
                Toast.makeText(context, "User not logged in", Toast.LENGTH_SHORT).show();
            });
        }
    }

    @JavascriptInterface
    public void getPantryItems() {
        SharedPreferences prefs = context.getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
        String email = prefs.getString("email", "");

        if (!email.isEmpty()) {
            // 首先獲取用戶 ID，然後獲取 Pantry 物品
            supabaseHelper.getUserId(email, new SupabaseHelper.SupabaseCallback<Integer>() {
                @Override
                public void onSuccess(Integer userId) {
                    if (userId != -1) {
                        supabaseHelper.getUserPantryItems(userId, new SupabaseHelper.SupabaseCallback<List<PantryItem>>() {
                            @Override
                            public void onSuccess(List<PantryItem> pantryItems) {
                                try {
                                    JSONArray itemsArray = new JSONArray();
                                    for (PantryItem item : pantryItems) {
                                        JSONObject itemObj = new JSONObject();
                                        itemObj.put("id", item.getId());
                                        itemObj.put("name", item.getName());
                                        itemObj.put("date", item.getDateAdded());
                                        itemsArray.put(itemObj);
                                    }

                                    final String itemsJson = itemsArray.toString();
                                    Log.d("WebAppInterface", "Pantry items: " + itemsJson);

                                    ((Activity) context).runOnUiThread(() -> {
                                        webView.evaluateJavascript(
                                                "displayPantryItems(" + itemsJson + ")",
                                                null
                                        );
                                    });
                                } catch (JSONException e) {
                                    e.printStackTrace();
                                    ((Activity) context).runOnUiThread(() -> {
                                        Toast.makeText(context, "Error parsing pantry items", Toast.LENGTH_SHORT).show();
                                    });
                                }
                            }

                            @Override
                            public void onError(String errorMessage) {
                                Log.e("WebAppInterface", "Failed to get pantry items: " + errorMessage);
                                ((Activity) context).runOnUiThread(() -> {
                                    Toast.makeText(context, "Failed to get pantry items: " + errorMessage, Toast.LENGTH_SHORT).show();
                                });
                            }
                        });
                    } else {
                        ((Activity) context).runOnUiThread(() -> {
                            Toast.makeText(context, "User not found", Toast.LENGTH_SHORT).show();
                        });
                    }
                }

                @Override
                public void onError(String errorMessage) {
                    ((Activity) context).runOnUiThread(() -> {
                        Toast.makeText(context, "Failed to get user ID: " + errorMessage, Toast.LENGTH_SHORT).show();
                    });
                }
            });
        } else {
            ((Activity) context).runOnUiThread(() -> {
                Toast.makeText(context, "User not logged in", Toast.LENGTH_SHORT).show();
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
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean("isLoggedIn", false);
        editor.apply();

        ((Activity)context).runOnUiThread(() -> {
            Toasty.success(context, "Logged out successfully", Toast.LENGTH_SHORT, true).show();
            ((MainActivity)context).getWebView().loadUrl("file:///android_asset/login.html");
        });
    }

    @JavascriptInterface
    public void deleteAccount() {
        // 從 SharedPreferences 獲取當前登錄用戶的郵箱
        SharedPreferences prefs = context.getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
        String currentUserEmail = prefs.getString("email", "");

        if (currentUserEmail.isEmpty()) {
            Toast.makeText(context, "Unable to delete account: User information not found", Toast.LENGTH_SHORT).show();
            return;
        }

        // 從數據庫刪除用戶
        supabaseHelper.deleteUser(currentUserEmail, new SupabaseHelper.SupabaseCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean result) {
                if (result) {
                    // 清除 SharedPreferences 中的用戶信息
                    prefs.edit().clear().apply();

                    // 返回登錄頁面
                    ((Activity)context).runOnUiThread(() -> {
                        Toast.makeText(context, "Account deleted successfully", Toast.LENGTH_SHORT).show();
                        ((MainActivity)context).getWebView().loadUrl("file:///android_asset/login.html");
                    });
                } else {
                    // 顯示錯誤消息
                    Toast.makeText(context, "Deleting account failed, please try again later", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onError(String errorMessage) {
                ((Activity) context).runOnUiThread(() -> {
                    Toast.makeText(context, "Failed to delete account: " + errorMessage, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    @JavascriptInterface
    public void getPantryItemsForRecipe() {
        SharedPreferences prefs = context.getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
        String email = prefs.getString("email", "");

        if (!email.isEmpty()) {
            // 首先獲取用戶 ID，然後獲取 Pantry 物品
            supabaseHelper.getUserId(email, new SupabaseHelper.SupabaseCallback<Integer>() {
                @Override
                public void onSuccess(Integer userId) {
                    if (userId != -1) {
                        supabaseHelper.getUserPantryItems(userId, new SupabaseHelper.SupabaseCallback<List<PantryItem>>() {
                            @Override
                            public void onSuccess(List<PantryItem> pantryItems) {
                                ((Activity) context).runOnUiThread(() -> {
                                    JSONArray jsonArray = new JSONArray();
                                    for (PantryItem item : pantryItems) {
                                        try {
                                            JSONObject jsonItem = new JSONObject();
                                            jsonItem.put("id", item.getId());
                                            jsonItem.put("name", item.getName());
                                            jsonArray.put(jsonItem);
                                        } catch (JSONException e) {
                                            e.printStackTrace();
                                        }
                                    }
                                    final String itemsJson = jsonArray.toString();
                                    webView.evaluateJavascript("updatePantryItemsForRecipe('" + itemsJson + "')", null);
                                });
                            }

                            @Override
                            public void onError(String errorMessage) {
                                ((Activity) context).runOnUiThread(() -> {
                                    Toast.makeText(context, "Failed to get pantry items for recipe: " + errorMessage, Toast.LENGTH_SHORT).show();
                                });
                            }
                        });
                    } else {
                        ((Activity) context).runOnUiThread(() -> {
                            Toast.makeText(context, "User not found", Toast.LENGTH_SHORT).show();
                        });
                    }
                }

                @Override
                public void onError(String errorMessage) {
                    ((Activity) context).runOnUiThread(() -> {
                        Toast.makeText(context, "Failed to get user ID: " + errorMessage, Toast.LENGTH_SHORT).show();
                    });
                }
            });
        } else {
            ((Activity) context).runOnUiThread(() -> {
                Toast.makeText(context, "User not logged in", Toast.LENGTH_SHORT).show();
            });
        }
    }

    @JavascriptInterface
    public void generateRecipe(String recipeDataJson) {
        Log.d("RecipeGenerator", "generateRecipe method called with data: " + recipeDataJson);
        generatedRecipe = "";
        currentImageUrl = "";


        new Thread(() -> {
            try {
                // 解析食譜數據
                JSONObject recipeData = new JSONObject(recipeDataJson);
                JSONArray typesArray = recipeData.getJSONArray("types");
                JSONArray ingredientsArray = recipeData.getJSONArray("ingredients");

                // 構建類型字符串
                StringBuilder typesBuilder = new StringBuilder();
                for (int i = 0; i < typesArray.length(); i++) {
                    if (i > 0) typesBuilder.append(", ");
                    typesBuilder.append(typesArray.getString(i));
                }

                // 構建食材字符串
                StringBuilder ingredientsBuilder = new StringBuilder();
                for (int i = 0; i < ingredientsArray.length(); i++) {
                    if (i > 0) ingredientsBuilder.append(", ");
                    ingredientsBuilder.append(ingredientsArray.getString(i));
                }

                // 構建 Gemini API 請求
                OkHttpClient client = new OkHttpClient.Builder()
                        .connectTimeout(60, TimeUnit.SECONDS)
                        .writeTimeout(60, TimeUnit.SECONDS)
                        .readTimeout(60, TimeUnit.SECONDS)
                        .build();

                // 構建提示
                String prompt = "Create a detailed recipe using these ingredients: " +
                        ingredientsBuilder.toString() + ". The recipe should be " +
                        typesBuilder.toString() + ". Include a title, ingredients list with measurements, " +
                        "step-by-step instructions, cooking time, and nutritional information if possible. " +
                        "Format the response in HTML with appropriate tags for a mobile app display. Please strictly follow the format do not add any additional information and images.";

                Log.d("RecipeGenerator", "Prompt: " + prompt);

                // 構建 Gemini API 請求體
                JSONObject requestJson = new JSONObject();

                JSONArray contentsArray = new JSONArray();
                JSONObject contentObject = new JSONObject();

                JSONArray partsArray = new JSONArray();
                JSONObject textPart = new JSONObject();
                textPart.put("text", prompt);
                partsArray.put(textPart);

                contentObject.put("parts", partsArray);
                contentsArray.put(contentObject);

                requestJson.put("contents", contentsArray);

                JSONObject generationConfig = new JSONObject();
                generationConfig.put("temperature", 0.7);
                generationConfig.put("topP", 0.95);
                generationConfig.put("topK", 40);
                requestJson.put("generationConfig", generationConfig);

                String requestBodyString = requestJson.toString();
                Log.d("RecipeGenerator", "Request body: " + requestBodyString);

                RequestBody body = RequestBody.create(requestBodyString, JSON);

                // 構建 URL 並添加 API 密鑰作為查詢參數
                String url = "https://generativelanguage.googleapis.com/v1/models/gemini-1.5-flash:generateContent?key=" + GEMINI_API_KEY;

                Request request = new Request.Builder()
                        .url(url)
                        .addHeader("Content-Type", "application/json")
                        .post(body)
                        .build();

                Log.d("RecipeGenerator", "Created request with headers: " + request.headers());

                // 發送請求並處理響應
                Log.d("RecipeGenerator", "Sending request to Gemini API...");
                Response response = client.newCall(request).execute();

                // 獲取響應體
                String responseBody = response.body().string();
                Log.d("RecipeGenerator", "Response code: " + response.code());
                Log.d("RecipeGenerator", "Response body (first 500 chars): " +
                        (responseBody.length() > 500 ? responseBody.substring(0, 500) + "..." : responseBody));

                // 檢查響應是否成功
                if (!response.isSuccessful()) {
                    Log.e("RecipeGenerator", "Unsuccessful response: " + response.code());
                    throw new IOException("Unexpected response code: " + response.code() + "\nBody: " + responseBody);
                }

                // 解析 Gemini 響應
                Log.d("RecipeGenerator", "Parsing JSON response");
                JSONObject responseJson = new JSONObject(responseBody);

                if (!responseJson.has("candidates")) {
                    Log.e("RecipeGenerator", "No 'candidates' field in response");
                    throw new JSONException("API response does not contain 'candidates' field");
                }

                JSONArray candidatesArray = responseJson.getJSONArray("candidates");

                if (candidatesArray.length() == 0) {
                    Log.e("RecipeGenerator", "Empty 'candidates' array in response");
                    throw new JSONException("API response contains empty 'candidates' array");
                }

                JSONObject candidate = candidatesArray.getJSONObject(0);
                JSONObject content = candidate.getJSONObject("content");
                JSONArray parts = content.getJSONArray("parts");
                JSONObject part = parts.getJSONObject(0);
                String recipeContent = part.getString("text");

                // 去除所有 Markdown 代碼塊標記
                recipeContent = cleanMarkdownFormatting(recipeContent);

                Log.d("RecipeGenerator", "Cleaned recipe content: " + recipeContent);

                // 保存生成的食譜
                generatedRecipe = recipeContent;

                // 創建一個 final 變量來存儲 recipeContent，以便在 lambda 表達式中使用
                final String finalRecipeContent = recipeContent;

                // 通知 JavaScript 食譜已生成
                Log.d("RecipeGenerator", "Updating UI with generated recipe");
                ((Activity) context).runOnUiThread(() -> {
                    webView.loadUrl("file:///android_asset/recipe_result.html?saved=false");

                    // 在頁面加載完成後，確保清除任何保存的食譜ID
                    webView.setWebViewClient(new WebViewClient() {
                        @Override
                        public void onPageFinished(WebView view, String url) {
                            super.onPageFinished(view, url);

                            // 確保清除任何保存的食譜ID
                            webView.evaluateJavascript(
                                "document.body.removeAttribute('data-saved-recipe-id');",
                                null
                            );
                        }
                    });
                });

                Log.d("RecipeGenerator", "Recipe generation completed successfully");

            } catch (java.net.SocketTimeoutException e) {
                Log.e("RecipeGenerator", "Request timed out", e);
                ((Activity) context).runOnUiThread(() -> {
                    Toast.makeText(context, "Request timed out. Please try again.", Toast.LENGTH_LONG).show();
                    webView.evaluateJavascript(
                            "document.getElementById('loading-recipe').style.display = 'none'; " +
                                    "document.getElementById('generate-btn').disabled = false;",
                            null
                    );
                });
            } catch (Exception e) {
                e.printStackTrace();
                Log.e("RecipeGenerator", "Error generating recipe", e);

                // 通知用戶生成失敗
                ((Activity) context).runOnUiThread(() -> {
                    Toast.makeText(context, "Failed to generate recipe: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    webView.evaluateJavascript(
                            "document.getElementById('loading-recipe').style.display = 'none'; " +
                                    "document.getElementById('generate-btn').disabled = false;",
                            value -> Log.d("RecipeGenerator", "JavaScript error callback result: " + value)
                    );
                });
            }
        }).start();
    }

    // 輔助方法：轉義 JavaScript 字符串
    private String escapeJsString(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("<script>", "")
                .replace("</script>", "");
    }

    @JavascriptInterface
    public void getGeneratedRecipe() {
        Log.d("RecipeDebug", "getGeneratedRecipe called, currentImageUrl: " + (currentImageUrl != null ? currentImageUrl : "null"));

        if (generatedRecipe != null && !generatedRecipe.isEmpty()) {
            ((Activity) context).runOnUiThread(() -> {
                webView.evaluateJavascript(
                    "displayRecipe(" + JSONObject.quote(generatedRecipe) + ")",
                    null
                );

                // 檢查是否已經有圖片URL（從已保存的食譜加載）
                if (currentImageUrl != null && !currentImageUrl.isEmpty()) {
                    Log.d("RecipeDebug", "Using existing image URL: " + currentImageUrl);
                    webView.evaluateJavascript(
                        "displayRecipeImage(" + JSONObject.quote(currentImageUrl) + ")",
                        null
                    );
                } else {
                    // 只有在沒有現有圖片的情況下才生成新圖片
                    Log.d("RecipeDebug", "No existing image URL, generating new image");
                    // 從食譜內容中提取標題
                    Pattern pattern = Pattern.compile("<h1[^>]*>(.*?)</h1>");
                    Matcher matcher = pattern.matcher(generatedRecipe);
                    if (matcher.find()) {
                        String recipeName = matcher.group(1);
                        Log.d("RecipeDebug", "Extracted recipe name: " + recipeName);
                        generateRecipeImage(recipeName);
                    } else {
                        Log.d("RecipeDebug", "Could not extract recipe name from content");
                    }
                }
            });
        } else {
            ((Activity) context).runOnUiThread(() -> {
                Toast.makeText(context, "No recipe generated yet", Toast.LENGTH_SHORT).show();
            });
        }
    }

    // 從 HTML 內容中提取食譜標題
    private String extractRecipeTitle(String htmlContent) {
        try {
            // 嘗試從 h1 標籤中提取標題
            Pattern pattern = Pattern.compile("<h1>(.*?)</h1>");
            Matcher matcher = pattern.matcher(htmlContent);
            if (matcher.find()) {
                return matcher.group(1);
            }

            // 如果沒有 h1 標籤，嘗試從 title 標籤中提取
            pattern = Pattern.compile("<title>(.*?)</title>");
            matcher = pattern.matcher(htmlContent);
            if (matcher.find()) {
                return matcher.group(1);
            }

            // 如果都沒有找到，返回默認標題
            return "Delicious Recipe";
        } catch (Exception e) {
            Log.e("RecipeGenerator", "Error extracting recipe title", e);
            return "Delicious Recipe";
        }
    }

    @JavascriptInterface
    public void saveRecipe(String title, String content, String imageUrl) {
        SharedPreferences prefs = context.getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
        String email = prefs.getString("email", "");

        if (!email.isEmpty()) {
            supabaseHelper.getUserId(email, new SupabaseHelper.SupabaseCallback<Integer>() {
                @Override
                public void onSuccess(Integer userId) {
                    if (userId != -1) {
                        supabaseHelper.saveRecipe(userId, title, content, imageUrl, new SupabaseHelper.SupabaseCallback<Boolean>() {
                            @Override
                            public void onSuccess(Boolean result) {
                                ((Activity) context).runOnUiThread(() -> {
                                    Toast.makeText(context, "Recipe saved", Toast.LENGTH_SHORT).show();
                                    webView.loadUrl("file:///android_asset/home.html");
                                });
                            }

                            @Override
                            public void onError(String errorMessage) {
                                ((Activity) context).runOnUiThread(() -> {
                                    Toast.makeText(context, "Failed to save recipe: " + errorMessage, Toast.LENGTH_SHORT).show();
                                });
                            }
                        });
                    } else {
                        ((Activity) context).runOnUiThread(() -> {
                            Toast.makeText(context, "User does not exist", Toast.LENGTH_SHORT).show();
                        });
                    }
                }

                @Override
                public void onError(String errorMessage) {
                    ((Activity) context).runOnUiThread(() -> {
                        Toast.makeText(context, "Failed to obtain user ID:" + errorMessage, Toast.LENGTH_SHORT).show();
                    });
                }
            });
        } else {
            ((Activity) context).runOnUiThread(() -> {
                Toast.makeText(context, "User not logged in", Toast.LENGTH_SHORT).show();
            });
        }
    }

    /**
     * 分享食譜
     * @param title 食譜標題
     * @param content 食譜內容
     */
    @JavascriptInterface
    public void shareRecipe(String title, String content) {
        Log.d("RecipeDebug", "shareRecipe called with title: " + title);

        // 創建分享意圖
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");

        // 設置分享內容
        String shareText = title + "\n\n";

        // 從HTML內容中提取純文本
        String plainText = Html.fromHtml(content, Html.FROM_HTML_MODE_COMPACT).toString();
        shareText += plainText;

        // 添加分享信息
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, title);
        shareIntent.putExtra(Intent.EXTRA_TEXT, shareText);

        // 啟動分享對話框
        ((Activity) context).startActivity(Intent.createChooser(shareIntent, "Share Recipe"));
    }

    @JavascriptInterface
    public void removeFromPantry(int itemId) {
        supabaseHelper.deleteItemFromPantry(itemId, new SupabaseHelper.SupabaseCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean result) {
                ((Activity) context).runOnUiThread(() -> {
                    Toast.makeText(context, "Item removed from pantry", Toast.LENGTH_SHORT).show();
                    webView.evaluateJavascript("loadPantryItems();", null);
                });
            }

            @Override
            public void onError(String errorMessage) {
                ((Activity) context).runOnUiThread(() -> {
                    Toast.makeText(context, "Failed to remove item: " + errorMessage, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    @JavascriptInterface
    public void viewPantry() {
        ((Activity) context).runOnUiThread(() -> {
            webView.loadUrl("file:///android_asset/pantry.html");
        });
    }

    @JavascriptInterface
    public void createRecipe() {
        ((Activity) context).runOnUiThread(() -> {
            webView.loadUrl("file:///android_asset/recipe_creator.html");
        });
    }

    @JavascriptInterface
    public void addPantryItem(String itemName) {
        SharedPreferences prefs = context.getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
        String email = prefs.getString("email", "");

        if (!email.isEmpty()) {
            supabaseHelper.getUserId(email, new SupabaseHelper.SupabaseCallback<Integer>() {
                @Override
                public void onSuccess(Integer userId) {
                    if (userId != -1) {
                        supabaseHelper.addItemToPantry(userId, itemName, new SupabaseHelper.SupabaseCallback<Boolean>() {
                            @Override
                            public void onSuccess(Boolean result) {
                                ((Activity) context).runOnUiThread(() -> {
                                    Toast.makeText(context, "Item added to Pantry", Toast.LENGTH_SHORT).show();
                                    // 刷新 Pantry 列表
                                    webView.evaluateJavascript("loadPantryItems();", null);
                                });
                            }

                            @Override
                            public void onError(String errorMessage) {
                                ((Activity) context).runOnUiThread(() -> {
                                    Toast.makeText(context, "Adding item failed:" + errorMessage, Toast.LENGTH_SHORT).show();
                                });
                            }
                        });
                    } else {
                        ((Activity) context).runOnUiThread(() -> {
                            Toast.makeText(context, "User does not exist", Toast.LENGTH_SHORT).show();
                        });
                    }
                }

                @Override
                public void onError(String errorMessage) {
                    ((Activity) context).runOnUiThread(() -> {
                        Toast.makeText(context, "Failed to obtain user ID:" + errorMessage, Toast.LENGTH_SHORT).show();
                    });
                }
            });
        } else {
            ((Activity) context).runOnUiThread(() -> {
                Toast.makeText(context, "User not logged in", Toast.LENGTH_SHORT).show();
            });
        }
    }

    // 輔助方法：清理 Markdown 格式
    private String cleanMarkdownFormatting(String markdown) {
        if (markdown == null) return "";

        // 移除所有代碼塊標記（包括語言標識符）
        String cleaned = markdown.replaceAll("```[a-zA-Z]*\\n", "")
                                .replaceAll("```", "");

        // 處理其他可能的 Markdown 格式（如果需要）
        // 例如，可以保留標題、列表等格式，或者將它們轉換為 HTML

        // 移除多餘的空行（如果需要）
        cleaned = cleaned.replaceAll("\\n{3,}", "\n\n");

        return cleaned;
    }

    @JavascriptInterface
    public void generateRecipeImage(String recipeName) {
        Log.d("ImageGenerator", "Generating image for recipe: " + recipeName);

        // 創建一個不會觸發內容政策的提示詞
        // String prompt = "Create a simple square illustration of " + recipeName + ". The image should be colorful, appetizing, and visually appealing. Make it look like a hand-drawn food illustration. Please make the image square with dimensions 300x300 pixels.";
        String prompt = "Create a realistic, photographic image of a complete dish of " + recipeName + ". The image should be square with dimensions 100x100 pixels. Show the entire dish with natural lighting and realistic textures. No cartoon style, make it look like a professional food photograph.";
        try {
            // 創建 Gemini API 請求
            JSONObject requestBody = new JSONObject();
            requestBody.put("model", "gemini-2.0-flash-exp-image-generation");  // 使用實驗性圖像生成模型

            // 創建 contents 數組
            JSONArray contentsArray = new JSONArray();
            JSONObject userContent = new JSONObject();
            userContent.put("role", "user");

            JSONArray partsArray = new JSONArray();
            JSONObject textPart = new JSONObject();
            textPart.put("text", prompt);
            partsArray.put(textPart);

            userContent.put("parts", partsArray);
            contentsArray.put(userContent);

            requestBody.put("contents", contentsArray);

            // 設置生成配置，包括響應模態
            JSONObject generationConfig = new JSONObject();
            generationConfig.put("temperature", 0.7);
            generationConfig.put("topP", 1.0);
            generationConfig.put("topK", 32);
            generationConfig.put("maxOutputTokens", 2048);

            // 重要：添加 responseModalities 參數
            JSONArray responseModalities = new JSONArray();
            responseModalities.put("Text");
            responseModalities.put("Image");
            generationConfig.put("responseModalities", responseModalities);

            requestBody.put("generationConfig", generationConfig);

            // 輸出完整的請求體以進行調試
            String requestBodyString = requestBody.toString();
            Log.d("ImageGenerator", "Request body: " + requestBodyString);

            // 創建 OkHttp 請求
            Request request = new Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash-exp-image-generation:generateContent?key=" + GEMINI_API_KEY)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(requestBodyString, MediaType.parse("application/json")))
                .build();

            // 發送請求
            new Thread(() -> {
                try {
                    OkHttpClient client = new OkHttpClient.Builder()
                        .connectTimeout(60, TimeUnit.SECONDS)
                        .writeTimeout(60, TimeUnit.SECONDS)
                        .readTimeout(60, TimeUnit.SECONDS)
                        .build();

                    Log.d("ImageGenerator", "Sending request to Gemini API...");
                    Response response = client.newCall(request).execute();
                    String responseBody = response.body().string();

                    Log.d("ImageGenerator", "Response code: " + response.code());
                    Log.d("ImageGenerator", "Response body: " + responseBody);

                    if (response.isSuccessful()) {
                        JSONObject jsonResponse = new JSONObject(responseBody);

                        // 檢查是否有候選項
                        if (!jsonResponse.has("candidates")) {
                            Log.e("ImageGenerator", "No 'candidates' field in response");
                            ((Activity) context).runOnUiThread(() -> {
                                Toast.makeText(context, "API response format error: missing candidates", Toast.LENGTH_SHORT).show();
                            });
                            return;
                        }

                        // 解析響應
                        JSONArray candidates = jsonResponse.getJSONArray("candidates");
                        if (candidates.length() > 0) {
                            JSONObject candidate = candidates.getJSONObject(0);

                            // 檢查是否有 content
                            if (!candidate.has("content")) {
                                Log.e("ImageGenerator", "No 'content' field in candidate");
                                ((Activity) context).runOnUiThread(() -> {
                                    Toast.makeText(context, "API response format error: missing content", Toast.LENGTH_SHORT).show();
                                });
                                return;
                            }

                            JSONObject content = candidate.getJSONObject("content");

                            // 檢查是否有 parts
                            if (!content.has("parts")) {
                                Log.e("ImageGenerator", "No 'parts' field in content");
                                ((Activity) context).runOnUiThread(() -> {
                                    Toast.makeText(context, "API response format error: missing parts", Toast.LENGTH_SHORT).show();
                                });
                                return;
                            }

                            JSONArray parts = content.getJSONArray("parts");
                            Log.d("ImageGenerator", "Found " + parts.length() + " parts in response");

                            boolean imageFound = false;
                            for (int i = 0; i < parts.length(); i++) {
                                JSONObject part = parts.getJSONObject(i);
                                Log.d("ImageGenerator", "Examining part " + i + ": " + part.toString());

                                if (part.has("inlineData")) {
                                    JSONObject inlineData = part.getJSONObject("inlineData");
                                    String imageData = inlineData.getString("data"); // Base64 encoded image
                                    String mimeType = inlineData.getString("mimeType");

                                    Log.d("ImageGenerator", "Found image data with MIME type: " + mimeType);
                                    Log.d("ImageGenerator", "Base64 data length: " + imageData.length());

                                    // 創建完整的 data URL
                                    final String dataUrl = "data:" + mimeType + ";base64," + imageData;

                                    // 通知 JavaScript 圖片已生成
                                    ((Activity) context).runOnUiThread(() -> {
                                        // 使用 JavaScript 轉義處理 data URL
                                        String escapedUrl = dataUrl.replace("\\", "\\\\")
                                                                  .replace("'", "\\'")
                                                                  .replace("\n", "\\n")
                                                                  .replace("\r", "\\r");

                                        String jsCode = "displayRecipeImage('" + escapedUrl + "')";
                                        Log.d("ImageGenerator", "Executing JavaScript to display image");
                                        webView.evaluateJavascript(jsCode, value -> {
                                            Log.d("ImageGenerator", "JavaScript execution result: " + value);
                                        });
                                    });

                                    imageFound = true;
                                    break;
                                } else {
                                    Log.d("ImageGenerator", "Part " + i + " does not contain inlineData");
                                    // 檢查是否有文本內容
                                    if (part.has("text")) {
                                        Log.d("ImageGenerator", "Part " + i + " contains text: " + part.getString("text"));
                                    }
                                }
                            }

                            if (!imageFound) {
                                Log.e("ImageGenerator", "No image data found in response parts");
                                ((Activity) context).runOnUiThread(() -> {
                                    Toast.makeText(context, "The API did not return image data, so the prompt word may need to be adjusted", Toast.LENGTH_LONG).show();
                                });
                            }
                        } else {
                            Log.e("ImageGenerator", "No candidates returned");
                            ((Activity) context).runOnUiThread(() -> {
                                Toast.makeText(context, "The API returned no candidates", Toast.LENGTH_SHORT).show();
                            });
                        }
                    } else {
                        Log.e("ImageGenerator", "Image generation failed with code: " + response.code());
                        Log.e("ImageGenerator", "Response body: " + responseBody);

                        // 嘗試解析錯誤消息
                        String errorMessage = "Unknown error";
                        try {
                            JSONObject errorJson = new JSONObject(responseBody);
                            if (errorJson.has("error")) {
                                JSONObject error = errorJson.getJSONObject("error");
                                if (error.has("message")) {
                                    errorMessage = error.getString("message");
                                }
                            }
                        } catch (Exception e) {
                            Log.e("ImageGenerator", "Error parsing error response", e);
                        }

                        final String finalErrorMessage = errorMessage;
                        ((Activity) context).runOnUiThread(() -> {
                            Toast.makeText(context, "Image generation failed:" + finalErrorMessage, Toast.LENGTH_LONG).show();
                        });
                    }
                } catch (Exception e) {
                    Log.e("ImageGenerator", "Error generating image", e);
                    ((Activity) context).runOnUiThread(() -> {
                        Toast.makeText(context, "Image generation failed:" + e.getMessage(), Toast.LENGTH_LONG).show();
                    });
                }
            }).start();
        } catch (Exception e) {
            Log.e("ImageGenerator", "Error creating JSON request", e);
            Toast.makeText(context, "Error creating request:" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @JavascriptInterface
    public void loadSavedRecipes() {
        SharedPreferences prefs = context.getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
        String email = prefs.getString("email", "");

        if (!email.isEmpty()) {
            supabaseHelper.getUserId(email, new SupabaseHelper.SupabaseCallback<Integer>() {
                @Override
                public void onSuccess(Integer userId) {
                    if (userId != -1) {
                        supabaseHelper.getUserRecipes(userId, new SupabaseHelper.SupabaseCallback<List<Recipe>>() {
                            @Override
                            public void onSuccess(List<Recipe> recipes) {
                                try {
                                    // 將食譜列表轉換為 JSON
                                    JSONArray recipesArray = new JSONArray();
                                    for (Recipe recipe : recipes) {
                                        JSONObject recipeObj = new JSONObject();
                                        recipeObj.put("id", recipe.getId());
                                        recipeObj.put("title", recipe.getTitle());
                                        recipeObj.put("imageUrl", recipe.getImageUrl());
                                        recipesArray.put(recipeObj);
                                    }

                                    final String recipesJson = recipesArray.toString();

                                    ((Activity) context).runOnUiThread(() -> {
                                        webView.evaluateJavascript(
                                            "displaySavedRecipes(" + JSONObject.quote(recipesJson) + ")",
                                            null
                                        );
                                    });
                                } catch (JSONException e) {
                                    Log.e("RecipeLoader", "Error creating recipes JSON", e);
                                    ((Activity) context).runOnUiThread(() -> {
                                        Toast.makeText(context, "Failed to load recipe:" + e.getMessage(), Toast.LENGTH_SHORT).show();
                                    });
                                }
                            }

                            @Override
                            public void onError(String errorMessage) {
                                Log.e("RecipeLoader", "Error loading recipes: " + errorMessage);
                                ((Activity) context).runOnUiThread(() -> {
                                    Toast.makeText(context, "Failed to load recipe:" + errorMessage, Toast.LENGTH_SHORT).show();
                                });
                            }
                        });
                    } else {
                        ((Activity) context).runOnUiThread(() -> {
                            Toast.makeText(context, "User does not exist", Toast.LENGTH_SHORT).show();
                        });
                    }
                }

                @Override
                public void onError(String errorMessage) {
                    Log.e("RecipeLoader", "Error getting user ID: " + errorMessage);
                    ((Activity) context).runOnUiThread(() -> {
                        Toast.makeText(context, "Failed to obtain user ID:" + errorMessage, Toast.LENGTH_SHORT).show();
                    });
                }
            });
        } else {
            ((Activity) context).runOnUiThread(() -> {
                Toast.makeText(context, "Please log in first", Toast.LENGTH_SHORT).show();
            });
        }
    }

    @JavascriptInterface
    public void loadRecipeDetails(int recipeId) {
        Log.d("RecipeDebug", "loadRecipeDetails called for recipe ID: " + recipeId);

        // 清除之前的食譜內容和圖片URL
        generatedRecipe = "";
        currentImageUrl = "";

        supabaseHelper.getRecipeById(recipeId, new SupabaseHelper.SupabaseCallback<Recipe>() {
            @Override
            public void onSuccess(Recipe recipe) {
                // 保存當前食譜內容和圖片URL
                generatedRecipe = recipe.getContent();
                currentImageUrl = recipe.getImageUrl();

                ((Activity) context).runOnUiThread(() -> {
                    // 導航到食譜結果頁面，添加saved=true參數
                    webView.loadUrl("file:///android_asset/recipe_result.html?saved=true");

                    // 在頁面加載完成後，顯示食譜內容和圖片
                    webView.setWebViewClient(new WebViewClient() {
                        @Override
                        public void onPageFinished(WebView view, String url) {
                            super.onPageFinished(view, url);

                            // 標記這是一個已保存的食譜
                            webView.evaluateJavascript(
                                "markAsSavedRecipe(" + recipe.getId() + ")",
                                null
                            );

                            // 顯示食譜內容
                            webView.evaluateJavascript(
                                "displayRecipe(" + JSONObject.quote(generatedRecipe) + ")",
                                null
                            );

                            // 如果有圖片，顯示圖片
                            if (currentImageUrl != null && !currentImageUrl.isEmpty()) {
                                webView.evaluateJavascript(
                                    "displayRecipeImage(" + JSONObject.quote(currentImageUrl) + ")",
                                    null
                                );
                            }
                        }
                    });
                });
            }

            @Override
            public void onError(String errorMessage) {
                Log.e("RecipeLoader", "Error loading recipe details: " + errorMessage);
                ((Activity) context).runOnUiThread(() -> {
                    Toast.makeText(context, "Failed to load recipe details:" + errorMessage, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    @JavascriptInterface
    public void deleteRecipe(int recipeId) {
        Log.d("RecipeDebug", "deleteRecipe called for recipe ID: " + recipeId);

        // 檢查URL參數，確保只有已保存的食譜才能被刪除
        ((Activity) context).runOnUiThread(() -> {
            webView.evaluateJavascript(
                "(function() { " +
                    "const urlParams = new URLSearchParams(window.location.search); " +
                    "return urlParams.get('saved') === 'true'; " +
                "})();",
                value -> {
                    boolean isSavedRecipe = Boolean.parseBoolean(value);
                    if (!isSavedRecipe) {
                        Log.d("RecipeDebug", "Attempted to delete a non-saved recipe, ignoring");
                        ((Activity) context).runOnUiThread(() -> {
                            Toast.makeText(context, "Cannot delete a recipe that hasn't been saved", Toast.LENGTH_SHORT).show();
                        });
                        return;
                    }

                    // 繼續刪除已保存的食譜
                    proceedWithRecipeDeletion(recipeId);
                }
            );
        });
    }

    // 將原來的刪除邏輯移到這個方法中
    private void proceedWithRecipeDeletion(int recipeId) {
        SharedPreferences prefs = context.getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
        String email = prefs.getString("email", "");

        if (!email.isEmpty()) {
            supabaseHelper.getUserId(email, new SupabaseHelper.SupabaseCallback<Integer>() {
                @Override
                public void onSuccess(Integer userId) {
                    if (userId != -1) {
                        supabaseHelper.deleteRecipe(recipeId, userId, new SupabaseHelper.SupabaseCallback<Boolean>() {
                            @Override
                            public void onSuccess(Boolean result) {
                                ((Activity) context).runOnUiThread(() -> {
                                    if (result) {
                                        Toast.makeText(context, "Recipe deleted successfully", Toast.LENGTH_SHORT).show();
                                        // 返回主頁
                                        webView.loadUrl("file:///android_asset/home.html");
                                    } else {
                                        Toast.makeText(context, "Failed to delete recipe", Toast.LENGTH_SHORT).show();
                                    }
                                });
                            }

                            @Override
                            public void onError(String errorMessage) {
                                Log.e("RecipeDebug", "Error deleting recipe: " + errorMessage);
                                ((Activity) context).runOnUiThread(() -> {
                                    Toast.makeText(context, "Error deleting recipe: " + errorMessage, Toast.LENGTH_SHORT).show();
                                });
                            }
                        });
                    } else {
                        ((Activity) context).runOnUiThread(() -> {
                            Toast.makeText(context, "User not found", Toast.LENGTH_SHORT).show();
                        });
                    }
                }

                @Override
                public void onError(String errorMessage) {
                    Log.e("RecipeDebug", "Error getting user ID: " + errorMessage);
                    ((Activity) context).runOnUiThread(() -> {
                        Toast.makeText(context, "Error getting user ID: " + errorMessage, Toast.LENGTH_SHORT).show();
                    });
                }
            });
        } else {
            ((Activity) context).runOnUiThread(() -> {
                Toast.makeText(context, "User not logged in", Toast.LENGTH_SHORT).show();
            });
        }
    }

    /**
     * 更新用戶郵箱
     */
    @JavascriptInterface
    public void updateUserEmail(String newEmail) {
        Log.d("ProfileDebug", "updateUserEmail called with: " + newEmail);

        SharedPreferences prefs = context.getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
        String currentEmail = prefs.getString("email", "");

        if (!currentEmail.isEmpty()) {
            supabaseHelper.getUserId(currentEmail, new SupabaseHelper.SupabaseCallback<Integer>() {
                @Override
                public void onSuccess(Integer userId) {
                    if (userId != -1) {
                        supabaseHelper.updateUserEmail(userId, newEmail, new SupabaseHelper.SupabaseCallback<Boolean>() {
                            @Override
                            public void onSuccess(Boolean result) {
                                ((Activity) context).runOnUiThread(() -> {
                                    if (result) {
                                        // 更新本地存儲的郵箱
                                        SharedPreferences.Editor editor = prefs.edit();
                                        editor.putString("email", newEmail);
                                        editor.apply();

                                        // 通知前端更新成功
                                        webView.evaluateJavascript(
                                            "handleUpdateSuccess('Email updated successfully')",
                                            null
                                        );
                                    } else {
                                        webView.evaluateJavascript(
                                            "handleUpdateError('Failed to update email')",
                                            null
                                        );
                                    }
                                });
                            }

                            @Override
                            public void onError(String errorMessage) {
                                Log.e("ProfileDebug", "Error updating email: " + errorMessage);
                                ((Activity) context).runOnUiThread(() -> {
                                    webView.evaluateJavascript(
                                        "handleUpdateError('" + errorMessage + "')",
                                        null
                                    );
                                });
                            }
                        });
                    } else {
                        ((Activity) context).runOnUiThread(() -> {
                            webView.evaluateJavascript(
                                "handleUpdateError('User not found')",
                                null
                            );
                        });
                    }
                }

                @Override
                public void onError(String errorMessage) {
                    Log.e("ProfileDebug", "Error getting user ID: " + errorMessage);
                    ((Activity) context).runOnUiThread(() -> {
                        webView.evaluateJavascript(
                            "handleUpdateError('" + errorMessage + "')",
                            null
                        );
                    });
                }
            });
        } else {
            ((Activity) context).runOnUiThread(() -> {
                webView.evaluateJavascript(
                    "handleUpdateError('User not logged in')",
                    null
                );
            });
        }
    }

    /**
     * 更新用戶密碼
     */
    @JavascriptInterface
    public void updateUserPassword(String currentPassword, String newPassword) {
        Log.d("ProfileDebug", "updateUserPassword called");

        SharedPreferences prefs = context.getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
        String email = prefs.getString("email", "");

        if (!email.isEmpty()) {
            // 首先驗證當前密碼
            supabaseHelper.signIn(email, currentPassword, new SupabaseHelper.SupabaseCallback<Boolean>() {
                @Override
                public void onSuccess(Boolean result) {
                    if (result) {
                        // 當前密碼正確，更新密碼
                        supabaseHelper.getUserId(email, new SupabaseHelper.SupabaseCallback<Integer>() {
                            @Override
                            public void onSuccess(Integer userId) {
                                if (userId != -1) {
                                    supabaseHelper.updateUserPassword(userId, newPassword, new SupabaseHelper.SupabaseCallback<Boolean>() {
                                        @Override
                                        public void onSuccess(Boolean updateResult) {
                                            ((Activity) context).runOnUiThread(() -> {
                                                if (updateResult) {
                                                    // 通知前端更新成功
                                                    webView.evaluateJavascript(
                                                        "handleUpdateSuccess('Password updated successfully')",
                                                        null
                                                    );
                                                } else {
                                                    webView.evaluateJavascript(
                                                        "handleUpdateError('Failed to update password')",
                                                        null
                                                    );
                                                }
                                            });
                                        }

                                        @Override
                                        public void onError(String errorMessage) {
                                            Log.e("ProfileDebug", "Error updating password: " + errorMessage);
                                            ((Activity) context).runOnUiThread(() -> {
                                                webView.evaluateJavascript(
                                                    "handleUpdateError('" + errorMessage + "')",
                                                    null
                                                );
                                            });
                                        }
                                    });
                                } else {
                                    ((Activity) context).runOnUiThread(() -> {
                                        webView.evaluateJavascript(
                                            "handleUpdateError('User not found')",
                                            null
                                        );
                                    });
                                }
                            }

                            @Override
                            public void onError(String errorMessage) {
                                Log.e("ProfileDebug", "Error getting user ID: " + errorMessage);
                                ((Activity) context).runOnUiThread(() -> {
                                    webView.evaluateJavascript(
                                        "handleUpdateError('" + errorMessage + "')",
                                        null
                                    );
                                });
                            }
                        });
                    } else {
                        ((Activity) context).runOnUiThread(() -> {
                            webView.evaluateJavascript(
                                "handleUpdateError('Current password is incorrect')",
                                null
                            );
                        });
                    }
                }

                @Override
                public void onError(String errorMessage) {
                    Log.e("ProfileDebug", "Error verifying current password: " + errorMessage);
                    ((Activity) context).runOnUiThread(() -> {
                        webView.evaluateJavascript(
                            "handleUpdateError('" + errorMessage + "')",
                            null
                        );
                    });
                }
            });
        } else {
            ((Activity) context).runOnUiThread(() -> {
                webView.evaluateJavascript(
                    "handleUpdateError('User not logged in')",
                    null
                );
            });
        }
    }
}
