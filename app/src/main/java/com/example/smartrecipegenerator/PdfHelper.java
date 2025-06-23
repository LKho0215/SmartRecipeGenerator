package com.example.smartrecipegenerator;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.text.Html;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.Base64;
import android.util.Log;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PdfHelper {
    private static final String TAG = "PdfHelper";
    private static final int PAGE_WIDTH = 595;
    private static final int PAGE_HEIGHT = 842;
    private static final int MARGIN = 50;
    private static final int TITLE_TEXT_SIZE = 24;
    private static final int SUBTITLE_TEXT_SIZE = 16;
    private static final int CONTENT_TEXT_SIZE = 12;
    private static final int LINE_SPACING = 8;
    private static final int SECTION_SPACING = 20;
    private static final int MAX_IMAGE_WIDTH = PAGE_WIDTH - (MARGIN * 2);
    private static final int MAX_IMAGE_HEIGHT = 300;

    /**
     * @param context
     * @param title
     * @param htmlContent
     * @param base64Image
     */
    public static void generateAndShareRecipePdf(Context context, String title, String htmlContent, String base64Image) {
        try {
            String safeTitle = title.replaceAll("[^a-zA-Z0-9._-]", "_").trim();
            if (safeTitle.isEmpty()) {
                safeTitle = "recipe";
            }

            File pdfFile = new File(context.getCacheDir(), safeTitle + "_recipe.pdf");

            String plainText = Html.fromHtml(htmlContent, Html.FROM_HTML_MODE_COMPACT).toString();

            List<RecipeSection> sections = parseRecipeSections(plainText, title);

            Bitmap image = null;
            if (base64Image != null && !base64Image.isEmpty()) {
                String base64Data = base64Image;
                if (base64Image.contains(",")) {
                    base64Data = base64Image.split(",")[1];
                }

                try {
                    byte[] decodedBytes = Base64.decode(base64Data, Base64.DEFAULT);
                    image = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);

                    if (image != null) {
                        image = scaleImage(image, MAX_IMAGE_WIDTH, MAX_IMAGE_HEIGHT);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error decoding image: " + e.getMessage());
                }
            }

            PdfDocument pdfDocument = new PdfDocument();

            Paint titlePaint = new Paint();
            titlePaint.setColor(Color.BLACK);
            titlePaint.setTextSize(TITLE_TEXT_SIZE);
            titlePaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            titlePaint.setTextAlign(Paint.Align.CENTER);

            Paint subtitlePaint = new Paint();
            subtitlePaint.setColor(Color.rgb(33, 33, 33));
            subtitlePaint.setTextSize(SUBTITLE_TEXT_SIZE);
            subtitlePaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));

            TextPaint contentPaint = new TextPaint();
            contentPaint.setColor(Color.BLACK);
            contentPaint.setTextSize(CONTENT_TEXT_SIZE);

            int pageNumber = 1;
            PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create();
            PdfDocument.Page page = pdfDocument.startPage(pageInfo);
            Canvas canvas = page.getCanvas();

            int yOffset = MARGIN;
            boolean titleDrawn = false;

            canvas.drawText(title, PAGE_WIDTH / 2f, yOffset + TITLE_TEXT_SIZE, titlePaint);
            yOffset += TITLE_TEXT_SIZE + 20;
            titleDrawn = true;

            if (image != null) {
                float imageX = (PAGE_WIDTH - image.getWidth()) / 2f;
                canvas.drawBitmap(image, imageX, yOffset, null);
                yOffset += image.getHeight() + 20;
            }

            for (RecipeSection section : sections) {
                int spaceNeeded = SUBTITLE_TEXT_SIZE + SECTION_SPACING;
                if (yOffset + spaceNeeded > PAGE_HEIGHT - MARGIN) {
                    pdfDocument.finishPage(page);
                    pageNumber++;
                    pageInfo = new PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create();
                    page = pdfDocument.startPage(pageInfo);
                    canvas = page.getCanvas();
                    yOffset = MARGIN;
                }

                if (!section.title.trim().equalsIgnoreCase(title.trim())) {
                    TextPaint sectionTitlePaint = new TextPaint(subtitlePaint);

                    StaticLayout titleLayout = StaticLayout.Builder.obtain(
                            section.title, 0, section.title.length(),
                            sectionTitlePaint, PAGE_WIDTH - (MARGIN * 2))
                            .setLineSpacing(LINE_SPACING, 1.0f)
                            .setIncludePad(false)
                            .build();

                    if (yOffset + titleLayout.getHeight() + 10 > PAGE_HEIGHT - MARGIN) {
                        pdfDocument.finishPage(page);
                        pageNumber++;
                        pageInfo = new PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create();
                        page = pdfDocument.startPage(pageInfo);
                        canvas = page.getCanvas();
                        yOffset = MARGIN;
                    }

                    canvas.save();
                    canvas.translate(MARGIN, yOffset);
                    titleLayout.draw(canvas);
                    canvas.restore();

                    yOffset += titleLayout.getHeight() + 10;
                }

                int availableHeight = PAGE_HEIGHT - yOffset - MARGIN;

                StaticLayout textLayout = StaticLayout.Builder.obtain(
                        section.content, 0, section.content.length(),
                        contentPaint, PAGE_WIDTH - (MARGIN * 2))
                        .setLineSpacing(LINE_SPACING, 1.0f)
                        .setIncludePad(false)
                        .build();

                if (textLayout.getHeight() <= availableHeight) {
                    canvas.save();
                    canvas.translate(MARGIN, yOffset);
                    textLayout.draw(canvas);
                    canvas.restore();

                    yOffset += textLayout.getHeight() + SECTION_SPACING;
                } else {
                    int lineCount = textLayout.getLineCount();
                    int currentLine = 0;

                    while (currentLine < lineCount) {
                        int linesOnThisPage = 0;
                        int heightSoFar = 0;

                        while (currentLine + linesOnThisPage < lineCount &&
                               heightSoFar + textLayout.getLineBottom(currentLine + linesOnThisPage) -
                               textLayout.getLineTop(currentLine) <= availableHeight) {
                            linesOnThisPage++;
                            heightSoFar = textLayout.getLineBottom(currentLine + linesOnThisPage - 1) -
                                           textLayout.getLineTop(currentLine);
                        }

                        if (linesOnThisPage > 0) {
                            int startOffset = textLayout.getLineStart(currentLine);
                            int endOffset = textLayout.getLineEnd(currentLine + linesOnThisPage - 1);

                            StaticLayout partialLayout = StaticLayout.Builder.obtain(
                                    section.content, startOffset, endOffset,
                                    contentPaint, PAGE_WIDTH - (MARGIN * 2))
                                    .setLineSpacing(LINE_SPACING, 1.0f)
                                    .setIncludePad(false)
                                    .build();

                            canvas.save();
                            canvas.translate(MARGIN, yOffset);
                            partialLayout.draw(canvas);
                            canvas.restore();

                            currentLine += linesOnThisPage;
                        } else {
                            currentLine++;
                        }

                        if (currentLine < lineCount) {
                            pdfDocument.finishPage(page);
                            pageNumber++;
                            pageInfo = new PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create();
                            page = pdfDocument.startPage(pageInfo);
                            canvas = page.getCanvas();
                            yOffset = MARGIN;
                            availableHeight = PAGE_HEIGHT - yOffset - MARGIN;
                        }
                    }

                    yOffset += SECTION_SPACING;
                }
            }

            pdfDocument.finishPage(page);

            FileOutputStream fos = new FileOutputStream(pdfFile);
            pdfDocument.writeTo(fos);
            pdfDocument.close();
            fos.close();

            sharePdf(context, pdfFile);

        } catch (IOException e) {
            Log.e(TAG, "Error generating PDF: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static List<RecipeSection> parseRecipeSections(String text, String title) {
        List<RecipeSection> sections = new ArrayList<>();

        String[] commonSections = {"Ingredients", "Instructions", "Cooking Time", "Nutritional Information"};

        String[] lines = text.split("\n");

        StringBuilder currentContent = new StringBuilder();
        String currentTitle = "";
        boolean inSection = false;

        int startLine = 0;
        String titleLowerCase = title.toLowerCase().trim();

        while (startLine < lines.length &&
               (lines[startLine].trim().isEmpty() ||
                lines[startLine].toLowerCase().trim().contains(titleLowerCase))) {
            startLine++;
        }

        for (int i = startLine; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;

            if (line.equalsIgnoreCase(title) ||
                (line.toLowerCase().contains(titleLowerCase) && line.length() < title.length() * 1.5)) {
                continue;
            }

            boolean isSectionTitle = false;

            for (String sectionName : commonSections) {
                if (line.toLowerCase().startsWith(sectionName.toLowerCase())) {
                    isSectionTitle = true;
                    break;
                }
            }

            if (isSectionTitle) {
                if (!currentTitle.isEmpty() && currentContent.length() > 0) {
                    sections.add(new RecipeSection(currentTitle, currentContent.toString().trim()));
                    currentContent = new StringBuilder();
                }
                currentTitle = line;
                inSection = true;
            } else {
                if (currentTitle.isEmpty()) {
                    currentTitle = title;
                }

                if (currentContent.length() > 0) {
                    currentContent.append("\n");
                }
                currentContent.append(line);
                inSection = true;
            }
        }

        if (!currentTitle.isEmpty() && currentContent.length() > 0) {
            sections.add(new RecipeSection(currentTitle, currentContent.toString().trim()));
        }

        if (sections.isEmpty()) {
            sections.add(new RecipeSection(title, text.trim()));
        }

        return sections;
    }

    /**
     *
     * @param context
     * @param pdfFile
     */
    private static void sharePdf(Context context, File pdfFile) {
        Uri contentUri = FileProvider.getUriForFile(
                context,
                context.getPackageName() + ".fileprovider",
                pdfFile);

        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("application/pdf");
        shareIntent.putExtra(Intent.EXTRA_STREAM, contentUri);
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        context.startActivity(Intent.createChooser(shareIntent, "Share Recipe PDF"));
    }

    /**
     *
     * @param source
     * @param maxWidth
     * @param maxHeight
     * @return
     */
    private static Bitmap scaleImage(Bitmap source, int maxWidth, int maxHeight) {
        int width = source.getWidth();
        int height = source.getHeight();

        float ratioX = (float) maxWidth / width;
        float ratioY = (float) maxHeight / height;
        float ratio = Math.min(ratioX, ratioY);

        int newWidth = (int) (width * ratio);
        int newHeight = (int) (height * ratio);

        return Bitmap.createScaledBitmap(source, newWidth, newHeight, true);
    }

    private static class RecipeSection {
        public String title;
        public String content;

        public RecipeSection(String title, String content) {
            this.title = title;
            this.content = content;
        }
    }
}