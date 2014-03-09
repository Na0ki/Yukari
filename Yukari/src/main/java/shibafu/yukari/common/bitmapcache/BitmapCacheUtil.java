package shibafu.yukari.common.bitmapcache;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Environment;
import android.support.v4.util.LruCache;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import shibafu.yukari.util.StringUtil;

/**
 * Created by shibafu on 14/03/09.
 */
class BitmapCacheUtil {
    public static Bitmap getImage(String key, Context context,
                                  String directory, LruCache<String, Bitmap> cache) {
        key = StringUtil.encodeKey(key);
        //メモリ上のキャッシュから取得を試みる
        Bitmap image = cache.get(key);
        if (image == null && context != null) {
            //無かったらファイルから取得を試みる
            File cacheDir;
            if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
                cacheDir = context.getExternalCacheDir();
            }
            else {
                cacheDir = context.getCacheDir();
            }
            cacheDir = new File(cacheDir, directory);
            if (!cacheDir.exists()) {
                cacheDir.mkdirs();
            }
            File cacheFile = new File(cacheDir, key);
            if (cacheFile.exists()) {
                //存在していたらファイルを読み込む
                image = BitmapFactory.decodeFile(cacheFile.getPath());
                cacheFile.setLastModified(System.currentTimeMillis());
            }
        }
        return image;
    }

    public static void putImage(String key, Bitmap image, Context context,
                                String directory, LruCache<String, Bitmap> cache) {
        if (key == null || image == null) return;

        key = StringUtil.encodeKey(key);
        //メモリ上のキャッシュと、ファイルに書き込む
        cache.put(key, image);
        if (context != null) {
            File cacheDir;
            if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
                cacheDir = context.getExternalCacheDir();
            }
            else {
                cacheDir = context.getCacheDir();
            }
            cacheDir = new File(cacheDir, directory);
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
}