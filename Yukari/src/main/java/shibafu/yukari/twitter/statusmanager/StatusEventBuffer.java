package shibafu.yukari.twitter.statusmanager;

import shibafu.yukari.twitter.AuthUserRecord;
import shibafu.yukari.twitter.statusimpl.PreformedStatus;
import twitter4j.Status;

/**
 * �c�C�[�g�̈ꎞ�I�ȕێ��ƃr���[�ւ̍Ĕz�M�@�\���������܂��B
 *
 * Created by shibafu on 2015/07/27.
 */
class StatusEventBuffer implements EventBuffer{
    private AuthUserRecord from;
    private Status status;
    private boolean muted;

    public StatusEventBuffer(AuthUserRecord from, PreformedStatus status, boolean muted) {
        this.from = from;
        this.status = status;
        this.muted = muted;
    }

    @Override
    public void sendBufferedEvent(StatusManager.StatusListener listener) {
        listener.onStatus(from, (PreformedStatus) status, muted);
    }
}
