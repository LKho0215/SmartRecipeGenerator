package com.example.smartrecipegenerator;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Build;

import org.tensorflow.lite.support.image.ImageProcessor;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.image.ops.ResizeOp;
import org.tensorflow.lite.task.core.BaseOptions;
import org.tensorflow.lite.task.vision.classifier.Classifications;
import org.tensorflow.lite.task.vision.classifier.ImageClassifier;

import java.io.IOException;
import java.util.List;

public class FruitClassifier {
    private static final String MODEL_PATH = "your_model.tflite";
    private ImageClassifier classifier;

    public FruitClassifier(Context context) throws IOException {
        // 创建分类器
        ImageClassifier.ImageClassifierOptions options = ImageClassifier.ImageClassifierOptions.builder()
                .setBaseOptions(BaseOptions.builder().build())
                .setMaxResults(3)  // 返回前3个最可能的结果
                .build();
        classifier = ImageClassifier.createFromFileAndOptions(
                context,
                MODEL_PATH,
                options);
    }

    public List<Classifications> classify(Bitmap image) {
        // 图像预处理
        ImageProcessor imageProcessor = new ImageProcessor.Builder()
                .add(new ResizeOp(224, 224, ResizeOp.ResizeMethod.BILINEAR))  // 调整为模型需要的尺寸
                .build();

        TensorImage tensorImage = new TensorImage();
        tensorImage.load(image);
        tensorImage = imageProcessor.process(tensorImage);

        // 执行分类
        return classifier.classify(tensorImage);
    }
}