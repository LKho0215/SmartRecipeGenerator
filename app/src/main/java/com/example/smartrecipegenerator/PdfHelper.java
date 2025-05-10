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
    private static final int PAGE_WIDTH = 595; // A4 width in points (72 points = 1 inch)
    private static final int PAGE_HEIGHT = 842; // A4 height in points
    private static final int MARGIN = 50;
    private static final int TITLE_TEXT_SIZE = 24;
    private static final int SUBTITLE_TEXT_SIZE = 16;
    private static final int CONTENT_TEXT_SIZE = 12;
    private static final int LINE_SPACING = 8;
    private static final int SECTION_SPACING = 20;
    private static final int MAX_IMAGE_WIDTH = PAGE_WIDTH - (MARGIN * 2);
    private static final int MAX_IMAGE_HEIGHT = 300;

    /**
     * Generate a PDF from recipe data and share it
     *
     * @param context The context
     * @param title The recipe title
     * @param htmlContent The recipe content in HTML format
     * @param base64Image The base64 encoded image (with or without data URL prefix)
     */
    public static void generateAndShareRecipePdf(Context context, String title, String htmlContent, String base64Image) {
        try {
            // Create PDF file in cache directory
            File pdfFile = new File(context.getCacheDir(), "recipe_" + System.currentTimeMillis() + ".pdf");

            // Convert HTML to plain text
            String plainText = Html.fromHtml(htmlContent, Html.FROM_HTML_MODE_COMPACT).toString();

            // Parse the content into sections
            List<RecipeSection> sections = parseRecipeSections(plainText, title);

            // Decode base64 image
            Bitmap image = null;
            if (base64Image != null && !base64Image.isEmpty()) {
                // Extract base64 data from data URL if present
                String base64Data = base64Image;
                if (base64Image.contains(",")) {
                    base64Data = base64Image.split(",")[1];
                }

                try {
                    byte[] decodedBytes = Base64.decode(base64Data, Base64.DEFAULT);
                    image = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);

                    // Scale the image to fit within the page while maintaining aspect ratio
                    if (image != null) {
                        image = scaleImage(image, MAX_IMAGE_WIDTH, MAX_IMAGE_HEIGHT);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error decoding image: " + e.getMessage());
                }
            }

            // Generate the PDF
            PdfDocument pdfDocument = new PdfDocument();

            // Set up paint objects for different text elements
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

            // Start creating the document
            int pageNumber = 1;
            PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create();
            PdfDocument.Page page = pdfDocument.startPage(pageInfo);
            Canvas canvas = page.getCanvas();

            int yOffset = MARGIN;
            boolean titleDrawn = false;

            // Draw title only on the first page
            canvas.drawText(title, PAGE_WIDTH / 2f, yOffset + TITLE_TEXT_SIZE, titlePaint);
            yOffset += TITLE_TEXT_SIZE + 20; // Space after title
            titleDrawn = true;

            // Draw image on the first page if available
            if (image != null) {
                float imageX = (PAGE_WIDTH - image.getWidth()) / 2f;
                canvas.drawBitmap(image, imageX, yOffset, null);
                yOffset += image.getHeight() + 20; // Space after image
            }

            // Draw each section
            for (RecipeSection section : sections) {
                // Check if we need to start a new page (if not enough space for section title + at least some content)
                int spaceNeeded = SUBTITLE_TEXT_SIZE + SECTION_SPACING;
                if (yOffset + spaceNeeded > PAGE_HEIGHT - MARGIN) {
                    pdfDocument.finishPage(page);
                    pageNumber++;
                    pageInfo = new PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create();
                    page = pdfDocument.startPage(pageInfo);
                    canvas = page.getCanvas();
                    yOffset = MARGIN;
                }

                // Draw section title if it's not the main title (avoid repetition)
                if (!section.title.trim().equalsIgnoreCase(title.trim())) {
                    // Handle potentially long section titles by wrapping them
                    TextPaint sectionTitlePaint = new TextPaint(subtitlePaint);

                    // Create text layout for section title to properly wrap long titles
                    StaticLayout titleLayout = StaticLayout.Builder.obtain(
                            section.title, 0, section.title.length(),
                            sectionTitlePaint, PAGE_WIDTH - (MARGIN * 2))
                            .setLineSpacing(LINE_SPACING, 1.0f)
                            .setIncludePad(false)
                            .build();

                    // Check if we need a new page for this title
                    if (yOffset + titleLayout.getHeight() + 10 > PAGE_HEIGHT - MARGIN) {
                        pdfDocument.finishPage(page);
                        pageNumber++;
                        pageInfo = new PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create();
                        page = pdfDocument.startPage(pageInfo);
                        canvas = page.getCanvas();
                        yOffset = MARGIN;
                    }

                    // Draw the wrapped section title
                    canvas.save();
                    canvas.translate(MARGIN, yOffset);
                    titleLayout.draw(canvas);
                    canvas.restore();

                    yOffset += titleLayout.getHeight() + 10;
                }

                // Calculate available height for content on this page
                int availableHeight = PAGE_HEIGHT - yOffset - MARGIN;

                // Prepare the text layout
                StaticLayout textLayout = StaticLayout.Builder.obtain(
                        section.content, 0, section.content.length(),
                        contentPaint, PAGE_WIDTH - (MARGIN * 2))
                        .setLineSpacing(LINE_SPACING, 1.0f)
                        .setIncludePad(false)
                        .build();

                // Check if content fits on current page
                if (textLayout.getHeight() <= availableHeight) {
                    // Content fits, draw it
                    canvas.save();
                    canvas.translate(MARGIN, yOffset);
                    textLayout.draw(canvas);
                    canvas.restore();

                    yOffset += textLayout.getHeight() + SECTION_SPACING;
                } else {
                    // Content doesn't fit, need to split across pages
                    int lineCount = textLayout.getLineCount();
                    int currentLine = 0;

                    while (currentLine < lineCount) {
                        // Find how many lines fit on the current page
                        int linesOnThisPage = 0;
                        int heightSoFar = 0;

                        while (currentLine + linesOnThisPage < lineCount &&
                               heightSoFar + textLayout.getLineBottom(currentLine + linesOnThisPage) -
                               textLayout.getLineTop(currentLine) <= availableHeight) {
                            linesOnThisPage++;
                            heightSoFar = textLayout.getLineBottom(currentLine + linesOnThisPage - 1) -
                                           textLayout.getLineTop(currentLine);
                        }

                        // Draw the lines that fit
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
                            // Just in case something went wrong and we couldn't fit any lines
                            currentLine++;
                        }

                        // Move to next page if there are more lines
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

            // Finish the last page
            pdfDocument.finishPage(page);

            // Write to file
            FileOutputStream fos = new FileOutputStream(pdfFile);
            pdfDocument.writeTo(fos);
            pdfDocument.close();
            fos.close();

            // Share the PDF
            sharePdf(context, pdfFile);

        } catch (IOException e) {
            Log.e(TAG, "Error generating PDF: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Parse the recipe text into sections
     */
    private static List<RecipeSection> parseRecipeSections(String text, String title) {
        List<RecipeSection> sections = new ArrayList<>();

        // Only use the most common and reliable section headers
        String[] commonSections = {"Ingredients", "Instructions", "Cooking Time", "Nutritional Information"};

        // Split text by newlines to get lines
        String[] lines = text.split("\n");

        StringBuilder currentContent = new StringBuilder();
        String currentTitle = "";
        boolean inSection = false;

        // Remove any duplicated titles at the beginning
        int startLine = 0;
        String titleLowerCase = title.toLowerCase().trim();

        // Skip any lines at the beginning that contain the title text
        while (startLine < lines.length &&
               (lines[startLine].trim().isEmpty() ||
                lines[startLine].toLowerCase().trim().contains(titleLowerCase))) {
            startLine++;
        }

        for (int i = startLine; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;

            // Skip any line that's identical to or very similar to the main title
            if (line.equalsIgnoreCase(title) ||
                (line.toLowerCase().contains(titleLowerCase) && line.length() < title.length() * 1.5)) {
                continue;
            }

            // Check if this line is a section title
            boolean isSectionTitle = false;

            // Check exact matches with common section titles
            for (String sectionName : commonSections) {
                if (line.toLowerCase().startsWith(sectionName.toLowerCase())) {
                    isSectionTitle = true;
                    break;
                }
            }

            if (isSectionTitle) {
                // Save previous section if it exists
                if (!currentTitle.isEmpty() && currentContent.length() > 0) {
                    sections.add(new RecipeSection(currentTitle, currentContent.toString().trim()));
                    currentContent = new StringBuilder();
                }
                currentTitle = line;
                inSection = true;
            } else {
                // If no title has been set yet, use the recipe title
                if (currentTitle.isEmpty()) {
                    currentTitle = title;
                }

                // Add to current content
                if (currentContent.length() > 0) {
                    currentContent.append("\n");
                }
                currentContent.append(line);
                inSection = true;
            }
        }

        // Add the last section
        if (!currentTitle.isEmpty() && currentContent.length() > 0) {
            sections.add(new RecipeSection(currentTitle, currentContent.toString().trim()));
        }

        // If no sections were identified, create a single section with the whole content
        if (sections.isEmpty()) {
            sections.add(new RecipeSection(title, text.trim()));
        }

        return sections;
    }

    /**
     * Share the generated PDF file
     *
     * @param context The context
     * @param pdfFile The PDF file to share
     */
    private static void sharePdf(Context context, File pdfFile) {
        // Get content URI using FileProvider
        Uri contentUri = FileProvider.getUriForFile(
                context,
                context.getPackageName() + ".fileprovider",
                pdfFile);

        // Create intent
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("application/pdf");
        shareIntent.putExtra(Intent.EXTRA_STREAM, contentUri);
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        // Start the sharing activity
        context.startActivity(Intent.createChooser(shareIntent, "Share Recipe PDF"));
    }

    /**
     * Scale a bitmap to fit within maxWidth and maxHeight while maintaining aspect ratio
     *
     * @param source The source bitmap
     * @param maxWidth Maximum width
     * @param maxHeight Maximum height
     * @return Scaled bitmap
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

    /**
     * Helper class to represent a section of the recipe
     */
    private static class RecipeSection {
        public String title;
        public String content;

        public RecipeSection(String title, String content) {
            this.title = title;
            this.content = content;
        }
    }
}