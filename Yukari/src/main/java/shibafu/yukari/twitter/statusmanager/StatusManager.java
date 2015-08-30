package shibafu.yukari.twitter.statusmanager;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.util.LongSparseArray;
import android.util.Log;
import android.util.Pair;
import android.widget.Toast;
import lombok.Value;
import org.jetbrains.annotations.NotNull;
import shibafu.yukari.R;
import shibafu.yukari.common.HashCache;
import shibafu.yukari.common.NotificationType;
import shibafu.yukari.common.Suppressor;
import shibafu.yukari.common.async.ParallelAsyncTask;
import shibafu.yukari.common.async.SimpleAsyncTask;
import shibafu.yukari.common.async.TwitterAsyncTask;
import shibafu.yukari.database.AutoMuteConfig;
import shibafu.yukari.database.CentralDatabase;
import shibafu.yukari.database.MuteConfig;
import shibafu.yukari.service.TwitterService;
import shibafu.yukari.twitter.AuthUserRecord;
import shibafu.yukari.twitter.statusimpl.*;
import shibafu.yukari.twitter.streaming.*;
import shibafu.yukari.twitter.streaming.StreamListener;
import shibafu.yukari.util.AutoRelease;
import shibafu.yukari.util.Releasable;
import shibafu.yukari.util.StringUtil;
import twitter4j.*;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Created by shibafu on 14/03/08.
 */
public class StatusManager implements Releasable {
    private static LongSparseArray<PreformedStatus> receivedStatuses = new LongSparseArray<>(512);

    private static final boolean PUT_STREAM_LOG = false;

    public static final int UPDATE_FAVED = 1;
    public static final int UPDATE_UNFAVED = 2;
    public static final int UPDATE_DELETED = 3;
    public static final int UPDATE_DELETED_DM = 4;
    public static final int UPDATE_NOTIFY = 5;
    public static final int UPDATE_FORCE_UPDATE_UI = 6;
    public static final int UPDATE_REST_COMPLETED = 7;
    public static final int UPDATE_WIPE_TWEETS = 0xff;

    private static final String LOG_TAG = "StatusManager";

    @AutoRelease private TwitterService service;
    @AutoRelease private Suppressor suppressor;
    private List<AutoMuteConfig> autoMuteConfigs;

    @AutoRelease private Context context;
    @AutoRelease private SharedPreferences sharedPreferences;
    private Handler handler;

    //通知マネージャ
    @AutoRelease private StatusNotifier notifier;

    //RT-Response Listen (Key:RTed User ID, Value:Response StandBy Status)
    private LongSparseArray<PreformedStatus> retweetResponseStandBy = new LongSparseArray<>();

    //キャッシュ
    private HashCache hashCache;

    //ステータス
    private boolean isStarted;

    //実行中の非同期REST
    private Map<Long, ParallelAsyncTask<Void, Void, Void>> workingRestQueries = new HashMap<>();

