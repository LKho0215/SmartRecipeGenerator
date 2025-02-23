package com.example.smartrecipegenerator;

import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.Image;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

public class ImageUtils {
    public static void yuv420toNV21(Image image, int[] argb) {
        final int width = image.getWidth();
        final int height = image.getHeight();
        final byte[][] planes = new byte[3][];
        for (int i = 0; i < planes.length; i++) {
            planes[i] = new byte[image.getPlanes()[i].getBuffer().remaining()];
            image.getPlanes()[i].getBuffer().get(planes[i]);
        }

        int yRowStride = image.getPlanes()[0].getRowStride();
        int uvRowStride = image.getPlanes()[1].getRowStride();
        int uvPixelStride = image.getPlanes()[1].getPixelStride();

        int pos = 0;
        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                int y = planes[0][i * yRowStride + j] & 0xff;
                int u = planes[1][((i >> 1) * uvRowStride) + ((j >> 1) * uvPixelStride)] & 0xff;
                int v = planes[2][((i >> 1) * uvRowStride) + ((j >> 1) * uvPixelStride)] & 0xff;
                y = y < 16 ? 16 : y;

                int r = (int) (1.164f * (y - 16) + 1.596f * (v - 128));
                int g = (int) (1.164f * (y - 16) - 0.813f * (v - 128) - 0.391f * (u - 128));
                int b = (int) (1.164f * (y - 16) + 2.018f * (u - 128));

                r = r < 0 ? 0 : (r > 255 ? 255 : r);
                g = g < 0 ? 0 : (g > 255 ? 255 : g);
                b = b < 0 ? 0 : (b > 255 ? 255 : b);

                argb[pos++] = 0xff000000 | (r << 16) | (g << 8) | b;
            }
        }
    }

    public static Bitmap mediaImageToBitmap(Image image) {
        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
        byte[] bytes = new byte[buffer.capacity()];
        buffer.get(bytes);
        
        YuvImage yuvImage = new YuvImage(bytes, ImageFormat.NV21, 
            image.getWidth(), image.getHeight(), null);
        
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        yuvImage.compressToJpeg(
            new Rect(0, 0, yuvImage.getWidth(), yuvImage.getHeight()), 
            100, out);
        
        byte[] imageBytes = out.toByteArray();
        return android.graphics.BitmapFactory.decodeByteArray(
            imageBytes, 0, imageBytes.length);
    }
} 