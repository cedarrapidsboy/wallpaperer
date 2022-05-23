package com.moosedrive.wallpaperer;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.preference.PreferenceManager;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.LinkedHashMap;

/**
 * The type Image store.
 */
public class ImageStore {
    private static ImageStore store = null;
    private LinkedHashMap<String, ImageObject> images;

    private ImageStore() {
        this.images = new LinkedHashMap<>();
    }

    /**
     * Gets instance.
     *
     * @return the instance
     */
    public static synchronized ImageStore getInstance() {
        if (store == null) {
            store = new ImageStore();
        }
        return store;
    }

    /**
     * Load from prefs image store. This will clear the current ImageStore.
     *
     * @param context the context
     */
    public synchronized void updateFromPrefs(Context context) {
        ImageStore is = store;
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        try {
            JSONArray imageArray = new JSONArray(prefs.getString("sources", "[]"));

            for (int i = 0; i < imageArray.length(); i++) {
                ParcelFileDescriptor pfd;
                Uri uri = Uri.parse(imageArray.getJSONObject(i).getString("uri"));
                try {
                    pfd = context.getContentResolver().openFileDescriptor(uri, "r");
                    pfd.close();
                    is.addImageObject(uri,
                            imageArray.getJSONObject(i).getString("id"),
                            imageArray.getJSONObject(i).getString("name"),
                            imageArray.getJSONObject(i).getLong("size"),
                            imageArray.getJSONObject(i).getString("type"),
                            new Date(imageArray.getJSONObject(i).getLong("date")));
                } catch (FileNotFoundException e) {
                    //StorageUtils.releasePersistableUriPermission(context, uri);
                    System.out.println("ERROR: loadFromPrefs: File no longer exists.");
                    e.printStackTrace();
                } catch (NoSuchAlgorithmException | IOException | JSONException e) {
                    e.printStackTrace();
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    /**
     * Add image object.
     *
     * @param uri      the uri
     * @param filename the filename
     * @param size     the size
     * @param type     the type
     * @param date     the date
     * @throws NoSuchAlgorithmException the no such algorithm exception
     * @throws IOException              the io exception
     */
    @SuppressWarnings("UnusedReturnValue")
    public synchronized ImageObject addImageObject(Uri uri, String id, String filename, long size, String type, Date date) throws NoSuchAlgorithmException, IOException {
        ImageObject img = new ImageObject(uri, id, filename, size, type, date);
        addImageObject(img);
        return img;
    }

    /**
     * Add image object.
     *
     * @param img the img
     */
    public synchronized void addImageObject(ImageObject img) {
        addImageObject(img, -1);
    }

    /**
     * Add image object.
     *
     * @param img      the img
     * @param position the position Where to place the new object, or -1 to append
     */
    public synchronized void addImageObject(ImageObject img, int position) {
        if (images.size() == 0 || position < 0 || position > (images.size() - 1)) {
            images.put(img.getId(), img);
        } else {
            int curPos = 0;
            LinkedHashMap<String, ImageObject> oldImages = images;
            LinkedHashMap<String, ImageObject> newImages = new LinkedHashMap<>();
            for (String key : oldImages.keySet()) {
                if (curPos == position)
                    newImages.put(img.getId(), img);
                newImages.put(key, oldImages.get(key));
                curPos++;
            }
            images = newImages;
        }
    }

    /**
     * Del image object image object.
     *
     * @param id the id
     */
    public synchronized void delImageObject(String id) {
        images.remove(id);
    }


    /**
     * Gets image object.
     *
     * @param id the id
     * @return the image object
     */
    public ImageObject getImageObject(String id) {
        return images.get(id);
    }

    public ImageObject getImageObjectByName(String name) {
        for (ImageObject img : images.values()){
            if (img.getName().equals(name))
                return img;
        }
        return null;
    }

    /**
     * Gets image object.
     *
     * @param i the
     * @return the image object
     */
    public ImageObject getImageObject(int i) {
        return getImageObjectArray()[i];
    }

    /**
     * Get image object array image object [ ].
     *
     * @return the image object [ ]
     */
    public ImageObject[] getImageObjectArray() {
        return images.values().toArray(new ImageObject[0]);
    }

    /**
     * @param id Unique ID of the ImageObject
     * @return position in image store, or -1 if not found
     */
    public int getPosition(String id) {
        String[] keys = images.keySet().toArray(new String[0]);
        int pos = -1;
        for (int i = 0; i < keys.length; i++) {
            if (keys[i].equals(id))
                pos = i;
        }
        return pos;
    }

    /**
     * Clear.
     */
    public synchronized void clear() {
        images.clear();
    }

    /**
     * Size int.
     *
     * @return the int
     */
    public int size() {
        return images.size();
    }

    /**
     * Save to prefs.
     *
     * @param context the context
     */
//long size, String type, Date date
    public synchronized void saveToPrefs(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor edit = prefs.edit();
        JSONArray imageArray = new JSONArray();
        for (ImageObject io : images.values().toArray(new ImageObject[0])) {
            try {
                JSONObject imageJson = new JSONObject();
                imageJson.put("uri", io.getUri().toString());
                imageJson.put("id", io.getId());
                imageJson.put("name", io.getName());
                imageJson.put("size", io.getSize());
                imageJson.put("type", io.getType());
                imageJson.put("date", io.getDate().getTime());
                imageArray.put(imageJson);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        edit.putString("sources", imageArray.toString());
        edit.apply();
    }

}
