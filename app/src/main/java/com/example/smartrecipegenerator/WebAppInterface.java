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
import com.example.smartrecipegenerator.BuildConfig;

public class WebAppInterface {
    private Context context;
    private WebView webView;
    private SupabaseHelper supabaseHelper;

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final String GEMINI_API_KEY = BuildConfig.GEMINI_API_KEY;
    private String generatedRecipe = "";
    private String currentImageUrl = "";

    WebAppInterface(Context context, WebView webView) {
        this.context = context;
        this.webView = webView;
        this.supabaseHelper = new SupabaseHelper(context);
    }

    @JavascriptInterface
    public void signIn(String email, String password) {
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

    private void saveLoginState(String email) {
        SharedPreferences prefs = context.getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean("isLoggedIn", true);
        editor.putString("email", email);
        editor.apply();
    }

    @JavascriptInterface
    public void signUp(String email, String password) {
        if (password.length() < 6) {
            ((Activity) context).runOnUiThread(() -> {
                Toasty.error(context, "Password must be at least 6 characters long", Toast.LENGTH_SHORT, true).show();
            });
            return;
        }

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

    private String hashPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            byte[] hash = digest.digest(password.getBytes(StandardCharsets.UTF_8));

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
            supabaseHelper.getUserId(email, new SupabaseHelper.SupabaseCallback<Integer>() {
                @Override
                public void onSuccess(Integer userId) {
                    if (userId != -1) {
                        supabaseHelper.updateUserName(userId, newName, new SupabaseHelper.SupabaseCallback<Boolean>() {
                            @Override
                            public void onSuccess(Boolean result) {
                                ((Activity) context).runOnUiThread(() -> {
                                    if (result) {
                                        SharedPreferences.Editor editor = prefs.edit();
                                        editor.putString("name", newName);
                                        editor.apply();

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
        SharedPreferences prefs = context.getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
        String currentUserEmail = prefs.getString("email", "");

        if (currentUserEmail.isEmpty()) {
            Toast.makeText(context, "Unable to delete account: User information not found", Toast.LENGTH_SHORT).show();
            return;
        }

        supabaseHelper.deleteUser(currentUserEmail, new SupabaseHelper.SupabaseCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean result) {
                if (result) {
                    prefs.edit().clear().apply();

                    ((Activity)context).runOnUiThread(() -> {
                        Toast.makeText(context, "Account deleted successfully", Toast.LENGTH_SHORT).show();
                        ((MainActivity)context).getWebView().loadUrl("file:///android_asset/login.html");
                    });
                } else {
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
                JSONObject recipeData = new JSONObject(recipeDataJson);
                JSONArray typesArray = recipeData.getJSONArray("types");
                JSONArray ingredientsArray = recipeData.getJSONArray("ingredients");

                StringBuilder typesBuilder = new StringBuilder();
                for (int i = 0; i < typesArray.length(); i++) {
                    if (i > 0) typesBuilder.append(", ");
                    typesBuilder.append(typesArray.getString(i));
                }

                StringBuilder ingredientsBuilder = new StringBuilder();
                for (int i = 0; i < ingredientsArray.length(); i++) {
                    if (i > 0) ingredientsBuilder.append(", ");
                    ingredientsBuilder.append(ingredientsArray.getString(i));
                }

                OkHttpClient client = new OkHttpClient.Builder()
                        .connectTimeout(60, TimeUnit.SECONDS)
                        .writeTimeout(60, TimeUnit.SECONDS)
                        .readTimeout(60, TimeUnit.SECONDS)
                        .build();

                String prompt = "Create a detailed recipe using these ingredients: " +
                        ingredientsBuilder.toString() + ". The recipe should be " +
                        typesBuilder.toString() + ". Include a title, ingredients list with measurements, " +
                        "step-by-step instructions, cooking time, and nutritional information if possible. " +
                        "Format the response in HTML with appropriate tags for a mobile app display. Please strictly follow the format do not add any additional information and images.";

                Log.d("RecipeGenerator", "Prompt: " + prompt);

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

                String url = "https://generativelanguage.googleapis.com/v1/models/gemini-1.5-flash:generateContent?key=" + GEMINI_API_KEY;

                Request request = new Request.Builder()
                        .url(url)
                        .addHeader("Content-Type", "application/json")
                        .post(body)
                        .build();

                Log.d("RecipeGenerator", "Created request with headers: " + request.headers());

                Log.d("RecipeGenerator", "Sending request to Gemini API...");
                Response response = client.newCall(request).execute();

                String responseBody = response.body().string();
                Log.d("RecipeGenerator", "Response code: " + response.code());
                Log.d("RecipeGenerator", "Response body (first 500 chars): " +
                        (responseBody.length() > 500 ? responseBody.substring(0, 500) + "..." : responseBody));

                if (!response.isSuccessful()) {
                    Log.e("RecipeGenerator", "Unsuccessful response: " + response.code());
                    throw new IOException("Unexpected response code: " + response.code() + "\nBody: " + responseBody);
                }

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

                recipeContent = cleanMarkdownFormatting(recipeContent);

                Log.d("RecipeGenerator", "Cleaned recipe content: " + recipeContent);

                generatedRecipe = recipeContent;

                final String finalRecipeContent = recipeContent;

                Log.d("RecipeGenerator", "Updating UI with generated recipe");
                ((Activity) context).runOnUiThread(() -> {
                    webView.loadUrl("file:///android_asset/recipe_result.html?saved=false");

                    webView.setWebViewClient(new WebViewClient() {
                        @Override
                        public void onPageFinished(WebView view, String url) {
                            super.onPageFinished(view, url);

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

                if (currentImageUrl != null && !currentImageUrl.isEmpty()) {
                    Log.d("RecipeDebug", "Using existing image URL: " + currentImageUrl);
                    webView.evaluateJavascript(
                        "displayRecipeImage(" + JSONObject.quote(currentImageUrl) + ")",
                        null
                    );
                } else {
                    Log.d("RecipeDebug", "No existing image URL, generating new image");
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

    private String extractRecipeTitle(String htmlContent) {
        try {
            Pattern pattern = Pattern.compile("<h1>(.*?)</h1>");
            Matcher matcher = pattern.matcher(htmlContent);
            if (matcher.find()) {
                return matcher.group(1);
            }

            pattern = Pattern.compile("<title>(.*?)</title>");
            matcher = pattern.matcher(htmlContent);
            if (matcher.find()) {
                return matcher.group(1);
            }

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
     * @param title
     * @param content
     */
    @JavascriptInterface
    public void shareRecipe(String title, String content) {
        Log.d("RecipeDebug", "shareRecipe called with title: " + title);

        if (currentImageUrl != null && !currentImageUrl.isEmpty()) {
            PdfHelper.generateAndShareRecipePdf(context, title, content, currentImageUrl);
        } else {
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");

            String shareText = title + "\n\n";

            String plainText = Html.fromHtml(content, Html.FROM_HTML_MODE_COMPACT).toString();
            shareText += plainText;

            shareIntent.putExtra(Intent.EXTRA_SUBJECT, title);
            shareIntent.putExtra(Intent.EXTRA_TEXT, shareText);

            ((Activity) context).startActivity(Intent.createChooser(shareIntent, "Share Recipe"));
        }
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

    private String cleanMarkdownFormatting(String markdown) {
        if (markdown == null) return "";

        String cleaned = markdown.replaceAll("```[a-zA-Z]*\\n", "")
                                .replaceAll("```", "");

        cleaned = cleaned.replaceAll("\\n{3,}", "\n\n");

        return cleaned;
    }

    @JavascriptInterface
    public void generateRecipeImage(String recipeName) {
        Log.d("ImageGenerator", "Generating image for recipe: " + recipeName);

        String prompt = "Create a realistic, photographic image of a complete dish of " + recipeName + ". The image should be square with dimensions 100x100 pixels. Show the entire dish with natural lighting and realistic textures. No cartoon style, make it look like a professional food photograph.";
        try {
            JSONObject requestBody = new JSONObject();
            requestBody.put("model", "gemini-2.0-flash-exp-image-generation");

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

            JSONObject generationConfig = new JSONObject();
            generationConfig.put("temperature", 0.7);
            generationConfig.put("topP", 1.0);
            generationConfig.put("topK", 32);
            generationConfig.put("maxOutputTokens", 2048);

            JSONArray responseModalities = new JSONArray();
            responseModalities.put("Text");
            responseModalities.put("Image");
            generationConfig.put("responseModalities", responseModalities);

            requestBody.put("generationConfig", generationConfig);

            String requestBodyString = requestBody.toString();
            Log.d("ImageGenerator", "Request body: " + requestBodyString);

            Request request = new Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash-exp-image-generation:generateContent?key=" + GEMINI_API_KEY)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(requestBodyString, MediaType.parse("application/json")))
                .build();

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

                        if (!jsonResponse.has("candidates")) {
                            Log.e("ImageGenerator", "No 'candidates' field in response");
                            ((Activity) context).runOnUiThread(() -> {
                                Toast.makeText(context, "API response format error: missing candidates", Toast.LENGTH_SHORT).show();
                            });
                            return;
                        }

                        JSONArray candidates = jsonResponse.getJSONArray("candidates");
                        if (candidates.length() > 0) {
                            JSONObject candidate = candidates.getJSONObject(0);

                            if (!candidate.has("content")) {
                                Log.e("ImageGenerator", "No 'content' field in candidate");
                                ((Activity) context).runOnUiThread(() -> {
                                    Toast.makeText(context, "API response format error: missing content", Toast.LENGTH_SHORT).show();
                                });
                                return;
                            }

                            JSONObject content = candidate.getJSONObject("content");

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
                                    String imageData = inlineData.getString("data");
                                    String mimeType = inlineData.getString("mimeType");

                                    Log.d("ImageGenerator", "Found image data with MIME type: " + mimeType);
                                    Log.d("ImageGenerator", "Base64 data length: " + imageData.length());

                                    final String dataUrl = "data:" + mimeType + ";base64," + imageData;

                                    ((Activity) context).runOnUiThread(() -> {
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

        generatedRecipe = "";
        currentImageUrl = "";

        supabaseHelper.getRecipeById(recipeId, new SupabaseHelper.SupabaseCallback<Recipe>() {
            @Override
            public void onSuccess(Recipe recipe) {
                generatedRecipe = recipe.getContent();
                currentImageUrl = recipe.getImageUrl();

                ((Activity) context).runOnUiThread(() -> {
                    webView.loadUrl("file:///android_asset/recipe_result.html?saved=true");

                    webView.setWebViewClient(new WebViewClient() {
                        @Override
                        public void onPageFinished(WebView view, String url) {
                            super.onPageFinished(view, url);

                            webView.evaluateJavascript(
                                "markAsSavedRecipe(" + recipe.getId() + ")",
                                null
                            );

                            webView.evaluateJavascript(
                                "displayRecipe(" + JSONObject.quote(generatedRecipe) + ")",
                                null
                            );

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

                    proceedWithRecipeDeletion(recipeId);
                }
            );
        });
    }

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
                                        SharedPreferences.Editor editor = prefs.edit();
                                        editor.putString("email", newEmail);
                                        editor.apply();

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

    @JavascriptInterface
    public void updateUserPassword(String currentPassword, String newPassword) {
        Log.d("ProfileDebug", "updateUserPassword called");

        SharedPreferences prefs = context.getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
        String email = prefs.getString("email", "");

        if (!email.isEmpty()) {
            String hashedCurrentPassword = hashPassword(currentPassword);

            supabaseHelper.signIn(email, hashedCurrentPassword, new SupabaseHelper.SupabaseCallback<Boolean>() {
                @Override
                public void onSuccess(Boolean result) {
                    if (result) {
                        String hashedNewPassword = hashPassword(newPassword);

                        supabaseHelper.getUserId(email, new SupabaseHelper.SupabaseCallback<Integer>() {
                            @Override
                            public void onSuccess(Integer userId) {
                                if (userId != -1) {
                                    supabaseHelper.updateUserPassword(userId, hashedNewPassword, new SupabaseHelper.SupabaseCallback<Boolean>() {
                                        @Override
                                        public void onSuccess(Boolean updateResult) {
                                            ((Activity) context).runOnUiThread(() -> {
                                                if (updateResult) {
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