    //Streaming
    private List<StreamUser> streamUsers = new ArrayList<>();
    private Map<String, FilterStream> filterMap = new HashMap<>();
    private StreamListener listener = new StreamListener() {

        @Override
        public void onFavorite(Stream from, User user, User user2, Status status) {
            if (PUT_STREAM_LOG) Log.d("onFavorite", String.format("f:%s s:%d", from.getUserRecord().ScreenName, status.getId()));
            pushEventQueue(new UpdateEventBuffer(from.getUserRecord(), UPDATE_FAVED, new FavFakeStatus(status.getId(), true, user)));

            userUpdateDelayer.enqueue(status.getUser(), user, user2);

            if (from.getUserRecord().NumericId == user.getId()) return;

            PreformedStatus preformedStatus = new PreformedStatus(status, from.getUserRecord());
            boolean[] mute = suppressor.decision(preformedStatus);
            boolean[] muteUser = suppressor.decisionUser(user);
            if (!(mute[MuteConfig.MUTE_NOTIF_FAV] || muteUser[MuteConfig.MUTE_NOTIF_FAV])) {
                notifier.showNotification(R.integer.notification_faved, preformedStatus, user);
                createHistory(from, HistoryStatus.KIND_FAVED, user, preformedStatus);
            }
        }

        @Override
        public void onUnfavorite(Stream from, User user, User user2, Status status) {
            if (PUT_STREAM_LOG) Log.d("onUnfavorite", String.format("f:%s s:%s", from.getUserRecord().ScreenName, status.getText()));
            pushEventQueue(new UpdateEventBuffer(from.getUserRecord(), UPDATE_UNFAVED, new FavFakeStatus(status.getId(), false, user)));
        }

        @Override
        public void onFollow(Stream from, User user, User user2) {

        }

        @Override
        public void onDirectMessage(Stream from, DirectMessage directMessage) {
            userUpdateDelayer.enqueue(directMessage.getSender());
            pushEventQueue(new MessageEventBuffer(from.getUserRecord(), directMessage));

            boolean checkOwn = service.isMyTweet(directMessage) != null;
            if (!checkOwn) {
                notifier.showNotification(R.integer.notification_message, directMessage, directMessage.getSender());
            }
        }

        @Override
        public void onBlock(Stream from, User user, User user2) {

        }

        @Override
        public void onUnblock(Stream from, User user, User user2) {

        }

        @Override
        public void onStatus(Stream from, Status status) {
            statusQueue.enqueue(from, status);
        }

        @Override
        public void onDelete(Stream from, StatusDeletionNotice statusDeletionNotice) {
            pushEventQueue(new UpdateEventBuffer(from.getUserRecord(), UPDATE_DELETED, new FakeStatus(statusDeletionNotice.getStatusId())));
        }

        @SuppressWarnings("unused")
        public void onWipe(Stream from) {
            pushEventQueue(new UpdateEventBuffer(from.getUserRecord(), UPDATE_WIPE_TWEETS, new FakeStatus(0)));
        }

        @SuppressWarnings("unused")
        public void onForceUpdateUI(Stream from) {
            pushEventQueue(new UpdateEventBuffer(from.getUserRecord(), UPDATE_FORCE_UPDATE_UI, new FakeStatus(0)));
        }

        @SuppressWarnings("unused")
        public void onRestCompleted(Stream from) {
            pushEventQueue(new UpdateEventBuffer(from.getUserRecord(), UPDATE_REST_COMPLETED, new RestCompletedStatus(((RestStream) from).getTag())));
        }

        @Override
        public void onDeletionNotice(Stream from, long directMessageId, long userId) {
            pushEventQueue(new UpdateEventBuffer(from.getUserRecord(), UPDATE_DELETED_DM, new FakeStatus(directMessageId)));
        }

        private void createHistory(Stream from, int kind, User eventBy, Status status) {
            HistoryStatus historyStatus = new HistoryStatus(System.currentTimeMillis(), kind, eventBy, status);
            EventBuffer eventBuffer = new UpdateEventBuffer(from.getUserRecord(), UPDATE_NOTIFY, historyStatus);
            pushEventQueue(eventBuffer);
            updateBuffer.add(eventBuffer);
        }

        private void pushEventQueue(EventBuffer event) {
            for (StatusListener sl : statusListeners) {
                event.sendBufferedEvent(sl);
            }
            for (Map.Entry<StatusListener, Queue<EventBuffer>> e : statusBuffer.entrySet()) {
                e.getValue().offer(event);
            }
        }

        private boolean deliver(StatusListener sl, Stream from) {
            if (sl.getStreamFilter() == null && from instanceof StreamUser) {
                return true;
            }
            else if (from instanceof FilterStream &&
                    ((FilterStream) from).getQuery().equals(sl.getStreamFilter())) {
                return true;
            }
            else if (from instanceof RestStream &&
                    ((RestStream) from).getTag().equals(sl.getRestTag())) {
                return true;
            }
            else return false;
        }

        private StatusQueue statusQueue = new StatusQueue();
        class StatusQueue {
            private BlockingQueue<Pair<Stream, Status>> queue = new LinkedBlockingQueue<>();

            public StatusQueue() {
                new Thread(new Worker(), "StatusQueue").start();
            }

            public void enqueue(Stream from, Status status) {
                queue.offer(new Pair<>(from, status));
            }

            class Worker implements Runnable {

                private void onStatus(final Stream from, Status status) {
                    if (from == null || status == null || status.getUser() == null || statusListeners == null) {
                        if (PUT_STREAM_LOG) Log.d(LOG_TAG, "onStatus, NullPointer!");
                        return;
                    }
                    if (PUT_STREAM_LOG) Log.d(LOG_TAG,
                            String.format("onStatus(Registered Listener %d): %s from %s :@%s %s",
                                    statusListeners.size(),
                                    StringUtil.formatDate(status.getCreatedAt()), from.getUserRecord().ScreenName,
                                    status.getUser().getScreenName(), status.getText()));
                    userUpdateDelayer.enqueue(status.getUser());
                    if (status.isRetweet()) {
                        userUpdateDelayer.enqueue(status.getRetweetedStatus().getUser());
                    }
                    AuthUserRecord user = from.getUserRecord();

                    //自分のツイートかどうかマッチングを行う
                    AuthUserRecord checkOwn = service.isMyTweet(status);
                    if (checkOwn != null) {
                        //自分のツイートであれば受信元は発信元アカウントということにする
                        user = checkOwn;
                    }

                    PreformedStatus preformedStatus = new PreformedStatus(status, user);

                    //オートミュート判定を行う
                    AutoMuteConfig autoMute = null;
                    for (AutoMuteConfig config : autoMuteConfigs) {
                        boolean match = false;
                        switch (config.getMatch()) {
                            case AutoMuteConfig.MATCH_EXACT:
                                match = preformedStatus.getText().equals(config.getQuery());
                                break;
                            case AutoMuteConfig.MATCH_PARTIAL:
                                match = preformedStatus.getText().contains(config.getQuery());
                                break;
                            case AutoMuteConfig.MATCH_REGEX:
                                try {
                                    Pattern pattern = Pattern.compile(config.getQuery());
                                    Matcher matcher = pattern.matcher(preformedStatus.getText());
                                    match = matcher.find();
                                } catch (PatternSyntaxException ignore) {}
                                break;
                        }
                        if (match) {
                            autoMute = config;
                        }
                    }
                    if (autoMute != null) {
                        Log.d(LOG_TAG, "AutoMute! : @" + preformedStatus.getSourceUser().getScreenName());
                        getDatabase().updateRecord(autoMute.getMuteConfig(preformedStatus.getSourceUser().getScreenName(), System.currentTimeMillis() + 3600000));
                        service.updateMuteConfig();
                    }

                    //ミュートチェックを行う
                    boolean[] mute = suppressor.decision(preformedStatus);
                    if (mute[MuteConfig.MUTE_IMAGE_THUMB]) {
                        preformedStatus.setCensoredThumbs(true);
                    }

                    boolean muted = mute[MuteConfig.MUTE_TWEET_RTED] ||
                            (!preformedStatus.isRetweet() && mute[MuteConfig.MUTE_TWEET]) ||
                            (preformedStatus.isRetweet() && mute[MuteConfig.MUTE_RETWEET]);
                    PreformedStatus deliverStatus = new MetaStatus(preformedStatus, from.getClass().getSimpleName());
                    PreformedStatus standByStatus = retweetResponseStandBy.get(preformedStatus.getUser().getId());
                    if (new NotificationType(sharedPreferences.getInt("pref_notif_respond", 0)).isEnabled() &&
                            !preformedStatus.isRetweet() &&
                            standByStatus != null && !preformedStatus.getText().startsWith("@")) {
                        //Status is RT-Respond
                        retweetResponseStandBy.remove(preformedStatus.getUser().getId());
                        deliverStatus = new RespondNotifyStatus(preformedStatus, standByStatus);
                        notifier.showNotification(R.integer.notification_respond, deliverStatus, deliverStatus.getUser());
                    }
                    for (StatusListener sl : statusListeners) {
                        if (deliver(sl, from)) {
                            sl.onStatus(user, deliverStatus, muted);
                        }
                    }
                    deliverStatus = (deliverStatus instanceof MetaStatus)? new MetaStatus(preformedStatus, "queued") : deliverStatus;
                    for (Map.Entry<StatusListener, Queue<EventBuffer>> e : statusBuffer.entrySet()) {
                        if (deliver(e.getKey(), from)) {
                            e.getValue().offer(new StatusEventBuffer(user, deliverStatus, muted));
                        }
                    }

                    if (status.isRetweet() &&
                            !mute[MuteConfig.MUTE_NOTIF_RT] &&
                            status.getRetweetedStatus().getUser().getId() == user.NumericId &&
                            checkOwn == null) {
                        notifier.showNotification(R.integer.notification_retweeted, preformedStatus, status.getUser());
                        createHistory(from, HistoryStatus.KIND_RETWEETED, status.getUser(), preformedStatus.getRetweetedStatus());

                        //Put Response Stand-By
                        retweetResponseStandBy.put(preformedStatus.getUser().getId(), preformedStatus);
                    }
                    else if (!status.isRetweet() && !mute[MuteConfig.MUTE_NOTIF_MENTION]) {
                        UserMentionEntity[] userMentionEntities = status.getUserMentionEntities();
                        for (UserMentionEntity ume : userMentionEntities) {
                            if (ume.getId() == user.NumericId) {
                                notifier.showNotification(R.integer.notification_replied, preformedStatus, status.getUser());
                            }
                        }
                    }

                    HashtagEntity[] hashtagEntities = status.getHashtagEntities();
                    for (HashtagEntity he : hashtagEntities) {
                        hashCache.put("#" + he.getText());
                    }

                    receivedStatuses.put(preformedStatus.getId(), preformedStatus);
                    loadQuotedEntities(preformedStatus);
                }

                @SuppressWarnings("InfiniteLoopStatement")
                @Override
                public void run() {
                    while (true) {
                        Pair<Stream, Status> p;
                        try {
                            p = queue.take();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                            continue;
                        }

                        onStatus(p.first, p.second);
                    }
                }
            }
        }
    };

