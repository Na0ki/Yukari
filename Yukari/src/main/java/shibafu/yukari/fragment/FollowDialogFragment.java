package shibafu.yukari.fragment;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.app.DialogFragment;
import android.support.v7.widget.PopupMenu;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;

import java.util.ArrayList;
import java.util.List;

import shibafu.yukari.R;
import shibafu.yukari.common.ImageLoaderTask;
import shibafu.yukari.common.SimpleAsyncTask;
import shibafu.yukari.service.TwitterService;
import shibafu.yukari.twitter.AuthUserRecord;
import twitter4j.Relationship;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.User;

/**
 * Created by shibafu on 14/01/25.
 */
public class FollowDialogFragment extends DialogFragment {

    public static final String ARGUMENT_TARGET = "target";

    public static final int RELATION_NONE = 0;
    public static final int RELATION_FOLLOW = 1;
    public static final int RELATION_BLOCK = 2;
    public static final int RELATION_PRE_R4S = 3;

    private AlertDialog dialog;

    private List<ListEntry> entryList = new ArrayList<ListEntry>();
    private User targetUser;
    private ListView listView;

    private TwitterService service;
    private boolean serviceBound = false;

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Bundle args = getArguments();
        targetUser = (User) args.getSerializable(ARGUMENT_TARGET);

        listView = new ListView(getActivity());
        listView.setChoiceMode(AbsListView.CHOICE_MODE_NONE);

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle("フォロー状態を確認中...");
        builder.setView(listView);
        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {

            }
        });
        builder.setNegativeButton("キャンセル", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
            }
        });

        dialog = builder.create();
        return dialog;
    }

    @Override
    public void onStart() {
        super.onStart();
        getActivity().bindService(new Intent(getActivity(), TwitterService.class), connection, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onStop() {
        super.onStop();
        if (serviceBound) {
            getActivity().unbindService(connection);
            serviceBound = false;
        }
    }

    private class ListEntry {
        private AuthUserRecord userRecord;
        private Relationship relationship;
        private int beforeRelation;
        private int afterRelation;
        private boolean isTargetfollower;

        private ListEntry(AuthUserRecord userRecord, Relationship relationship) {
            this.userRecord = userRecord;
            this.relationship = relationship;

            if (relationship.isSourceBlockingTarget()) {
                beforeRelation = RELATION_BLOCK;
            }
            else if (relationship.isSourceFollowingTarget()) {
                beforeRelation = RELATION_FOLLOW;
            }
            else {
                beforeRelation = RELATION_NONE;
            }
            afterRelation = beforeRelation;

            isTargetfollower = relationship.isTargetFollowingSource();
        }

        public AuthUserRecord getUserRecord() {
            return userRecord;
        }

        public Relationship getRelationship() {
            return relationship;
        }

        public int getBeforeRelation() {
            return beforeRelation;
        }

        public int getAfterRelation() {
            return afterRelation;
        }

        public boolean isTargetfollower() {
            return isTargetfollower;
        }
    }

    private class Adapter extends ArrayAdapter<ListEntry> {

        private LayoutInflater inflater;

        public Adapter(Context context, List<ListEntry> objects) {
            super(context, 0, objects);
            inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View v = convertView;
            if (v == null) {
                v = inflater.inflate(R.layout.row_follow, null);
            }

            ListEntry e = getItem(position);
            if (e != null) {
                ImageView ivOwn = (ImageView) v.findViewById(R.id.ivFoOwn);
                ivOwn.setTag(e.getUserRecord().ProfileImageUrl);
                new ImageLoaderTask(getContext(), ivOwn).executeIf(e.getUserRecord().ProfileImageUrl);

                ImageView ivTarget = (ImageView) v.findViewById(R.id.ivFoTarget);
                ivTarget.setTag(targetUser.getProfileImageURLHttps());
                new ImageLoaderTask(getContext(), ivTarget).executeIf(targetUser.getProfileImageURLHttps());

                ImageView ivRelation = (ImageView) v.findViewById(R.id.ivFollowStatus);
                Button btnFollow = (Button) v.findViewById(R.id.btnFollow);
                setStatus(e, btnFollow, ivRelation);

                ImageButton ibMenu = (ImageButton) v.findViewById(R.id.ibMenu);
                ibMenu.setTag(e);
                ibMenu.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        final ListEntry e = (ListEntry) view.getTag();

                        PopupMenu popupMenu = new PopupMenu(getContext(), view);
                        popupMenu.inflate(R.menu.follow);
                        popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                            @Override
                            public boolean onMenuItemClick(MenuItem menuItem) {
                                switch (menuItem.getItemId()) {
                                    case R.id.action_block:
                                        return true;
                                    case R.id.action_report:
                                        return true;
                                }
                                return true;
                            }
                        });
                        popupMenu.show();
                    }
                });
            }

            return v;
        }

        private void setStatus(ListEntry e, Button btnFollow, ImageView ivRelation) {
            if (e.afterRelation == RELATION_BLOCK) {
                btnFollow.setText("ブロック中");
            }
            else if (e.getAfterRelation() == RELATION_FOLLOW) {
                btnFollow.setText("フォロー解除");
                if (e.isTargetfollower) {
                    ivRelation.setImageResource(R.drawable.ic_f_friend);
                }
                else {
                    ivRelation.setImageResource(R.drawable.ic_f_follow);
                }
            }
            else if (e.isTargetfollower) {
                btnFollow.setText("フォロー");
                ivRelation.setImageResource(R.drawable.ic_f_follower);
            }
            else {
                btnFollow.setText("フォロー");
                ivRelation.setImageResource(R.drawable.ic_f_not);
            }
        }
    }

    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            TwitterService.TweetReceiverBinder binder = (TwitterService.TweetReceiverBinder) service;
            FollowDialogFragment.this.service = binder.getService();
            serviceBound = true;

            if (entryList.isEmpty()) {
                new SimpleAsyncTask() {
                    @Override
                    protected Void doInBackground(Void... voids) {
                        Twitter twitter = FollowDialogFragment.this.service.getTwitter();
                        for (AuthUserRecord userRecord : FollowDialogFragment.this.service.getUsers()) {
                            twitter.setOAuthAccessToken(userRecord.getAccessToken());
                            Relationship relationship = null;
                            try {
                                relationship = twitter.showFriendship(userRecord.NumericId, targetUser.getId());
                            } catch (TwitterException e) {
                                e.printStackTrace();
                            }
                            entryList.add(new ListEntry(userRecord, relationship));
                        }
                        return null;
                    }

                    @Override
                    protected void onPostExecute(Void aVoid) {
                        Adapter adapter = new Adapter(getActivity(), entryList);
                        listView.setAdapter(adapter);
                        dialog.setTitle("フォロー状態 @" + targetUser.getScreenName());
                    }
                }.execute();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            serviceBound = false;
        }
    };
}
