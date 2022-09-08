package com.moosedrive.wallpaperer;

import android.content.Context;
import android.net.Uri;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * The type Image object.
 */
public class ImageObject {
    private final String id;
    private final String name;
    private final Uri uri;
    private final long size;
    private final String type;
    private final Date date;
    private boolean isGenerating;
    private static ThreadPoolExecutor tpe = (ThreadPoolExecutor) Executors.newFixedThreadPool(2);

    public Uri getThumbUri(Context context) {
        if (thumbUri == null) {
            if (!isGenerating)
                generateThumbnail(context);
            return uri;
        }
        return thumbUri;
    }

    public void generateThumbnail(Context context) {
        tpe.submit(() -> {
            isGenerating = true;
            thumbUri = StorageUtils.getThumbnailUri(context, this);
            isGenerating = false;
        });
    }

    private Uri thumbUri;
    /**
     * Instantiates a new Image object.
     *
     * @param uri      the uri
     * @param fileName the file name
     * @param size     the size
     * @param type     the type
     * @param date     the date
     * @throws NoSuchAlgorithmException the no such algorithm exception
     * @throws IOException              the io exception
     */
    public ImageObject(Uri uri, String id, String fileName, long size, String type, Date date) throws NoSuchAlgorithmException, IOException {
        this.uri = uri;
        this.id = id;
        name = fileName;
        this.size = size;
        this.type = type;
        this.date = date;
        this.thumbUri = null;
        this.isGenerating = false;
    }

    /**
     * Gets name.
     *
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * Gets id.
     *
     * @return the id
     */
    public String getId() {
        return id;
    }

    /**
     * Gets uri.
     *
     * @return the uri
     */
    public Uri getUri() {
        return uri;
    }

    /**
     * Gets size.
     *
     * @return the size
     */
    public long getSize() {
        return size;
    }


    /**
     * Gets type.
     *
     * @return the type
     */
    public String getType() {
        return type;
    }

    /**
     * Gets date.
     *
     * @return the date
     */
    public Date getDate() {
        return date;
    }
}
