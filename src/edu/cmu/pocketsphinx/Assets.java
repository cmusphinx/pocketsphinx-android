package edu.cmu.pocketsphinx;

import java.io.*;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;

import static android.os.Environment.getExternalStorageState;


/**
 * Provides utility methods for copying asset files to external storage.
 *
 * @author Alexander Solovets
 */
public class Assets {

    private static final String TAG = Assets.class.getSimpleName();

    /**
     * Synchronizes asset files with the content on external storage. There
     * must be special file "assets.lst" among the application assets
     * containing relative paths of assets to synchronize. If the corresponding
     * path does not exist on the external storage it is copied. If the path
     * exists checksums are compared and the asset is copied only if there is a
     * mismatch. Checksum is stored in a separate asset with the name that
     * consists of the original name and a suffix that depends on the checksum
     * algorithm (e.g. MD5). Checksum files are copied along with the
     * corresponding asset files.
     *
     * @param Context application context
     * @return path to the root of resources directory on external storage
     * @throws IOException if an I/O error occurs or "assets.lst" is missing
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
     * Copies application asset files to external storage. Recursively copies
     * asset files to a directory located on external storage and unique for
     * application. If a file already exists it will be overwritten.
     *
     * <p>In general this method should not be used to
     * synchronize application resources and is only provided for compatibility
     * with projects without "smart" asset setup. If you are looking for quick
     * and "smart" synchronization that does not overwrite existing files use
     * {@link #syncAssets(Context, String)}.
     *
     * @param context application context
     * @param path    relative path to asset file or directory
     * @return path to the root of resources directory on external storage
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
     * Returns external files directory for the application. Returns path to
     * directory on external storage which is guaranteed to be unique for the
     * running application.
     *
     * @param content application context
     * @return path to application directory or null if it does not exists
     * @throws IOException if the directory does not exist
     *
     * @see android.content.Context#getExternalFilesDir
     * @see android.os.Environment#getExternalStorageState
     */
    public static File getApplicationDir(Context context) throws IOException {
        File dir = context.getExternalFilesDir(null);
        if (null == dir)
            throw new IOException("cannot get external files dir, " +
                                  "external storage state is " +
                                  getExternalStorageState());
        return dir;
    }

    /**
     * Copies raw asset resources to external storage of the device. Copies
     * raw asset resources to external storage of the device. Implementation
     * is borrowed from Apache Commons.
     *
     * @param source source stream
     * @param dest   destination stream
     * @throws IOException if an I/O error occurs
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
