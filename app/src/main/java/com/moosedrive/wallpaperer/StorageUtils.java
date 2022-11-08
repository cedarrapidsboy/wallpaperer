package com.moosedrive.wallpaperer;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;

import androidx.exifinterface.media.ExifInterface;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public class StorageUtils {

    private static final String THUMBDIR = "thumbs";

    public static Bitmap resizeBitmapCenter(int newWidth, int newHeight, Bitmap source, boolean crop) {
        int sourceWidth = source.getWidth();
        int sourceHeight = source.getHeight();

        // Compute the scaling factors to fit the new height and width, respectively.
        // To cover the final image, the final scaling will be the bigger
        // of these two.
        float xScale = (float) newWidth / sourceWidth;
        float yScale = (float) newHeight / sourceHeight;
        float scale = (crop) ? Math.max(xScale, yScale) : Math.min(xScale, yScale);

        // Now get the size of the source bitmap when scaled
        float scaledWidth = scale * sourceWidth;
        float scaledHeight = scale * sourceHeight;

        // Let's find out the upper left coordinates if the scaled bitmap
        // should be centered in the new size give by the parameters
        float left = (newWidth - scaledWidth) / 2;
        float top = (newHeight - scaledHeight) / 2;

        // The target rectangle for the new, scaled version of the source bitmap will now
        // be
        RectF targetRect = new RectF(left, top, left + scaledWidth, top + scaledHeight);

        // Finally, we create a new bitmap of the specified size and draw our new,
        // scaled bitmap onto it.
        Bitmap dest = Bitmap.createBitmap(newWidth, newHeight, source.getConfig());
        Canvas canvas = new Canvas(dest);
        canvas.drawBitmap(source, null, targetRect, null);

        return dest;
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
        try (InputStream source = context.getContentResolver().openInputStream(uri)){

            return getSHA256(source, 1024);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static File getStorageFolder(Context context) {
        String sPictureStorage = context.getFilesDir().getPath();
        return new File(sPictureStorage);
    }

    public static Uri getThumbnailUri(Context context, ImageObject imgObj) {
        @SuppressWarnings("ConstantConditions") File thumbnailFile = new File(new File(imgObj.getUri().getPath()).getParentFile().getPath() + File.separator + THUMBDIR + File.separator + imgObj.getId());
        if (!thumbnailFile.exists()) {
            try {
                Uri newThumbUri = saveThumbnail(context, imgObj.getUri(), imgObj.getId());
                if (newThumbUri != null)
                    thumbnailFile = new File(newThumbUri.getPath());
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
        }
        return Uri.fromFile(thumbnailFile);
    }

    public static Uri saveThumbnail(Context context, Uri sourceuri, String destFileName) throws IOException {
        String destinationDir = getStorageFolder(context).getPath() + File.separator + THUMBDIR;
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
                File destinationFile = new File(destination);

                try (InputStream input = context.getContentResolver().openInputStream(sourceuri);
                BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(destination))) {
                    // Recompress before writing to new file
                    Bitmap originalBm = BitmapFactory.decodeStream(input);
                    originalBm = resizeBitmapCenter(512, 512, originalBm, true);
                    originalBm.compress(Bitmap.CompressFormat.WEBP, 50, bos);
                    return Uri.fromFile(destinationFile);
                }

            }
        return null;
    }

    public static Uri saveBitmap(Context context, Uri sourceUri, long maxSizeCompressed, String destinationDir, String destFileName, boolean recompress) throws IOException {
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
                try (InputStream input = context.getContentResolver().openInputStream(sourceUri);
                     BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(destination))) {
                    // Recompress before writing to new file
                    Bitmap originalBm = BitmapFactory.decodeStream(input);
                    originalBm.compress(Bitmap.CompressFormat.WEBP, 75, bos);
                    destinationFile = new File(destination);
                }
            }
            // Copy the original if requested, or if the compressed version is bigger
            if (!recompress || (maxSizeCompressed > 0 && destinationFile.length() > maxSizeCompressed)) {
                try (InputStream input = context.getContentResolver().openInputStream(sourceUri);
                     BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(destination))) {
                    // Write to new file unchanged
                    int originalSize = input.available();
                    try (BufferedInputStream bis = new BufferedInputStream(input)) {
                        byte[] buf = new byte[originalSize];
                        //bis.read(buf);
                        while (bis.read(buf) != -1) {
                            bos.write(buf);
                        }
                    }
                }
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

    public static boolean fileExists(Uri uri) {
        File file = new File(uri.getPath());
        return file.exists();
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

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public static void cleanUpImage(String destinationDir, ImageObject img) {
        File destDir = new File(destinationDir);
        File[] ls = destDir.listFiles();
        if (ls != null) {
            for (File f : ls) {
                if (f.isFile() && img.getName().equals(f.getName())) {
                    @SuppressWarnings("ConstantConditions") File thumbnail = new File(
                            f.getParentFile().getPath()
                                    + File.separator
                                    + THUMBDIR
                                    + File.separator
                                    + img.getId());
                    if (thumbnail.exists())
                        thumbnail.delete();
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
                if (f.isFile() && is.getImageObjectByName(f.getName()) == null) {
                    //noinspection ResultOfMethodCallIgnored
                    f.delete();
                }
            }
        }
        File thumbDir = new File(destDir + File.separator + THUMBDIR);
        if (thumbDir.exists()) {
            ls = thumbDir.listFiles();
            if (ls != null) {
                for (File f : ls) {
                    if (f.isFile() && is.getImageObject(f.getName()) == null) {
                        //noinspection ResultOfMethodCallIgnored
                        f.delete();
                    }
                }
            }
        }
    }

    /**
     * Gets creation date. If it is not available, returns 0 (epoch time).
     *
     * @param context the context
     * @param uri     the uri
     * @return the creation date in millis or 0
     */
    public static long getCreationDate(Context context, Uri uri) {
        long modDate = Long.parseLong(getFileAttrib(uri, DocumentsContract.Document.COLUMN_LAST_MODIFIED, context));
        // Set the creation date to the modification date
        long creationDate = modDate;
        try {
            // Try to get creation date from Exif data
            try (InputStream fis = context.getContentResolver().openInputStream(uri)) {
                // Expect to see debug messages: D/ExifInterface: No image meets the size requirements of a thumbnail image.
                ExifInterface exifData = new ExifInterface(fis);
                StringBuilder sb_format = new StringBuilder();
                StringBuilder sb_date = new StringBuilder();
                // Get Date, Time, and TZ offset from Exif fields (default to +00:00 offset)
                if (exifData.hasAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)) {
                    sb_format.append("yyyy:MM:dd HH:mm:ssXXX");
                    sb_date.append(exifData.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL));
                    if (exifData.hasAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL)) {
                        sb_date.append(exifData.getAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL));
                    } else {
                        sb_date.append("+00:00");
                    }
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern(sb_format.toString());
                    creationDate = ZonedDateTime.parse(sb_date.toString(), formatter).toInstant().toEpochMilli();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            // If all else fails, try to get creation date another way
            BasicFileAttributes attribs = Files.readAttributes(Paths.get(new File(uri.getPath()).toURI()), BasicFileAttributes.class);
            if (creationDate == modDate && attribs.creationTime().toMillis() > 0)
                creationDate = attribs.creationTime().toMillis();
        } catch (NoSuchFileException e) {
            //ignore -- couldn't resolve the uri to a file
        } catch (IOException e) {
            e.printStackTrace();
        }
        return creationDate;
    }
}
