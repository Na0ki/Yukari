package shibafu.yukari.media2;

import org.junit.Assert;
import org.junit.Test;

public class PixivTest {
    @Test
    public void resolveMedia() throws Exception {
        Pixiv pixiv = new Pixiv("https://www.pixiv.net/member_illust.php?mode=medium&illust_id=60320776");
        Media.ResolveInfo resolveInfo = pixiv.resolveMedia();
        Assert.assertNotNull(resolveInfo);
        Assert.assertNotNull(resolveInfo.getStream());

        byte[] header = new byte[2];
        int read = resolveInfo.getStream().read(header, 0, header.length);
        Assert.assertEquals(header.length, read);

        Assert.assertArrayEquals(new byte[]{(byte) 0xff, (byte) 0xd8}, header);
    }

    @Test
    public void resolveThumbnail() throws Exception {
        Pixiv pixiv = new Pixiv("https://www.pixiv.net/member_illust.php?mode=medium&illust_id=60320776");
        Media.ResolveInfo resolveInfo = pixiv.resolveThumbnail();
        Assert.assertNotNull(resolveInfo);
        Assert.assertNotNull(resolveInfo.getStream());

        byte[] header = new byte[2];
        int read = resolveInfo.getStream().read(header, 0, header.length);
        Assert.assertEquals(header.length, read);

        Assert.assertArrayEquals(new byte[]{(byte) 0xff, (byte) 0xd8}, header);
    }

}