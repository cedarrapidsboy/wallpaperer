package com.moosedrive.wallpaperer;

import android.net.Uri;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.Date;

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
