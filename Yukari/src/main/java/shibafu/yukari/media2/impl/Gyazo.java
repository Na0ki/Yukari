package shibafu.yukari.media2.impl;

import android.support.annotation.NonNull;
import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import shibafu.yukari.media2.MemoizeMedia;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class Gyazo extends MemoizeMedia {
    private String mediaUrl;
    private String thumbUrl;

    /**
     * @param browseUrl メディアの既知のURL
     */
    public Gyazo(@NonNull String browseUrl) {
        super(browseUrl.replace("http://", "https://"));
    }

    @Override
    protected String resolveMediaUrl() throws IOException {
        if (mediaUrl == null) {
            resolveInternal();
        }
        return mediaUrl;
    }

    @Override
    protected String resolveThumbnailUrl() throws IOException {
        if (thumbUrl == null) {
            resolveInternal();
        }
        return thumbUrl;
    }

    @Override
    public boolean canPreview() {
        return true;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Gyazo)) return false;
        if (!super.equals(o)) return false;

        Gyazo gyazo = (Gyazo) o;

        if (mediaUrl != null ? !mediaUrl.equals(gyazo.mediaUrl) : gyazo.mediaUrl != null) return false;
        return thumbUrl != null ? thumbUrl.equals(gyazo.thumbUrl) : gyazo.thumbUrl == null;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (mediaUrl != null ? mediaUrl.hashCode() : 0);
        result = 31 * result + (thumbUrl != null ? thumbUrl.hashCode() : 0);
        return result;
    }

    private void resolveInternal() throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(getBrowseUrl() + ".json").openConnection();
        conn.setReadTimeout(10000);
        BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        StringBuilder sb = new StringBuilder();
        try {
            String s;
            while ((s = br.readLine()) != null) {
                sb.append(s);
            }
        } finally {
            br.close();
            conn.disconnect();
        }

        GyazoResponse response = new Gson().fromJson(sb.toString(), GyazoResponse.class);
        mediaUrl = response.url;
        thumbUrl = response.thumbUrl;
    }

    private static class GyazoResponse {
        public String url;
        @SerializedName("thumb_url")
        public String thumbUrl;
    }
}
