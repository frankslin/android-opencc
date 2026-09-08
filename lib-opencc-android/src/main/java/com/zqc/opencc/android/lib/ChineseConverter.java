package com.zqc.opencc.android.lib;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;

/**
 * Created by zhangqichuan on 29/2/16.
 */
public class ChineseConverter {

    private static final String TAG = "ChineseConverter";

    /** Asset folder holding the OpenCC configs and dictionaries, mirrored into the files directory. */
    private static final String DATA_FOLDER = "openccdata";

    /**
     * Name of the marker file inside {@link #DATA_FOLDER}. The bundled copy records which
     * dictionary data this build of the library ships; the copy in the files directory records
     * which data was installed there. They are compared on the first conversion in each process
     * and the data is re-installed when they differ, so a library upgrade takes effect without
     * the application having to call {@link #clearDictDataFolder(Context)}.
     */
    private static final String VERSION_FILE = "VERSION";

    // StandardCharsets.UTF_8 needs API 19; Charset.forName() works on every API level.
    private static final Charset UTF_8 = Charset.forName("UTF-8");

    private static final Object LOCK = new Object();

    /** Absolute path of the data folder verified to hold the bundled data version in this process. */
    private static volatile String verifiedDataFolder;

    /***
     * @param text           the text to be converted to
     * @param conversionType the conversion type
     * @param context        android context
     * @return the converted text
     * @throws IllegalStateException if the dictionary data could not be installed into the
     *                               application's files directory, or is missing or corrupt
     *                               there and cannot be loaded
     */
    public static String convert(String text, ConversionType conversionType, Context context) {
        File dataFolder = ensureDictionaryData(context);
        byte[] converted = convert(text.getBytes(UTF_8), conversionType.getValue(),
                dataFolder.getAbsolutePath());
        return converted == null ? null : new String(converted, UTF_8);
    }

    /***
     * Clear the dictionary data folder. The data is installed again on the next call to
     * {@link #convert(String, ConversionType, Context)}. Calling this is no longer required
     * after a library upgrade: a changed dictionary version is detected and installed
     * automatically.
     * @param context android context
     */
    public static void clearDictDataFolder(Context context) {
        synchronized (LOCK) {
            deleteRecursive(dataFolder(context));
            verifiedDataFolder = null;
            clearConverterCache();
        }
    }

    /**
     * Releases the OpenCC converters kept in native memory.
     *
     * The first conversion with a given {@link ConversionType} loads that type's dictionaries
     * (a few MB for the phrase-based types) and keeps the converter for later calls, which
     * makes them fast. Call this from a low-memory callback such as
     * {@code ComponentCallbacks2.onTrimMemory()} if the app wants that memory back; the next
     * conversion reloads what it needs.
     */
    public static native void clearConverterCache();

    /**
     * The text is passed as UTF-8 bytes rather than as a String: JNI's string
     * functions use Modified UTF-8, which cannot represent characters outside
     * the Basic Multilingual Plane the way OpenCC's dictionaries expect. See
     * the comment on the native side.
     */
    private static native byte[] convert(byte[] utf8Text, String configFile, String absoluteDataFolderPath);

    private static File dataFolder(Context context) {
        return new File(context.getFilesDir(), DATA_FOLDER);
    }

    /**
     * Makes sure the files directory holds the dictionary data bundled with this build of the
     * library, installing it when it is absent or belongs to another version.
     *
     * Concurrent first calls from several threads are serialised on {@link #LOCK}; after the
     * first successful check the fast path is a single volatile read. The check is per process
     * and not per installation, so two processes of the same app starting at the same moment
     * may both copy the data. Both write identical files and the marker last, so the outcome is
     * still a complete, consistent installation.
     */
    private static File ensureDictionaryData(Context context) {
        File dataFolder = dataFolder(context);
        String path = dataFolder.getAbsolutePath();
        if (path.equals(verifiedDataFolder)) {
            return dataFolder;
        }
        synchronized (LOCK) {
            if (path.equals(verifiedDataFolder)) {
                return dataFolder;
            }
            AssetManager assetManager = context.getAssets();
            String bundledVersion = readBundledVersion(assetManager);
            String installedVersion = readInstalledVersion(dataFolder);
            if (!bundledVersion.equals(installedVersion)) {
                Log.i(TAG, "Installing dictionary data " + bundledVersion
                        + (installedVersion.isEmpty() ? "" : " over " + installedVersion));
                deleteRecursive(dataFolder);
                installAssets(assetManager, dataFolder);
                clearConverterCache();
            }
            verifiedDataFolder = path;
            return dataFolder;
        }
    }

    private static String readBundledVersion(AssetManager assetManager) {
        String version;
        try (InputStream in = assetManager.open(DATA_FOLDER + "/" + VERSION_FILE)) {
            version = readAll(in).trim();
        } catch (IOException e) {
            throw new IllegalStateException(
                    "The bundled " + DATA_FOLDER + "/" + VERSION_FILE + " asset is missing", e);
        }
        if (version.isEmpty()) {
            // An empty marker would compare equal to "nothing installed" and skip the copy.
            throw new IllegalStateException("The bundled " + DATA_FOLDER + "/" + VERSION_FILE + " asset is empty");
        }
        return version;
    }

    /** Returns the installed data version, or an empty string when nothing (complete) is installed. */
    private static String readInstalledVersion(File dataFolder) {
        File versionFile = new File(dataFolder, VERSION_FILE);
        if (!versionFile.isFile()) {
            return "";
        }
        try (InputStream in = new FileInputStream(versionFile)) {
            return readAll(in).trim();
        } catch (IOException e) {
            Log.w(TAG, "Cannot read " + versionFile + ", reinstalling dictionary data", e);
            return "";
        }
    }

    /**
     * Copies every file of the asset folder into the data folder. The version marker is written
     * last, so an interrupted copy leaves no marker behind and is redone on the next call.
     */
    private static void installAssets(AssetManager assetManager, File dataFolder) {
        String[] files;
        try {
            files = assetManager.list(DATA_FOLDER);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot list the " + DATA_FOLDER + " assets", e);
        }
        if (files == null || files.length == 0) {
            throw new IllegalStateException("The " + DATA_FOLDER + " assets are missing");
        }
        if (!dataFolder.isDirectory() && !dataFolder.mkdirs()) {
            throw new IllegalStateException("Cannot create " + dataFolder);
        }
        for (String filename : files) {
            if (!filename.equals(VERSION_FILE)) {
                copyAsset(assetManager, filename, dataFolder);
            }
        }
        copyAsset(assetManager, VERSION_FILE, dataFolder);
    }

    private static void copyAsset(AssetManager assetManager, String filename, File dataFolder) {
        File outFile = new File(dataFolder, filename);
        try (InputStream in = assetManager.open(DATA_FOLDER + "/" + filename);
             OutputStream out = new FileOutputStream(outFile)) {
            copyStream(in, out);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to copy asset " + filename + " to " + outFile, e);
        }
    }

    private static void copyStream(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
    }

    private static String readAll(InputStream in) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        copyStream(in, bytes);
        return new String(bytes.toByteArray(), UTF_8);
    }

    private static void deleteRecursive(File fileOrDirectory) {
        File[] children = fileOrDirectory.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursive(child);
            }
        }
        if (fileOrDirectory.exists() && !fileOrDirectory.delete()) {
            Log.w(TAG, "Could not delete " + fileOrDirectory);
        }
    }

    static {
        System.loadLibrary("ChineseConverter");
    }
}
