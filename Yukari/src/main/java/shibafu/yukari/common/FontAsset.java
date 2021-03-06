package shibafu.yukari.common;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.graphics.Typeface;
import android.preference.PreferenceManager;
import android.util.Log;

import java.io.File;

/**
 * Created by Shibafu on 13/08/02.
 */
public class FontAsset {

    public static final String SYSTEM_FONT_ID = "*SYS*";
    public static final String BUNDLE_FONT_ID = "*BUNDLE*";
    public static final String FONT_NAME = "Koruri-Regular.ttf";
    private static FontAsset instance;
    private Typeface font;

    private FontAsset() {
        instance = null;
    }
    private FontAsset(Typeface typeface) {
        font = typeface;
    }

    public static FontAsset getInstance(Context context) {
        if (instance == null) {
            try {
                SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
                String fileName = preferences.getString("pref_font_file", BUNDLE_FONT_ID);
                if (preferences.getBoolean("pref_font_face", false) || SYSTEM_FONT_ID.equals(fileName)) {
                    Log.d("FontAsset", "システムフォントを使用します");
                    instance = new FontAsset(Typeface.DEFAULT);
                } else if (BUNDLE_FONT_ID.equals(fileName)) {
                    Log.d("FontAsset", "バンドルフォントを使用します");
                    instance = new FontAsset(getBundleFont(context.getAssets()));
                } else if (checkFontFileExists(context, fileName)) {
                    Log.d("FontAsset", fileName + "を使用します");
                    instance = new FontAsset(Typeface.createFromFile(getFontFileExtPath(context, fileName)));
                }
                if (instance.getFont() == null) {
                    throw new RuntimeException("フォント読み込みに失敗しました");
                }
            } catch (RuntimeException e) {
                Log.e("FontAsset", "Font Error!!");
                instance = new FontAsset(getBundleFont(context.getAssets()));
            }
            Log.d("FontAsset", "Font Loaded!");
        }
        return instance;
    }

    public static void reloadInstance(Context context) {
        instance = null;
        getInstance(context);
    }

    public static boolean checkFontFileExists(Context context, String filename) {
        File dir = new File(context.getExternalFilesDir(null), "font");
        if (!dir.exists()) {
            dir.mkdirs();
        }

        return new File(dir, filename).exists();
    }

    public static File getFontFileExtPath(Context context, String fileName) {
        return new File(new File(context.getExternalFilesDir(null), "font"), fileName);
    }

    public static Typeface getBundleFont(AssetManager assetManager) {
        return Typeface.createFromAsset(assetManager, FONT_NAME);
    }

    public Typeface getFont() {
        return font;
    }
}
