package shibafu.yukari.twitter.statusmanager;

import shibafu.yukari.twitter.statusmanager.StatusManager;

/**
 * �z�M����ꎞ�I�Ɏ�������M�X�e�[�^�X�́A�f�[�^�̕ێ��ƍĔz�M�@�\��񋟂��܂��B
 *
 * Created by shibafu on 2015/07/27.
 */
interface EventBuffer {
    /**
     * �w��̃��X�i�ɑ΂��ăX�e�[�^�X�̍Ĕz�M�����s���܂��B
     * @param listener �z�M�惊�X�i
     */
    void sendBufferedEvent(StatusManager.StatusListener listener);
}
