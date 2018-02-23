package shibafu.yukari.fragment.tabcontent

import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.preference.PreferenceManager
import android.support.v4.util.LongSparseArray
import android.support.v4.widget.SwipeRefreshLayout
import android.support.v7.app.AppCompatActivity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import org.eclipse.collections.impl.set.mutable.primitive.LongHashSet
import shibafu.yukari.R
import shibafu.yukari.activity.MainActivity
import shibafu.yukari.activity.ProfileActivity
import shibafu.yukari.activity.StatusActivity
import shibafu.yukari.common.TabType
import shibafu.yukari.common.TweetAdapter
import shibafu.yukari.entity.ExceptionStatus
import shibafu.yukari.entity.LoadMarker
import shibafu.yukari.entity.NotifyHistory
import shibafu.yukari.entity.Status
import shibafu.yukari.entity.User
import shibafu.yukari.filter.FilterQuery
import shibafu.yukari.filter.compiler.FilterCompilerException
import shibafu.yukari.filter.compiler.QueryCompiler
import shibafu.yukari.fragment.SimpleListDialogFragment
import shibafu.yukari.fragment.base.ListTwitterFragment
import shibafu.yukari.linkage.RestQuery
import shibafu.yukari.linkage.TimelineEvent
import shibafu.yukari.linkage.TimelineObserver
import shibafu.yukari.twitter.AuthUserRecord
import shibafu.yukari.twitter.entity.TwitterStatus
import shibafu.yukari.twitter.statusimpl.PreformedStatus
import shibafu.yukari.util.AttrUtil
import shibafu.yukari.util.defaultSharedPreferences
import shibafu.yukari.util.putDebugLog
import shibafu.yukari.util.putWarnLog

/**
 * 時系列順に要素を並べて表示するタブの基底クラス
 */
open class TimelineFragment : ListTwitterFragment(), TimelineTab, TimelineObserver, SwipeRefreshLayout.OnRefreshListener, SimpleListDialogFragment.OnDialogChoseListener {
    var title: String = ""
    var mode: Int = 0
    var rawQuery: String = FilterQuery.VOID_QUERY_STRING
    var query: FilterQuery = FilterQuery.VOID_QUERY

    override val timelineId: String
        get() {
            val args = arguments
            if (args.containsKey(TwitterListFragment.EXTRA_ID)) {
                return args.getLong(TwitterListFragment.EXTRA_ID).toString()
            } else {
                return this.toString()
            }
        }

    protected val statuses: MutableList<Status> = arrayListOf()
    protected val mutedStatuses: MutableList<Status> = arrayListOf()
    protected val users: MutableList<AuthUserRecord> = arrayListOf()

    protected var statusAdapter: TweetAdapter? = null

    // タイムライン容量制限
    private var statusCapacity: Int = 256

    // SwipeRefreshLayout
    private var swipeRefreshLayout: SwipeRefreshLayout? = null

    private val handler: Handler = Handler(Looper.getMainLooper())
    private val onScrollListeners: MutableList<AbsListView.OnScrollListener> = arrayListOf()

    // リクエスト管理
    private val loadingTaskKeys = arrayListOf<Long>()
    private val queryingLoadMarkers = LongSparseArray<Long>() // TaskKey, LoadMarker.Id

    // ダブルクリック抑止
    private var blockingDoubleClick = false
    private var enableDoubleClickBlocker = false

    // 未読管理
    private val unreadSet = LongHashSet()
    private val unreadNotifierBehavior = UnreadNotifierBehavior(this, statuses, unreadSet)

