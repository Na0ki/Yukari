package shibafu.yukari.mastodon

import android.content.Intent
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.sys1yagi.mastodon4j.MastodonClient
import com.sys1yagi.mastodon4j.api.Handler
import com.sys1yagi.mastodon4j.api.Retryable
import com.sys1yagi.mastodon4j.api.Shutdownable
import com.sys1yagi.mastodon4j.api.entity.Notification
import com.sys1yagi.mastodon4j.api.entity.Status
import com.sys1yagi.mastodon4j.api.exception.Mastodon4jRequestException
import com.sys1yagi.mastodon4j.api.method.Streaming
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import shibafu.yukari.database.Provider
import shibafu.yukari.database.StreamChannelState
import shibafu.yukari.entity.NotifyHistory
import shibafu.yukari.linkage.ProviderStream
import shibafu.yukari.linkage.StreamChannel
import shibafu.yukari.linkage.TimelineHub
import shibafu.yukari.mastodon.entity.DonStatus
import shibafu.yukari.mastodon.entity.DonUser
import shibafu.yukari.service.TwitterService
import shibafu.yukari.twitter.AuthUserRecord
import shibafu.yukari.util.putDebugLog

class MastodonStream : ProviderStream {
    override var channels: List<StreamChannel> = emptyList()
        private set

    private lateinit var service: TwitterService

    override fun onCreate(service: TwitterService) {
        Log.d(LOG_TAG, "onCreate")

        this.service = service

        service.users.forEach { user ->
            if (user.Provider.apiType == Provider.API_MASTODON) {
                addUser(user)
            }
        }
    }

    override fun onStart() {
        Log.d(LOG_TAG, "onStart")

        val channelStates = service.database.getRecords(StreamChannelState::class.java)
        channels.forEach { ch ->
            if (!ch.isRunning) {
                val state = channelStates.firstOrNull { it.accountId == ch.userRecord.InternalId && it.channelId == ch.channelId }
                if (state != null && state.isActive) {
                    ch.start()
                }
            }
        }
    }

    override fun onDestroy() {
        Log.d(LOG_TAG, "onDestroy")

        channels.forEach(StreamChannel::stop)
        channels = emptyList()
    }

    override fun addUser(userRecord: AuthUserRecord): List<StreamChannel> {
        Log.d(LOG_TAG, "Add user: @${userRecord.ScreenName}")

        if (channels.any { it.userRecord == userRecord }) {
            Log.d(LOG_TAG, "@${userRecord.ScreenName} is already added.")
            return emptyList()
        }

        val ch = listOf(
                UserStreamChannel(service, userRecord),
                LocalStreamChannel(service, userRecord),
                PublicStreamChannel(service, userRecord)
        )
        channels += ch
        return ch
    }

    override fun removeUser(userRecord: AuthUserRecord) {
        Log.d(LOG_TAG, "Remove user: @${userRecord.ScreenName}")

        val ch = channels.filter { it.userRecord == userRecord }
        ch.forEach(StreamChannel::stop)
        channels -= ch
    }

    fun isConnectedHashTagStream(userRecord: AuthUserRecord, tag: String, scope: Scope): Boolean {
        return channels.any { it.userRecord == userRecord && it is HashTagStreamChannel && it.scope == scope && it.tag == tag }
    }

    fun startHashTagStream(userRecord: AuthUserRecord, tag: String, scope: Scope) {
        if (channels.any { it.userRecord == userRecord && it is HashTagStreamChannel && it.scope == scope && it.tag == tag }) {
            return
        }

        val ch = HashTagStreamChannel(service, userRecord, tag, scope)
        ch.start()
        channels += ch

        android.os.Handler(Looper.getMainLooper()).post {
            Toast.makeText(service.applicationContext, "Start HashTagStream:$tag", Toast.LENGTH_SHORT).show()
        }
    }

    fun stopHashTagStream(userRecord: AuthUserRecord, tag: String, scope: Scope) {
        val ch = channels.first { it.userRecord == userRecord && it is HashTagStreamChannel && it.scope == scope && it.tag == tag }
        ch.stop()
        channels -= ch

        android.os.Handler(Looper.getMainLooper()).post {
            Toast.makeText(service.applicationContext, "Stop HashTagStream:$tag", Toast.LENGTH_SHORT).show()
        }
    }