    private List<StatusListener> statusListeners = new ArrayList<>();
    private Map<StatusListener, Queue<EventBuffer>> statusBuffer = new HashMap<>();
    private List<EventBuffer> updateBuffer = new ArrayList<>();

    private UserUpdateDelayer userUpdateDelayer;

    public StatusManager(TwitterService service) {
        this.context = this.service = service;

        this.suppressor = service.getSuppressor();
        this.sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        this.handler = new Handler();

        //通知マネージャの開始
        notifier = new StatusNotifier(service);

        //ハッシュタグキャッシュのロード
        hashCache = new HashCache(context);

        //遅延処理スレッドの開始
        userUpdateDelayer = new UserUpdateDelayer(service.getDatabase());
    }

    @Override
    public void release() {
        streamUsers.clear();
        statusListeners.clear();
        statusBuffer.clear();

        for (ParallelAsyncTask<Void, Void, Void> task : workingRestQueries.values()) {
            if (task != null && !task.isCancelled()) {
                task.cancel(true);
            }
        }

        receivedStatuses.clear();

        notifier.release();
        userUpdateDelayer.shutdown();

        Releasable.super.release();
    }

    private CentralDatabase getDatabase() {
        return service == null ? null : service.getDatabase();
    }

    public HashCache getHashCache() {
        return hashCache;
    }

