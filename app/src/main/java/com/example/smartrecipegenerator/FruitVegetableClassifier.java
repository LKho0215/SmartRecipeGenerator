package com.example.smartrecipegenerator;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.support.common.FileUtil;
import org.tensorflow.lite.support.common.TensorOperator;
import org.tensorflow.lite.support.image.ImageProcessor;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.image.ops.ResizeOp;
import org.tensorflow.lite.support.common.ops.NormalizeOp;
import org.tensorflow.lite.support.label.TensorLabel;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;

import java.io.File;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.util.List;
import java.util.Map;

public class FruitVegetableClassifier {
    private static final String TAG = "FruitVegClassifier";
    private static final String MODEL_PATH = "fruit_vegetable_model.tflite";
    private static final String LABEL_PATH = "labels.txt";
    private static final int IMAGE_SIZE = 224;
    private static final float[] NORMALIZE_MEAN = new float[] {127.5f, 127.5f, 127.5f};
    private static final float[] NORMALIZE_STD = new float[] {127.5f, 127.5f, 127.5f};

    private final Context context;
    private Interpreter tflite;
    private List<String> labels;
    private final ImageProcessor imageProcessor;
    private TensorBuffer outputBuffer;

    public FruitVegetableClassifier(Context context) {
        this.context = context;
        try {
            MappedByteBuffer tfliteModel = FileUtil.loadMappedFile(context, MODEL_PATH);
            tflite = new Interpreter(tfliteModel);
            labels = FileUtil.loadLabels(context, LABEL_PATH);

            imageProcessor = new ImageProcessor.Builder()
                .add(new ResizeOp(IMAGE_SIZE, IMAGE_SIZE, ResizeOp.ResizeMethod.BILINEAR))
                .add(new NormalizeOp(NORMALIZE_MEAN, NORMALIZE_STD))
                .build();

            int[] shape = tflite.getOutputTensor(0).shape();
            outputBuffer = TensorBuffer.createFixedSize(shape, tflite.getOutputTensor(0).dataType());

        } catch (IOException e) {
            Log.e(TAG, "Error loading model or labels: ", e);
            throw new RuntimeException("Unable to load model or labels: " + e.getMessage());
        }
    }

    public String classifyImage(File imageFile) {
        try {
            Bitmap bitmap = android.graphics.BitmapFactory.decodeFile(imageFile.getAbsolutePath());
            if (bitmap == null) return null;

            TensorImage tensorImage = new TensorImage(tflite.getInputTensor(0).dataType());
            tensorImage.load(bitmap);
            tensorImage = imageProcessor.process(tensorImage);

            tflite.run(tensorImage.getBuffer(), outputBuffer.getBuffer());

            Map<String, Float> labeledProbability =
                new TensorLabel(labels, outputBuffer).getMapWithFloatValue();

            float maxProb = 0;
            String bestLabel = null;
            for (Map.Entry<String, Float> entry : labeledProbability.entrySet()) {
                if (entry.getValue() > maxProb) {
                    maxProb = entry.getValue();
                    bestLabel = entry.getKey();
                }
            }

            if (bestLabel != null) {
                return bestLabel + " (" + String.format("%.1f", maxProb * 100) + "%)";
            }
            return null;

        } catch (Exception e) {
            Log.e(TAG, "Error classifying image: ", e);
            return null;
        }
    }

    public void close() {
        if (tflite != null) {
            tflite.close();
            tflite = null;
        }
    }
}