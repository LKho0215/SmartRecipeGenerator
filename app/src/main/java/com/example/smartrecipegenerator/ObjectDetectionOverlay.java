package com.example.smartrecipegenerator;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

public class ObjectDetectionOverlay extends View {
    private Paint boxPaint;
    private Paint textPaint;
    private Paint bgPaint;
    private String classificationResult;
    private RectF centerBox;
    
    // 調整框的大小比例（0.8 表示佔螢幕寬度的 80%）
    private static final float BOX_SIZE_RATIO = 0.8f;

    public ObjectDetectionOverlay(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        boxPaint = new Paint();
        boxPaint.setColor(Color.GREEN);
        boxPaint.setStyle(Paint.Style.STROKE);
        boxPaint.setStrokeWidth(8.0f);  // 加粗邊框線條

        textPaint = new Paint();
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(60.0f);    // 加大文字大小
        textPaint.setStyle(Paint.Style.FILL);

        bgPaint = new Paint();
        bgPaint.setColor(Color.argb(160, 0, 0, 0));  // 調整背景透明度
        
        // 在初始化時就更新框的位置
        post(this::updateCenterBox);
    }

    public void setClassificationResult(String result) {
        this.classificationResult = result;
        invalidate();
    }

    private void updateCenterBox() {
        float centerX = getWidth() / 2f;
        float centerY = getHeight() / 2f;
        float boxSize = Math.min(getWidth(), getHeight()) * BOX_SIZE_RATIO;
        
        centerBox = new RectF(
            centerX - boxSize/2,
            centerY - boxSize/2,
            centerX + boxSize/2,
            centerY + boxSize/2
        );
        invalidate();  // 強制重繪
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        
        if (centerBox != null) {
            // 始終繪製邊框
            canvas.drawRect(centerBox, boxPaint);

            // 只有在有識別結果時才繪製文字
            if (classificationResult != null) {
                float textWidth = textPaint.measureText(classificationResult);
                float textHeight = 80f;  // 增加文字背景高度

                canvas.drawRect(
                    centerBox.left,
                    centerBox.top - textHeight,
                    centerBox.left + textWidth + 40,  // 增加文字左右間距
                    centerBox.top,
                    bgPaint
                );

                canvas.drawText(classificationResult, 
                    centerBox.left + 20,  // 增加文字左邊距
                    centerBox.top - textHeight/3,  // 調整文字垂直位置
                    textPaint
                );
            }
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        updateCenterBox();
    }
} 