    // スクロールロック
    private var lockedScrollId = -1L
    private var lockedYPosition = 0
    private val scrollUnlockHandler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message?) {
            lockedScrollId = -1
        }
    }

    override fun onAttach(context: Context?) {
        super.onAttach(context)

        if (context is MainActivity) {
            statuses.addAll(context.getStatusesList(timelineId))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val arguments = arguments
        title = arguments.getString(TwitterListFragment.EXTRA_TITLE) ?: ""
        mode = arguments.getInt(TwitterListFragment.EXTRA_MODE, -1)
        rawQuery = arguments.getString(EXTRA_FILTER_QUERY) ?: FilterQuery.VOID_QUERY_STRING
        arguments.getSerializable(TwitterListFragment.EXTRA_USER)?.let { users += it as AuthUserRecord }
    }

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        when (mode) {
            TabType.TABTYPE_TRACE, TabType.TABTYPE_DM ->
                return super.onCreateView(inflater, container, savedInstanceState)
        }
        val v = inflater!!.inflate(R.layout.fragment_swipelist, container, false)

        swipeRefreshLayout = v.findViewById(R.id.swipe_refresh_layout) as SwipeRefreshLayout?
        swipeRefreshLayout?.setColorSchemeResources(AttrUtil.resolveAttribute(context.theme, R.attr.colorPrimary))
        swipeRefreshLayout?.setOnRefreshListener(this)

        val swipeActionStatusView = v.findViewById(R.id.swipeActionStatusFrame)
        swipeActionStatusView?.visibility = View.INVISIBLE

        unreadNotifierBehavior.onCreateView(v)

        return v
    }

    override fun onStart() {
        super.onStart()
        unreadNotifierBehavior.onStart()
    }

    override fun onResume() {
        super.onResume()

        val activity = activity
        if (activity !is MainActivity) {
            if (activity is AppCompatActivity) {
                activity.supportActionBar?.title = title
            } else {
                activity.title = title
            }
        }

        enableDoubleClickBlocker = defaultSharedPreferences.getBoolean("pref_block_doubleclock", false)
        blockingDoubleClick = false
    }

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        statusAdapter = TweetAdapter(context, users, null, statuses)
        listAdapter = statusAdapter

        listView.setOnScrollListener(object : AbsListView.OnScrollListener {
            override fun onScroll(view: AbsListView?, firstVisibleItem: Int, visibleItemCount: Int, totalItemCount: Int) {
                onScrollListeners.forEach { it.onScroll(view, firstVisibleItem, visibleItemCount, totalItemCount) }
            }

            override fun onScrollStateChanged(view: AbsListView?, scrollState: Int) {
                onScrollListeners.forEach { it.onScrollStateChanged(view, scrollState) }
            }
        })
        onScrollListeners.add(unreadNotifierBehavior)
        onScrollListeners.add(object : AbsListView.OnScrollListener {
            override fun onScroll(view: AbsListView?, firstVisibleItem: Int, visibleItemCount: Int, totalItemCount: Int) {
                if (TwitterListFragment.USE_INSERT_LOG) putDebugLog("onScroll called, lockedScrollId = $lockedScrollId, y = $lockedYPosition")
            }

            override fun onScrollStateChanged(view: AbsListView?, scrollState: Int) {
                if (scrollState == AbsListView.OnScrollListener.SCROLL_STATE_FLING) {
                    lockedScrollId = -1
                }
            }
        })
        unreadNotifierBehavior.onViewCreated(view, savedInstanceState)
    }

    override fun onStop() {
        super.onStop()
        unreadNotifierBehavior.onStop()
    }

    override fun onDetach() {
        super.onDetach()
        onScrollListeners.remove(unreadNotifierBehavior)
        unreadNotifierBehavior.onDetach()
        if (isTwitterServiceBound) {
            twitterService?.timelineHub?.removeObserver(this)
        }
        listAdapter = null
        statusAdapter = null
        if (activity is MainActivity) {
            (activity as MainActivity).getStatusesList(timelineId).addAll(statuses)
        }
    }

    override fun onListItemClick(l: ListView?, v: View?, position: Int, id: Long) {
        if (blockingDoubleClick) {
            return
        }

        if (position < statuses.size) {
            val result: Boolean = {
                val clickedElement = statuses[position]
                when (clickedElement) {
                    is LoadMarker -> {
                        if (clickedElement.taskKey < 0) {
                            query.sources.firstOrNull { it.hashCode().toString() == clickedElement.loadMarkerTag }?.let {
                                // リクエストの発行
                                val userRecord = it.sourceAccount ?: return@let
                                val restQuery = it.getRestQuery() ?: return@let
                                val params = RestQuery.Params(maxId = clickedElement.id,
                                        loadMarkerTag = it.hashCode().toString(),
                                        loadMarkerDate = clickedElement.createdAt)
                                val taskKey = twitterService.statusLoader.requestRestQuery(timelineId,
                                        userRecord, restQuery, params)
                                clickedElement.taskKey = taskKey
                                loadingTaskKeys += taskKey
                                queryingLoadMarkers.put(taskKey, clickedElement.id)
                                statusCapacity += 100
                                // Viewの表示更新
                                val visiblePosition = position - listView.firstVisiblePosition
                                if (visiblePosition > -1) {
                                    val view: View? = listView.getChildAt(visiblePosition)
                                    view?.findViewById(R.id.pbLoading)?.visibility = View.VISIBLE
                                    (view?.findViewById(R.id.tvLoading) as? TextView)?.text = "loading"
                                }
                            }
                        }
                        true
                    }
                    is TwitterStatus -> {
                        val intent = Intent(activity, StatusActivity::class.java)
                        intent.putExtra(StatusActivity.EXTRA_STATUS, PreformedStatus(clickedElement.status, clickedElement.representUser))
                        intent.putExtra(StatusActivity.EXTRA_USER, clickedElement.representUser)
                        startActivity(intent)
                        true
                    }
                    is NotifyHistory -> {
                        val bundle = Bundle()
                        bundle.putSerializable("status", clickedElement.status)
                        val dialog = SimpleListDialogFragment.newInstance(DIALOG_REQUEST_HISTORY_MENU,
                                "メニュー", null, null, null,
                                listOf("@${clickedElement.status.user.screenName}", "詳細を開く"),
                                bundle)
                        dialog.setTargetFragment(this, 0)
                        dialog.show(fragmentManager, "history_menu")
                        true
                    }
                    else -> false
                }
            }()
            // コマンド実行成功後、次回onResumeまでクリックイベントを無視する
            if (result && enableDoubleClickBlocker) {
                blockingDoubleClick = true
            }
        }
    }

    override fun isCloseable(): Boolean = false

    override fun scrollToTop() {
        try {
            listView?.setSelection(0)
            unreadNotifierBehavior.clearUnreadNotifier()
        } catch (e: IllegalStateException) {
            e.printStackTrace()
            val activity = activity
            if (activity?.applicationContext != null) {
                Toast.makeText(activity.applicationContext, e.localizedMessage, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun scrollToBottom() {
        try {
            val count = listAdapter?.count ?: 1
            listView?.setSelection(count - 1)
        } catch (e: IllegalStateException) {
            e.printStackTrace()
            val activity = activity
            if (activity?.applicationContext != null) {
                Toast.makeText(activity.applicationContext, e.localizedMessage, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun scrollToOldestUnread() {
        try {
            if (unreadSet.isEmpty) {
                listView.setSelection(0)
            } else {
                val lastUnreadId = unreadSet.min()
                for (position in 0 until statuses.size) {
                    if (statuses[position].id == lastUnreadId) {
                        listView.setSelection(position)
                        break
                    }
                }
            }
        } catch (e: IllegalStateException) {
            e.printStackTrace()
            if (activity?.applicationContext != null) {
                Toast.makeText(activity.applicationContext, e.localizedMessage, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun scrollToPrevPage() {
        try {
            listView?.let {
                it.smoothScrollBy(-it.height, 100)
                it.setSelection(it.firstVisiblePosition)
            }
        } catch (e: IllegalStateException) {
            e.printStackTrace()
            val activity = activity
            if (activity?.applicationContext != null) {
                Toast.makeText(activity.applicationContext, e.localizedMessage, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun scrollToNextPage() {
        try {
            listView?.let {
                it.smoothScrollBy(it.height, 100)
                it.setSelection(it.firstVisiblePosition)
            }
        } catch (e: IllegalStateException) {
            e.printStackTrace()
            val activity = activity
            if (activity?.applicationContext != null) {
                Toast.makeText(activity.applicationContext, e.localizedMessage, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onServiceConnected() {
        // ユーザ情報のバインド
        if (users.isEmpty()) {
            users += twitterService.users
        }

        // TL初期容量の決定
        statusCapacity = users.size * CAPACITY_INITIAL_FACTOR

        if (statusAdapter != null) {
            statusAdapter?.setUserExtras(twitterService.userExtras)
            statusAdapter?.setStatusLoader(twitterService.statusLoader)
        }

        // クエリコンパイル
        try {
            query = QueryCompiler.compile(users, rawQuery)
        } catch (e: FilterCompilerException) {
            handler.post {
                statuses.add(ExceptionStatus(Long.MAX_VALUE, users.first(),
                        Exception("クエリのコンパイル中にエラーが発生しました。")))
                statuses.add(ExceptionStatus(Long.MAX_VALUE - 1, users.first(), e))
                notifyDataSetChanged()
            }
            query = FilterQuery.VOID_QUERY
        }

        // イベント購読開始
        twitterService?.timelineHub?.addObserver(this)

        // 初期読み込み
        if (statuses.isEmpty()) {
            val statusLoader = twitterService?.statusLoader ?: return
            query.sources.forEach { source ->
                val userRecord = source.sourceAccount ?: return@forEach
                val restQuery = source.getRestQuery() ?: return@forEach
                val params = RestQuery.Params(loadMarkerTag = source.hashCode().toString())
                loadingTaskKeys += statusLoader.requestRestQuery(timelineId, userRecord, restQuery, params)
            }
            if (loadingTaskKeys.isEmpty()) {
                swipeRefreshLayout?.isRefreshing = false
            }
        }
    }

    override fun onServiceDisconnected() {}

    override fun onRefresh() {
        if (query.sources.isEmpty()) {
            swipeRefreshLayout?.isRefreshing = false
            return
        }

        swipeRefreshLayout?.isRefreshing = true
        unreadNotifierBehavior.clearUnreadNotifier()
        val statusLoader = twitterService?.statusLoader ?: return
        query.sources.forEach { source ->
            val userRecord = source.sourceAccount ?: return@forEach
            val restQuery = source.getRestQuery() ?: return@forEach
            val params = RestQuery.Params(loadMarkerTag = source.hashCode().toString())
            loadingTaskKeys += statusLoader.requestRestQuery(timelineId, userRecord, restQuery, params)
        }
        if (loadingTaskKeys.isEmpty()) {
            swipeRefreshLayout?.isRefreshing = false
        }
    }

    override fun onDialogChose(requestCode: Int, which: Int, value: String?, extras: Bundle?) {
        when (requestCode) {
            DIALOG_REQUEST_HISTORY_MENU -> {
                if (extras == null) return
                val status = extras.getSerializable("status") as Status

                when (which) {
                    // プロフィール
                    0 -> {
                        val intent = Intent(activity.applicationContext, ProfileActivity::class.java)
                        intent.putExtra(ProfileActivity.EXTRA_USER, status.representUser)
                        intent.putExtra(ProfileActivity.EXTRA_TARGET, status.user.id) // TODO: マルチサービス非互換
                        startActivity(intent)
                    }
                    // 詳細を開く
                    1 -> {
                        val intent = Intent(activity.applicationContext, StatusActivity::class.java)
                        intent.putExtra(StatusActivity.EXTRA_USER, status.representUser)
                        intent.putExtra(StatusActivity.EXTRA_STATUS, status)
                        startActivity(intent)
                    }
                    DialogInterface.BUTTON_NEGATIVE -> blockingDoubleClick = false
                }
            }
        }
    }

    override fun onTimelineEvent(event: TimelineEvent) {
        fun finishRestRequest(taskKey: Long) {
            loadingTaskKeys.remove(taskKey)
            if (queryingLoadMarkers.indexOfKey(taskKey) > -1) {
                statuses.firstOrNull { it is LoadMarker && it.taskKey == taskKey }?.let {
                    handler.post { deleteElement(it.javaClass, it.id) }
                }
                queryingLoadMarkers.remove(taskKey)
            }
            if (loadingTaskKeys.isEmpty()) {
                handler.post { swipeRefreshLayout?.isRefreshing = false }
            }
        }
        fun setFavoriteState(eventFrom: User, eventStatus: Status, isFavorited: Boolean) {
            statuses.forEach { status ->
                if (status.javaClass == eventStatus.javaClass && status.id == eventStatus.id) {
                    status.metadata.favoritedUsers.put(eventFrom.id, isFavorited)
                    if (status.user.id == eventStatus.representUser.NumericId && !status.receivedUsers.contains(eventStatus.representUser)) {
                        status.receivedUsers.add(eventStatus.representUser)
                    }
                    notifyDataSetChanged()
                    return
                }
            }
            mutedStatuses.forEach { status ->
                if (status.javaClass == eventStatus.javaClass && status.id == eventStatus.id) {
                    status.metadata.favoritedUsers.put(eventFrom.id, isFavorited)
                    if (status.user.id == eventStatus.representUser.NumericId && !status.receivedUsers.contains(eventStatus.representUser)) {
                        status.receivedUsers.add(eventStatus.representUser)
                    }
                    return
                }
            }
        }

        when (event) {
            is TimelineEvent.Received -> {
                val status = event.status
                val queryVariables = mapOf<String, Any?>(
                        "passive" to event.passive
                )

                if (statuses.contains(status)) return
                if (status !is LoadMarker && !query.evaluate(status, users, queryVariables)) return

                if (event.muted) {
                    if (TwitterListFragment.USE_INSERT_LOG) putDebugLog("TimelineFragment", "[$rawQuery] onStatus : Muted ... $status")

                    mutedStatuses += status
                } else {
                    if (TwitterListFragment.USE_INSERT_LOG) putDebugLog("[$rawQuery] onStatus : Insert  ... $status")

                    val useScrollLock = defaultSharedPreferences.getBoolean("pref_lock_scroll_after_reload", false)
                    handler.post { insertElement(status, !event.passive && useScrollLock && status !is LoadMarker) }
                }
            }
            is TimelineEvent.RestRequestCompleted -> {
                if (event.timelineId == timelineId) {
                    if (TwitterListFragment.USE_INSERT_LOG) putDebugLog("onUpdatedStatus : Rest Completed ... taskKey=${event.taskKey} , left loadingTaskKeys.size=${loadingTaskKeys.size}")

                    finishRestRequest(event.taskKey)
                }
            }
            is TimelineEvent.RestRequestCancelled -> {
                if (event.timelineId == timelineId) {
                    finishRestRequest(event.taskKey)
                }
            }
            is TimelineEvent.Notify -> {
                if (mode == TabType.TABTYPE_HISTORY) {
                    handler.post { insertElement(event.notify, false) }
                }
            }
            is TimelineEvent.Favorite -> {
                setFavoriteState(event.from, event.status, true)
            }
            is TimelineEvent.Unfavorite -> {
                setFavoriteState(event.from, event.status, false)
            }
            is TimelineEvent.Delete -> {
                handler.post { deleteElement(event.type, event.id) }
                mutedStatuses.removeAll { it.javaClass == event.type && it.id == event.id }
            }
            is TimelineEvent.Wipe -> {
                handler.post {
                    statuses.clear()
                    notifyDataSetChanged()
                }
                mutedStatuses.clear()
            }
            is TimelineEvent.ForceUpdateUI -> {
                handler.post {
                    notifyDataSetChanged()
                }
            }
        }
    }

    /**
     * TL要素挿入の前処理として、挿入位置の判定とマージを実施します。
     * @param status Status
     * @return 挿入位置、負数の場合は実際にリストに挿入する必要はないが別の処理が必要。(PRE_INSERT_から始まる定数を参照)
     */
    private fun preInsertElement(status: Status): Int {
        // 代表操作アカウントの書き換えが必要か確認する
        if (status is TwitterStatus) {
            // TODO: 現状、Twitter向けにしか優先機能が使えない。どっかでマイグレーションする。
            // 自身の所有するStatusの場合、書き換えてはいけない
            if (!status.isOwnedStatus()) {
                // 優先アカウント設定が存在するか？
                val userExtras = twitterService.userExtras.firstOrNull { it.id == status.originStatus.user.id }
                if (userExtras != null && userExtras.priorityAccount != null) {
                    status.representUser = userExtras.priorityAccount
                    if (!status.receivedUsers.contains(userExtras.priorityAccount)) {
                        status.receivedUsers.add(userExtras.priorityAccount)
                    }
                }
            }
        }

        if (status is LoadMarker) {
            for (i in 0 until statuses.size) {
                if (statuses[i] is LoadMarker) {
                    val compareTo = statuses[i] as LoadMarker

                    if (status.providerApiType == compareTo.providerApiType && status.anchorStatusId == compareTo.anchorStatusId && status.user.id == compareTo.user.id) {
                        // 同じ情報を持つLoadMarkerなので、挿入しない
                        return PRE_INSERT_DUPLICATED
                    }
                } else if (status > statuses[i]) {
                    return i
                }
            }
        } else {
            for (i in 0 until statuses.size) {
                // Order by ID, API Type
                if (status.id == statuses[i].id) {
                    if (status.providerApiType == statuses[i].providerApiType) {
                        statuses[i].merge(status)
                        return PRE_INSERT_MERGED
                    } else if (status.providerApiType < statuses[i].providerApiType) {
                        return i
                    }
                } else if (status > statuses[i]) {
                    return i
                }
            }
        }
        return statuses.size
    }

    /**
     * TLに要素を挿入します。既に存在する場合はマージします。
     * @param status Status
     * @param useScrollLock スクロールロックを使うかどうか
     */
    private fun insertElement(status: Status, useScrollLock: Boolean) {
        val position = preInsertElement(status)
        when (position) {
            PRE_INSERT_DUPLICATED -> return
            PRE_INSERT_MERGED -> notifyDataSetChanged()
            else -> if (!statuses.contains(status)) {
                if (position < statuses.size && statuses[position].id == status.id) {
                    return
                }
                statuses.add(position, status)
                notifyDataSetChanged()
                if (statusCapacity < statuses.size) {
                    val iterator = statuses.listIterator(statusCapacity)
                    while (iterator.hasNext()) {
                        unreadSet.remove(iterator.next().id)
                        iterator.remove()
                        notifyDataSetChanged()
                    }
                }
            }
        }

        if (listView == null) {
            putWarnLog("Insert: ListView is null. DROPPED! ($status, $position)")
            return
        }
        
        // ここからスクロール制御回りの処理

        val firstPos = listView.firstVisiblePosition
        val firstView = listView.getChildAt(0)
        val y = firstView?.top ?: 0
        if (!useScrollLock && (statuses.size == 1 || firstPos == 0 && y > -1)) {
            listView.setSelection(0)

            if (TwitterListFragment.USE_INSERT_LOG) putDebugLog("Scroll Position = 0 (Top) ... $status")
        } else if (lockedScrollId > -1) {
            for (i in statuses.indices) {
                if (statuses[i].id <= lockedScrollId) {
                    listView.setSelectionFromTop(i, y)
                    if (position < i) {
                        unreadSet.add(status.id)
                    }

                    scrollUnlockHandler.removeCallbacksAndMessages(null)
                    scrollUnlockHandler.sendMessageDelayed(scrollUnlockHandler.obtainMessage(0, i, y), 200)

                    if (TwitterListFragment.USE_INSERT_LOG) putDebugLog("Scroll Position = $i (Locked strict) ... $status")
                    break
                }
            }
        } else if (position <= firstPos) {
            if (statuses.size <= 1 && status is LoadMarker) {
                // 要素数1の時にマーカーを掴むとずっと下にスクロールされてしまうので回避する
            } else {
                var lockedPosition = firstPos + 1
                if (lockedPosition < statuses.size) {
                    if (statuses[lockedPosition] is LoadMarker) {
                        lockedPosition = firstPos
                    }
                } else {
                    lockedPosition = firstPos
                }

                unreadSet.add(status.id)
                listView.setSelectionFromTop(lockedPosition, y)

                lockedScrollId = statuses[lockedPosition].id
                lockedYPosition = y

                scrollUnlockHandler.removeCallbacksAndMessages(null)
                scrollUnlockHandler.sendMessageDelayed(scrollUnlockHandler.obtainMessage(0, firstPos + 1, y), 200)

                if (TwitterListFragment.USE_INSERT_LOG) putDebugLog("Scroll Position = $lockedScrollId (Locked) ... $status")
            }
        } else {
            if (TwitterListFragment.USE_INSERT_LOG) putDebugLog("Scroll Position = $firstPos (Not changed) ... $status")
        }

        unreadNotifierBehavior.updateUnreadNotifier()
    }

    /**
     * TLから要素を削除します。
     * @param type 削除対象の型
     * @param id 削除対象のID
     */
    private fun deleteElement(type: Class<out Status>, id: Long) {
        if (listView == null) {
            putWarnLog("Delete: ListView is null. DROPPED! (${type.name}, $id)")
            return
        }

        val iterator = statuses.iterator()
        while (iterator.hasNext()) {
            val status = iterator.next()
            if (status.javaClass == type && status.id == id) {
                iterator.remove()
                notifyDataSetChanged()
                if (unreadSet.contains(id)) {
                    unreadSet.remove(id)
                    unreadNotifierBehavior.updateUnreadNotifier()
                }
                break
            }
        }
    }

    private fun notifyDataSetChanged() {
        statusAdapter?.notifyDataSetChanged()
    }

    companion object {
        const val EXTRA_FILTER_QUERY = "filterQuery"

        /** TL容量の初期化係数 */
        private const val CAPACITY_INITIAL_FACTOR = 256

        /** [preInsertElement] : 既に同一の要素が存在している(挿入禁止) */
        private const val PRE_INSERT_DUPLICATED = -1
        /** [preInsertElement] : 同一要素があったためマージを行った(Viewの制御のみ必要) */
        private const val PRE_INSERT_MERGED = -2

        /** ダイアログID : NotifyHistory クリックメニュー */
        private const val DIALOG_REQUEST_HISTORY_MENU = 1
    }
}

/**
 * 未読ビューの振る舞い制御
 */
private class UnreadNotifierBehavior(private val parent: TimelineFragment,
                                     private val statuses: MutableList<Status>,
                                     private val unreadSet: LongHashSet) : AbsListView.OnScrollListener {
    private var lastShowedFirstItemId: Long = -1
    private var lastShowedFirstItemY = 0
    private var unreadNotifierView: View? = null
    private var tvUnreadCount: TextView? = null

    fun onCreateView(parentView: View) {
        unreadNotifierView = parentView.findViewById(R.id.unreadNotifier)
        tvUnreadCount = unreadNotifierView?.findViewById(R.id.textView) as TextView
    }

    fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        val unreadNotifier = unreadNotifierView
        if (unreadNotifier != null) {
            if (PreferenceManager.getDefaultSharedPreferences(parent.activity).getString("pref_theme", "light").endsWith("dark")) {
                unreadNotifier.setBackgroundResource(R.drawable.dialog_full_material_dark)
            } else {
                unreadNotifier.setBackgroundResource(R.drawable.dialog_full_material_light)
            }

            unreadNotifier.setOnClickListener { _ -> parent.scrollToOldestUnread() }
        }
    }

    fun onStart() {
        if (lastShowedFirstItemId > -1) {
            for (position in 0 until statuses.size) {
                if (statuses[position].id == lastShowedFirstItemId) {
                    parent.listView.setSelectionFromTop(position, lastShowedFirstItemY)
                    break
                }
            }
            updateUnreadNotifier()
        }
    }

    fun onStop() {
        lastShowedFirstItemId = parent.listView.getItemIdAtPosition(parent.listView.firstVisiblePosition)
        lastShowedFirstItemY = parent.listView.getChildAt(0)?.top ?: 0
    }

    fun onDetach() {
        unreadNotifierView = null
        tvUnreadCount = null
    }

    fun updateUnreadNotifier() {
        val unreadNotifier = unreadNotifierView ?: return

        if (unreadSet.size() < 1) {
            unreadNotifier.visibility = View.INVISIBLE
            return
        }
        tvUnreadCount?.text = "新着 ${unreadSet.size()}件"

        unreadNotifier.visibility = View.VISIBLE
    }

    fun clearUnreadNotifier() {
        unreadSet.clear()
        updateUnreadNotifier()
    }

    override fun onScrollStateChanged(view: AbsListView?, scrollState: Int) {}

    override fun onScroll(view: AbsListView?, firstVisibleItem: Int, visibleItemCount: Int, totalItemCount: Int) {
        var i = firstVisibleItem
        while (i < i + visibleItemCount && i < statuses.size) {
            val element = statuses[firstVisibleItem]
            if (unreadSet.contains(element.id)) {
                unreadSet.remove(element.id)
            }
            ++i
        }
        updateUnreadNotifier()
    }
}