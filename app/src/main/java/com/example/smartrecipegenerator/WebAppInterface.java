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

public class WebAppInterface {
    private Context context;
    private WebView webView;
    private SupabaseHelper supabaseHelper;

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final String GEMINI_API_KEY = "AIzaSyB6cHgVlwh15xwLhgpRnpubCwV4AdUV0Q0"; // 替換為你的 Gemini API 密鑰
    private String generatedRecipe = "";

    WebAppInterface(Context context, WebView webView) {
        this.context = context;
        this.webView = webView;
        this.supabaseHelper = new SupabaseHelper(context);
    }

    @JavascriptInterface
    public void signIn(String email, String password) {
        supabaseHelper.signIn(email, password, new SupabaseHelper.SupabaseCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean result) {
                if (result) {
                    ((Activity) context).runOnUiThread(() -> {
                        Toasty.success(context, "Login Successful", Toast.LENGTH_SHORT, true).show();
                        ((MainActivity) context).getWebView().loadUrl("file:///android_asset/home.html");
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

    @JavascriptInterface
    public void signUp(String email, String password) {
        supabaseHelper.signUp(email, password, new SupabaseHelper.SupabaseCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean result) {
                ((Activity) context).runOnUiThread(() -> {
                    Toasty.success(context, "Registration Successful", Toast.LENGTH_SHORT, true).show();
                    ((MainActivity) context).getWebView().loadUrl("file:///android_asset/login.html");
                });
            }

            @Override
            public void onError(String errorMessage) {
                ((Activity) context).runOnUiThread(() -> {
                    Toasty.error(context, "Registration Failed: " + errorMessage, Toast.LENGTH_SHORT, true).show();
                });
            }
        });
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
    public void updateUsername(String newName) {
        SharedPreferences prefs = context.getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
        String currentUserEmail = prefs.getString("email", "");

        supabaseHelper.updateUsername(currentUserEmail, newName, new SupabaseHelper.SupabaseCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean result) {
                ((Activity) context).runOnUiThread(() -> {
                    Toast.makeText(context, "Username updated successfully", Toast.LENGTH_SHORT).show();
                    webView.evaluateJavascript("window.location.reload();", null);
                });
            }

            @Override
            public void onError(String errorMessage) {
                ((Activity) context).runOnUiThread(() -> {
                    Toast.makeText(context, "Failed to update username: " + errorMessage, Toast.LENGTH_SHORT).show();
                });
            }
        });
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
        supabaseHelper.deleteUser(currentUserEmail, new SupabaseHelper.SupabaseCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean result) {
                if (result) {
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
                    // 添加一個 Toast 消息，確認 UI 更新
                    Toast.makeText(context, "Recipe generated successfully", Toast.LENGTH_SHORT).show();

                    // 導航到食譜結果頁面
                    webView.loadUrl("file:///android_asset/recipe_result.html");
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
        Log.d("RecipeGenerator", "getGeneratedRecipe called, returning recipe of length: " + 
              (generatedRecipe != null ? generatedRecipe.length() : 0));
        
        if (generatedRecipe != null && !generatedRecipe.isEmpty()) {
            ((Activity) context).runOnUiThread(() -> {
                // 調用 JavaScript 函數顯示食譜
                webView.evaluateJavascript(
                        "displayRecipe(" + JSONObject.quote(generatedRecipe) + ")",
                        null
                );
                
                // 提取食譜標題（假設是 HTML 中的 h1 標籤內容）
                String recipeTitle = extractRecipeTitle(generatedRecipe);
                Log.d("RecipeGenerator", "Extracted recipe title: " + recipeTitle);
                
                // 生成食譜圖片
                generateRecipeImage(recipeTitle);
            });
        } else {
            ((Activity) context).runOnUiThread(() -> {
                Toast.makeText(context, "No recipe has been generated yet", Toast.LENGTH_SHORT).show();
                webView.loadUrl("file:///android_asset/home.html");
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
                                    Toast.makeText(context, "食譜已保存", Toast.LENGTH_SHORT).show();
                                });
                            }
                            
                            @Override
                            public void onError(String errorMessage) {
                                ((Activity) context).runOnUiThread(() -> {
                                    Toast.makeText(context, "保存食譜失敗: " + errorMessage, Toast.LENGTH_SHORT).show();
                                });
                            }
                        });
                    } else {
                        ((Activity) context).runOnUiThread(() -> {
                            Toast.makeText(context, "用戶不存在", Toast.LENGTH_SHORT).show();
                        });
                    }
                }
                
                @Override
                public void onError(String errorMessage) {
                    ((Activity) context).runOnUiThread(() -> {
                        Toast.makeText(context, "獲取用戶 ID 失敗: " + errorMessage, Toast.LENGTH_SHORT).show();
                    });
                }
            });
        } else {
            ((Activity) context).runOnUiThread(() -> {
                Toast.makeText(context, "用戶未登錄", Toast.LENGTH_SHORT).show();
            });
        }
    }

    @JavascriptInterface
    public void shareRecipe(String content) {
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT, content);
        context.startActivity(Intent.createChooser(shareIntent, "分享食譜"));
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
                                    Toast.makeText(context, "物品已添加到 Pantry", Toast.LENGTH_SHORT).show();
                                    // 刷新 Pantry 列表
                                    webView.evaluateJavascript("loadPantryItems();", null);
                                });
                            }
                            
                            @Override
                            public void onError(String errorMessage) {
                                ((Activity) context).runOnUiThread(() -> {
                                    Toast.makeText(context, "添加物品失敗: " + errorMessage, Toast.LENGTH_SHORT).show();
                                });
                            }
                        });
                    } else {
                        ((Activity) context).runOnUiThread(() -> {
                            Toast.makeText(context, "用戶不存在", Toast.LENGTH_SHORT).show();
                        });
                    }
                }
                
                @Override
                public void onError(String errorMessage) {
                    ((Activity) context).runOnUiThread(() -> {
                        Toast.makeText(context, "獲取用戶 ID 失敗: " + errorMessage, Toast.LENGTH_SHORT).show();
                    });
                }
            });
        } else {
            ((Activity) context).runOnUiThread(() -> {
                Toast.makeText(context, "用戶未登錄", Toast.LENGTH_SHORT).show();
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
                                Toast.makeText(context, "API 響應格式錯誤: 缺少 candidates", Toast.LENGTH_SHORT).show();
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
                                    Toast.makeText(context, "API 響應格式錯誤: 缺少 content", Toast.LENGTH_SHORT).show();
                                });
                                return;
                            }
                            
                            JSONObject content = candidate.getJSONObject("content");
                            
                            // 檢查是否有 parts
                            if (!content.has("parts")) {
                                Log.e("ImageGenerator", "No 'parts' field in content");
                                ((Activity) context).runOnUiThread(() -> {
                                    Toast.makeText(context, "API 響應格式錯誤: 缺少 parts", Toast.LENGTH_SHORT).show();
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
                                    Toast.makeText(context, "API 未返回圖片數據，可能需要調整提示詞", Toast.LENGTH_LONG).show();
                                });
                            }
                        } else {
                            Log.e("ImageGenerator", "No candidates returned");
                            ((Activity) context).runOnUiThread(() -> {
                                Toast.makeText(context, "API 未返回候選項", Toast.LENGTH_SHORT).show();
                            });
                        }
                    } else {
                        Log.e("ImageGenerator", "Image generation failed with code: " + response.code());
                        Log.e("ImageGenerator", "Response body: " + responseBody);
                        
                        // 嘗試解析錯誤消息
                        String errorMessage = "未知錯誤";
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
                            Toast.makeText(context, "圖片生成失敗: " + finalErrorMessage, Toast.LENGTH_LONG).show();
                        });
                    }
                } catch (Exception e) {
                    Log.e("ImageGenerator", "Error generating image", e);
                    ((Activity) context).runOnUiThread(() -> {
                        Toast.makeText(context, "圖片生成錯誤: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    });
                }
            }).start();
        } catch (Exception e) {
            Log.e("ImageGenerator", "Error creating JSON request", e);
            Toast.makeText(context, "創建請求錯誤: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
