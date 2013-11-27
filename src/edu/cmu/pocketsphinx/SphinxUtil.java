package edu.cmu.pocketsphinx;

import java.io.*;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;

import static android.os.Environment.getExternalStorageState;


public class SphinxUtil {

    private static final String TAG = SphinxUtil.class.getSimpleName();

    /**
     * Synchronizes application asset files.
     *
     * This method does not use slow built-in asset listing method and reads
     * "assets.lst" to get a list of files to synchronize. For each entry in
     * the list it copies asset file only if it does not exist on external
     * storage or there is hash mismatch between the two. The hash value must
     * be provided in a single line file with the same name as the target file
     * and ".md5" suffix. See PocketSphinxAndroidDemo for the reference
     * implementation of asset setup.
     *
     * @param Context Application context.
     *
     * @return Path to the root of resources directory on external storage.
     */
    public static File syncAssets(Context context) throws IOException {
        AssetManager assets = context.getAssets();
        Reader reader = new InputStreamReader(assets.open("assets.lst"));
        BufferedReader br = new BufferedReader(reader);
        File extDir = getApplicationDir(context);
        String path;

        while (null != (path = br.readLine())) {
            File extFile = new File(extDir, path);
            String md5Path = path + ".md5";
            File extHash = new File(extDir, md5Path);
            extFile.getParentFile().mkdirs();

            try {
                // Read asset hash.
                reader = new InputStreamReader(assets.open(md5Path));
                String hash = new BufferedReader(reader).readLine();
                // Read file hash and compare.
                reader = new InputStreamReader(new FileInputStream(extHash));
                if (hash.equals(new BufferedReader(reader).readLine())) {
                    Log.i(TAG, "skip " + path + ", checksums match");
                    continue;
                }
            } catch (IOException e) {
            }

            Log.i(TAG, "copy " + path + " to " + extFile);
            copyStream(assets.open(path), new FileOutputStream(extFile));
            InputStream hashStream = assets.open(md5Path);
            copyStream(hashStream, new FileOutputStream(extHash));
        }

        return extDir;
    }

    /**
     * Copies application asset files to external storage.
     *
     * Recursively copies asset files stored in .apk to a directory located on
     * external storage and unique for application. If a file already exists it
     * will be overwritten. In general this method should not be used to
     * synchronize application resources and is only provided for compatibility
     * with projects without "smart" asset setup. If you are looking for quick
     * and "smart" synchronization that does not overwrite existing files use
     * {@link #syncAssets(Context, String)}.
     *
     * @param context Application context.
     * @param path    Relative path to asset file or directory.
     *
     * @return Path to the root of resources directory on external storage.
     *
     * @see #syncAssets
     */
    public static File copyAssets(Context context, String path)
        throws IOException
    {
        File extPath = new File(getApplicationDir(context), path);
        AssetManager assets = context.getAssets();
        String[] content = assets.list(path);

        if (content.length > 0) {
            for (String item : content)
                copyAssets(context, new File(path, item).getPath());
        } else {
            Log.i(TAG, "copy " + path + " to " + extPath);
            extPath.getParentFile().mkdirs();
            copyStream(assets.open(path), new FileOutputStream(extPath));
        }

        return extPath;
    }

    /**
     * Returns external files directory for the application.
     *
     * Returns path to directory on external storage which is guaranteed to be
     * unique for the running application.
     *
     * @param content Application context
     *
     * @returns Path to application directory or null if it does not exists
     *
     * @see android.content.Context#getExternalFilesDir
     * @see android.os.Environment#getExternalStorageState
     */
    public static File getApplicationDir(Context context)
        throws IOException
    {
        File dir = context.getExternalFilesDir(null);
        if (null == dir)
            throw new IOException("cannot get external files dir, " +
                                  "external storage state is " +
                                  getExternalStorageState());
        return dir;
    }

    /**
     * Copies raw asset resources to external storage of the device.
     *
     * Copies raw asset resources to external storage of the device.
     * Implementation is borrowed from Apache Commons.
     */
    private static void copyStream(InputStream source, OutputStream dest)
        throws IOException
    {
        byte[] buffer = new byte[1024];
        int nread;

        while ((nread = source.read(buffer)) != -1) {
            if (nread == 0) {
                nread = source.read();
                if (nread < 0)
                    break;

                dest.write(nread);
                continue;
            }

            dest.write(buffer, 0, nread);
        }
    }
}

/* vim: set ts=4 sw=4: */
