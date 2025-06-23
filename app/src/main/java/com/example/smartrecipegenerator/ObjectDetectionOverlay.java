package com.example.smartrecipegenerator;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.DashPathEffect;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import java.util.ArrayList;
import java.util.List;

public class ObjectDetectionOverlay extends View {
    private static final String TAG = "ObjectDetectionOverlay";
    private Paint boxPaint;
    private Paint textPaint;
    private Paint bgPaint;
    private Paint activeAreaPaint;
    private Paint outsideAreaPaint;
    private String classificationResult;
    private RectF centerBox;

    private List<YoloV5Detector.Detection> detections = new ArrayList<>();
    private YoloV5Detector.Detection selectedDetection = null;
    private DetectionSelectionListener selectionListener;

    private static final int MODEL_INPUT_SIZE = 640;

    public ObjectDetectionOverlay(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        boxPaint = new Paint();
        boxPaint.setColor(Color.GREEN);
        boxPaint.setStyle(Paint.Style.STROKE);
        boxPaint.setStrokeWidth(8.0f);

        textPaint = new Paint();
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(60.0f);
        textPaint.setStyle(Paint.Style.FILL);

        bgPaint = new Paint();
        bgPaint.setColor(Color.argb(160, 0, 0, 0));

        activeAreaPaint = new Paint();
        activeAreaPaint.setColor(Color.WHITE);
        activeAreaPaint.setStyle(Paint.Style.STROKE);
        activeAreaPaint.setStrokeWidth(4.0f);
        activeAreaPaint.setPathEffect(new DashPathEffect(new float[]{10, 10}, 0));
        activeAreaPaint.setAlpha(180);

        outsideAreaPaint = new Paint();
        outsideAreaPaint.setColor(Color.BLACK);
        outsideAreaPaint.setStyle(Paint.Style.FILL);
        outsideAreaPaint.setAlpha(0);
    }

    public void setClassificationResult(String result) {
        this.classificationResult = result;
        invalidate();
    }

    public void setDetections(List<YoloV5Detector.Detection> detections, float scaleFactorX, float scaleFactorY) {
        this.detections = detections;

        if (!detections.isEmpty()) {
            Log.d(TAG, "Setting " + detections.size() + " detections");
            Log.d(TAG, "View dimensions: " + getWidth() + "x" + getHeight());
            Log.d(TAG, "Device orientation: " + (getHeight() > getWidth() ? "portrait" : "landscape"));

            for (int i = 0; i < detections.size(); i++) {
                YoloV5Detector.Detection d = detections.get(i);
                RectF box = d.getBoundingBox();
                Log.d(TAG, String.format("Detection %d - %s (%.0f%%) - Model space: [%.1f,%.1f,%.1f,%.1f]",
                      i, d.getLabel(), d.getConfidence() * 100,
                      box.left, box.top, box.right, box.bottom));
            }
        }

        invalidate();
    }

    public void setDetectionSelectionListener(DetectionSelectionListener listener) {
        this.selectionListener = listener;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);


