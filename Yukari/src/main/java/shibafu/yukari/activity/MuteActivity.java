package shibafu.yukari.activity;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.app.ListFragment;
import android.support.v7.app.ActionBarActivity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.util.List;

import shibafu.yukari.R;
import shibafu.yukari.database.MuteConfig;
import shibafu.yukari.fragment.DriveConnectionDialogFragment;
import shibafu.yukari.fragment.SimpleAlertDialogFragment;
import shibafu.yukari.service.TwitterService;
import shibafu.yukari.service.TwitterServiceDelegate;

/**
 * Created by shibafu on 14/04/22.
 */
public class MuteActivity extends ActionBarActivity implements TwitterServiceDelegate{

    public static final String EXTRA_QUERY = "query";
    public static final String EXTRA_SCOPE = "scope";

    private static final String FRAGMENT_TAG = "inner";
    private TwitterService service;
    private boolean serviceBound = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_parent);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        if (savedInstanceState == null) {
            FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
            InnerFragment fragment = new InnerFragment();
            transaction.replace(R.id.frame, fragment, FRAGMENT_TAG);
            transaction.commit();

            Intent intent = getIntent();
            if (intent.hasExtra(EXTRA_QUERY)) {
                MuteConfig config = new MuteConfig(
                        intent.getIntExtra(EXTRA_SCOPE, 0),
                        MuteConfig.MATCH_EXACT,
                        MuteConfig.MUTE_TWEET,
                        intent.getStringExtra(EXTRA_QUERY)
                );
                MuteConfigDialogFragment dialogFragment = MuteConfigDialogFragment.newInstance(config, fragment);
                dialogFragment.show(getSupportFragmentManager(), "config");
            }
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onStart() {
        super.onStart();
        bindService(new Intent(this, TwitterService.class), connection, BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        super.onStop();
        unbindService(connection);
    }

    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            TwitterService.TweetReceiverBinder binder = (TwitterService.TweetReceiverBinder) service;
            MuteActivity.this.service = binder.getService();
            serviceBound = true;

            findInnerFragment().reloadList();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            serviceBound = false;
        }
    };

    public InnerFragment findInnerFragment() {
        return ((InnerFragment)getSupportFragmentManager().findFragmentByTag(FRAGMENT_TAG));
    }

    @Override
    public TwitterService getTwitterService() {
        return service;
    }

    public static class InnerFragment extends ListFragment implements
            SimpleAlertDialogFragment.OnDialogChoseListener,
            DriveConnectionDialogFragment.OnDriveImportCompletedListener {
        private static final String DRIVE_FILE_NAME = "mute.json";
        private static final String DRIVE_MIME_TYPE = "application/json";

        private static final int DIALOG_DELETE = 0;
        private static final int DIALOG_IMPORT = 1;
        private static final int DIALOG_EXPORT = 2;

        private List<MuteConfig> configs;
        private MuteConfig deleteReserve = null;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setHasOptionsMenu(true);
        }

        @Override
        public void onActivityCreated(Bundle savedInstanceState) {
            super.onActivityCreated(savedInstanceState);
            getListView().setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
                @Override
                public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                    deleteReserve = configs.get(position);
                    SimpleAlertDialogFragment dialogFragment = SimpleAlertDialogFragment.newInstance(
                            DIALOG_DELETE,
                            "確認", "設定を削除しますか?", "OK", "キャンセル"
                    );
                    dialogFragment.setTargetFragment(InnerFragment.this, 1);
                    dialogFragment.show(getChildFragmentManager(), "alert");
                    return true;
                }
            });

        }

        @Override
        public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
            inflater.inflate(R.menu.mute, menu);
        }

        @Override
        public boolean onOptionsItemSelected(MenuItem item) {
            switch (item.getItemId()) {
                case R.id.action_add: {
                    MuteConfigDialogFragment dialogFragment = MuteConfigDialogFragment.newInstance(null, this);
                    dialogFragment.show(getFragmentManager(), "config");
                    return true;
                }
                case R.id.action_import: {
                    SimpleAlertDialogFragment dialogFragment = SimpleAlertDialogFragment.newInstance(
                            DIALOG_IMPORT,
                            "Driveからインポート",
                            "Driveから設定をインポートします。\nこの端末のミュート設定は全て上書きされます！！\n実行してもよろしいですか？",
                            "OK",
                            "キャンセル"
                    );
                    dialogFragment.setTargetFragment(this, 0);
                    dialogFragment.show(getFragmentManager(), "dimport");
                    return true;
                }
                case R.id.action_export: {
                    SimpleAlertDialogFragment dialogFragment = SimpleAlertDialogFragment.newInstance(
                            DIALOG_EXPORT,
                            "Driveにエクスポート",
                            "Driveに設定をエクスポートします。\n既にエクスポートしたデータがある場合上書きします。\n実行してもよろしいですか？",
                            "OK",
                            "キャンセル"
                    );
                    dialogFragment.setTargetFragment(this, 0);
                    dialogFragment.show(getFragmentManager(), "dimport");
                    return true;
                }
            }
            return super.onOptionsItemSelected(item);
        }

        @Override
        public void onListItemClick(ListView l, View v, int position, long id) {
            MuteConfig config = configs.get(position);
            MuteConfigDialogFragment dialogFragment = MuteConfigDialogFragment.newInstance(config, this);
            dialogFragment.show(getFragmentManager(), "config");
        }

        public void updateMuteConfig(MuteConfig config) {
            TwitterService twitterService = ((MuteActivity) getActivity()).getTwitterService();
            twitterService.getDatabase().updateMuteConfig(config);
            twitterService.updateMuteConfig();

            reloadList();
        }

        public void reloadList() {
            configs = ((MuteActivity) getActivity()).getTwitterService().getDatabase().getMuteConfig();
            setListAdapter(new Adapter(getActivity(), configs));
        }

        @Override
        public void onDialogChose(int requestCode, int which) {
            if (which == DialogInterface.BUTTON_POSITIVE) {
                switch (requestCode) {
                    case DIALOG_DELETE:
                        if (deleteReserve != null) {
                            TwitterService twitterService = ((MuteActivity) getActivity()).getTwitterService();
                            twitterService.getDatabase().deleteMuteConfig(deleteReserve.getId());
                            twitterService.updateMuteConfig();
                            reloadList();
                            Toast.makeText(getActivity(), "設定を削除しました", Toast.LENGTH_LONG).show();
                        }
                        deleteReserve = null;
                        break;
                    case DIALOG_IMPORT:
                        DriveConnectionDialogFragment.newInstance(DRIVE_FILE_NAME, DRIVE_MIME_TYPE, this)
                                .show(getFragmentManager(), "import");
                        break;
                    case DIALOG_EXPORT:
                        DriveConnectionDialogFragment.newInstance(
                                DRIVE_FILE_NAME, DRIVE_MIME_TYPE, new Gson().toJson(configs).getBytes())
                                .show(getFragmentManager(), "export");
                        break;
                }
            }
        }

        @Override
        public void onDriveImportCompleted(byte[] bytes) {
            List<MuteConfig> newConfigs = new Gson().fromJson(new String(bytes), new TypeToken<List<MuteConfig>>(){}.getType());
            TwitterService twitterService = ((MuteActivity) getActivity()).getTwitterService();
            twitterService.getDatabase().importMuteConfigs(newConfigs);
            twitterService.updateMuteConfig();
            reloadList();
        }

        private class Adapter extends ArrayAdapter<MuteConfig> {
            private LayoutInflater inflater;
            private String[] targetValues, matchValues, eraseValues;

            public Adapter(Context context, List<MuteConfig> objects) {
                super(context, 0, objects);
                inflater = (LayoutInflater) context.getSystemService(LAYOUT_INFLATER_SERVICE);

                Resources res = context.getResources();

                targetValues = res.getStringArray(R.array.mute_target_values);
                matchValues = res.getStringArray(R.array.mute_match_values);
                eraseValues = res.getStringArray(R.array.mute_erase_values);
            }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                if (convertView == null) {
                    convertView = inflater.inflate(android.R.layout.simple_list_item_2, null);
                }
                MuteConfig item = getItem(position);
                if (item != null) {
                    TextView tv = (TextView) convertView.findViewById(android.R.id.text1);
                    tv.setText(item.getQuery());

                    tv = (TextView) convertView.findViewById(android.R.id.text2);
                    tv.setText(String.format("%s[%s] - %s",
                            targetValues[item.getScope()],
                            matchValues[item.getMatch()],
                            eraseValues[item.getMute()]));
                }
                return convertView;
            }
        }
    }

    public static class MuteConfigDialogFragment extends DialogFragment {

        public static MuteConfigDialogFragment newInstance(MuteConfig config, Fragment target) {
            MuteConfigDialogFragment dialogFragment = new MuteConfigDialogFragment();
            Bundle args = new Bundle();
            args.putSerializable("config", config);
            dialogFragment.setArguments(args);
            dialogFragment.setTargetFragment(target, 1);
            return dialogFragment;
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            View v = getActivity().getLayoutInflater().inflate(R.layout.dialog_mute, null);
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
                v.setBackgroundColor(Color.WHITE);
            }
            final EditText edit = (EditText) v.findViewById(R.id.etMuteTarget);
            final Spinner spTarget = (Spinner) v.findViewById(R.id.spMuteTarget);
            final Spinner spMatch = (Spinner) v.findViewById(R.id.spMuteMatch);
            final Spinner spErase = (Spinner) v.findViewById(R.id.spMuteErase);

            MuteConfig config = (MuteConfig) getArguments().getSerializable("config");
            String title = "新規追加";
            if (config != null) {
                edit.setText(config.getQuery());

                spTarget.setSelection(config.getScope());
                spMatch.setSelection(config.getMatch());
                spErase.setSelection(config.getMute());

                title = "編集";
            }

            AlertDialog dialog = new AlertDialog.Builder(getActivity())
                    .setTitle(title)
                    .setView(v)
                    .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dismiss();
                            InnerFragment innerFragment = (InnerFragment) getTargetFragment();
                            if (innerFragment == null) {
                                throw new RuntimeException("TargetFragmentが設定されてないよ！！！１１");
                            }
                            MuteConfig config = (MuteConfig) getArguments().getSerializable("config");
                            if (config == null) {
                                config = new MuteConfig(spTarget.getSelectedItemPosition(),
                                        spMatch.getSelectedItemPosition(),
                                        spErase.getSelectedItemPosition(),
                                        edit.getText().toString());
                            } else {
                                config.setScope(spTarget.getSelectedItemPosition());
                                config.setMatch(spMatch.getSelectedItemPosition());
                                config.setMute(spErase.getSelectedItemPosition());
                                config.setQuery(edit.getText().toString());
                            }
                            innerFragment.updateMuteConfig(config);
                        }
                    })
                    .setNegativeButton("キャンセル", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                        }
                    })
                    .create();

            return dialog;
        }
    }

}