    public ArrayList<AuthUserRecord> getActiveUsers() {
        ArrayList<AuthUserRecord> activeUsers = new ArrayList<>();
        for (StreamUser su : streamUsers) {
            activeUsers.add(su.getUserRecord());
        }
        return activeUsers;
    }

    public int getStreamUsersCount() {
        return streamUsers.size();
    }

    //<editor-fold desc="Connectivity">
    public boolean isStarted() {
        return isStarted;
    }

    public void start() {
        Log.d(LOG_TAG, "call start");

        for (StreamUser u : streamUsers) {
            u.start();
        }
        for (Map.Entry<String, FilterStream> e : filterMap.entrySet()) {
            e.getValue().start();
        }

        isStarted = true;
    }

    public void stop() {
        Log.d(LOG_TAG, "call stop");

        for (Map.Entry<String, FilterStream> e : filterMap.entrySet()) {
            e.getValue().stop();
        }
        for (StreamUser su : streamUsers) {
            su.stop();
        }

        isStarted = false;
    }

    public void startAsync() {
        ParallelAsyncTask.executeParallel(this::start);
    }

    public void stopAsync() {
        ParallelAsyncTask.executeParallel(this::stop);
    }

    public void shutdownAll() {
        Log.d(LOG_TAG, "call shutdownAll");

        for (Map.Entry<String, FilterStream> e : filterMap.entrySet()) {
            e.getValue().stop();
        }
        for (StreamUser su : streamUsers) {
            su.stop();
        }

        release();
    }