        if (detections != null && !detections.isEmpty()) {
            drawDetections(canvas);
        } else if (centerBox != null && classificationResult != null) {
            canvas.drawRect(centerBox, boxPaint);

            if (classificationResult != null) {
                float textWidth = textPaint.measureText(classificationResult);
                float textHeight = 80f;

                canvas.drawRect(
                    centerBox.left,
                    centerBox.top - textHeight,
                    centerBox.left + textWidth + 40,
                    centerBox.top,
                    bgPaint
                );

                canvas.drawText(classificationResult,
                    centerBox.left + 20,
                    centerBox.top - textHeight/3,
                    textPaint
                );
            }
        }
    }

    private void drawDetections(Canvas canvas) {
        Paint selectedBoxPaint = new Paint(boxPaint);
        selectedBoxPaint.setColor(Color.YELLOW);
        selectedBoxPaint.setStrokeWidth(12.0f);

        int[] classColors = {
            Color.GREEN, Color.RED, Color.BLUE, Color.MAGENTA,
            Color.CYAN, Color.YELLOW, Color.rgb(255, 165, 0)
        };

        float scaleX = (float) getWidth() / MODEL_INPUT_SIZE;
        float scaleY = (float) getHeight() / MODEL_INPUT_SIZE;

        Log.d(TAG, String.format("Drawing detections - Model space is %dx%d, ViewSize is %dx%d, Scale: %.2fx%.2f",
              MODEL_INPUT_SIZE, MODEL_INPUT_SIZE, getWidth(), getHeight(), scaleX, scaleY));

        for (YoloV5Detector.Detection detection : detections) {
            Paint currentBoxPaint = new Paint(boxPaint);
            if (detection == selectedDetection) {
                currentBoxPaint = selectedBoxPaint;
            } else {
                int colorIndex = detection.getClassId() % classColors.length;
                currentBoxPaint.setColor(classColors[colorIndex]);
                currentBoxPaint.setStrokeWidth(8.0f);
            }

            RectF box = detection.getBoundingBox();

            RectF scaledBox;

            boolean isPortrait = getHeight() > getWidth();

            if (isPortrait) {
                float centerOffsetX = getWidth() / 2;
                float centerOffsetY = getHeight() / 2;

                float normalizedCenterX = ((box.left + box.right) / 2 - 320) / 320;
                float normalizedCenterY = ((box.top + box.bottom) / 2 - 320) / 320;

                float boxWidth = (box.right - box.left) * scaleX;
                float boxHeight = (box.bottom - box.top) * scaleY;

                float mappedCenterX = centerOffsetX + (normalizedCenterX * centerOffsetX * 0.7f);
                float mappedCenterY = centerOffsetY + (normalizedCenterY * centerOffsetY * 0.7f);

                scaledBox = new RectF(
                    mappedCenterX - (boxWidth / 2),
                    mappedCenterY - (boxHeight / 2),
                    mappedCenterX + (boxWidth / 2),
                    mappedCenterY + (boxHeight / 2)
                );
            } else {
                scaledBox = new RectF(
                    (box.left * scaleX),
                    (box.top * scaleY),
                    (box.right * scaleX),
                    (box.bottom * scaleY)
                );
            }

            Log.d(TAG, "Drawing " + detection.getLabel() +
                  " model coordinates: " + box.toString() +
                  " view coordinates: " + scaledBox.toString() +
                  " orientation: " + (isPortrait ? "portrait" : "landscape"));

            Paint shadowPaint = new Paint(currentBoxPaint);
            shadowPaint.setColor(Color.BLACK);
            shadowPaint.setAlpha(100);
            shadowPaint.setStrokeWidth(currentBoxPaint.getStrokeWidth() + 8);
            canvas.drawRect(scaledBox, shadowPaint);

            canvas.drawRect(scaledBox, currentBoxPaint);

            Paint fillPaint = new Paint();
            fillPaint.setStyle(Paint.Style.FILL);
            fillPaint.setColor(currentBoxPaint.getColor());
            fillPaint.setAlpha(60);
            canvas.drawRect(scaledBox, fillPaint);

            String labelText = String.format("%s %.0f%%", detection.getLabel(), detection.getConfidence() * 100);
            float textWidth = textPaint.measureText(labelText);

            Paint labelBgPaint = new Paint(bgPaint);
            labelBgPaint.setColor(currentBoxPaint.getColor());
            labelBgPaint.setAlpha(200);

            canvas.drawRect(
                scaledBox.left,
                scaledBox.top - 70,
                scaledBox.left + textWidth + 20,
                scaledBox.top,
                labelBgPaint
            );

            Paint textShadowPaint = new Paint(textPaint);
            textShadowPaint.setColor(Color.BLACK);
            textShadowPaint.setAlpha(180);
            canvas.drawText(labelText, scaledBox.left + 11, scaledBox.top - 19, textShadowPaint);

            canvas.drawText(labelText, scaledBox.left + 10, scaledBox.top - 20, textPaint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN && detections != null && !detections.isEmpty()) {
            float touchX = event.getX();
            float touchY = event.getY();

            YoloV5Detector.Detection previousSelection = selectedDetection;
            selectedDetection = null;

            float scaleX = (float) getWidth() / MODEL_INPUT_SIZE;
            float scaleY = (float) getHeight() / MODEL_INPUT_SIZE;

            for (YoloV5Detector.Detection detection : detections) {
                RectF box = detection.getBoundingBox();

                RectF scaledBox;
                boolean isPortrait = getHeight() > getWidth();

                if (isPortrait) {
                    float centerOffsetX = getWidth() / 2;
                    float centerOffsetY = getHeight() / 2;

                    float normalizedCenterX = ((box.left + box.right) / 2 - 320) / 320;
                    float normalizedCenterY = ((box.top + box.bottom) / 2 - 320) / 320;

                    float boxWidth = (box.right - box.left) * scaleX;
                    float boxHeight = (box.bottom - box.top) * scaleY;

                    float mappedCenterX = centerOffsetX + (normalizedCenterX * centerOffsetX * 0.7f);
                    float mappedCenterY = centerOffsetY + (normalizedCenterY * centerOffsetY * 0.7f);

                    scaledBox = new RectF(
                        mappedCenterX - (boxWidth / 2),
                        mappedCenterY - (boxHeight / 2),
                        mappedCenterX + (boxWidth / 2),
                        mappedCenterY + (boxHeight / 2)
                    );
                } else {
                    scaledBox = new RectF(
                        (box.left * scaleX),
                        (box.top * scaleY),
                        (box.right * scaleX),
                        (box.bottom * scaleY)
                    );
                }

                if (scaledBox.contains(touchX, touchY)) {
                    selectedDetection = detection;
                    break;
                }
            }

            if (selectedDetection != previousSelection && selectionListener != null) {
                selectionListener.onDetectionSelected(selectedDetection);
            }

            invalidate();
            return true;
        }
        return super.onTouchEvent(event);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
    }

    public interface DetectionSelectionListener {
        void onDetectionSelected(YoloV5Detector.Detection detection);
    }
}