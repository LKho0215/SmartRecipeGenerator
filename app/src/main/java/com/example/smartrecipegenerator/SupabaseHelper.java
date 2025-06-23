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
import com.example.smartrecipegenerator.BuildConfig;

public class SupabaseHelper {
    private static final String TAG = "SupabaseHelper";

    private static final String SUPABASE_URL = "https://zudkfhhtyrwtsrsvtxlf.supabase.co";
    private static final String SUPABASE_KEY = BuildConfig.SUPABASE_API_KEY;

    private static final String TABLE_USERS = "users";
    private static final String TABLE_PANTRY = "pantry";
    private static final String TABLE_RECIPES = "recipes";

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private final OkHttpClient client = new OkHttpClient();

    private final Context context;

    public SupabaseHelper(Context context) {
        this.context = context;
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

    /**
     *
     * @param email
     * @param password
     * @param callback
     */
    public void signUp(String email, String password, final SupabaseCallback<Boolean> callback) {
        try {
            String tempUsername = generateTempUsername();

            JSONObject requestBody = new JSONObject();
            requestBody.put("name", tempUsername);
            requestBody.put("email", email);
            requestBody.put("password", password);

            Request request = new Request.Builder()
                .url(SUPABASE_URL + "/rest/v1/" + TABLE_USERS)
                .addHeader("apikey", SUPABASE_KEY)
                .addHeader("Authorization", "Bearer " + SUPABASE_KEY)
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "return=representation")
                .post(RequestBody.create(requestBody.toString(), JSON))
                .build();

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
     *
     * @param email
     * @param password
     * @param callback
     */
    public void signIn(String email, String password, final SupabaseCallback<Boolean> callback) {
        try {
            Request request = new Request.Builder()
                .url(SUPABASE_URL + "/rest/v1/" + TABLE_USERS + "?email=eq." + email + "&password=eq." + password + "&select=id,email")
                .addHeader("apikey", SUPABASE_KEY)
                .addHeader("Authorization", "Bearer " + SUPABASE_KEY)
                .get()
                .build();

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
     *
     * @param email
     * @param callback
     */
    public void getUserInfo(String email, final SupabaseCallback<UserInfo> callback) {
        try {
            Request request = new Request.Builder()
                .url(SUPABASE_URL + "/rest/v1/" + TABLE_USERS + "?email=eq." + email + "&select=name,email")
                .addHeader("apikey", SUPABASE_KEY)
                .addHeader("Authorization", "Bearer " + SUPABASE_KEY)
                .get()
                .build();

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
     *
     * @param userId
     * @param newName
     * @param callback
     */
    public void updateUserName(int userId, String newName, final SupabaseCallback<Boolean> callback) {
        try {
            JSONObject requestBody = new JSONObject();
            requestBody.put("name", newName);

            RequestBody body = RequestBody.create(
                MediaType.parse("application/json"),
                requestBody.toString()
            );

            Request request = new Request.Builder()
                .url(SUPABASE_URL + "/rest/v1/" + TABLE_USERS + "?id=eq." + userId)
                .addHeader("apikey", SUPABASE_KEY)
                .addHeader("Authorization", "Bearer " + SUPABASE_KEY)
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "return=minimal")
                .patch(body)
                .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "Update name failed", e);
                    callback.onError(e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (response.isSuccessful()) {
                        callback.onSuccess(true);
                    } else {
                        String errorBody = response.body().string();
                        Log.e(TAG, "Update name failed: " + errorBody);
                        callback.onError("Update name failed: " + errorBody);
                    }
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error updating name", e);
            callback.onError(e.getMessage());
        }
    }

    /**
     *
     * @param userId
     * @param newEmail
     * @param callback
     */
    public void updateUserEmail(int userId, String newEmail, final SupabaseCallback<Boolean> callback) {
        try {
            JSONObject requestBody = new JSONObject();
            requestBody.put("email", newEmail);

            RequestBody body = RequestBody.create(
                MediaType.parse("application/json"),
                requestBody.toString()
            );

            Request request = new Request.Builder()
                .url(SUPABASE_URL + "/rest/v1/" + TABLE_USERS + "?id=eq." + userId)
                .addHeader("apikey", SUPABASE_KEY)
                .addHeader("Authorization", "Bearer " + SUPABASE_KEY)
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "return=minimal")
                .patch(body)
                .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "Update email failed", e);
                    callback.onError(e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (response.isSuccessful()) {
                        callback.onSuccess(true);
                    } else {
                        String errorBody = response.body().string();
                        Log.e(TAG, "Update email failed: " + errorBody);
                        callback.onError("Update email failed: " + errorBody);
                    }
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error updating email", e);
            callback.onError(e.getMessage());
        }
    }

    /**
     *
     * @param userId
     * @param newPassword
     * @param callback
     */
    public void updateUserPassword(int userId, String newPassword, final SupabaseCallback<Boolean> callback) {
        try {
            JSONObject requestBody = new JSONObject();
            requestBody.put("password", newPassword);

            RequestBody body = RequestBody.create(
                MediaType.parse("application/json"),
                requestBody.toString()
            );

            Request request = new Request.Builder()
                .url(SUPABASE_URL + "/rest/v1/" + TABLE_USERS + "?id=eq." + userId)
                .addHeader("apikey", SUPABASE_KEY)
                .addHeader("Authorization", "Bearer " + SUPABASE_KEY)
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "return=minimal")
                .patch(body)
                .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "Update password failed", e);
                    callback.onError(e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (response.isSuccessful()) {
                        callback.onSuccess(true);
                    } else {
                        String errorBody = response.body().string();
                        Log.e(TAG, "Update password failed: " + errorBody);
                        callback.onError("Update password failed: " + errorBody);
                    }
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error updating password", e);
            callback.onError(e.getMessage());
        }
    }

    /**
     *
     * @param email
     * @param callback
     */
    public void getUserId(String email, final SupabaseCallback<Integer> callback) {
        try {
            Request request = new Request.Builder()
                .url(SUPABASE_URL + "/rest/v1/" + TABLE_USERS + "?email=eq." + email + "&select=id")
                .addHeader("apikey", SUPABASE_KEY)
                .addHeader("Authorization", "Bearer " + SUPABASE_KEY)
                .get()
                .build();

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
                                callback.onSuccess(-1);
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
     *
     * @param userId
     * @param itemName
     * @param callback
     */
    public void addItemToPantry(int userId, String itemName, final SupabaseCallback<Boolean> callback) {
        try {
            JSONObject requestBody = new JSONObject();
            requestBody.put("user_id", userId);
            requestBody.put("item_name", itemName);

            Request request = new Request.Builder()
                .url(SUPABASE_URL + "/rest/v1/" + TABLE_PANTRY)
                .addHeader("apikey", SUPABASE_KEY)
                .addHeader("Authorization", "Bearer " + SUPABASE_KEY)
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "return=representation")
                .post(RequestBody.create(requestBody.toString(), JSON))
                .build();

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
     *
     * @param userId
     * @param callback
     */
    public void getUserPantryItems(int userId, final SupabaseCallback<List<PantryItem>> callback) {
        try {
            Log.d(TAG, "Getting pantry items for user ID: " + userId);

            Request request = new Request.Builder()
                .url(SUPABASE_URL + "/rest/v1/" + TABLE_PANTRY + "?user_id=eq." + userId + "&order=created_at.desc")
                .addHeader("apikey", SUPABASE_KEY)
                .addHeader("Authorization", "Bearer " + SUPABASE_KEY)
                .get()
                .build();

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
     *
     * @param itemId
     * @param callback
     */
    public void deleteItemFromPantry(int itemId, final SupabaseCallback<Boolean> callback) {
        try {
            Request request = new Request.Builder()
                .url(SUPABASE_URL + "/rest/v1/" + TABLE_PANTRY + "?id=eq." + itemId)
                .addHeader("apikey", SUPABASE_KEY)
                .addHeader("Authorization", "Bearer " + SUPABASE_KEY)
                .delete()
                .build();

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
     *
     * @param email
     * @param callback
     */
    public void deleteUser(String email, final SupabaseCallback<Boolean> callback) {
        try {
            Request request = new Request.Builder()
                .url(SUPABASE_URL + "/rest/v1/" + TABLE_USERS + "?email=eq." + email)
                .addHeader("apikey", SUPABASE_KEY)
                .addHeader("Authorization", "Bearer " + SUPABASE_KEY)
                .delete()
                .build();

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
     *
     * @param userId
     * @param title
     * @param content
     * @param imageUrl
     * @param callback
     */
    public void saveRecipe(int userId, String title, String content, String imageUrl, final SupabaseCallback<Boolean> callback) {
        try {
            JSONObject requestBody = new JSONObject();
            requestBody.put("user_id", userId);
            requestBody.put("title", title);
            requestBody.put("content", content);
            requestBody.put("image_url", imageUrl);

            Request request = new Request.Builder()
                .url(SUPABASE_URL + "/rest/v1/" + TABLE_RECIPES)
                .addHeader("apikey", SUPABASE_KEY)
                .addHeader("Authorization", "Bearer " + SUPABASE_KEY)
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "return=representation")
                .post(RequestBody.create(requestBody.toString(), JSON))
                .build();

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
     *
     * @param userId
     * @param callback
     */
    public void getUserRecipes(int userId, final SupabaseCallback<List<Recipe>> callback) {
        try {
            Request request = new Request.Builder()
                .url(SUPABASE_URL + "/rest/v1/" + TABLE_RECIPES + "?user_id=eq." + userId + "&order=created_at.desc")
                .addHeader("apikey", SUPABASE_KEY)
                .addHeader("Authorization", "Bearer " + SUPABASE_KEY)
                .get()
                .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "Get user recipes failed", e);
                    callback.onError(e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (response.isSuccessful()) {
                        String responseBody = response.body().string();
                        try {
                            JSONArray recipesArray = new JSONArray(responseBody);
                            List<Recipe> recipes = new ArrayList<>();

                            for (int i = 0; i < recipesArray.length(); i++) {
                                JSONObject recipeObj = recipesArray.getJSONObject(i);
                                Recipe recipe = new Recipe(
                                    recipeObj.getInt("id"),
                                    recipeObj.getInt("user_id"),
                                    recipeObj.getString("title"),
                                    recipeObj.getString("content"),
                                    recipeObj.optString("image_url", ""),
                                    recipeObj.optString("created_at", "")
                                );
                                recipes.add(recipe);
                            }

                            callback.onSuccess(recipes);
                        } catch (JSONException e) {
                            Log.e(TAG, "Error parsing recipes", e);
                            callback.onError("Error parsing recipes: " + e.getMessage());
                        }
                    } else {
                        String errorBody = response.body().string();
                        Log.e(TAG, "Get user recipes failed: " + errorBody);
                        callback.onError("Get user recipes failed: " + errorBody);
                    }
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error getting user recipes", e);
            callback.onError(e.getMessage());
        }
    }

    /**
     *
     * @param recipeId
     * @param callback
     */
    public void getRecipeById(int recipeId, final SupabaseCallback<Recipe> callback) {
        try {
            Request request = new Request.Builder()
                .url(SUPABASE_URL + "/rest/v1/" + TABLE_RECIPES + "?id=eq." + recipeId)
                .addHeader("apikey", SUPABASE_KEY)
                .addHeader("Authorization", "Bearer " + SUPABASE_KEY)
                .get()
                .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "Get recipe failed", e);
                    callback.onError(e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (response.isSuccessful()) {
                        String responseBody = response.body().string();
                        try {
                            JSONArray recipesArray = new JSONArray(responseBody);
                            if (recipesArray.length() > 0) {
                                JSONObject recipeObj = recipesArray.getJSONObject(0);
                                Recipe recipe = new Recipe(
                                    recipeObj.getInt("id"),
                                    recipeObj.getInt("user_id"),
                                    recipeObj.getString("title"),
                                    recipeObj.getString("content"),
                                    recipeObj.optString("image_url", ""),
                                    recipeObj.optString("created_at", "")
                                );
                                callback.onSuccess(recipe);
                            } else {
                                callback.onError("Recipe not found");
                            }
                        } catch (JSONException e) {
                            Log.e(TAG, "Error parsing recipe", e);
                            callback.onError("Error parsing recipe: " + e.getMessage());
                        }
                    } else {
                        String errorBody = response.body().string();
                        Log.e(TAG, "Get recipe failed: " + errorBody);
                        callback.onError("Get recipe failed: " + errorBody);
                    }
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error getting recipe", e);
            callback.onError(e.getMessage());
        }
    }

    /**
     *
     * @param recipeId
     * @param userId
     * @param callback
     */
    public void deleteRecipe(int recipeId, int userId, final SupabaseCallback<Boolean> callback) {
        try {
            Log.d(TAG, "Deleting recipe with ID: " + recipeId + " for user ID: " + userId);

            Request request = new Request.Builder()
                .url(SUPABASE_URL + "/rest/v1/" + TABLE_RECIPES + "?id=eq." + recipeId + "&user_id=eq." + userId)
                .addHeader("apikey", SUPABASE_KEY)
                .addHeader("Authorization", "Bearer " + SUPABASE_KEY)
                .delete()
                .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "Delete recipe failed", e);
                    callback.onError(e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (response.isSuccessful()) {
                        Log.d(TAG, "Recipe deleted successfully");
                        callback.onSuccess(true);
                    } else {
                        String errorBody = response.body().string();
                        Log.e(TAG, "Delete recipe failed: " + errorBody);
                        callback.onError("Delete recipe failed: " + errorBody);
                    }
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error deleting recipe", e);
            callback.onError(e.getMessage());
        }
    }

    public interface SupabaseCallback<T> {
        void onSuccess(T result);
        void onError(String errorMessage);
    }
}