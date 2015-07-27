package shibafu.yukari.twitter.statusmanager;

import shibafu.yukari.twitter.AuthUserRecord;
import twitter4j.DirectMessage;

/**
 * �_�C���N�g���b�Z�[�W�̈ꎞ�I�ȕێ��ƃr���[�ւ̍Ĕz�M�@�\���������܂��B
 *
 * Created by shibafu on 2015/07/27.
 */
class MessageEventBuffer implements EventBuffer{
    private AuthUserRecord from;
    private DirectMessage directMessage;

    public MessageEventBuffer(AuthUserRecord from, DirectMessage directMessage) {
        this.from = from;
        this.directMessage = directMessage;
    }

    @Override
    public void sendBufferedEvent(StatusManager.StatusListener listener) {
        listener.onDirectMessage(from, directMessage);
    }
}
