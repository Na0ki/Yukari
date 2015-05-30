package shibafu.dissonance.fragment.tabcontent;

import java.util.ArrayList;
import java.util.List;

import shibafu.dissonance.common.Suppressor;
import shibafu.dissonance.common.async.ParallelAsyncTask;
import shibafu.dissonance.database.Bookmark;
import shibafu.dissonance.database.MuteConfig;
import shibafu.dissonance.twitter.AuthUserRecord;
import shibafu.dissonance.twitter.StatusManager;

/**
 * Created by shibafu on 14/02/13.
 */
public class BookmarkListFragment extends TweetListFragment {

    @Override
    protected void executeLoader(int requestMode, AuthUserRecord userRecord) {
        BookmarkLoader loader = new BookmarkLoader();
        switch (requestMode) {
            case LOADER_LOAD_UPDATE:
                elements.clear();
                adapterWrap.notifyDataSetChanged();
                clearUnreadNotifier();
            case LOADER_LOAD_INIT:
                loader.execute();
                break;
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (isServiceBound() && !elements.isEmpty()) {
            executeLoader(LOADER_LOAD_UPDATE, null);
        }
    }

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        executeLoader(LOADER_LOAD_INIT, null);
    }

    @Override
    public void onServiceDisconnected() {}

    @Override
    public boolean isCloseable() {
        return true;
    }

    private class BookmarkLoader extends ParallelAsyncTask<Void, Void, List<Bookmark>> {

        @Override
        protected List<Bookmark> doInBackground(Void... params) {
            if (!isServiceBound()) return new ArrayList<>();
            return getTwitterService().getDatabase().getBookmarks();
        }

        @Override
        protected void onPreExecute() {
            changeFooterProgress(true);
        }

        @Override
        protected void onPostExecute(List<Bookmark> bookmarks) {
            Suppressor suppressor = getTwitterService().getSuppressor();
            boolean[] mute;
            int position;
            for (Bookmark status : bookmarks) {
                AuthUserRecord checkOwn = getTwitterService().isMyTweet(status);
                if (checkOwn != null) {
                    status.setOwner(checkOwn);
                }

                mute = suppressor.decision(status);
                if (mute[MuteConfig.MUTE_IMAGE_THUMB]) {
                    status.setCensoredThumbs(true);
                }

                if (!(  mute[MuteConfig.MUTE_TWEET_RTED] ||
                        (!status.isRetweet() && mute[MuteConfig.MUTE_TWEET]) ||
                        (status.isRetweet() && mute[MuteConfig.MUTE_RETWEET]))) {
                    position = prepareInsertStatus(status);
                    if (position > -1) {
                        elements.add(position, status);
                    }
                }
                else {
                    stash.add(status);
                }

                StatusManager.getReceivedStatuses().put(status.getId(), status);
            }
            adapterWrap.notifyDataSetChanged();
            changeFooterProgress(false);
            setRefreshComplete();
        }
    }

}