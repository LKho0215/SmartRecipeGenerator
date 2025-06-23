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

        sound = new MediaActionSound();
        sound.load(MediaActionSound.SHUTTER_CLICK);

        try {
            cameraExecutor = Executors.newSingleThreadExecutor();
            classifier = new FruitVegetableClassifier(this);

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

        captureButton.setEnabled(false);

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
                        processImageWithYolo(photoFile);
                    } else {
                        String result = classifier.classifyImage(photoFile);
                        runOnUiThread(() -> {
                            dismissLoadingDialog();
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
                        dismissLoadingDialog();
                        captureButton.setEnabled(true);

                        Toast.makeText(CameraActivity.this,
                            "Photo capture failed", Toast.LENGTH_SHORT).show();
                    });
                }
            }
        );
    }

    private void showLoadingDialog(String message) {
        runOnUiThread(() -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage(message);
            builder.setCancelable(false);

            android.widget.ProgressBar progressBar = new android.widget.ProgressBar(this);
            progressBar.setIndeterminate(true);

            android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
            layout.setOrientation(android.widget.LinearLayout.HORIZONTAL);
            layout.setPadding(30, 30, 30, 30);
            layout.setGravity(android.view.Gravity.CENTER);

            layout.addView(progressBar);
            android.widget.TextView textView = new android.widget.TextView(this);
            textView.setText(message);
            textView.setPadding(20, 0, 0, 0);
            layout.addView(textView);

            builder.setView(layout);

            loadingDialog = builder.create();
            loadingDialog.show();
        });
    }

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
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = 1;
            Bitmap bitmap = BitmapFactory.decodeFile(imageFile.getAbsolutePath(), options);

            if (bitmap == null) {
                Log.e(TAG, "Failed to decode bitmap from file");
                runOnUiThread(() -> {
                    dismissLoadingDialog();
                    captureButton.setEnabled(true);

                    Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show();
                });
                return;
            }

            Log.d(TAG, "Original bitmap size: " + bitmap.getWidth() + "x" + bitmap.getHeight());

            Matrix matrix = new Matrix();
            Bitmap rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
            Log.d(TAG, "Rotated bitmap size: " + rotatedBitmap.getWidth() + "x" + rotatedBitmap.getHeight());

            try {
                File debugFile = new File(getCacheDir(), "debug_photo.jpg");
                java.io.FileOutputStream out = new java.io.FileOutputStream(debugFile);
                rotatedBitmap.compress(Bitmap.CompressFormat.JPEG, 100, out);
                out.flush();
                out.close();
                Log.d(TAG, "Saved debug image to " + debugFile.getAbsolutePath());
            } catch (Exception e) {
                Log.e(TAG, "Error saving debug image", e);
            }

            List<Bitmap> processedBitmaps = createProcessedBitmaps(rotatedBitmap);

            List<YoloV5Detector.Detection> allDetections = new ArrayList<>();

            for (Bitmap processedBitmap : processedBitmaps) {
                List<YoloV5Detector.Detection> detections = yoloDetector.detectObjects(processedBitmap);
                allDetections.addAll(detections);
            }

            List<YoloV5Detector.Detection> mergedDetections = mergeDetections(allDetections);

            Log.d(TAG, "Detection completed, found " + mergedDetections.size() + " objects after merging");

            int previewWidth = previewView.getWidth();
            int previewHeight = previewView.getHeight();
            Log.d(TAG, "Preview dimensions: " + previewWidth + "x" + previewHeight);

            runOnUiThread(() -> {
                dismissLoadingDialog();
                captureButton.setEnabled(true);

                if (mergedDetections.isEmpty()) {
                    Log.d(TAG, "No objects detected");
                    Toast.makeText(this, "No objects detected - try taking another photo", Toast.LENGTH_LONG).show();
                } else {
                    for (YoloV5Detector.Detection detection : mergedDetections) {
                        RectF box = detection.getBoundingBox();
                        Log.d(TAG, "Detected: " + detection.getLabel() +
                              " (confidence: " + Math.min(1.0f, detection.getConfidence()) +
                              ") at [" + box.left + "," + box.top + "," + box.right + "," + box.bottom + "]");
                    }

                    showDetectionSelectionDialog(mergedDetections);
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "Error processing image with YOLO: " + e.getMessage(), e);
            runOnUiThread(() -> {
                dismissLoadingDialog();
                captureButton.setEnabled(true);

                Toast.makeText(this, "Error detecting objects: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            });
        }
    }

    private List<Bitmap> createProcessedBitmaps(Bitmap original) {
        List<Bitmap> processedImages = new ArrayList<>();

        processedImages.add(original);

        Bitmap enhancedBitmap = enhanceContrast(original, 1.2f);
        processedImages.add(enhancedBitmap);

        Bitmap sharpenedBitmap = sharpenImage(original);
        processedImages.add(sharpenedBitmap);

        return processedImages;
    }
    private Bitmap enhanceContrast(Bitmap src, float contrast) {
        Bitmap dest = Bitmap.createBitmap(
                src.getWidth(), src.getHeight(), src.getConfig());

        Canvas canvas = new Canvas(dest);
        Paint paint = new Paint();

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

    private Bitmap sharpenImage(Bitmap src) {
        Bitmap dest = Bitmap.createBitmap(
                src.getWidth(), src.getHeight(), src.getConfig());

        RenderScript rs = RenderScript.create(this);
        ScriptIntrinsicConvolve3x3 convolution =
                ScriptIntrinsicConvolve3x3.create(rs, Element.U8_4(rs));

        Allocation input = Allocation.createFromBitmap(rs, src);
        Allocation output = Allocation.createFromBitmap(rs, dest);

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

    private List<YoloV5Detector.Detection> mergeDetections(List<YoloV5Detector.Detection> allDetections) {
        Map<String, List<YoloV5Detector.Detection>> detectionsByClass = new HashMap<>();

        for (YoloV5Detector.Detection detection : allDetections) {
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

        List<YoloV5Detector.Detection> mergedDetections = new ArrayList<>();

        for (Map.Entry<String, List<YoloV5Detector.Detection>> entry : detectionsByClass.entrySet()) {
            List<YoloV5Detector.Detection> classDetections = entry.getValue();

            Collections.sort(classDetections, (d1, d2) ->
                    Float.compare(d2.getConfidence(), d1.getConfidence()));

            List<YoloV5Detector.Detection> keptDetections = new ArrayList<>();

            while (!classDetections.isEmpty()) {
                YoloV5Detector.Detection current = classDetections.remove(0);
                keptDetections.add(current);

                Iterator<YoloV5Detector.Detection> it = classDetections.iterator();
                while (it.hasNext()) {
                    YoloV5Detector.Detection detection = it.next();
                    float iou = calculateIoU(current.getBoundingBox(), detection.getBoundingBox());
                    if (iou > 0.45) {
                        it.remove();
                    }
                }
            }

            mergedDetections.addAll(keptDetections);
        }

        return mergedDetections;
    }

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

    private void showDetectionSelectionDialog(List<YoloV5Detector.Detection> detections) {
        if (detections.isEmpty()) return;

        Map<String, Float> uniqueItems = new HashMap<>();
        for (YoloV5Detector.Detection detection : detections) {
            String label = detection.getLabel();
            float confidence = detection.getConfidence();

            if (!uniqueItems.containsKey(label) || confidence > uniqueItems.get(label)) {
                uniqueItems.put(label, confidence);
            }
        }

        String[] items = uniqueItems.keySet().toArray(new String[0]);
        boolean[] checkedItems = new boolean[items.length];

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select detected items to add to pantry");

        builder.setMultiChoiceItems(items, checkedItems, (dialog, which, isChecked) -> {
            checkedItems[which] = isChecked;
        });

        builder.setPositiveButton("Add Selected", (dialog, which) -> {
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
                processMultipleRecognitionResults(selectedItems);
            }
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> {
            dialog.dismiss();
            finish();
        });

        builder.setCancelable(false);
        builder.show();
    }

    private void processMultipleRecognitionResults(List<String> recognizedItems) {
        if (recognizedItems.isEmpty()) {
            Toast.makeText(this, "No items selected", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        SharedPreferences prefs = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
        String email = prefs.getString("email", "");

        if (email.isEmpty()) {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        SupabaseHelper supabaseHelper = new SupabaseHelper(this);

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

                final int[] itemsProcessed = {0};
                final int[] successCount = {0};
                final StringBuilder failedItems = new StringBuilder();

                for (String item : recognizedItems) {
                    supabaseHelper.addItemToPantry(userId, item, new SupabaseHelper.SupabaseCallback<Boolean>() {
                        @Override
                        public void onSuccess(Boolean result) {
                            itemsProcessed[0]++;
                            if (result) successCount[0]++;

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

    private void showSingleDetectionDialog(String result) {
                            String itemName = result;
                            if (result.contains("(")) {
                                itemName = result.substring(0, result.indexOf("(")).trim();
                            }

                            final String finalItemName = itemName;

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
            SharedPreferences prefs = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
            String email = prefs.getString("email", "");

            if (!email.isEmpty()) {
                SupabaseHelper supabaseHelper = new SupabaseHelper(this);

                supabaseHelper.getUserId(email, new SupabaseHelper.SupabaseCallback<Integer>() {
                    @Override
                    public void onSuccess(Integer userId) {
                        if (userId != -1) {
                            supabaseHelper.addItemToPantry(userId, recognizedItem, new SupabaseHelper.SupabaseCallback<Boolean>() {
                                @Override
                                public void onSuccess(Boolean result) {
                                    runOnUiThread(() -> {
                                        Toast.makeText(CameraActivity.this,
                                            "Added " + recognizedItem + " to your pantry",
                                            Toast.LENGTH_SHORT).show();

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