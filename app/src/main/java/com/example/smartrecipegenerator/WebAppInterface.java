package com.example.smartrecipegenerator;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.provider.MediaStore;
import android.webkit.JavascriptInterface;
import android.widget.Toast;

import androidx.annotation.OptIn;
import androidx.camera.core.ExperimentalGetImage;

import es.dmoral.toasty.Toasty;

public class WebAppInterface {
    Context mContext;
    DatabaseHelper dbHelper;

    WebAppInterface(Context c) {
        mContext = c;
        dbHelper = new DatabaseHelper(c);
    }

    @JavascriptInterface
    public void signIn(String email, String password) {
        if (dbHelper.checkUser(email, password)) {
            Toasty.success(mContext, "Login Successful", Toast.LENGTH_SHORT, true).show();
            ((Activity)mContext).runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    ((MainActivity)mContext).getWebView().loadUrl("file:///android_asset/home.html");
                }
            });
        } else {
            Toasty.error(mContext, "Invalid Credentials", Toast.LENGTH_SHORT, true).show();
        }
    }

    @JavascriptInterface
    public void signUp(String email, String password) {
        if (dbHelper.insertUser(email, password)) {
            Toasty.success(mContext, "Registration Successful", Toast.LENGTH_SHORT, true).show();
            ((Activity)mContext).runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    ((MainActivity)mContext).getWebView().loadUrl("file:///android_asset/login.html");
                }
            });
        } else {
            Toasty.error(mContext, "Registration Failed", Toast.LENGTH_SHORT, true).show();
        }
    }

    @OptIn(markerClass = ExperimentalGetImage.class)
    @JavascriptInterface
    public void snapPhoto() {
        try {
            Intent intent = new Intent(mContext, CameraActivity.class);
            mContext.startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(mContext, "啟動相機失敗: " + e.getMessage(), 
                Toast.LENGTH_SHORT).show();
        }
    }
}
