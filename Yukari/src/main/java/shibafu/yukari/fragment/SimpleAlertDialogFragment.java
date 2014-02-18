package shibafu.yukari.fragment;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;

/**
 * Created by shibafu on 14/02/18.
 */
public class SimpleAlertDialogFragment extends DialogFragment implements DialogInterface.OnClickListener {

    public static final String ARG_TITLE = "title";
    public static final String ARG_MESSAGE = "message";
    public static final String ARG_POSSITIVE = "possitive";
    public static final String ARG_NEGATIVE = "negative";

    public static SimpleAlertDialogFragment newInstance(
            String title, String message, String possitive, String negative) {
        SimpleAlertDialogFragment fragment = new SimpleAlertDialogFragment();
        Bundle args = new Bundle();
        args.putString(ARG_TITLE, title);
        args.putString(ARG_MESSAGE, message);
        args.putString(ARG_POSSITIVE, possitive);
        args.putString(ARG_NEGATIVE, negative);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Bundle args = getArguments();
        String title = args.getString(ARG_TITLE);
        String message = args.getString(ARG_MESSAGE);
        String possitive = args.getString(ARG_POSSITIVE);
        String negative = args.getString(ARG_NEGATIVE);

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

        if (title != null) builder.setTitle(title);
        if (message != null) builder.setMessage(message);
        if (possitive != null) builder.setPositiveButton(possitive, this);
        if (negative != null) builder.setNegativeButton(negative, this);

        return builder.create();
    }

    @Override
    public void onClick(DialogInterface dialogInterface, int i) {
        dismiss();

        DialogInterface.OnClickListener listener = null;
        if (getParentFragment() != null &&
                getParentFragment() instanceof DialogInterface.OnClickListener) {
            listener = (DialogInterface.OnClickListener) getParentFragment();
        }
        else if (getTargetFragment() != null &&
                getTargetFragment() instanceof DialogInterface.OnClickListener) {
            listener = (DialogInterface.OnClickListener) getTargetFragment();
        }
        else if (getActivity() != null && getActivity() instanceof DialogInterface.OnClickListener) {
            listener = (DialogInterface.OnClickListener) getActivity();
        }

        if (listener != null) {
            listener.onClick(dialogInterface, i);
        }
    }
}