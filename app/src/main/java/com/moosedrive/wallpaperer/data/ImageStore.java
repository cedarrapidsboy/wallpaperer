package com.moosedrive.wallpaperer.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.preference.PreferenceManager;

import androidx.annotation.NonNull;

import com.moosedrive.wallpaperer.R;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

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
    private final HashMap<String, ImageObject> referenceImages;
    private final List<ImageObject> orderedImages;
    private final List<TreeSet<ImageObject>> sortedImages;
    private final Set<ImageStoreListener> listeners = new HashSet<>();
    private int sortCriteria = SORT_BY_CUSTOM;
    private String lastWallpaperId = "";
    private final Context context;
    private ImageStore(Context context) {
        this.context = context.getApplicationContext();
        // The entire image repository
        referenceImages = new LinkedHashMap<>();
        // The user-ordered list of images
        orderedImages = new LinkedList<>();
        // The pre-sorted sets of images (a list of sets)
        sortedImages = new ArrayList<>();
        //SORT_BY_NAME==0
        sortedImages.add(new TreeSet<>(Comparator.comparing(ImageObject::getName)
                .thenComparing(ImageObject::getCreationDate)
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
                .thenComparing(ImageObject::getCreationDate)
                .thenComparing(ImageObject::getId)));
    }

    /**
     * Gets instance.
     *
     * @param context the context
     * @return the instance
     */
    public static synchronized ImageStore getInstance(Context context) {
        if (store == null) {
            store = new ImageStore(context);
        }
        return store;
    }

    /**
     * Image objects to json json array.
     *
     * @param objects the objects
     * @return the json array
     */
    @NonNull
    public static JSONArray imageObjectsToJson(Collection<ImageObject> objects) {
        JSONArray imageArray = new JSONArray();
        objects.forEach(io -> {
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
        });
        return imageArray;
    }

    /**
     * Parse json array linked list.
     *
     * @param context    the context
     * @param imageArray the image array
     * @param ignoreUri  the ignore uri
     * @return the linked list
     * @throws JSONException the json exception
     */
    public static LinkedList<ImageObject> parseJsonArray(Context context, JSONArray imageArray, boolean ignoreUri) throws JSONException {
        LinkedList<ImageObject> loadedImgs = new LinkedList<>();
        for (int i = 0; i < imageArray.length(); i++) {
            Uri uri = Uri.parse(imageArray.getJSONObject(i).getString("uri"));
            if (!ignoreUri) {
                try (ParcelFileDescriptor ignored = context.getContentResolver().openFileDescriptor(uri, "r")) {
                    //noinspection unused
                    int x = 0; //ignore
                } catch (FileNotFoundException e) {
                    System.out.println("ERROR: updateFromPrefs: File no longer exists. " + uri.toString());
                    e.printStackTrace();
                    return loadedImgs;
                } catch (IOException e) {
                    e.printStackTrace();
                    return loadedImgs;
                }
            }
            try {
                Date addedDate = (imageArray.getJSONObject(i).has("added_date"))
                        ? new Date(imageArray.getJSONObject(i).getLong("added_date"))
                        : new Date();
                Date creationDate = (imageArray.getJSONObject(i).has("date"))
                        ? new Date(imageArray.getJSONObject(i).getLong("date"))
                        : new Date();
                ImageObject io = new ImageObject(uri,
                        imageArray.getJSONObject(i).getString("id"),
                        imageArray.getJSONObject(i).getString("name"),
                        imageArray.getJSONObject(i).getLong("size"),
                        imageArray.getJSONObject(i).getString("type"),
                        addedDate,
                        creationDate);
                io.setColor(imageArray.getJSONObject(i).getInt("color"));
                loadedImgs.add(io);
            } catch (NoSuchAlgorithmException | JSONException | IOException e) {
                e.printStackTrace();
            }
        }
        return loadedImgs;
    }

    /**
     * Gets last wallpaper id.
     *
     * @return the last wallpaper id
     */
    public synchronized String getActiveId() {
        return lastWallpaperId;
    }

    /**
     * Gets active wallpaper position.
     *
     * @return the last wallpaper pos or -1
     */
    public synchronized int getActivePos() {
        return getPosition(lastWallpaperId);
    }

    /**
     * Finds the next valid image object in the active view.
     * Advances the active ImageObject to the next image.
     * Returns the ImageObject, or null if there is no other
     * valid ImageObject and clears the active object.
     *
     * @return the next image after the active one, or null
     */
    public synchronized ImageObject activateNext(){
        ImageObject nextImageObject = null;
        int listLength = getImageObjectArray().length;
        if (listLength == 1)
            nextImageObject = getImageObject(0);
        else if (listLength > 1) {
            int startPos = getActivePos();
            if (startPos == -1 || startPos == listLength - 1)
                nextImageObject = getImageObject(0);
            else
                nextImageObject = getImageObject(startPos + 1);
        }
        setActive((nextImageObject != null)?nextImageObject.getId():"");
        return nextImageObject;
    }

    /**
     * Sets the active ImageObject by id.
     *
     * @param id and ImageObject id
     */
    public synchronized void setActive(String id) {
        if (!this.lastWallpaperId.equals(id)) {
            String prevId = this.lastWallpaperId;
            this.lastWallpaperId = id;
            listeners
                    .stream()
                    .filter(Objects::nonNull)
                    .forEach(listener -> listener.onSetActive(getImageObject(this.lastWallpaperId), getImageObject(prevId)));
        }
    }

    /**
     * Shuffle the CUSTOM list. Current active wallpaper will be moved to position 0.
     */
    public synchronized void shuffle() {
        Collections.shuffle(orderedImages);
        if (!lastWallpaperId.isEmpty()) {
            ImageObject swapImage = referenceImages.get(lastWallpaperId);
            orderedImages.remove(swapImage);
            orderedImages.add(0, swapImage);
        }
        listeners
                .stream()
                .filter(Objects::nonNull)
                .forEach(ImageStoreListener::onShuffle);
    }

    /**
     * Save to prefs.
     */
    public synchronized void saveToPrefs() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor edit = prefs.edit();
        JSONArray imageArray = imageObjectsToJson(orderedImages);
        edit.putString("sources", imageArray.toString());
        edit.putInt("sort", getSortCriteria());
        edit.putString(context.getString(R.string.last_wallpaper), lastWallpaperId);
        edit.apply();
    }

    /**
     * Load from prefs image store. This will clear the current ImageStore.
     *
     * @param context the context
     */
    public synchronized void updateFromPrefs(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        LinkedList<ImageObject> loadedImgs = new LinkedList<>();
        try {
            JSONArray imageArray = new JSONArray(prefs.getString("sources", "[]"));
            loadedImgs = parseJsonArray(context, imageArray, false);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        replace(loadedImgs);
        setActive(prefs.getString(context.getString(R.string.last_wallpaper), ""));
        setSortCriteria(prefs.getInt("sort", SORT_DEFAULT));
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
        addImageObject(imgTry, refPosition, true);
    }

    /**
     * Add image object.
     *  @param imgTry      the img
     * @param refPosition the position Where to place the new object, or -1 to append
     */
    private synchronized void addImageObject(ImageObject imgTry, int refPosition, boolean updateView) {
        ImageObject img = referenceImages.put(imgTry.getId(), imgTry);
        int index = refPosition;
        if (img == null || img != imgTry) {
            if (index < 0 || index > orderedImages.size())
                index = orderedImages.size();
            orderedImages.add(index, imgTry);
            sortedImages.forEach(imgarray -> imgarray.add(imgTry));
            if (updateView)
                listeners.stream()
                    .filter(Objects::nonNull)
                    .forEach(listener -> listener.onAdd(imgTry, getPosition(imgTry.getId())));
        }
    }

    /**
     * Del image object image object.
     *
     * @param id the id
     */
    public synchronized void delImageObject(String id) {
        ImageObject deadImgWalking = referenceImages.get(id);
        if (deadImgWalking != null) {
            int pos = getPosition(id);
            referenceImages.remove(id);
            orderedImages.remove(deadImgWalking);
            sortedImages.forEach(imgArray -> imgArray.remove(deadImgWalking));
            if (getActiveId().equals(deadImgWalking.getId())) {
                setActive("");
            }
            listeners
                    .stream()
                    .filter(Objects::nonNull)
                    .forEach(listener -> listener.onDelete(deadImgWalking, pos));
        }
    }

    /**
     * Gets image object.
     *
     * @param id the id
     * @return the image object or null if not found
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
     * @return the image object or null if not found
     */
    public synchronized ImageObject getImageObject(int i) {
        if (i < 0 || i >= referenceImages.size())
            return null;
        return getImageObjectArray()[i];
    }

    /**
     * Get image object array image object [ ].
     *
     * @return the image object [ ]
     */
    public synchronized ImageObject[] getImageObjectArray() {
        return getImageObjectArray(sortCriteria);
    }

    /**
     * Get image object array image object [ ].
     * Criteria is SORT_BY_CUSTOM/NAME/DATE/SIZE
     *
     * @param criteria the criteria
     * @return the image object [ ]
     */
    public synchronized ImageObject[] getImageObjectArray(int criteria) {
        try {
            if (criteria == SORT_BY_CUSTOM)
                return orderedImages.toArray(new ImageObject[0]);
            else
                return sortedImages.get(criteria).toArray(new ImageObject[0]);
        } catch (IndexOutOfBoundsException e){
            return getReferenceObjects().toArray(new ImageObject[0]);
        }
    }

    /**
     * Get all ImageObject items in the library
     *
     * @return A new collection of the objects
     */
    public synchronized Collection<ImageObject> getReferenceObjects() {
        return new ArrayList<>(referenceImages.values());
    }

    /**
     * Add.
     *
     * @param col the col
     */
    public synchronized void add(Collection<ImageObject> col){
        col.forEach(img -> addImageObject(img, -1, false));
        listeners.stream()
                .filter(Objects::nonNull)
                .forEach(ImageStoreListener::onReplace);
    }

    /**
     * Replace all objects in the ImageStore with the provided collection
     *
     * @param col the col
     */
    public synchronized void replace(Collection<ImageObject> col) {
        store.clear(true);
        col.forEach(this::addImageObject);
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
        return orderedImages.indexOf(referenceImages.get(id));
    }

    /**
     * Clear.
     *
     * @param listsOnly the lists only
     */
    public synchronized void clear(boolean listsOnly) {
        referenceImages.clear();
        orderedImages.clear();
        if (!listsOnly)
            setActive("");
        sortedImages.forEach(TreeSet::clear);
        listeners
                .stream()
                .filter(Objects::nonNull)
                .forEach(ImageStoreListener::onClear);
    }

    /**
     * Size int.
     *
     * @return the int
     */
    public synchronized int size() {
        return referenceImages.size();
    }

    /**
     * Gets sort criteria.
     *
     * @return the sort criteria
     */
    public synchronized int getSortCriteria() {
        return sortCriteria;
    }

    /**
     * Sets sort criteria.
     *
     * @param sortCriteria the sort criteria
     */
    public synchronized void setSortCriteria(int sortCriteria) {
        int prevSortCriteria = this.sortCriteria;
        this.sortCriteria = sortCriteria;
        listeners
                .stream()
                .filter(Objects::nonNull)
                .forEach(listener -> listener.onSortCriteriaChanged(prevSortCriteria));
    }

    /**
     * Move image object boolean.
     *
     * @param object the object
     * @param newPos the new pos
     */
    public synchronized void moveImageObject(ImageObject object, int newPos) {
        if (referenceImages.containsKey(object.getId())) {
            int prevPos = getPosition(object.getId());
            boolean wasActive = getActiveId().equals(object.getId());
            delImageObject(object.getId());
            if (wasActive)
                lastWallpaperId = object.getId();
            addImageObject(object, newPos);
            listeners.stream()
                    .filter(Objects::nonNull)
                    .forEach(listener -> listener.onMove(prevPos, newPos));
        }
    }

    /**
     * Add sort listener.
     *
     * @param sl the sl
     */
    public void addListener(ImageStoreListener sl) {
        listeners.add(sl);
    }

    /**
     * Remove sort listener.
     *
     * @param sl the sl
     */
    public void removeListener(ImageStoreListener sl) {
        listeners.remove(sl);
    }

    /**
     * The interface Image store sort listener.
     */
    public interface ImageStoreListener {
        /**
         * On image store sort changed.
         *
         * @param prevSortCriteria the prev sort criteria
         */
        void onSortCriteriaChanged(int prevSortCriteria);

        /**
         * On delete.
         *
         * @param obj     the obj
         * @param lastPos the last pos
         */
        @SuppressWarnings("unused")
        void onDelete(ImageObject obj, int lastPos);

        /**
         * On shuffle.
         */
        void onShuffle();

        /**
         * On clear.
         */
        void onClear();

        /**
         * On move.
         *
         * @param oldPos the old pos
         * @param newPos the new pos
         */
        void onMove(int oldPos, int newPos);

        /**
         * On set active.
         *
         * @param activeObj the active obj
         * @param prevObj   the prev obj
         */
        void onSetActive(ImageObject activeObj, ImageObject prevObj);

        /**
         * On add.
         *
         * @param obj the obj
         * @param pos the pos
         */
        @SuppressWarnings("unused")
        void onAdd(ImageObject obj, int pos);

        /**
         * On replace.
         */
        void onReplace();
    }


}
