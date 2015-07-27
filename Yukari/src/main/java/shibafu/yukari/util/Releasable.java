package shibafu.yukari.util;

import java.lang.reflect.Field;

/**
 * ���\�[�X�̉�����K�v�ł��邱�Ƃ𖾎����A�܂����̎�i��񋟂��܂��B
 *
 * Created by shibafu on 2015/07/28.
 */
public interface Releasable {
    default void release() {
        for (Field field : getClass().getDeclaredFields()) {
            AutoRelease autoRelease = field.getAnnotation(AutoRelease.class);
            if (autoRelease != null) {
                try {
                    field.setAccessible(true);
                    field.set(this, null);
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
