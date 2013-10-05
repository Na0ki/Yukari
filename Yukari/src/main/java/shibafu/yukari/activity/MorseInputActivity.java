package shibafu.yukari.activity;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;

import shibafu.util.MorseCodec;
import shibafu.yukari.R;

/**
 * Created by Shibafu on 13/08/14.
 */
public class MorseInputActivity extends Activity {

    private AlertDialog currentDialog;

    private TextView tvPreview;
    private EditText etInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
        View v = inflater.inflate(R.layout.view_morse, null);

        tvPreview = (TextView) v.findViewById(R.id.tvMorsePreview);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
            tvPreview.setTextColor(Color.WHITE);
        }
        etInput = (EditText) v.findViewById(R.id.etMorseInput);
        etInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                tvPreview.setText(MorseCodec.encode(s.toString()));
            }
        });
        etInput.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                    imm.showSoftInput(etInput, InputMethodManager.SHOW_IMPLICIT);
                }
            }
        });

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("モールス変換");
        builder.setView(v);
        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String out = MorseCodec.encode(etInput.getText().toString());
                Intent intent = new Intent();
                intent.putExtra("replace_key", out);
                setResult(RESULT_OK, intent);
                finish();
            }
        });
        builder.setNegativeButton("キャンセル", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                currentDialog = null;
                setResult(RESULT_CANCELED);
                finish();
            }
        });
        builder.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                dialog.dismiss();
                currentDialog = null;
                setResult(RESULT_CANCELED);
                finish();
            }
        });

        currentDialog = builder.create();
        currentDialog.show();

        this.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (currentDialog != null) {
            currentDialog.show();
        }
        else {
            setResult(RESULT_CANCELED);
            finish();
        }
    }
}