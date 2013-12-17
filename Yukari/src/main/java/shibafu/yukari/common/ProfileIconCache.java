package shibafu.yukari.common;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Environment;
import android.support.v4.util.LruCache;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

/**
 * Created by Shibafu on 13/10/28.
 */
public class ProfileIconCache {

    private static int maxCacheSize = 8 * 1024 * 1024;

    private static LruCache<String, Bitmap> lruCache = new LruCache<String, Bitmap>(maxCacheSize) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return value.getRowBytes() * value.getHeight();
        }
    };

    public static Bitmap getImage(String key, Context context) {
        key = encodeKey(key);
        //メモリ上のキャッシュから取得を試みる
        Bitmap image = lruCache.get(key);
        if (image == null && context != null) {
            //無かったらファイルから取得を試みる
            File cacheDir;
            if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
                cacheDir = context.getExternalCacheDir();
            }
            else {
                cacheDir = context.getCacheDir();
            }
            cacheDir = new File(cacheDir, "icon");
            if (!cacheDir.exists()) {
                cacheDir.mkdirs();
            }
            File cacheFile = new File(cacheDir, key);
            if (cacheFile.exists()) {
                //存在していたらファイルを読み込む
                image = BitmapFactory.decodeFile(cacheFile.getPath());
            }
        }
        return image;
    }

    public static void putImage(String key, Bitmap image, Context context) {
        key = encodeKey(key);
        //メモリ上のキャッシュと、ファイルに書き込む
        lruCache.put(key, image);
        if (context != null) {
            File cacheDir;
            if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
                cacheDir = context.getExternalCacheDir();
            }
            else {
                cacheDir = context.getCacheDir();
            }
            cacheDir = new File(cacheDir, "icon");
            if (!cacheDir.exists()) {
                cacheDir.mkdirs();
            }
            File cacheFile = new File(cacheDir, key);
            synchronized (context) {
                if (!cacheFile.exists()) {
                    //存在していなかったらファイルを書き込む
                    try {
                        FileOutputStream fos = new FileOutputStream(cacheFile);
                        image.compress(Bitmap.CompressFormat.PNG, 100, fos);
                        fos.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    private static String encodeKey(String key) {
        try {
            return URLEncoder.encode(key, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            return key;
        }
    }

}