package shibafu.yukari.fragment;

import android.support.v7.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.DialogFragment;
import android.support.v7.widget.PopupMenu;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import shibafu.yukari.R;
import shibafu.yukari.common.bitmapcache.ImageLoaderTask;
import shibafu.yukari.twitter.AuthUserRecord;
import twitter4j.Relationship;
import twitter4j.User;

/**
 * Created by shibafu on 14/01/25.
 */
public class FollowDialogFragment extends DialogFragment {

    public static final String ARGUMENT_TARGET = "target";
    public static final String ARGUMENT_KNOWN_RELATIONS = "known_relations";
    public static final String ARGUMENT_ALL_R4S = "r4s";

    public static final int RELATION_NONE = 0;
    public static final int RELATION_FOLLOW = 1;
    public static final int RELATION_BLOCK = 2;
    public static final int RELATION_PRE_R4S = 3;
    public static final int RELATION_UNBLOCK = 4;
    public static final int RELATION_CUTOFF = 5;

    private AlertDialog dialog;

    private List<ListEntry> entryList = new ArrayList<>();
    private User targetUser;
    private ListView listView;

    public interface FollowDialogCallback {
        void onChangedRelationships(List<RelationClaim> claims);
    }

    public class RelationClaim {
        private AuthUserRecord sourceAccount;
        private long targetUser;
        private int newRelation;

        public RelationClaim(AuthUserRecord sourceAccount, long targetUser, int newRelation) {
            this.sourceAccount = sourceAccount;
            this.targetUser = targetUser;
            this.newRelation = newRelation;
        }

        public AuthUserRecord getSourceAccount() {
            return sourceAccount;
        }

        public long getTargetUser() {
            return targetUser;
        }

        public int getNewRelation() {
            return newRelation;
        }
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Bundle args = getArguments();
        targetUser = (User) args.getSerializable(ARGUMENT_TARGET);

        listView = new ListView(getActivity());
        listView.setChoiceMode(AbsListView.CHOICE_MODE_NONE);

        LinkedHashMap<AuthUserRecord, Relationship> relationships =
                (LinkedHashMap<AuthUserRecord, Relationship>) ((Object[]) args.getSerializable(ARGUMENT_KNOWN_RELATIONS))[0];
        for (AuthUserRecord userRecord : relationships.keySet()) {
            Relationship relationship = relationships.get(userRecord);
            entryList.add(new ListEntry(userRecord, relationship));
        }

        if (args.getBoolean(ARGUMENT_ALL_R4S, false)) {
            if ("toshi_a".equals(targetUser.getScreenName())) {
                Toast.makeText(getActivity(), "ｱｱｱｯwwwwミクッター作者に全垢r4sかまそうとしてるｩwwwwwwwwwwwww\n\n(toshi_a proof を発動しました)", Toast.LENGTH_LONG).show();
            } else {
                for (ListEntry e : entryList) {
                    e.afterRelation = RELATION_PRE_R4S;
                }
            }
        }

        Adapter adapter = new Adapter(getActivity(), entryList);
        listView.setAdapter(adapter);

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle("フォロー状態 @" + targetUser.getScreenName());
        builder.setView(listView);
        builder.setPositiveButton("OK", (dialogInterface, i) -> {
            FollowDialogCallback callback = (FollowDialogCallback) getTargetFragment();
            if (callback != null) {
                List<RelationClaim> claims = new ArrayList<>();
                for (ListEntry entry : entryList) {
                    if (entry.beforeRelation != entry.afterRelation) {
                        claims.add(new RelationClaim(entry.getUserRecord(), targetUser.getId(), entry.afterRelation));
                    }
                }
                callback.onChangedRelationships(claims);
            }
        });
        builder.setNegativeButton("キャンセル", (dialogInterface, i) -> {
        });

        dialog = builder.create();
        return dialog;
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
        private boolean visibleCutoff = false;

