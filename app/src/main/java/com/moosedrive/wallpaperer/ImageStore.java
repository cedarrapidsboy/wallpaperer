package com.moosedrive.wallpaperer;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.os.StatFs;
import android.preference.PreferenceManager;
import android.provider.DocumentsContract;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;

/**
 * The type Image store.
 */
public class ImageStore {
    /**
     * The constant SORT_BY_CUSTOM.
     */
    public static final int SORT_BY_CUSTOM = -1;
    /**
     * The constant SORT_BY_NAME.
     */
    public static final int SORT_BY_NAME = 0;
    /**
     * The constant SORT_BY_DATE.
     */
    public static final int SORT_BY_DATE = 1;
    /**
     * The constant SORT_BY_SIZE.
     */
    public static final int SORT_BY_SIZE = 2;
    /**
     * The constant SORT_DEFAULT.
     */
    public static final int SORT_DEFAULT = SORT_BY_CUSTOM;
    private static ImageStore store = null;

    private int sortCriteria = SORT_BY_CUSTOM;
    private LinkedHashMap<String, ImageObject> referenceImages;

    private final ArrayList<Collection<ImageObject>> sortedImages = new ArrayList<>();private String lastWallpaperId = "";
    private int lastWallpaperPos = -1;

    private ImageStore() {
        this.referenceImages = new LinkedHashMap<>();
        //SORT_BY_NAME==0
        sortedImages.add(new TreeSet<>(Comparator.comparing(ImageObject::getName)
                .thenComparing(ImageObject::getAddedDate)
                .thenComparingLong(ImageObject::getSize)
                .thenComparing(ImageObject::getId)));
        //SORT_BY_DATE==1
        sortedImages.add(new TreeSet<>(Comparator.comparing(ImageObject::getCreationDate)
                .thenComparing(ImageObject::getName)
                .thenComparingLong(ImageObject::getSize)
                .thenComparing(ImageObject::getId)));
        //SORT_BY_SIZE==2
        sortedImages.add(new TreeSet<>(Comparator.comparingLong(ImageObject::getSize)
                .thenComparing(ImageObject::getName)
                .thenComparing(ImageObject::getAddedDate)
                .thenComparing(ImageObject::getId)));
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
     * Gets last wallpaper id.
     *
     * @return the last wallpaper id
     */
    public String getLastWallpaperId() {
        return lastWallpaperId;
    }

    /**
     * Gets last wallpaper pos.
     *
     * @return the last wallpaper pos
     */
    public int getLastWallpaperPos() {
        return lastWallpaperPos;
    }

    /**
     * Sets last wallpaper pos.
     *
     * @param lastPos the last pos
     */
    public void setLastWallpaperPos(int lastPos) {
        lastWallpaperPos = lastPos;
    }

    /**
     * Sets last wallpaper id.
     *
     * @param lastWallpaperId the last wallpaper id
     * @param force           the force
     */
    public void setLastWallpaperId(String lastWallpaperId, boolean force) {
        this.lastWallpaperId = lastWallpaperId;
        if (force || !lastWallpaperId.equals(""))
            this.lastWallpaperPos = getPosition(lastWallpaperId);
    }

    /**
     * Shuffle the CUSTOM list. Current active wallpaper will be moved to position 0.
     */
    public void shuffleImages(){
        List<String> list = new ArrayList<>(referenceImages.keySet());
        // Shuffle all the images
        Collections.shuffle(list);
        // Rebuild the hashmap with the shuffled list
        LinkedHashMap<String, ImageObject> shuffleMap = new LinkedHashMap<>();
        list.forEach(k->shuffleMap.put(k, referenceImages.get(k)));
        referenceImages = shuffleMap;
        // Move the active wallpaper to the top
        if (lastWallpaperPos > -1 && !lastWallpaperId.equals("")) {
            ImageObject moveToFront = referenceImages.get(lastWallpaperId);
            if (moveToFront != null){
                referenceImages.remove(moveToFront.getId());
                addImageObject(moveToFront, 0);
            }
            lastWallpaperPos = 0;
        }
    }

    /**
     * Save to prefs.
     *
     * @param context the context
     */
    public synchronized void saveToPrefs(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor edit = prefs.edit();
        JSONArray imageArray = new JSONArray();
        for (ImageObject io : referenceImages.values().toArray(new ImageObject[0])) {
            try {
                JSONObject imageJson = new JSONObject();
                imageJson.put("uri", io.getUri().toString());
                imageJson.put("id", io.getId());
                imageJson.put("name", io.getName());
                imageJson.put("size", io.getSize());
                imageJson.put("type", io.getType());
                imageJson.put("date", io.getCreationDate().getTime());
                imageJson.put("added_date", io.getAddedDate().getTime());
                imageJson.put("color", io.getColor());
                imageArray.put(imageJson);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        edit.putString("sources", imageArray.toString());
        edit.putInt("sort",getSortCriteria());
        edit.putString(context.getString(R.string.last_wallpaper), lastWallpaperId);
        edit.putInt(context.getString(R.string.last_wallpaper_pos), lastWallpaperPos);
        edit.apply();
    }

    /**
     * Load from prefs image store. This will clear the current ImageStore.
     *
     * @param context the context
     */
    public synchronized void updateFromPrefs(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        try {
            JSONArray imageArray = new JSONArray(prefs.getString("sources", "[]"));
            for (int i = 0; i < imageArray.length(); i++) {
                Uri uri = Uri.parse(imageArray.getJSONObject(i).getString("uri"));
                try (ParcelFileDescriptor ignored = context.getContentResolver().openFileDescriptor(uri, "r")){
                    Date addedDate = (imageArray.getJSONObject(i).has("added_date"))
                            ?new Date(imageArray.getJSONObject(i).getLong("added_date"))
                            :new Date();
                    Date creationDate = (imageArray.getJSONObject(i).has("date"))
                            ?new Date(imageArray.getJSONObject(i).getLong("date"))
                            :new Date();
                    ImageObject io = addImageObject(uri,
                            imageArray.getJSONObject(i).getString("id"),
                            imageArray.getJSONObject(i).getString("name"),
                            imageArray.getJSONObject(i).getLong("size"),
                            imageArray.getJSONObject(i).getString("type"),
                            addedDate,
                            creationDate);
                    io.setColor(imageArray.getJSONObject(i).getInt("color"));
                } catch (FileNotFoundException e) {
                    System.out.println("ERROR: updateFromPrefs: File no longer exists.");
                    e.printStackTrace();
                } catch (NoSuchAlgorithmException | IOException | JSONException e) {
                    e.printStackTrace();
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        lastWallpaperId = prefs.getString(context.getString(R.string.last_wallpaper), "");
        lastWallpaperPos = prefs.getInt(context.getString(R.string.last_wallpaper_pos), -1);
        setSortCriteria(prefs.getInt("sort",SORT_DEFAULT));
    }

    /**
     * Add image object.
     *
     * @param uri          the uri
     * @param id           the id
     * @param filename     the filename
     * @param size         the size
     * @param type         the type
     * @param addedDate    the added date
     * @param creationDate the date
     * @return the image object
     * @throws NoSuchAlgorithmException the no such algorithm exception
     * @throws IOException              the io exception
     */
    @SuppressWarnings("UnusedReturnValue")
    public synchronized ImageObject addImageObject(
            Uri uri,
            String id,
            String filename,
            long size,
            String type,
            Date addedDate,
            Date creationDate) throws NoSuchAlgorithmException, IOException {
        ImageObject img = new ImageObject(uri, id, filename, size, type, addedDate, creationDate);
        addImageObject(img);
        return img;
    }

    /**
     * Add image object to the end of the map.
     *
     * @param img the img
     */
    public synchronized void addImageObject(ImageObject img) {
        addImageObject(img, -1);
    }

    /**
     * Add image object.
     *
     * @param imgTry      the img
     * @param refPosition the position Where to place the new object, or -1 to append
     */
    public synchronized void addImageObject(ImageObject imgTry, int refPosition) {
        ImageObject img = imgTry;
        if (referenceImages.size() == 0 || refPosition < 0 || refPosition > (referenceImages.size() - 1)) {
            // add to end of map
            img = referenceImages.put(img.getId(), img);
        } else {
            // rebuild the map and insert the image along the way
            int curPos = 0;
            LinkedHashMap<String, ImageObject> oldImages = referenceImages;
            LinkedHashMap<String, ImageObject> newImages = new LinkedHashMap<>();
            for (String key : oldImages.keySet()) {
                if (curPos == refPosition)
                    img = newImages.put(imgTry.getId(), imgTry);
                if (!key.equals(imgTry.getId()))
                    newImages.put(key, oldImages.get(key));
                curPos++;
            }
            referenceImages = newImages;
        }
        //Add the image to each sorted set
        if (img == null)
            img = imgTry;
        for (Collection<ImageObject> imgArray : sortedImages){
            if (imgArray instanceof Set)
                imgArray.add(img);
        }
        //Reset the last wallpaper position since a new one was added and may have bumped it
        setLastWallpaperId(getLastWallpaperId(), false);
    }

    /**
     * Del image object image object.
     *
     * @param id the id
     */
    public synchronized void delImageObject(String id) {
        ImageObject deadImgWalking = referenceImages.get(id);
        referenceImages.remove(id);
        for (Collection<ImageObject> imgArray : sortedImages){
            imgArray.remove(deadImgWalking);
        }
        if (deadImgWalking != null && getLastWallpaperId().equals(deadImgWalking.getId())) {
            lastWallpaperId = "";
        } else {
            //Reset the last wallpaper position to the repositioned place
            setLastWallpaperId(getLastWallpaperId(), false);
        }
    }

    /**
     * Gets image object.
     *
     * @param id the id
     * @return the image object
     */
    public synchronized ImageObject getImageObject(String id) {
        return referenceImages.get(id);
    }

    /**
     * Gets image object by name.
     *
     * @param name the name
     * @return the image object by name
     */
    public synchronized ImageObject getImageObjectByName(String name) {
        for (ImageObject img : referenceImages.values()) {
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
    public synchronized ImageObject getImageObject(int i) {
        if (i < 0 || i >= getImageObjectArray().length)
            return null;
        return getImageObjectArray()[i];
    }

    /**
     * Get image object array image object [ ].
     *
     * @return the image object [ ]
     */
    public synchronized ImageObject[] getImageObjectArray() {
        if (sortCriteria == SORT_BY_CUSTOM)
            return referenceImages.values().toArray(new ImageObject[0]);
        else
            return sortedImages.get(sortCriteria).toArray(new ImageObject[0]);
    }

    /**
     * Replace.
     *
     * @param col the col
     */
    public void replace(Collection<ImageObject> col){
        store.clear(true);
        col.forEach(obj -> store.addImageObject(obj));
    }

    /**
     * Gets position.
     *
     * @param id Unique ID of the ImageObject
     * @return position in image store, or -1 if not found
     */
    public synchronized int getPosition(String id) {
        return Arrays.asList(getImageObjectArray()).indexOf(getImageObject(id));
    }

    /**
     * Gets reference position.
     *
     * @param id the id
     * @return the reference position
     */
    public synchronized int getReferencePosition(String id) {
        return Arrays.asList(referenceImages.keySet().toArray(new String[0])).indexOf(id);
    }

    /**
     * Clear.
     *
     * @param listsOnly the lists only
     */
    public synchronized void clear(boolean listsOnly) {
        referenceImages.clear();
        if (!listsOnly)
            setLastWallpaperId("", false);
        for (Collection<ImageObject> imgArray : sortedImages){
            imgArray.clear();
        }
    }

    /**
     * Size int.
     *
     * @return the int
     */
    public synchronized int size() {
        return referenceImages.size();
    }


    private final Set<ImageStore.ImageStoreSortListener> sortListeners = new HashSet<>();


    /**
     * Gets sort criteria.
     *
     * @return the sort criteria
     */
    public int getSortCriteria() {
        return sortCriteria;
    }

    /**
     * Sets sort criteria.
     *
     * @param sortCriteria the sort criteria
     */
    public void setSortCriteria(int sortCriteria) {
        this.sortCriteria = sortCriteria;
        if (!lastWallpaperId.equals("")) {
            //Update the position of the last wallpaper
            setLastWallpaperId(lastWallpaperId, false);
        }
        for (ImageStoreSortListener sl : sortListeners) {
            sl.onImageStoreSortChanged();
        }
    }

    /**
     * Move image object boolean.
     *
     * @param object the object
     * @param newPos the new pos
     * @return the boolean
     */
    public boolean moveImageObject(ImageObject object, int newPos){
        if (referenceImages.containsKey(object.getId())){
            referenceImages.remove(object.getId());
            addImageObject(object, newPos);
            return true;
        }
        return false;
    }

    /**
     * Add sort listener.
     *
     * @param sl the sl
     */
    public void addSortListener(ImageStoreSortListener sl) {
        sortListeners.add(sl);
    }

    /**
     * Remove sort listener.
     *
     * @param sl the sl
     */
    public void removeSortListener(ImageStoreSortListener sl) {
        sortListeners.remove(sl);
    }

    /**
     * The interface Image store sort listener.
     */
    public interface ImageStoreSortListener {
        /**
         * On image store sort changed.
         */
        void onImageStoreSortChanged();
    }

}
