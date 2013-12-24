package shibafu.yukari.activity;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;

import shibafu.yukari.R;

/**
 * Created by Shibafu on 13/12/21.
 */
public class ConfigActivity extends PreferenceActivity {

    @Override
    @SuppressWarnings("deprecation")
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.pref);

        Preference accountManagePref = findPreference("pref_accounts");
        accountManagePref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                startActivity(new Intent(ConfigActivity.this, AccountManageActivity.class));
                return true;
            }
        });

        Preference aboutVersionPref = findPreference("pref_about_version");
        {
            String summaryText = "";
            PackageManager pm = getPackageManager();
            try{
                PackageInfo packageInfo = pm.getPackageInfo(getPackageName(), 0);
                summaryText += "Version " + packageInfo.versionName;
            }catch(PackageManager.NameNotFoundException e){
                e.printStackTrace();
            }
            summaryText += "\nDeveloped by @shibafu528\n\n>> License Information";
            aboutVersionPref.setSummary(summaryText);
        }
        aboutVersionPref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                startActivity(new Intent(ConfigActivity.this, LicenseActivity.class));
                return true;
            }
        });

        Preference prevTimePref = findPreference("pref_prev_time");
        prevTimePref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                final SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ConfigActivity.this);
                int selectedFlags = sp.getInt("pref_prev_time", 0);
                final boolean[] selectedStates = new boolean[24];
                for (int i = 0; i < 24; ++i) {
                    selectedStates[i] = (selectedFlags & 0x01) == 1;
                    selectedFlags >>>= 1;
                }
                AlertDialog.Builder builder = new AlertDialog.Builder(ConfigActivity.this)
                        .setTitle("サムネイル非表示にする時間帯")
                        .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                int selectedFlags = 0;
                                for (int i = 23; i >= 0; --i) {
                                    selectedFlags <<= 1;
                                    selectedFlags |= selectedStates[i]?1:0;
                                }
                                sp.edit().putInt("pref_prev_time", selectedFlags).commit();
                            }
                        })
                        .setNegativeButton("キャンセル", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                            }
                        })
                        .setMultiChoiceItems(R.array.pref_prev_time_entries, selectedStates, new DialogInterface.OnMultiChoiceClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which, boolean isChecked) {
                                selectedStates[which] = isChecked;
                            }
                        });
                builder.create().show();
                return true;
            }
        });
    }
}