package com.example.smartrecipegenerator;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.google.common.util.concurrent.ListenableFuture;
import java.io.File;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import androidx.camera.core.ExperimentalGetImage;
import android.media.MediaActionSound;
import android.app.AlertDialog;
import android.content.SharedPreferences;

@ExperimentalGetImage
public class CameraActivity extends AppCompatActivity {
    private static final String TAG = "CameraActivity";
    private PreviewView previewView;
    private ImageCapture imageCapture;
    private ExecutorService cameraExecutor;
    private static final int REQUEST_CODE_PERMISSIONS = 10;
    private static final String[] REQUIRED_PERMISSIONS = {Manifest.permission.CAMERA};
    private FruitVegetableClassifier classifier;
    private Button captureButton;
    private ObjectDetectionOverlay overlay;
    private MediaActionSound sound;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        previewView = findViewById(R.id.viewFinder);
        overlay = findViewById(R.id.overlay);
        captureButton = findViewById(R.id.camera_capture_button);

        // 初始化音效
        sound = new MediaActionSound();
        sound.load(MediaActionSound.SHUTTER_CLICK);

        try {
            cameraExecutor = Executors.newSingleThreadExecutor();
            classifier = new FruitVegetableClassifier(this);

            if (allPermissionsGranted()) {
                startCamera();
            } else {
                ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
            }

            captureButton.setOnClickListener(v -> {
                sound.play(MediaActionSound.SHUTTER_CLICK);
                takePhoto();
            });

        } catch (Exception e) {
            Log.e(TAG, "Error in onCreate: ", e);
            Toast.makeText(this, "相機初始化失敗: " + e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void takePhoto() {
        if (imageCapture == null) return;

        File photoFile = new File(getCacheDir(), "temp_photo.jpg");
        ImageCapture.OutputFileOptions outputOptions = new ImageCapture.OutputFileOptions
                .Builder(photoFile)
                .build();

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            new ImageCapture.OnImageSavedCallback() {
                @Override
                public void onImageSaved(@NonNull ImageCapture.OutputFileResults output) {
                    String result = classifier.classifyImage(photoFile);
                    runOnUiThread(() -> {
                        if (result != null) {
                            overlay.setClassificationResult(result);
                            
                            // 處理結果字符串，去掉置信度部分
                            String itemName = result;
                            if (result.contains("(")) {
                                itemName = result.substring(0, result.indexOf("(")).trim();
                            }
                            
                            final String finalItemName = itemName;
                            
                            // 顯示確認添加到 Pantry 的對話框
                            new AlertDialog.Builder(CameraActivity.this)
                                .setTitle("Add to Pantry")
                                .setMessage("Do you want to add " + finalItemName + " to your pantry?")
                                .setPositiveButton("Yes", (dialog, which) -> {
                                    // 獲取當前用戶ID
                                    SharedPreferences prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE);
                                    String email = prefs.getString("email", "");
                                    DatabaseHelper dbHelper = new DatabaseHelper(CameraActivity.this);
                                    int userId = dbHelper.getUserId(email);
                                    
                                    if (userId != -1) {
                                        boolean added = dbHelper.addItemToPantry(userId, finalItemName);
                                        if (added) {
                                            Toast.makeText(CameraActivity.this, 
                                                finalItemName + " added to pantry", Toast.LENGTH_SHORT).show();
                                        } else {
                                            Toast.makeText(CameraActivity.this, 
                                                "Failed to add to pantry", Toast.LENGTH_SHORT).show();
                                        }
                                    }
                                })
                                .setNegativeButton("No", null)
                                .show();
                            
                        } else {
                            Toast.makeText(CameraActivity.this, 
                                "Could not identify object", Toast.LENGTH_SHORT).show();
                        }
                    });
                }

                @Override
                public void onError(@NonNull ImageCaptureException exc) {
                    Log.e(TAG, "Photo capture failed: " + exc.getMessage(), exc);
                    runOnUiThread(() -> {
                        Toast.makeText(CameraActivity.this, 
                            "Photo capture failed", Toast.LENGTH_SHORT).show();
                    });
                }
            }
        );
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = 
            ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                bindPreview(cameraProvider);
            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Error starting camera: ", e);
                Toast.makeText(this, "啟動相機失敗", Toast.LENGTH_SHORT).show();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindPreview(@NonNull ProcessCameraProvider cameraProvider) {
        try {
            Preview preview = new Preview.Builder().build();
            CameraSelector cameraSelector = new CameraSelector.Builder()
                    .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                    .build();

            imageCapture = new ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build();

            preview.setSurfaceProvider(previewView.getSurfaceProvider());

            // 綁定用例到生命週期
            cameraProvider.unbindAll();
            cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture);

        } catch (Exception e) {
            Log.e(TAG, "Use case binding failed", e);
            Toast.makeText(this, "相機綁定失敗", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean allPermissionsGranted() {
        return ContextCompat.checkSelfPermission(this, REQUIRED_PERMISSIONS[0]) == 
            PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, 
            @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera();
            } else {
                Toast.makeText(this, "需要相機權限", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 釋放音效資源
        if (sound != null) {
            sound.release();
            sound = null;
        }
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
        }
        if (classifier != null) {
            classifier.close();
        }
    }
} 