    enum class Scope(val channelId: String, val channelName: String) {
        FEDERATED("/hashtag", "HashTag"),
        LOCAL("/hashtag/local", "Local HashTag")
    }

    companion object {
        private const val LOG_TAG = "MastodonStream"

        const val USER_STREAM_ID = "MastodonStream.UserStreamChannel"
        const val PUBLIC_STREAM_ID = "MastodonStream.PublicStreamChannel"
        const val LOCAL_STREAM_ID = "MastodonStream.LocalStreamChannel"
        const val HASHTAG_STREAM_ID = "MastodonStream.HashTagStreamChannel"
    }
}

private class UserStreamChannel(private val service: TwitterService, override val userRecord: AuthUserRecord) : StreamChannel {
    override val channelId: String = "/user"
    override val channelName: String = "Home and Notifications (/user)"
    override val allowUserControl: Boolean = true
    override var isRunning: Boolean = false
        private set

    private val handler: Handler = StreamHandler(MastodonStream.USER_STREAM_ID, channelId, service, userRecord)
    private var shutdownable: Shutdownable? = null

    override fun start() {
        GlobalScope.launch {
            val client = service.getProviderApi(Provider.API_MASTODON).getApiClient(userRecord) as MastodonClient
            val streaming = Streaming(client)
            shutdownable = retryUntilConnect { streaming.user(handler) }
        }
        isRunning = true
    }

    override fun stop() {
        shutdownable?.shutdown()
        shutdownable = null
        isRunning = false
    }
}

private class PublicStreamChannel(private val service: TwitterService, override val userRecord: AuthUserRecord) : StreamChannel {
    override val channelId: String = "/public"
    override val channelName: String = "Federated (/public)"
    override val allowUserControl: Boolean = true
    override var isRunning: Boolean = false
        private set

    private val handler: Handler = StreamHandler(MastodonStream.PUBLIC_STREAM_ID, channelId, service, userRecord)
    private var shutdownable: Shutdownable? = null

    override fun start() {
        GlobalScope.launch {
            val client = service.getProviderApi(Provider.API_MASTODON).getApiClient(userRecord) as MastodonClient
            val streaming = Streaming(client)
            shutdownable = retryUntilConnect { streaming.federatedPublic(handler) }
        }
        isRunning = true
    }

    override fun stop() {
        shutdownable?.shutdown()
        shutdownable = null
        isRunning = false
    }
}

private class LocalStreamChannel(private val service: TwitterService, override val userRecord: AuthUserRecord) : StreamChannel {
    override val channelId: String = "/public/local"
    override val channelName: String = "Local (/public/local)"
    override val allowUserControl: Boolean = true
    override var isRunning: Boolean = false
        private set

    private val handler: Handler = StreamHandler(MastodonStream.LOCAL_STREAM_ID, channelId, service, userRecord)
    private var shutdownable: Shutdownable? = null

    override fun start() {
        GlobalScope.launch {
            val client = service.getProviderApi(Provider.API_MASTODON).getApiClient(userRecord) as MastodonClient
            val streaming = Streaming(client)
            shutdownable = retryUntilConnect { streaming.localPublic(handler) }
        }
        isRunning = true
    }

    override fun stop() {
        shutdownable?.shutdown()
        shutdownable = null
        isRunning = false
    }
}

private class HashTagStreamChannel(private val service: TwitterService,
                                   override val userRecord: AuthUserRecord,
                                   val tag: String,
                                   val scope: MastodonStream.Scope) : StreamChannel {
    override val channelId: String = "${scope.channelId}tag=$tag"
    override val channelName: String = "${scope.channelName} #$tag"
    override val allowUserControl: Boolean = false
    override var isRunning: Boolean = false
        private set

    private val handler: Handler = StreamHandler(MastodonStream.HASHTAG_STREAM_ID, channelId, service, userRecord)
    private var shutdownable: Shutdownable? = null

    override fun start() {
        GlobalScope.launch {
            val client = service.getProviderApi(Provider.API_MASTODON).getApiClient(userRecord) as MastodonClient
            val streaming = Streaming(client)
            shutdownable = retryUntilConnect {
                when (scope) {
                    MastodonStream.Scope.FEDERATED -> streaming.federatedHashtag(tag, handler)
                    MastodonStream.Scope.LOCAL -> streaming.localHashtag(tag, handler)
                }
            }
        }
        isRunning = true
    }

    override fun stop() {
        shutdownable?.shutdown()
        shutdownable = null
        isRunning = false
    }
}

