package com.example.smartrecipegenerator;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.provider.MediaStore;
import android.webkit.JavascriptInterface;
import android.widget.Toast;
import es.dmoral.toasty.Toasty;

import java.io.IOException;
import java.util.List;

public class WebAppInterface {
    Context mContext;
    DatabaseHelper dbHelper;
    private FruitClassifier fruitClassifier;

    WebAppInterface(Context c) {
        mContext = c;
        dbHelper = new DatabaseHelper(c);
        try {
            fruitClassifier = new FruitClassifier(c);
        } catch (IOException e) {
            e.printStackTrace();
        }
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

    @JavascriptInterface
    public void snapPhoto() {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (intent.resolveActivity(mContext.getPackageManager()) != null) {
            ((MainActivity) mContext).startActivityForResult(intent, 1);
        }
    }

    @JavascriptInterface
    public void classifyImage(Bitmap bitmap) {
        try {
            List<Classifications> results = fruitClassifier.classify(bitmap);
            // 处理分类结果
            Classifications classification = results.get(0);
            String topResult = classification.getCategories().get(0).getLabel();
            float confidence = classification.getCategories().get(0).getScore();

            // 显示结果
            ((Activity)mContext).runOnUiThread(() -> {
                Toasty.info(mContext,
                        "识别结果: " + topResult +
                                "\n置信度: " + String.format("%.2f%%", confidence * 100),
                        Toast.LENGTH_LONG).show();
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
