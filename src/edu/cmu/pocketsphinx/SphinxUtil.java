package edu.cmu.pocketsphinx;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;

import android.content.Context;

import android.content.res.AssetManager;

import android.util.Log;


public class SphinxUtil {

    private static final String TAG = SphinxUtil.class.getSimpleName();

    public static File syncAssets(Context context, String path) 
        throws IOException
    {
        File root = new File(context.getExternalFilesDir(null), path);
        copyAsset(context.getAssets(), path, root);

        return root;
    }

    private static void copyAsset(AssetManager assets, String src, File dst)
        throws IOException
    {
        String[] content = assets.list(src);

        if (content.length > 0) {
            for (String path : content) {
                String childAsset = new File(src, path).getPath();
                copyAsset(assets, childAsset, new File(dst, path));
            }
        } else {
            if (!dst.exists()) {
                dst.getParentFile().mkdirs();
                Log.d(TAG, "copy " + src + " to " + dst);
                copyStream(assets.open(src), new FileOutputStream(dst));
            } else {
                Log.d(TAG, "skip " + src + " - file exists");
            }
        }
    }

    /**
     * Copies raw asset resources to external storage of the device.
     *
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
