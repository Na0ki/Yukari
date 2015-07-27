package shibafu.yukari.twitter.statusmanager;

import shibafu.yukari.twitter.AuthUserRecord;
import shibafu.yukari.twitter.statusimpl.PreformedStatus;
import twitter4j.DirectMessage;
import twitter4j.Status;

/**
 * �X�g���[�~���O�C�x���g�̎�M�@�\��񋟂��܂��B
 *
 * Created by shibafu on 2015/07/28.
 */
public interface StatusListener {
    String getStreamFilter();

    void onStatus(AuthUserRecord from, PreformedStatus status, boolean muted);

    void onDirectMessage(AuthUserRecord from, DirectMessage directMessage);

    void onUpdatedStatus(AuthUserRecord from, int kind, Status status);
}
