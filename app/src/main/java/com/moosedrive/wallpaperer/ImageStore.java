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
     * The constant MINIMUM_REQUIRED_FREE_SPACE.
     */
    public static final long MINIMUM_REQUIRED_FREE_SPACE = 734003200L;
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
    private final Set<WallpaperAddedListener> wallpaperListeners = new HashSet<>();
    private final Set<ImageStoreSortListener> sortListeners = new HashSet<>();
    private final ArrayList<Collection<ImageObject>> sortedImages = new ArrayList<>();
    /**
     * The Loading done signal.
     */
    public CountDownLatch loadingDoneSignal;
    /**
     * The Loading errors.
     */
    public HashSet<String> loadingErrors = new HashSet<>();
    private int sortCriteria = SORT_BY_CUSTOM;
    private LinkedHashMap<String, ImageObject> referenceImages;
    private String lastWallpaperId = "";
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
        this.lastWallpaperPos = lastPos;
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
        Collections.shuffle(list);

        LinkedHashMap<String, ImageObject> shuffleMap = new LinkedHashMap<>();
        list.forEach(k->shuffleMap.put(k, referenceImages.get(k)));
        referenceImages = shuffleMap;
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
                    Date addedDate = (imageArray.getJSONObject(i).has("added_date"))
                            ?new Date(imageArray.getJSONObject(i).getLong("added_date"))
                            :new Date();
                    Date creationDate = (imageArray.getJSONObject(i).has("date"))
                            ?new Date(imageArray.getJSONObject(i).getLong("date"))
                            :new Date();
                    ImageObject io = is.addImageObject(uri,
                            imageArray.getJSONObject(i).getString("id"),
                            imageArray.getJSONObject(i).getString("name"),
                            imageArray.getJSONObject(i).getLong("size"),
                            imageArray.getJSONObject(i).getString("type"),
                            addedDate,
                            creationDate);
                    io.setColor(imageArray.getJSONObject(i).getInt("color"));
                } catch (FileNotFoundException e) {
                    System.out.println("ERROR: loadFromPrefs: File no longer exists.");
                    e.printStackTrace();
                } catch (NoSuchAlgorithmException | IOException | JSONException e) {
                    e.printStackTrace();
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        store.lastWallpaperId = prefs.getString(context.getString(R.string.last_wallpaper), "");
        store.lastWallpaperPos = prefs.getInt(context.getString(R.string.last_wallpaper_pos), -1);
        store.setSortCriteria(prefs.getInt("sort",SORT_DEFAULT));
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
    public synchronized ImageObject addImageObject(Uri uri, String id, String filename, long size, String type, Date addedDate, Date creationDate) throws NoSuchAlgorithmException, IOException {
        ImageObject img = new ImageObject(uri, id, filename, size, type, addedDate, creationDate);
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
     * @param imgTry      the img
     * @param refPosition the position Where to place the new object, or -1 to append
     */
    public synchronized void addImageObject(ImageObject imgTry, int refPosition) {
        ImageObject img = imgTry;
        if (referenceImages.size() == 0 || refPosition < 0 || refPosition > (referenceImages.size() - 1)) {
            img = referenceImages.put(img.getId(), img);
        } else {
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
        //Add the image to each sorted list
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
        if (deadImgWalking != null && store.getLastWallpaperId().equals(deadImgWalking.getId())) {
            lastWallpaperId = "";
        } else {
            //Reset the last wallpaper position to the repositioned place
            store.setLastWallpaperId(store.getLastWallpaperId(), false);
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
        ImageObject[] objs = getImageObjectArray();
        int pos = -1;
        for (int i = 0; i < objs.length; i++) {
            if (objs[i].getId().equals(id))
                pos = i;
        }
        return pos;
    }

    /**
     * Gets reference position.
     *
     * @param id the id
     * @return the reference position
     */
    public synchronized int getReferencePosition(String id) {
        ImageObject[] objs = referenceImages.values().toArray(new ImageObject[0]);
        int pos = -1;
        for (int i = 0; i < objs.length; i++) {
            if (objs[i].getId().equals(id))
                pos = i;
        }
        return pos;
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


    /**
     * Add wallpaper added listener.
     *
     * @param wal the wal
     */
    public void addWallpaperAddedListener(WallpaperAddedListener wal) {
        wallpaperListeners.add(wal);
    }

    /**
     * Remove wallpaper added listener.
     *
     * @param wal the wal
     */
    public void removeWallpaperAddedListener(WallpaperAddedListener wal) {
        wallpaperListeners.remove(wal);
    }

    /**
     * Add wallpapers from list of URI's.
     * Loading dialog is displayed and progress bar updated as wallpapers are added.
     *
     * @param context the context
     * @param sources the sources
     */
    public synchronized void addWallpapers(Context context, HashSet<Uri> sources) {
        boolean recompress = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context).getBoolean(context.getResources().getString(R.string.preference_recompress), false);
        loadingErrors = new HashSet<>();
        if (sources.size() > 0) {
            loadingDoneSignal = new CountDownLatch(sources.size());
            for (WallpaperAddedListener wal : wallpaperListeners)
                wal.onWallpaperLoadingStarted(sources.size());
            for (Uri uri : sources) {
                Thread t = new Thread(() -> {
                    File fImageStorageFolder = StorageUtils.getStorageFolder(context);
                    StatFs stats = new StatFs(fImageStorageFolder.getAbsolutePath());
                    long bytesAvailable = stats.getAvailableBlocksLong() * stats.getBlockSizeLong();
                    if (!fImageStorageFolder.exists() && !fImageStorageFolder.mkdirs())
                        loadingErrors.add(context.getString(R.string.loading_error_cannot_mkdir));
                    else if (bytesAvailable < MINIMUM_REQUIRED_FREE_SPACE)
                        loadingErrors.add(context.getString(R.string.loading_error_precheck_low_space));
                    else {
                        String hash = StorageUtils.getHash(context, uri);
                        if (hash == null)
                            hash = UUID.randomUUID().toString();
                        if (getImageObject(hash) == null) {
                            // Get file modification date from file attributes (if available, 0 otherwise)
                            String name = StorageUtils.getFileAttrib(uri, DocumentsContract.Document.COLUMN_DISPLAY_NAME, context);
                            String type = context.getContentResolver().getType(uri);
                            long creationDate = StorageUtils.getCreationDate(context, uri);
                            if (type.startsWith("image/")) {
                                try {
                                    String uuid = UUID.randomUUID().toString().replace("-", "").substring(0, 4);
                                    String filename = name + "_" + uuid;
                                    long size = Long.parseLong(StorageUtils.getFileAttrib(uri, DocumentsContract.Document.COLUMN_SIZE, context));
                                    Uri uCopiedFile = StorageUtils.saveBitmap(context, uri, size, fImageStorageFolder.getPath(), filename, recompress);
                                    if (recompress) type = "image/webp";
                                    size = StorageUtils.getFileSize(uCopiedFile);
                                    try {
                                        // The current date/time, used as creation date/time if all other methods of getting the file's date/time fail
                                        Date dNow = new Date();
                                        ImageObject img = new ImageObject(uCopiedFile, hash, filename, size, type, dNow, (creationDate > 0)?new Date(creationDate):dNow);
                                        img.generateThumbnail(context);
                                        img.setColor(img.getColorFromBitmap(context));
                                        addImageObject(img);
                                        for (WallpaperAddedListener wal : wallpaperListeners)
                                            wal.onWallpaperAdded(img);
                                    } catch (NoSuchAlgorithmException | IOException e) {
                                        e.printStackTrace();
                                    }
                                } catch (FileNotFoundException e) {
                                    loadingErrors.add(context.getString(R.string.loading_error_fnf));
                                } catch (IOException e) {
                                    loadingErrors.add(context.getString(R.string.loading_error_out_of_space));
                                }
                            } else {
                                loadingErrors.add(context.getString(R.string.loading_error_not_an_image));
                            }
                        }
                    }
                    for (WallpaperAddedListener wal : wallpaperListeners)
                        wal.onWallpaperLoadingIncrement(1);
                    loadingDoneSignal.countDown();
                });
                BackgroundExecutor.getExecutor().execute(t);
            }
            // UI work that waits for the image loading to complete
            BackgroundExecutor.getExecutor().execute(() -> {
                try {
                    loadingDoneSignal.await();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } finally {
                    StringBuilder sb = new StringBuilder();
                    for (String str : loadingErrors) {
                        sb.append(str);
                        sb.append(System.getProperty("line.separator"));
                    }
                    for (WallpaperAddedListener wal : wallpaperListeners)
                        wal.onWallpaperLoadingFinished(((loadingErrors.size()) == 0) ? WallpaperAddedListener.SUCCESS : WallpaperAddedListener.ERROR, sb.toString());
                }
            });
        }
    }

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

    /**
     * The interface Wallpaper added listener.
     */
    public interface WallpaperAddedListener {
        /**
         * The constant SUCCESS.
         */
        int SUCCESS = 0;
        /**
         * The constant ERROR.
         */
        int ERROR = 1;

        /**
         * On wallpaper added.
         *
         * @param img the img
         */
        void onWallpaperAdded(ImageObject img);

        /**
         * On wallpaper loading started.
         *
         * @param size the size
         */
        void onWallpaperLoadingStarted(int size);

        /**
         * On wallpaper loading increment.
         *
         * @param inc the inc
         */
        void onWallpaperLoadingIncrement(int inc);

        /**
         * On wallpaper loading finished.
         *
         * @param status  the status
         * @param message the message
         */
        void onWallpaperLoadingFinished(int status, String message);
    }
}