    public void startUserStream(AuthUserRecord userRecord) {
        StreamUser su = new StreamUser(context, userRecord);
        su.setListener(listener);
        streamUsers.add(su);
        if (isStarted) {
            su.start();
        }
    }

    public void stopUserStream(AuthUserRecord userRecord) {
        StreamUser remove = null;
        for (StreamUser su : streamUsers) {
            if (su.getUserRecord().equals(userRecord)) {
                su.stop();
                remove = su;
                break;
            }
        }
        if (remove != null) {
            statusListeners.remove(remove);
            streamUsers.remove(remove);
        }
    }

    public void startFilterStream(String query, AuthUserRecord manager) {
        if (!filterMap.containsKey(query)) {
            FilterStream filterStream = new FilterStream(context, manager, query);
            filterStream.setListener(listener);
            filterMap.put(query, filterStream);
            if (isStarted) {
                filterStream.start();
                showToast("Start FilterStream:" + query);
            }
        }
    }

    public void stopFilterStream(String query) {
        if (filterMap.containsKey(query)) {
            FilterStream filterStream = filterMap.get(query);
            filterStream.stop();
            filterMap.remove(query);
            showToast("Stop FilterStream:" + query);
        }
    }

    public void reconnectAsync() {
        SimpleAsyncTask.execute(() -> {
            for (Map.Entry<String, FilterStream> e : filterMap.entrySet()) {
                e.getValue().stop();
                e.getValue().start();
            }

            for (final StreamUser su : streamUsers) {
                su.stop();
                su.start();
            }
        });
    }
    //</editor-fold>

    //<editor-fold desc="Subscribe">
    public void addStatusListener(StatusListener l) {
        if (statusListeners != null && !statusListeners.contains(l)) {
            statusListeners.add(l);
            Log.d("TwitterService", "Added StatusListener");
            if (statusBuffer.containsKey(l)) {
                Queue<EventBuffer> eventBuffers = statusBuffer.get(l);
                Log.d("TwitterService", "バッファ内に" + eventBuffers.size() + "件のツイートが保持されています.");
                while (!eventBuffers.isEmpty()) {
                    eventBuffers.poll().sendBufferedEvent(l);
                }
                statusBuffer.remove(l);
            } else {
                Log.d("TwitterService", String.format("ヒストリUIと接続されました. %d件のイベントがバッファ内に保持されています.", updateBuffer.size()));
                for (EventBuffer eventBuffer : updateBuffer) {
                    eventBuffer.sendBufferedEvent(l);
                }
            }
        }
    }

    public void removeStatusListener(StatusListener l) {
        if (statusListeners != null && statusListeners.contains(l)) {
            statusListeners.remove(l);
            Log.d("TwitterService", "Removed StatusListener");
            statusBuffer.put(l, new LinkedList<>());
        }
    }
    //</editor-fold>

    private void showToast(final String text) {
        handler.post(() -> Toast.makeText(context.getApplicationContext(), text, Toast.LENGTH_SHORT).show());
    }

    public static LongSparseArray<PreformedStatus> getReceivedStatuses() {
        return receivedStatuses;
    }