        public Adapter(Context context, List<ListEntry> objects) {
            super(context, 0, objects);
            inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            visibleCutoff = PreferenceManager.getDefaultSharedPreferences(context).getBoolean("allow_cutoff", false);
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
                ImageLoaderTask.loadProfileIcon(getContext(), ivOwn, e.getUserRecord().ProfileImageUrl);

                ImageView ivTarget = (ImageView) v.findViewById(R.id.ivFoTarget);
                ivTarget.setTag(targetUser.getProfileImageURLHttps());
                ImageLoaderTask.loadProfileIcon(getContext(), ivTarget, targetUser.getBiggerProfileImageURLHttps());

                final ImageView ivRelation = (ImageView) v.findViewById(R.id.ivFollowStatus);
                final Button btnFollow = (Button) v.findViewById(R.id.btnFollow);
                btnFollow.setTag(e);
                btnFollow.setOnClickListener(view -> {
                    final ListEntry e1 = (ListEntry) view.getTag();

                    switch (e1.afterRelation) {
                        case RELATION_NONE:
                        case RELATION_UNBLOCK:
                            e1.afterRelation = RELATION_FOLLOW;
                            break;
                        case RELATION_BLOCK:
                            e1.afterRelation = RELATION_UNBLOCK;
                            break;
                        case RELATION_FOLLOW:
                        case RELATION_PRE_R4S:
                            if (e1.beforeRelation == RELATION_BLOCK) {
                                e1.afterRelation = RELATION_UNBLOCK;
                            }
                            else {
                                e1.afterRelation = RELATION_NONE;
                            }
                            break;
                        default:
                            e1.afterRelation = e1.beforeRelation;
                            break;
                    }

                    setStatus(e1, btnFollow, ivRelation);
                });
                setStatus(e, btnFollow, ivRelation);

                ImageButton ibMenu = (ImageButton) v.findViewById(R.id.ibMenu);
                ibMenu.setTag(e);
                ibMenu.setOnClickListener(view -> {
                    final ListEntry e1 = (ListEntry) view.getTag();

                    PopupMenu popupMenu = new PopupMenu(getContext(), view);
                    popupMenu.inflate(R.menu.follow);
                    popupMenu.getMenu().findItem(R.id.action_cutoff).setVisible(visibleCutoff);
                    popupMenu.setOnMenuItemClickListener(menuItem -> {
                        switch (menuItem.getItemId()) {
                            case R.id.action_block:
                                e1.afterRelation = RELATION_BLOCK;
                                setStatus(e1, btnFollow, ivRelation);
                                return true;
                            case R.id.action_report:
                                e1.afterRelation = RELATION_PRE_R4S;
                                setStatus(e1, btnFollow, ivRelation);
                                return true;
                            case R.id.action_cutoff:
                                e1.afterRelation = RELATION_CUTOFF;
                                setStatus(e1, btnFollow, ivRelation);
                                return true;
                        }
                        return true;
                    });
                    popupMenu.show();
                });

                TextView tvFoYou = (TextView) v.findViewById(R.id.tvFoYou);
                if (e.getRelationship().getSourceUserId() == e.getRelationship().getTargetUserId()) {
                    btnFollow.setVisibility(View.GONE);
                    ibMenu.setVisibility(View.GONE);
                    tvFoYou.setVisibility(View.VISIBLE);
                }
                else {
                    btnFollow.setVisibility(View.VISIBLE);
                    ibMenu.setVisibility(View.VISIBLE);
                    tvFoYou.setVisibility(View.GONE);
                }
            }

            return v;
        }

        private void setStatus(ListEntry e, Button btnFollow, ImageView ivRelation) {
            if (e.getAfterRelation() == RELATION_BLOCK) {
                btnFollow.setText("ブロック中");
                ivRelation.setImageResource(R.drawable.ic_f_not);
            }
            else if (e.getAfterRelation() == RELATION_PRE_R4S) {
                btnFollow.setText("報告取りやめ");
                ivRelation.setImageResource(R.drawable.ic_f_not);
            }
            else if (e.getAfterRelation() == RELATION_CUTOFF) {
                btnFollow.setText("ブロ解中断");
                ivRelation.setImageResource(R.drawable.ic_f_not);
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
}
