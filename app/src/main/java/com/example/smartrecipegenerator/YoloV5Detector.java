package com.example.smartrecipegenerator;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.Log;

import org.tensorflow.lite.Interpreter;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

public class YoloV5Detector {
    private static final String TAG = "YoloV5Detector";
    private static final String MODEL_FILE = "food-ingredients-int8-640.tflite";
    private static final String LABEL_FILE = "labels.txt";

    private static final int INPUT_SIZE = 640;
    private static final int OUTPUT_BOX_COUNT = 25200;
    private static final int OUTPUT_CLASS_COUNT = 120;
    private static final int BOX_DIMENSION = 125;

    private static final float INPUT_SCALE = 0.003921568859368563f;
    private static final float OUTPUT_SCALE = 0.007875115610659122f;
    private static final int OUTPUT_ZERO_POINT = 3;

    private static final float CONFIDENCE_THRESHOLD = 0.25f;
    private static final float IOU_THRESHOLD = 0.4f;
    private static final float OBJECTNESS_THRESHOLD = 0.2f;

    private static final float HIGH_CONF_MIN_SIZE = 120.0f;
    private static final float MED_CONF_MIN_SIZE = 160.0f;
    private static final float LOW_CONF_MIN_SIZE = 200.0f;

    private static final float HIGH_CONF_LEVEL = 0.3f;
    private static final float MED_CONF_LEVEL = 0.2f;

    private final Context context;
    private Interpreter tflite;
    private List<String> labels;
    private ByteBuffer inputBuffer;
    private int[] intValues = new int[INPUT_SIZE * INPUT_SIZE];
    private ByteBuffer outputBuffer;

    private float letterboxScale = 1.0f;
    private float letterboxOffsetX = 0.0f;
    private float letterboxOffsetY = 0.0f;

    public YoloV5Detector(Context context) throws IOException {
        this.context = context;
        MappedByteBuffer modelBuffer = loadModelFile();
        Interpreter.Options options = new Interpreter.Options();
        options.setNumThreads(4);
        tflite = new Interpreter(modelBuffer, options);

        labels = loadLabels();
        Log.d(TAG, "Loaded " + labels.size() + " labels");

        int[] inputShape = tflite.getInputTensor(0).shape();
        int[] outputShape = tflite.getOutputTensor(0).shape();
        Log.d(TAG, "Model input shape: " + Arrays.toString(inputShape));
        Log.d(TAG, "Model output shape: " + Arrays.toString(outputShape));

        float inp_scale = tflite.getInputTensor(0).quantizationParams().getScale();
        int inp_zero_point = tflite.getInputTensor(0).quantizationParams().getZeroPoint();
        float oup_scale = tflite.getOutputTensor(0).quantizationParams().getScale();
        int oup_zero_point = tflite.getOutputTensor(0).quantizationParams().getZeroPoint();

        Log.d(TAG, String.format("Input quantization: scale=%.10f, zero_point=%d", inp_scale, inp_zero_point));
        Log.d(TAG, String.format("Output quantization: scale=%.10f, zero_point=%d", oup_scale, oup_zero_point));

        if (Math.abs(inp_scale - INPUT_SCALE) > 0.0001) {
            Log.w(TAG, "Input scale differs from default, using model value: " + inp_scale);
        }
        if (Math.abs(oup_scale - OUTPUT_SCALE) > 0.0001) {
            Log.w(TAG, "Output scale differs from default, using model value: " + oup_scale);
        }
        if (oup_zero_point != OUTPUT_ZERO_POINT) {
            Log.w(TAG, "Output zero point differs from default, using model value: " + oup_zero_point);
        }

        inputBuffer = ByteBuffer.allocateDirect(1 * INPUT_SIZE * INPUT_SIZE * 3 * 1);
        inputBuffer.order(ByteOrder.nativeOrder());

        outputBuffer = ByteBuffer.allocateDirect(1 * OUTPUT_BOX_COUNT * BOX_DIMENSION * 1);
        outputBuffer.order(ByteOrder.nativeOrder());

        Log.d(TAG, "YoloV5Detector initialized with " + labels.size() + " labels for " + INPUT_SIZE + "x" + INPUT_SIZE + " input");
    }

