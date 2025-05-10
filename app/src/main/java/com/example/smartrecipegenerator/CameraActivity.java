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
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.widget.ImageView;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicConvolve3x3;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

@ExperimentalGetImage
public class CameraActivity extends AppCompatActivity {
    private static final String TAG = "CameraActivity";
    private PreviewView previewView;
    private ImageCapture imageCapture;
    private ExecutorService cameraExecutor;
    private static final int REQUEST_CODE_PERMISSIONS = 10;
    private static final String[] REQUIRED_PERMISSIONS = {Manifest.permission.CAMERA};
    private FruitVegetableClassifier classifier;
    private YoloV5Detector yoloDetector;
    private Button captureButton;
    private MediaActionSound sound;
    private File photoFile;
    private AlertDialog loadingDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        previewView = findViewById(R.id.viewFinder);
        captureButton = findViewById(R.id.camera_capture_button);

        // 初始化音效
        sound = new MediaActionSound();
        sound.load(MediaActionSound.SHUTTER_CLICK);

        try {
            cameraExecutor = Executors.newSingleThreadExecutor();
            classifier = new FruitVegetableClassifier(this);

            // 初始化 YOLO 檢測器
            try {
                yoloDetector = new YoloV5Detector(this);
            } catch (Exception e) {
                Log.e(TAG, "Error initializing YoloV5Detector: ", e);
                Toast.makeText(this, "Failed to initialize object detector: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }

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
            Toast.makeText(this, "Camera initialization failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void takePhoto() {
        if (imageCapture == null) return;

        // Disable capture button to prevent multiple captures
        captureButton.setEnabled(false);

        // Show loading dialog
        showLoadingDialog("Detecting possible items...");

        photoFile = new File(getCacheDir(), "temp_photo.jpg");
        ImageCapture.OutputFileOptions outputOptions = new ImageCapture.OutputFileOptions
                .Builder(photoFile)
                .build();

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            new ImageCapture.OnImageSavedCallback() {
                @Override
                public void onImageSaved(@NonNull ImageCapture.OutputFileResults output) {
                    if (yoloDetector != null) {
                        // 使用 YOLO 模型進行物體檢測
                        processImageWithYolo(photoFile);
                    } else {
                        // 如果 YOLO 初始化失敗，回退到原來的分類器
                        String result = classifier.classifyImage(photoFile);
                        runOnUiThread(() -> {
                            // Dismiss loading dialog
                            dismissLoadingDialog();
                            // Re-enable capture button
                            captureButton.setEnabled(true);

                            if (result != null) {
                                showSingleDetectionDialog(result);
                            } else {
                                Toast.makeText(CameraActivity.this,
                                    "Could not identify object", Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                }

                @Override
                public void onError(@NonNull ImageCaptureException exc) {
                    Log.e(TAG, "Photo capture failed: " + exc.getMessage(), exc);
                    runOnUiThread(() -> {
                        // Dismiss loading dialog
                        dismissLoadingDialog();
                        // Re-enable capture button
                        captureButton.setEnabled(true);

                        Toast.makeText(CameraActivity.this,
                            "Photo capture failed", Toast.LENGTH_SHORT).show();
                    });
                }
            }
        );
    }

    /**
     * Show a loading dialog to indicate detection is in progress
     */
    private void showLoadingDialog(String message) {
        runOnUiThread(() -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage(message);
            builder.setCancelable(false);

            // Create a ProgressBar
            android.widget.ProgressBar progressBar = new android.widget.ProgressBar(this);
            progressBar.setIndeterminate(true);

            // Create a horizontal layout
            android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
            layout.setOrientation(android.widget.LinearLayout.HORIZONTAL);
            layout.setPadding(30, 30, 30, 30);
            layout.setGravity(android.view.Gravity.CENTER);

            // Add ProgressBar and message to layout
            layout.addView(progressBar);
            android.widget.TextView textView = new android.widget.TextView(this);
            textView.setText(message);
            textView.setPadding(20, 0, 0, 0);
            layout.addView(textView);

            builder.setView(layout);

            // Create and show the dialog
            loadingDialog = builder.create();
            loadingDialog.show();
        });
    }

    /**
     * Dismiss the loading dialog
     */
    private void dismissLoadingDialog() {
        runOnUiThread(() -> {
            if (loadingDialog != null && loadingDialog.isShowing()) {
                loadingDialog.dismiss();
            }
        });
    }

    private void processImageWithYolo(File imageFile) {
        Log.d(TAG, "Starting image processing with YOLO");
        try {
            // Read image file with higher quality
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = 1; // Changed from 2 to 1 for higher quality (full resolution)
            Bitmap bitmap = BitmapFactory.decodeFile(imageFile.getAbsolutePath(), options);

            if (bitmap == null) {
                Log.e(TAG, "Failed to decode bitmap from file");
                runOnUiThread(() -> {
                    // Dismiss loading dialog
                    dismissLoadingDialog();
                    // Re-enable capture button
                    captureButton.setEnabled(true);

                    Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show();
                });
                return;
            }

            Log.d(TAG, "Original bitmap size: " + bitmap.getWidth() + "x" + bitmap.getHeight());

            // Rotate image since camera orientation is an issue
            Matrix matrix = new Matrix();
            matrix.postRotate(90); // Adjust rotation angle as needed
            Bitmap rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
            Log.d(TAG, "Rotated bitmap size: " + rotatedBitmap.getWidth() + "x" + rotatedBitmap.getHeight());

            // Save a copy of the rotated bitmap for debugging
            try {
                File debugFile = new File(getCacheDir(), "debug_photo.jpg");
                java.io.FileOutputStream out = new java.io.FileOutputStream(debugFile);
                rotatedBitmap.compress(Bitmap.CompressFormat.JPEG, 100, out); // Increased quality from 90 to 100
                out.flush();
                out.close();
                Log.d(TAG, "Saved debug image to " + debugFile.getAbsolutePath());
            } catch (Exception e) {
                Log.e(TAG, "Error saving debug image", e);
            }

            // 创建不同处理的图像
            List<Bitmap> processedBitmaps = createProcessedBitmaps(rotatedBitmap);

            // 对每张处理后的图像进行检测，并合并结果
            List<YoloV5Detector.Detection> allDetections = new ArrayList<>();

            for (Bitmap processedBitmap : processedBitmaps) {
                List<YoloV5Detector.Detection> detections = yoloDetector.detectObjects(processedBitmap);
                allDetections.addAll(detections);
            }

            // 应用NMS去除重复检测
            List<YoloV5Detector.Detection> mergedDetections = mergeDetections(allDetections);

            Log.d(TAG, "Detection completed, found " + mergedDetections.size() + " objects after merging");

            // Get preview dimensions for logging
            int previewWidth = previewView.getWidth();
            int previewHeight = previewView.getHeight();
            Log.d(TAG, "Preview dimensions: " + previewWidth + "x" + previewHeight);

            // Instead of showing on overlay, directly show selection dialog
            runOnUiThread(() -> {
                // Dismiss loading dialog
                dismissLoadingDialog();
                // Re-enable capture button
                captureButton.setEnabled(true);

                if (mergedDetections.isEmpty()) {
                    Log.d(TAG, "No objects detected");
                    Toast.makeText(this, "No objects detected - try taking another photo", Toast.LENGTH_LONG).show();
                } else {
                    // Log each detected object
                    for (YoloV5Detector.Detection detection : mergedDetections) {
                        RectF box = detection.getBoundingBox();
                        Log.d(TAG, "Detected: " + detection.getLabel() +
                              " (confidence: " + Math.min(1.0f, detection.getConfidence()) +
                              ") at [" + box.left + "," + box.top + "," + box.right + "," + box.bottom + "]");
                    }

                    // Show dialog for user to select from detected items
                    showDetectionSelectionDialog(mergedDetections);
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "Error processing image with YOLO: " + e.getMessage(), e);
            runOnUiThread(() -> {
                // Dismiss loading dialog
                dismissLoadingDialog();
                // Re-enable capture button
                captureButton.setEnabled(true);

                Toast.makeText(this, "Error detecting objects: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            });
        }
    }

    /**
     * 创建多种处理后的图像以提高检测准确率
     */
    private List<Bitmap> createProcessedBitmaps(Bitmap original) {
        List<Bitmap> processedImages = new ArrayList<>();

        // 添加原始图像
        processedImages.add(original);

        // 增强对比度图像
        Bitmap enhancedBitmap = enhanceContrast(original, 1.2f);
        processedImages.add(enhancedBitmap);

        // 增加边缘锐度
        Bitmap sharpenedBitmap = sharpenImage(original);
        processedImages.add(sharpenedBitmap);

        return processedImages;
    }

    /**
     * 增强图像对比度
     */
    private Bitmap enhanceContrast(Bitmap src, float contrast) {
        Bitmap dest = Bitmap.createBitmap(
                src.getWidth(), src.getHeight(), src.getConfig());

        Canvas canvas = new Canvas(dest);
        Paint paint = new Paint();

        // 创建对比度滤镜
        ColorMatrix cm = new ColorMatrix();
        cm.set(new float[] {
                contrast, 0, 0, 0, -0.5f * 255 * (contrast - 1),
                0, contrast, 0, 0, -0.5f * 255 * (contrast - 1),
                0, 0, contrast, 0, -0.5f * 255 * (contrast - 1),
                0, 0, 0, 1, 0
        });

        paint.setColorFilter(new ColorMatrixColorFilter(cm));
        canvas.drawBitmap(src, 0, 0, paint);

        return dest;
    }

    /**
     * 锐化图像
     */
    private Bitmap sharpenImage(Bitmap src) {
        Bitmap dest = Bitmap.createBitmap(
                src.getWidth(), src.getHeight(), src.getConfig());

        RenderScript rs = RenderScript.create(this);
        ScriptIntrinsicConvolve3x3 convolution =
                ScriptIntrinsicConvolve3x3.create(rs, Element.U8_4(rs));

        Allocation input = Allocation.createFromBitmap(rs, src);
        Allocation output = Allocation.createFromBitmap(rs, dest);

        // 锐化卷积核
        float[] coefficients = {
                0, -1, 0,
                -1, 5, -1,
                0, -1, 0
        };

        convolution.setCoefficients(coefficients);
        convolution.setInput(input);
        convolution.forEach(output);

        output.copyTo(dest);
        rs.destroy();

        return dest;
    }

    /**
     * 合并多个检测结果并去除重复
     */
    private List<YoloV5Detector.Detection> mergeDetections(List<YoloV5Detector.Detection> allDetections) {
        // 按类别分组
        Map<String, List<YoloV5Detector.Detection>> detectionsByClass = new HashMap<>();

        // Cap all confidence scores at 1.0 before processing
        for (YoloV5Detector.Detection detection : allDetections) {
            // Since we can't modify the confidence value directly (it's final), create a new Detection with capped confidence
            float cappedConfidence = Math.min(1.0f, detection.getConfidence());
            YoloV5Detector.Detection cappedDetection = new YoloV5Detector.Detection(
                detection.getLabel(),
                cappedConfidence,
                detection.getBoundingBox(),
                detection.getClassId()
            );

            String label = cappedDetection.getLabel();
            if (!detectionsByClass.containsKey(label)) {
                detectionsByClass.put(label, new ArrayList<>());
            }
            detectionsByClass.get(label).add(cappedDetection);
        }

        // 合并结果
        List<YoloV5Detector.Detection> mergedDetections = new ArrayList<>();

        for (Map.Entry<String, List<YoloV5Detector.Detection>> entry : detectionsByClass.entrySet()) {
            List<YoloV5Detector.Detection> classDetections = entry.getValue();

            // 按置信度排序
            Collections.sort(classDetections, (d1, d2) ->
                    Float.compare(d2.getConfidence(), d1.getConfidence()));

            // 应用NMS
            List<YoloV5Detector.Detection> keptDetections = new ArrayList<>();

            while (!classDetections.isEmpty()) {
                // 取出置信度最高的检测
                YoloV5Detector.Detection current = classDetections.remove(0);
                keptDetections.add(current);

                // 过滤掉与当前检测重叠的其他检测
                Iterator<YoloV5Detector.Detection> it = classDetections.iterator();
                while (it.hasNext()) {
                    YoloV5Detector.Detection detection = it.next();
                    float iou = calculateIoU(current.getBoundingBox(), detection.getBoundingBox());
                    if (iou > 0.45) {  // 调整IOU阈值
                        it.remove();
                    }
                }
            }

            mergedDetections.addAll(keptDetections);
        }

        return mergedDetections;
    }

    /**
     * 计算两个边界框的IoU
     */
    private float calculateIoU(RectF box1, RectF box2) {
        float intersectionLeft = Math.max(box1.left, box2.left);
        float intersectionTop = Math.max(box1.top, box2.top);
        float intersectionRight = Math.min(box1.right, box2.right);
        float intersectionBottom = Math.min(box1.bottom, box2.bottom);

        float intersectionArea = Math.max(0, intersectionRight - intersectionLeft) *
                                Math.max(0, intersectionBottom - intersectionTop);

        float box1Area = (box1.right - box1.left) * (box1.bottom - box1.top);
        float box2Area = (box2.right - box2.left) * (box2.bottom - box2.top);

        return intersectionArea / (box1Area + box2Area - intersectionArea);
    }

    // New method to show a dialog for selecting from detected items with checkboxes
    private void showDetectionSelectionDialog(List<YoloV5Detector.Detection> detections) {
        if (detections.isEmpty()) return;

        // Create a map to store unique items and their highest confidence
        Map<String, Float> uniqueItems = new HashMap<>();
        for (YoloV5Detector.Detection detection : detections) {
            String label = detection.getLabel();
            float confidence = detection.getConfidence();

            // If item already exists, keep the one with higher confidence
            if (!uniqueItems.containsKey(label) || confidence > uniqueItems.get(label)) {
                uniqueItems.put(label, confidence);
            }
        }

        // Convert map to arrays for dialog
        String[] items = uniqueItems.keySet().toArray(new String[0]);
        boolean[] checkedItems = new boolean[items.length];

        // Create and show the multi-selection dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select detected items to add to pantry");

        // Use multi-choice items (checkboxes)
        builder.setMultiChoiceItems(items, checkedItems, (dialog, which, isChecked) -> {
            checkedItems[which] = isChecked;
        });

        // Add "Add Selected" button
        builder.setPositiveButton("Add Selected", (dialog, which) -> {
            // Process all selected items
            List<String> selectedItems = new ArrayList<>();
            for (int i = 0; i < checkedItems.length; i++) {
                if (checkedItems[i]) {
                    selectedItems.add(items[i]);
                }
            }

            if (selectedItems.isEmpty()) {
                Toast.makeText(this, "No items selected", Toast.LENGTH_SHORT).show();
                finish();
            } else {
                // Process all selected items
                processMultipleRecognitionResults(selectedItems);
            }
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> {
            dialog.dismiss();
            finish(); // Return to previous activity
        });

        builder.setCancelable(false); // Prevent dismissing by tapping outside
        builder.show();
    }

    // New method to process multiple selected items
    private void processMultipleRecognitionResults(List<String> recognizedItems) {
        if (recognizedItems.isEmpty()) {
            Toast.makeText(this, "No items selected", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // 獲取當前用戶 ID
        SharedPreferences prefs = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
        String email = prefs.getString("email", "");

        if (email.isEmpty()) {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        SupabaseHelper supabaseHelper = new SupabaseHelper(this);

        // 首先獲取用戶 ID
        supabaseHelper.getUserId(email, new SupabaseHelper.SupabaseCallback<Integer>() {
            @Override
            public void onSuccess(Integer userId) {
                if (userId == -1) {
                    runOnUiThread(() -> {
                        Toast.makeText(CameraActivity.this, "用戶不存在", Toast.LENGTH_SHORT).show();
                        finish();
                    });
                    return;
                }

                // Track how many items we've processed
                final int[] itemsProcessed = {0};
                final int[] successCount = {0};
                final StringBuilder failedItems = new StringBuilder();

                // Process each item
                for (String item : recognizedItems) {
                    supabaseHelper.addItemToPantry(userId, item, new SupabaseHelper.SupabaseCallback<Boolean>() {
                        @Override
                        public void onSuccess(Boolean result) {
                            itemsProcessed[0]++;
                            if (result) successCount[0]++;

                            // Check if all items are processed
                            if (itemsProcessed[0] >= recognizedItems.size()) {
                                runOnUiThread(() -> {
                                    Toast.makeText(CameraActivity.this,
                                        "Added " + successCount[0] + " items to your pantry",
                                        Toast.LENGTH_SHORT).show();
                                    finish();
                                });
                            }
                        }

                        @Override
                        public void onError(String errorMessage) {
                            itemsProcessed[0]++;
                            failedItems.append(item).append(", ");

                            // Check if all items are processed
                            if (itemsProcessed[0] >= recognizedItems.size()) {
                                runOnUiThread(() -> {
                                    String message = "Added " + successCount[0] + " items to your pantry";
                                    if (successCount[0] < recognizedItems.size()) {
                                        message += ". Some items failed.";
                                    }
                                    Toast.makeText(CameraActivity.this, message, Toast.LENGTH_SHORT).show();
                                    finish();
                                });
                            }
                        }
                    });
                }
            }

            @Override
            public void onError(String errorMessage) {
                runOnUiThread(() -> {
                    Toast.makeText(CameraActivity.this,
                        "Failed to obtain user ID: " + errorMessage,
                        Toast.LENGTH_SHORT).show();
                    finish();
                });
            }
        });
    }

    // 顯示單一物體檢測的對話框
    private void showSingleDetectionDialog(String result) {
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
                                    processRecognitionResult(finalItemName);
                                })
                                .setNegativeButton("No", null)
                                .show();
    }

    private void processRecognitionResult(String recognizedItem) {
        if (recognizedItem != null && !recognizedItem.isEmpty()) {
            // 獲取當前用戶 ID
            SharedPreferences prefs = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
            String email = prefs.getString("email", "");

            if (!email.isEmpty()) {
                SupabaseHelper supabaseHelper = new SupabaseHelper(this);

                // 首先獲取用戶 ID
                supabaseHelper.getUserId(email, new SupabaseHelper.SupabaseCallback<Integer>() {
                    @Override
                    public void onSuccess(Integer userId) {
                        if (userId != -1) {
                            // 添加識別的物品到 Pantry
                            supabaseHelper.addItemToPantry(userId, recognizedItem, new SupabaseHelper.SupabaseCallback<Boolean>() {
                                @Override
                                public void onSuccess(Boolean result) {
                                    runOnUiThread(() -> {
                                        Toast.makeText(CameraActivity.this,
                                            "Added " + recognizedItem + " to your pantry",
                                            Toast.LENGTH_SHORT).show();

                                        // 返回主頁或 Pantry 頁面
                                        finish();
                                    });
                                }

                                @Override
                                public void onError(String errorMessage) {
                                    runOnUiThread(() -> {
                                        Toast.makeText(CameraActivity.this,
                                            "Adding item failed: " + errorMessage,
                                            Toast.LENGTH_SHORT).show();
                                        finish();
                                    });
                                }
                            });
                        } else {
                            runOnUiThread(() -> {
                                Toast.makeText(CameraActivity.this, "用戶不存在", Toast.LENGTH_SHORT).show();
                                finish();
                            });
                        }
                    }

                    @Override
                    public void onError(String errorMessage) {
                        runOnUiThread(() -> {
                            Toast.makeText(CameraActivity.this,
                                "Failed to obtain user ID: " + errorMessage,
                                Toast.LENGTH_SHORT).show();
                            finish();
                        });
                    }
                });
            } else {
                Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show();
                finish();
            }
        } else {
            Toast.makeText(this, "Unable to identify item", Toast.LENGTH_SHORT).show();
            finish();
        }
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
                Toast.makeText(this, "Failed to activate camera", Toast.LENGTH_SHORT).show();
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
            Toast.makeText(this, "Camera binding failed", Toast.LENGTH_SHORT).show();
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
                Toast.makeText(this, "Camera permissions not granted", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
            cameraExecutor.shutdown();

        if (classifier != null) {
            classifier.close();
        }

        if (yoloDetector != null) {
            yoloDetector.close();
        }
    }
}