    @SuppressWarnings("TryWithIdenticalCatches")
    public void sendFakeBroadcast(String methodName, Class[] argTypes, Object... args) {
        try {
            Class[] newArgTypes = new Class[argTypes.length+1];
            newArgTypes[0] = Stream.class;
            System.arraycopy(argTypes, 0, newArgTypes, 1, argTypes.length);
            Method m = listener.getClass().getMethod(methodName, newArgTypes);
            Object[] newArgs = new Object[args.length+1];
            System.arraycopy(args, 0, newArgs, 1, args.length);
            for (StreamUser streamUser : streamUsers) {
                newArgs[0] = streamUser;
                m.invoke(listener, newArgs);
            }
            for (Map.Entry<String, FilterStream> entry : filterMap.entrySet()) {
                newArgs[0] = entry.getValue();
                m.invoke(listener, newArgs);
            }
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
    }

    public void setAutoMuteConfigs(List<AutoMuteConfig> autoMuteConfigs) {
        this.autoMuteConfigs = autoMuteConfigs;
    }

    public void loadQuotedEntities(PreformedStatus preformedStatus) {
        @Value class Params {
            long id;
            AuthUserRecord userRecord;
        }

        for (Long id : preformedStatus.getQuoteEntities()) {
            if (receivedStatuses.indexOfKey(id) < 0) {
                new TwitterAsyncTask<Params>(context) {

                    @Override
                    protected TwitterException doInBackground(@NotNull Params... params) {
                        Twitter twitter = service.getTwitter();
                        AuthUserRecord userRecord = params[0].getUserRecord();
                        twitter.setOAuthAccessToken(userRecord.getAccessToken());
                        try {
                            twitter4j.Status s = twitter.showStatus(params[0].getId());
                            receivedStatuses.put(s.getId(), new PreformedStatus(s, userRecord));
                        } catch (TwitterException e) {
                            e.printStackTrace();
                            return e;
                        }
                        return null;
                    }

                    @Override
                    protected void onPostExecute(TwitterException e) {
                        sendFakeBroadcast("onForceUpdateUI", new Class[]{});
                    }
                }.executeParallel(new Params(id, preformedStatus.getRepresentUser()));
            }
        }
    }

    public void requestRestQuery(final String tag, final AuthUserRecord userRecord, final boolean appendLoadMarker, final RestQuery query) {
        final boolean isNarrowMode = sharedPreferences.getBoolean("pref_narrow", false);
        final long taskKey = System.currentTimeMillis();
        ParallelAsyncTask<Void, Void, Void> task = new ParallelAsyncTask<Void, Void, Void>() {
            private TwitterException exception;

            @Override
            protected Void doInBackground(Void... params) {
                Log.d("StatusManager", String.format("Begin AsyncREST: @%s - %s -> %s", userRecord.ScreenName, tag, query.getClass().getName()));

                Twitter twitter = service.getTwitter();
                twitter.setOAuthAccessToken(userRecord.getAccessToken());

                try {
                    Paging paging = new Paging();
                    if (!isNarrowMode) paging.setCount(60);

                    List<twitter4j.Status> responseList = query.getRestResponses(twitter, paging);
                    if (responseList == null) {
                        responseList = new ArrayList<>();
                    }
                    if (appendLoadMarker) {
                        LoadMarkerStatus markerStatus;

                        if (responseList.isEmpty()) {
                            markerStatus = new LoadMarkerStatus(
                                    paging.getMaxId(),
                                    userRecord.NumericId);
                        } else {
                            markerStatus = new LoadMarkerStatus(
                                    responseList.get(responseList.size() - 1).getId(),
                                    userRecord.NumericId);
                        }

                        responseList.add(markerStatus);
                    }

                    if (isCancelled()) return null;

                    // StreamManagerに流す
                    Stream stream = new RestStream(context, userRecord, tag);
                    try {
                        listener.getClass().getMethod("onRestCompleted", Stream.class).invoke(listener, stream);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    for (twitter4j.Status status : responseList) {
                        listener.onStatus(stream, status);
                    }

                    Log.d("StatusManager", String.format("Received REST: @%s - %s - %d statuses", userRecord.ScreenName, tag, responseList.size()));
                } catch (TwitterException e) {
                    e.printStackTrace();
                    exception = e;
                }
                return null;
            }

            @Override
            protected void onPostExecute(Void result) {
                workingRestQueries.remove(taskKey);
                if (exception != null) {
                    switch (exception.getStatusCode()) {
                        case 429:
                            Toast.makeText(context,
                                    String.format("[@%s]\nレートリミット超過\n次回リセット: %d分%d秒後\n時間を空けて再度操作してください",
                                            userRecord.ScreenName,
                                            exception.getRateLimitStatus().getSecondsUntilReset() / 60,
                                            exception.getRateLimitStatus().getSecondsUntilReset() % 60),
                                    Toast.LENGTH_SHORT).show();
                            break;
                        default:
                            Toast.makeText(context,
                                    String.format("[@%s]\n通信エラー: %d:%d\n%s",
                                            userRecord.ScreenName,
                                            exception.getStatusCode(),
                                            exception.getErrorCode(),
                                            exception.getErrorMessage()),
                                    Toast.LENGTH_SHORT).show();
                            break;
                    }
                }
            }
        };
        task.executeParallel();
        workingRestQueries.put(taskKey, task);
        Log.d("StatusManager", String.format("Requested REST: @%s - %s", userRecord.ScreenName, tag));
    }

}