    private MappedByteBuffer loadModelFile() throws IOException {
        AssetFileDescriptor fileDescriptor = context.getAssets().openFd(MODEL_FILE);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    private List<String> loadLabels() throws IOException {
        List<String> labels = new ArrayList<>();
        BufferedReader reader = new BufferedReader(new InputStreamReader(context.getAssets().open(LABEL_FILE)));
        String line;
        while ((line = reader.readLine()) != null) {
            labels.add(line);
        }
        reader.close();
        return labels;
    }

    public List<Detection> detectObjects(Bitmap bitmap) {
        try {
            Log.d(TAG, "Original bitmap dimensions: " + bitmap.getWidth() + "x" + bitmap.getHeight());

            Bitmap squareBitmap = createSquareBitmap(bitmap);
            Log.d(TAG, "Square cropped bitmap size: " + squareBitmap.getWidth() + "x" + squareBitmap.getHeight());

            Bitmap enhancedBitmap = enhanceImage(squareBitmap);
            Log.d(TAG, "Enhanced bitmap for better detection");

            Bitmap resizedBitmap = Bitmap.createScaledBitmap(enhancedBitmap, INPUT_SIZE, INPUT_SIZE, true);
            Log.d(TAG, "Resized bitmap to model input size: " + resizedBitmap.getWidth() + "x" + resizedBitmap.getHeight());

            convertBitmapToByteBuffer(resizedBitmap);

            long startTime = System.currentTimeMillis();
            runInference();
            long endTime = System.currentTimeMillis();
            Log.d(TAG, "Model inference took " + (endTime - startTime) + "ms");

            List<Detection> detections = processResults();

            List<Detection> filteredDetections = applyNMS(detections);

            if (filteredDetections.size() < 3) {
                Log.d(TAG, "Few detections found, trying additional detection angles");
                List<Detection> additionalDetections = new ArrayList<>();

                Matrix rotateMatrix = new Matrix();
                rotateMatrix.postRotate(5);
                Bitmap rotatedBitmap = Bitmap.createBitmap(resizedBitmap, 0, 0,
                    resizedBitmap.getWidth(), resizedBitmap.getHeight(), rotateMatrix, true);
                convertBitmapToByteBuffer(rotatedBitmap);
                runInference();
                List<Detection> rotatedDetections = processResults();
                additionalDetections.addAll(rotatedDetections);

                rotateMatrix.reset();
                rotateMatrix.postRotate(-5);
                rotatedBitmap = Bitmap.createBitmap(resizedBitmap, 0, 0,
                    resizedBitmap.getWidth(), resizedBitmap.getHeight(), rotateMatrix, true);
                convertBitmapToByteBuffer(rotatedBitmap);
                runInference();
                rotatedDetections = processResults();
                additionalDetections.addAll(rotatedDetections);

                detections.addAll(additionalDetections);

                filteredDetections = applyNMS(detections);
                Log.d(TAG, "After additional detections: found " + filteredDetections.size() + " objects");
            }

            for (Detection detection : filteredDetections) {
                Log.d(TAG, "Final detection: " + detection.getLabel() +
                    " (confidence: " + detection.getConfidence() + ") at " +
                    detection.getBoundingBox().toString());
            }

            return filteredDetections;
        } catch (Exception e) {
            Log.e(TAG, "Error detecting objects", e);
            return new ArrayList<>();
        }
    }
    private Bitmap createSquareBitmap(Bitmap originalBitmap) {
        int width = originalBitmap.getWidth();
        int height = originalBitmap.getHeight();
        int size = Math.min(width, height);

        int x = (width - size) / 2;
        int y = (height - size) / 2;

        return Bitmap.createBitmap(originalBitmap, x, y, size, size);
    }

    private List<Detection> mapDetectionsToOriginalImage(List<Detection> detections,
                                                        int originalWidth, int originalHeight) {
        List<Detection> mappedDetections = new ArrayList<>();

        for (Detection detection : detections) {
            RectF modelSpaceBox = detection.getBoundingBox();

            float x1 = (modelSpaceBox.left - letterboxOffsetX) / letterboxScale;
            float y1 = (modelSpaceBox.top - letterboxOffsetY) / letterboxScale;
            float x2 = (modelSpaceBox.right - letterboxOffsetX) / letterboxScale;
            float y2 = (modelSpaceBox.bottom - letterboxOffsetY) / letterboxScale;

            x1 = Math.max(0, Math.min(originalWidth, x1));
            y1 = Math.max(0, Math.min(originalHeight, y1));
            x2 = Math.max(0, Math.min(originalWidth, x2));
            y2 = Math.max(0, Math.min(originalHeight, y2));

            RectF originalSpaceBox = new RectF(x1, y1, x2, y2);

            mappedDetections.add(new Detection(
                detection.getLabel(),
                detection.getConfidence(),
                originalSpaceBox,
                detection.getClassId()
            ));
        }

        return mappedDetections;
    }

    private Bitmap letterboxImage(Bitmap originalBitmap, int targetWidth, int targetHeight) {
        Bitmap letterboxedBitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(letterboxedBitmap);
        canvas.drawColor(Color.BLACK);

        float originalAspect = (float) originalBitmap.getWidth() / originalBitmap.getHeight();
        float targetAspect = (float) targetWidth / targetHeight;

        if (originalAspect > targetAspect) {
            letterboxScale = (float) targetWidth / originalBitmap.getWidth();
            letterboxOffsetX = 0;
            letterboxOffsetY = (targetHeight - originalBitmap.getHeight() * letterboxScale) / 2;
        } else {
            letterboxScale = (float) targetHeight / originalBitmap.getHeight();
            letterboxOffsetX = (targetWidth - originalBitmap.getWidth() * letterboxScale) / 2;
            letterboxOffsetY = 0;
        }

        Matrix matrix = new Matrix();
        matrix.setScale(letterboxScale, letterboxScale);
        matrix.postTranslate(letterboxOffsetX, letterboxOffsetY);

        canvas.drawBitmap(originalBitmap, matrix, null);

        Log.d(TAG, String.format("Letterboxing: scale=%.2f, offsetX=%.1f, offsetY=%.1f",
               letterboxScale, letterboxOffsetX, letterboxOffsetY));
        Log.d(TAG, String.format("Original: %dx%d (%f), Target: %dx%d (%f)",
               originalBitmap.getWidth(), originalBitmap.getHeight(), originalAspect,
               targetWidth, targetHeight, targetAspect));

        return letterboxedBitmap;
    }

    private Bitmap enhanceImage(Bitmap input) {
        Bitmap output = Bitmap.createBitmap(input.getWidth(), input.getHeight(), input.getConfig());

        Canvas canvas = new Canvas(output);
        Paint paint = new Paint();

        ColorMatrix colorMatrix = new ColorMatrix();

        colorMatrix.set(new float[] {
            1.2f, 0, 0, 0, -20,
            0, 1.2f, 0, 0, -20,
            0, 0, 1.2f, 0, -20,
            0, 0, 0, 1, 0
        });

        paint.setColorFilter(new ColorMatrixColorFilter(colorMatrix));

        canvas.drawBitmap(input, 0, 0, paint);

        return output;
    }

    private void convertBitmapToByteBuffer(Bitmap bitmap) {
        inputBuffer.rewind();

        bitmap.getPixels(intValues, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());

        Log.d(TAG, "Converting bitmap to input buffer");

        long pixelSum = 0;
        long pixelSqSum = 0;
        int pixelCount = INPUT_SIZE * INPUT_SIZE;

        for (int i = 0; i < pixelCount; i++) {
            int pixel = intValues[i];
            int r = (pixel >> 16) & 0xFF;
            int g = (pixel >> 8) & 0xFF;
            int b = pixel & 0xFF;
            int gray = (r + g + b) / 3;

            pixelSum += gray;
            pixelSqSum += gray * gray;
        }

        float mean = (float) pixelSum / pixelCount;
        float variance = ((float) pixelSqSum / pixelCount) - (mean * mean);
        float stdDev = (float) Math.sqrt(variance);

        Log.d(TAG, String.format("Image statistics: mean=%.2f, stdDev=%.2f", mean, stdDev));

        float enhanceContrastThreshold = 50;
        float foodSaturationBoost = 1.2f;

        float contrastEnhanceFactor = (stdDev < enhanceContrastThreshold) ?
            40 / Math.max(stdDev, 10) : 1.0f;

        for (int y = 0; y < INPUT_SIZE; y++) {
            for (int x = 0; x < INPUT_SIZE; x++) {
                int pixelValue = intValues[y * INPUT_SIZE + x];

                int r = (pixelValue >> 16) & 0xFF;
                int g = (pixelValue >> 8) & 0xFF;
                int b = pixelValue & 0xFF;

                if (stdDev < enhanceContrastThreshold) {
                    r = enhancePixel(r, mean, contrastEnhanceFactor);
                    g = enhancePixel(g, mean, contrastEnhanceFactor);
                    b = enhancePixel(b, mean, contrastEnhanceFactor);
                }

                float[] hsv = new float[3];
                Color.RGBToHSV(r, g, b, hsv);
                hsv[1] = Math.min(1.0f, hsv[1] * foodSaturationBoost);
                int enhancedColor = Color.HSVToColor(hsv);

                r = (enhancedColor >> 16) & 0xFF;
                g = (enhancedColor >> 8) & 0xFF;
                b = enhancedColor & 0xFF;

                inputBuffer.put((byte) r);
                inputBuffer.put((byte) g);
                inputBuffer.put((byte) b);
            }
        }

        Log.d(TAG, "Input buffer prepared with size: " + inputBuffer.capacity() + " bytes");
    }

    private int enhancePixel(int value, float mean, float factor) {
        float adjusted = mean + (value - mean) * factor;
        return Math.max(0, Math.min(255, Math.round(adjusted)));
    }

    private void runInference() {
        try {
            outputBuffer.rewind();

            Map<Integer, Object> outputs = new HashMap<>();
            outputs.put(0, outputBuffer);
            Object[] inputs = {inputBuffer};

            tflite.runForMultipleInputsOutputs(inputs, outputs);

            Log.d(TAG, "Model inference completed successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error running model inference", e);
        }
    }

    private List<Detection> processResults() {
        List<Detection> detectionList = new ArrayList<>();

        outputBuffer.rewind();

        Log.d(TAG, "Processing model output (total boxes: " + OUTPUT_BOX_COUNT + ")");

        outputBuffer.rewind();
        StringBuilder sb = new StringBuilder("Sample output values (first 20 bytes): ");
        for (int i = 0; i < 20 && outputBuffer.hasRemaining(); i++) {
            sb.append(outputBuffer.get() & 0xFF).append(" ");
        }
        Log.d(TAG, sb.toString());
        outputBuffer.rewind();

        int highObjectnessCount = 0;
        float highestObjectness = 0;
        float highestConfidence = 0;

        Map<Integer, Integer> classFrequency = new HashMap<>();
        Map<Integer, Float> classTotalConfidence = new HashMap<>();

        try {
            for (int i = 0; i < OUTPUT_BOX_COUNT; i++) {
                byte[] boxData = new byte[BOX_DIMENSION];
                outputBuffer.get(boxData);

                float[] dequantized = new float[BOX_DIMENSION];
                for (int j = 0; j < BOX_DIMENSION; j++) {
                    dequantized[j] = OUTPUT_SCALE * ((int) (boxData[j] & 0xFF) - OUTPUT_ZERO_POINT);
                }

                float objectness = dequantized[4];

                if (objectness < OBJECTNESS_THRESHOLD) continue;

                float x = dequantized[0] * INPUT_SIZE;
                float y = dequantized[1] * INPUT_SIZE;
                float w = dequantized[2] * INPUT_SIZE;
                float h = dequantized[3] * INPUT_SIZE;

                int[] topClasses = new int[3];
                float[] topScores = new float[3];

                for (int c = 0; c < OUTPUT_CLASS_COUNT; c++) {
                    float score = dequantized[5 + c];
                    for (int j = 0; j < 3; j++) {
                        if (score > topScores[j]) {
                            for (int k = 2; k > j; k--) {
                                topClasses[k] = topClasses[k-1];
                                topScores[k] = topScores[k-1];
                            }
                            topClasses[j] = c;
                            topScores[j] = score;
                            break;
                        }
                    }
                }

                if (topClasses[0] >= labels.size() || topScores[0] < 0.5) {
                    continue;
                }

                float classClarity = (topScores[0] - topScores[1]) / Math.max(0.1f, topScores[0]);
                float adjustedObjectness = objectness * (1.0f + 0.2f * classClarity);
                adjustedObjectness = Math.min(1.0f, adjustedObjectness);

                float confidence = adjustedObjectness * topScores[0];

                confidence = Math.min(1.0f, confidence);

                if (confidence < CONFIDENCE_THRESHOLD) {
                    continue;
                }

                if (adjustedObjectness > highestObjectness) {
                    highestObjectness = adjustedObjectness;
                }
                if (confidence > highestConfidence) {
                    highestConfidence = confidence;
                }
                if (adjustedObjectness > 0.1) {
                    highObjectnessCount++;
                }

                int detectedClass = topClasses[0];
                classFrequency.put(detectedClass, classFrequency.getOrDefault(detectedClass, 0) + 1);
                classTotalConfidence.put(detectedClass,
                    classTotalConfidence.getOrDefault(detectedClass, 0f) + confidence);

                float expansionFactor = 1.8f;

                float centerX = x;
                float centerY = y;

                w = w * expansionFactor;
                h = h * expansionFactor;

                float minWidth, minHeight;
                if (confidence > HIGH_CONF_LEVEL) {
                    minWidth = minHeight = HIGH_CONF_MIN_SIZE;
                } else if (confidence > MED_CONF_LEVEL) {
                    minWidth = minHeight = MED_CONF_MIN_SIZE;
                } else {
                    minWidth = minHeight = LOW_CONF_MIN_SIZE;
                }

                w = Math.max(w, minWidth);
                h = Math.max(h, minHeight);

                float maxSize = INPUT_SIZE * 0.8f;
                w = Math.min(w, maxSize);
                h = Math.min(h, maxSize);

                float x1 = Math.max(0, centerX - w / 2);
                float y1 = Math.max(0, centerY - h / 2);
                float x2 = Math.min(INPUT_SIZE - 1, centerX + w / 2);
                float y2 = Math.min(INPUT_SIZE - 1, centerY + h / 2);

                if (x2 <= x1 || y2 <= y1 || (x2 - x1) < 5 || (y2 - y1) < 5) {
                    float boxSize = LOW_CONF_MIN_SIZE;
                    x1 = Math.max(0, centerX - boxSize / 2);
                    y1 = Math.max(0, centerY - boxSize / 2);
                    x2 = Math.min(INPUT_SIZE - 1, centerX + boxSize / 2);
                    y2 = Math.min(INPUT_SIZE - 1, centerY + boxSize / 2);
                }

                if (x2 <= x1 || y2 <= y1) {
                    continue;
                }

                Log.d(TAG, String.format("Box %d: obj=%.4f, class=%d (%s), conf=%.4f, box=[%.1f,%.1f,%.1f,%.1f]",
                        i, adjustedObjectness, detectedClass,
                        labels.get(detectedClass),
                        confidence, x1, y1, x2, y2));

                RectF rect = new RectF(x1, y1, x2, y2);
                detectionList.add(new Detection(
                        labels.get(detectedClass),
                        confidence,
                        rect,
                        detectedClass));
            }

            if (!classFrequency.isEmpty()) {
                StringBuilder classStats = new StringBuilder("Class distribution: ");
                for (Map.Entry<Integer, Integer> entry : classFrequency.entrySet()) {
                    int classId = entry.getKey();
                    int count = entry.getValue();
                    float avgConfidence = classTotalConfidence.get(classId) / count;
                    classStats.append(String.format("%s(%d,%.2f) ",
                        labels.get(classId), count, avgConfidence));
                }
                Log.d(TAG, classStats.toString());
            }

        } catch (Exception e) {
            Log.e(TAG, "Error processing detection results", e);
        }

        Log.d(TAG, String.format("Debug stats: highestObjectness=%.4f, highestConfidence=%.4f, boxesWithHighObjectness=%d",
               highestObjectness, highestConfidence, highObjectnessCount));
        Log.d(TAG, "Found " + detectionList.size() + " objects above threshold " + CONFIDENCE_THRESHOLD);
        return detectionList;
    }

    private List<Detection> applyNMS(List<Detection> detections) {
        if (detections.size() <= 1) {
            return detections;
        }

        List<Detection> result = new ArrayList<>();

        HashMap<String, List<Detection>> detectionsByClass = new HashMap<>();

        for (Detection detection : detections) {
            if (!detectionsByClass.containsKey(detection.getLabel())) {
                detectionsByClass.put(detection.getLabel(), new ArrayList<>());
            }
            detectionsByClass.get(detection.getLabel()).add(detection);
        }

        for (Map.Entry<String, List<Detection>> entry : detectionsByClass.entrySet()) {
            String labelName = entry.getKey();
            List<Detection> labelDetections = entry.getValue();

            if (labelDetections.size() == 1) {
                result.add(labelDetections.get(0));
                continue;
            }

            PriorityQueue<Detection> priorityQueue = new PriorityQueue<>(
                    labelDetections.size(),
                    (d1, d2) -> Float.compare(d2.getConfidence(), d1.getConfidence()));

            priorityQueue.addAll(labelDetections);

            int maxDetectionsPerClass = 10;
            int classCount = 0;

            while (!priorityQueue.isEmpty() && classCount < maxDetectionsPerClass) {
                Detection topDetection = priorityQueue.poll();

                if (topDetection.getConfidence() > 0.6) {
                    result.add(topDetection);
                    classCount++;
                    continue;
                }

                result.add(topDetection);
                classCount++;

                PriorityQueue<Detection> newQueue = new PriorityQueue<>(priorityQueue);
                priorityQueue.clear();

                while (!newQueue.isEmpty()) {
                    Detection detection = newQueue.poll();
                    float iou = calculateIoU(topDetection.getBoundingBox(), detection.getBoundingBox());

                    float dynamicIouThreshold = IOU_THRESHOLD;
                    if (topDetection.getConfidence() > 0.7) {
                        dynamicIouThreshold = IOU_THRESHOLD - 0.05f;
                    } else if (topDetection.getConfidence() < 0.3) {
                        dynamicIouThreshold = IOU_THRESHOLD + 0.05f;
                    }

                    if (iou <= dynamicIouThreshold) {
                        priorityQueue.add(detection);
                    }
                }
            }
        }

        Log.d(TAG, "After NMS: " + result.size() + " detections kept");
        return result;
    }

    private float calculateIoU(RectF box1, RectF box2) {
        float areaBox1 = (box1.right - box1.left) * (box1.bottom - box1.top);
        float areaBox2 = (box2.right - box2.left) * (box2.bottom - box2.top);

        float intersectLeft = Math.max(box1.left, box2.left);
        float intersectTop = Math.max(box1.top, box2.top);
        float intersectRight = Math.min(box1.right, box2.right);
        float intersectBottom = Math.min(box1.bottom, box2.bottom);

        float intersectWidth = Math.max(0, intersectRight - intersectLeft);
        float intersectHeight = Math.max(0, intersectBottom - intersectTop);
        float intersectArea = intersectWidth * intersectHeight;

        return intersectArea / (areaBox1 + areaBox2 - intersectArea);
    }

    public void drawDetections(Canvas canvas, List<Detection> detections, float scaleFactorX, float scaleFactorY) {
        Paint boxPaint = new Paint();
        boxPaint.setStyle(Paint.Style.STROKE);
        boxPaint.setStrokeWidth(5);

        Paint textBackgroundPaint = new Paint();
        textBackgroundPaint.setStyle(Paint.Style.FILL);
        textBackgroundPaint.setColor(Color.BLACK);
        textBackgroundPaint.setAlpha(160);

        Paint textPaint = new Paint();
        textPaint.setStyle(Paint.Style.FILL);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(40);

        for (Detection detection : detections) {
            boxPaint.setColor(Color.GREEN);

            RectF scaledBox = new RectF(
                    detection.getBoundingBox().left * scaleFactorX,
                    detection.getBoundingBox().top * scaleFactorY,
                    detection.getBoundingBox().right * scaleFactorX,
                    detection.getBoundingBox().bottom * scaleFactorY);

            canvas.drawRect(scaledBox, boxPaint);

            String labelText = String.format("%s %.2f", detection.getLabel(), detection.getConfidence());
            float textWidth = textPaint.measureText(labelText);

            canvas.drawRect(
                    scaledBox.left,
                    scaledBox.top - 45,
                    scaledBox.left + textWidth + 10,
                    scaledBox.top,
                    textBackgroundPaint);

            canvas.drawText(labelText, scaledBox.left + 5, scaledBox.top - 10, textPaint);
        }
    }

    public static class Detection {
        private final String label;
        private final float confidence;
        private final RectF boundingBox;
        private final int classId;

        public Detection(String label, float confidence, RectF boundingBox, int classId) {
            this.label = label;
            this.confidence = confidence;
            this.boundingBox = boundingBox;
            this.classId = classId;
        }

        public String getLabel() {
            return label;
        }

        public float getConfidence() {
            return confidence;
        }

        public RectF getBoundingBox() {
            return boundingBox;
        }

        public int getClassId() {
            return classId;
        }
    }

    public void close() {
        if (tflite != null) {
            tflite.close();
            tflite = null;
        }
    }
}