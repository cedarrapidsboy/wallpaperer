package com.moosedrive.wallpaperer;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.net.Uri;
import android.provider.DocumentsContract;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class StorageUtils {

    public static Bitmap resizeBitmapFitXY(int width, int height, Bitmap bitmap) {
        Bitmap background = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        float originalWidth = bitmap.getWidth(), originalHeight = bitmap.getHeight();
        Canvas canvas = new Canvas(background);
        float scale, xTranslation = 0.0f, yTranslation = 0.0f;
        if (originalWidth > originalHeight) {
            scale = height / originalHeight;
            xTranslation = (width - originalWidth * scale) / 2.0f;
        } else {
            scale = width / originalWidth;
            yTranslation = (height - originalHeight * scale) / 2.0f;
        }
        Matrix transformation = new Matrix();
        transformation.postTranslate(xTranslation, yTranslation);
        transformation.preScale(scale, scale);
        Paint paint = new Paint();
        paint.setFilterBitmap(true);
        canvas.drawBitmap(bitmap, transformation, paint);
        return background;
    }

    @SuppressWarnings("unused")
    public static Bitmap resizeAspect(int maxWidth, int maxHeight, Bitmap image) {
        if (maxHeight > 0 && maxWidth > 0) {
            int width = image.getWidth();
            int height = image.getHeight();
            float ratioBitmap = (float) width / (float) height;
            float ratioMax = (float) maxWidth / (float) maxHeight;

            int finalWidth = maxWidth;
            int finalHeight = maxHeight;
            if (ratioMax > ratioBitmap) {
                finalWidth = (int) ((float) maxHeight * ratioBitmap);
            } else {
                finalHeight = (int) ((float) maxWidth / ratioBitmap);
            }
            image = Bitmap.createScaledBitmap(image, finalWidth, finalHeight, true);
        }
        return image;
    }

    public static String getSHA256(InputStream stream, int bufferSize) throws NoSuchAlgorithmException, IOException {
        final byte[] buffer = new byte[bufferSize];
        final MessageDigest digest = MessageDigest.getInstance("MD5");

        int bytesRead;
        while ((bytesRead = stream.read(buffer)) != -1) {
            digest.update(buffer, 0, bytesRead);
        }

        return new BigInteger(1, digest.digest()).toString(16);
    }

    public static String getHash(Context context, Uri uri) {
        InputStream source = null;
        try {
            source = context.getContentResolver().openInputStream(uri);
            return getSHA256(source, 1024);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        } finally {
            if (source != null)
                try {
                    source.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
        }
    }

    public static Uri saveBitmap(Context context, Uri sourceuri, long size, String destinationDir, String destFileName, boolean recompress) throws IOException {

        BufferedInputStream bis = null;
        BufferedOutputStream bos = null;
        InputStream input = null;

        try {

            boolean directorySetupResult;
            File destDir = new File(destinationDir);
            if (!destDir.exists()) {
                directorySetupResult = destDir.mkdirs();
            } else if (!destDir.isDirectory()) {
                directorySetupResult = replaceFileWithDir(destinationDir);
            } else {
                directorySetupResult = true;
            }
            if (directorySetupResult) {
                String destination = destinationDir + File.separator + destFileName;
                File destinationFile = null;
                if (recompress) {
                    input = context.getContentResolver().openInputStream(sourceuri);
                    bos = new BufferedOutputStream(new FileOutputStream(destination));
                    // Recompress before writing to new file
                    Bitmap originalBm = BitmapFactory.decodeStream(input);
                    originalBm.compress(Bitmap.CompressFormat.JPEG, 90, bos);
                    input.close();
                    bos.flush();
                    bos.close();
                    destinationFile = new File(destination);
                }
                // Copy the original if requested, or if the compressed version is bigger
                if (!recompress || (size > 0 && destinationFile.length() > size)) {
                    input = context.getContentResolver().openInputStream(sourceuri);
                    bos = new BufferedOutputStream(new FileOutputStream(destination));
                    // Write to new file unchanged
                    int originalsize = input.available();
                    bis = new BufferedInputStream(input);
                    byte[] buf = new byte[originalsize];
                    //bis.read(buf);
                    while (bis.read(buf) != -1) {
                        bos.write(buf);
                    }
                }
            }
        } finally {
            try {
                if (input != null)
                    input.close();
                if (bos != null) {
                    bos.flush();
                    bos.close();
                }
                if (bis != null)
                    bis.close();
            } catch (Exception ignored) {
            }
        }

        return Uri.fromFile(new File(destinationDir + File.separator + destFileName));
    }

    private static boolean replaceFileWithDir(String path) {
        File file = new File(path);
        if (!file.exists()) {
            return file.mkdirs();
        } else if (file.delete()) {
            File folder = new File(path);
            return folder.mkdirs();
        }
        return false;
    }

    public static long getFileSize(Uri uri) {
        File file = new File(uri.getPath());
        if (file.exists())
            return file.length();
        return 0;
    }

    public static String getFileAttrib(Uri uri, String column, Context context) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int i = cursor.getColumnIndex(column);
                    if (i >= 0)
                        result = cursor.getString(i);
                }
            }
        }
        if (result == null) {
            if (column.equals(DocumentsContract.Document.COLUMN_DISPLAY_NAME)) {
                result = uri.getPath();
                int cut = result.lastIndexOf('/');
                if (cut != -1) {
                    result = result.substring(cut + 1);
                }
            } else {
                result = "0";
            }
        }
        return result;
    }

    public static void cleanUpImage(String destinationDir, ImageObject img) {
        File destDir = new File(destinationDir);
        File[] ls = destDir.listFiles();
        if (ls != null) {
            for (File f : ls) {
                if (img.getName().equals(f.getName())) {
                    //noinspection ResultOfMethodCallIgnored
                    f.delete();
                }
            }
        }
    }

    public static void CleanUpOrphans(String destinationDir) {
        File destDir = new File(destinationDir);
        ImageStore is = ImageStore.getInstance();
        File[] ls = destDir.listFiles();
        if (ls != null) {
            for (File f : ls) {
                if (is.getImageObjectByName(f.getName()) == null) {
                    //noinspection ResultOfMethodCallIgnored
                    f.delete();
                }
            }
        }
    }
}
