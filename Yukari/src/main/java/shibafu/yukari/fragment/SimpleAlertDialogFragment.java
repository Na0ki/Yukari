package shibafu.yukari.fragment;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;
import android.view.View;
import android.widget.Button;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * Created by shibafu on 14/02/18.
 */
public class SimpleAlertDialogFragment extends DialogFragment implements DialogInterface.OnClickListener {

    public static final String ARG_REQUEST_CODE = "requestcode";
    public static final String ARG_ICON = "icon";
    public static final String ARG_TITLE = "title";
    public static final String ARG_MESSAGE = "message";
    public static final String ARG_POSITIVE = "positive";
    public static final String ARG_NEUTRAL = "neutral";
    public static final String ARG_NEGATIVE = "negative";
    public static final String ARG_DISABLE_CAPS = "disable_caps";
    public static final String ARG_EXTRAS = "extras";

    public interface OnDialogChoseListener {
        void onDialogChose(int requestCode, int which, @Nullable Bundle extras);
    }

    public static SimpleAlertDialogFragment newInstance(
            int requestCode,
            String title, String message, String positive, String negative) {
        SimpleAlertDialogFragment fragment = new SimpleAlertDialogFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_REQUEST_CODE, requestCode);
        args.putString(ARG_TITLE, title);
        args.putString(ARG_MESSAGE, message);
        args.putString(ARG_POSITIVE, positive);
        args.putString(ARG_NEGATIVE, negative);
        fragment.setArguments(args);
        return fragment;
    }

    public static SimpleAlertDialogFragment newInstance(
            int requestCode,
            String title, String message, String positive, String neutral, String negative) {
        SimpleAlertDialogFragment fragment = new SimpleAlertDialogFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_REQUEST_CODE, requestCode);
        args.putString(ARG_TITLE, title);
        args.putString(ARG_MESSAGE, message);
        args.putString(ARG_POSITIVE, positive);
        args.putString(ARG_NEUTRAL, neutral);
        args.putString(ARG_NEGATIVE, negative);
        fragment.setArguments(args);
        return fragment;
    }

    public static SimpleAlertDialogFragment newInstance(
            int requestCode,
            int iconId,
            String title, String message, String positive, String negative) {
        SimpleAlertDialogFragment fragment = new SimpleAlertDialogFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_REQUEST_CODE, requestCode);
        args.putInt(ARG_ICON, iconId);
        args.putString(ARG_TITLE, title);
        args.putString(ARG_MESSAGE, message);
        args.putString(ARG_POSITIVE, positive);
        args.putString(ARG_NEGATIVE, negative);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Bundle args = getArguments();
        int iconId = args.getInt(ARG_ICON, -1);
        String title = args.getString(ARG_TITLE);
        String message = args.getString(ARG_MESSAGE);
        String positive = args.getString(ARG_POSITIVE);
        String neutral = args.getString(ARG_NEUTRAL);
        String negative = args.getString(ARG_NEGATIVE);
        boolean disableCaps = args.getBoolean(ARG_DISABLE_CAPS);

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

        if (iconId > -1) builder.setIcon(iconId);
        if (title != null) builder.setTitle(title);
        if (message != null) builder.setMessage(message);
        if (positive != null) builder.setPositiveButton(positive, this);
        if (neutral != null) builder.setNeutralButton(neutral, this);
        if (negative != null) builder.setNegativeButton(negative, this);

        Dialog dialog = builder.create();

        // 大文字化対策
        if (disableCaps) {
            dialog.setOnShowListener(d -> {
                View[] views = new View[]{
                        dialog.findViewById(android.R.id.button1),
                        dialog.findViewById(android.R.id.button2),
                        dialog.findViewById(android.R.id.button3)
                };
                for (View v : views) {
                    if (v instanceof Button) {
                        ((Button) v).setTransformationMethod(null);
                    }
                }
            });
        }

        return dialog;
    }

    @Override
    public void onClick(DialogInterface dialogInterface, int i) {
        dismiss();

        OnDialogChoseListener listener = null;
        if (getParentFragment() != null &&
                getParentFragment() instanceof OnDialogChoseListener) {
            listener = (OnDialogChoseListener) getParentFragment();
        } else if (getTargetFragment() != null &&
                getTargetFragment() instanceof OnDialogChoseListener) {
            listener = (OnDialogChoseListener) getTargetFragment();
        } else if (getActivity() != null && getActivity() instanceof OnDialogChoseListener) {
            listener = (OnDialogChoseListener) getActivity();
        }

        if (listener != null) {
            listener.onDialogChose(getArguments().getInt(ARG_REQUEST_CODE), i, getArguments().getBundle(ARG_EXTRAS));
        }
    }

    @Override
    public void onCancel(DialogInterface dialog) {
        onClick(dialog, DialogInterface.BUTTON_NEGATIVE);
    }

    @Setter
    @Accessors(chain = true)
    @RequiredArgsConstructor
    public static class Builder {
        final int requestCode;
        int iconId;
        String title;
        String message;
        String positive;
        String neutral;
        String negative;
        boolean disableCaps;
        Bundle extras;

        public SimpleAlertDialogFragment build() {
            SimpleAlertDialogFragment fragment = new SimpleAlertDialogFragment();
            Bundle args = new Bundle();
            args.putInt(ARG_REQUEST_CODE, requestCode);
            args.putInt(ARG_ICON, iconId);
            args.putString(ARG_TITLE, title);
            args.putString(ARG_MESSAGE, message);
            args.putString(ARG_POSITIVE, positive);
            args.putString(ARG_NEUTRAL, neutral);
            args.putString(ARG_NEGATIVE, negative);
            args.putBoolean(ARG_DISABLE_CAPS, disableCaps);
            args.putBundle(ARG_EXTRAS, extras);
            fragment.setArguments(args);
            return fragment;
        }
    }
}