private class StreamHandler(private val timelineId: String,
                            private val channelId: String,
                            private val service: TwitterService,
                            private val userRecord: AuthUserRecord) : Handler {
    private val hub: TimelineHub = service.timelineHub

    override fun onDelete(id: Long) {
        // TODO: ところでこれ、IDがインスタンスレベルでしか一意じゃない場合関係ないやつ消しとばすんですけど
        hub.onDelete(DonStatus::class.java, id)
    }

    override fun onNotification(notification: Notification) {
        val status = notification.status
        when (notification.type) {
            "mention" ->
                if (status != null) {
                    hub.onStatus(timelineId, DonStatus(status, userRecord), true)
                }
            "reblog" ->
                if (status != null) {
                    hub.onNotify(NotifyHistory.KIND_RETWEETED, DonUser(notification.account), DonStatus(status, userRecord))
                }
            "favourite" ->
                if (status != null) {
                    hub.onFavorite(DonUser(notification.account), DonStatus(status, userRecord))
                }
            "follow" -> {}
        }
    }

    override fun onStatus(status: Status) {
        hub.onStatus(timelineId, DonStatus(status, userRecord), true)

        val account = status.account
        if (account != null && account.url == userRecord.Url) {
            // プロフィール情報の更新
            service.database?.updateAccountProfile(userRecord.Provider.id,
                    account.id,
                    MastodonUtil.expandFullScreenName(account.acct, account.url),
                    account.displayName.takeIf { !it.isEmpty() } ?: account.userName,
                    account.avatar)
        }
    }

    override fun onDisconnected(retryable: Retryable) {
        val displayTimelineId = timelineId.replace("MastodonStream.", "").replace("Channel", "")
        var timeToSleep = 10000L

        putDebugLog("$timelineId@${userRecord.ScreenName}: Disconnected.")
        service.sendBroadcast(Intent().apply {
            action = TwitterService.ACTION_STREAM_DISCONNECTED
            putExtra(TwitterService.EXTRA_USER, userRecord)
            putExtra(TwitterService.EXTRA_CHANNEL_ID, channelId)
            putExtra(TwitterService.EXTRA_CHANNEL_NAME, displayTimelineId)
        })

        while (true) {
            putDebugLog("$timelineId@${userRecord.ScreenName}: Waiting for $timeToSleep milliseconds.")
            try {
                Thread.sleep(timeToSleep)

                // 再接続に失敗した時は例外が出る
                retryable.retry()
                putDebugLog("$timelineId@${userRecord.ScreenName}: Reconnected.")

                service.sendBroadcast(Intent().apply {
                    action = TwitterService.ACTION_STREAM_CONNECTED
                    putExtra(TwitterService.EXTRA_USER, userRecord)
                    putExtra(TwitterService.EXTRA_CHANNEL_ID, channelId)
                    putExtra(TwitterService.EXTRA_CHANNEL_NAME, displayTimelineId)
                })

                break
            } catch (e: InterruptedException) {
                putDebugLog("$timelineId@${userRecord.ScreenName}: Interrupted!")
                break
            } catch (e: Mastodon4jRequestException) {
                e.printStackTrace()
            }

            timeToSleep = minOf(timeToSleep * 2, 60000)
        }
    }
}

/**
 * Mastodonのストリーミング接続用ヘルパーメソッド。接続に成功するまで時間を置いてリトライします。
 * @param connector Streaming APIへの接続処理。単に [Streaming.user] などの呼出。
 */
private inline fun retryUntilConnect(connector: () -> Shutdownable): Shutdownable {
    var timeToSleep = 10000L

    while (true) {
        try {
            return connector()
        } catch (e: Mastodon4jRequestException) {
            e.printStackTrace()
        }

        Log.d("MastodonStream", "Failed to connect to stream api. Waiting for $timeToSleep milliseconds.")
        Thread.sleep(timeToSleep)
        timeToSleep = minOf(timeToSleep * 2, 60000L)
    }
}