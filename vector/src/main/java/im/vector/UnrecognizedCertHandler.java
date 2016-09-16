package im.vector;


import android.app.Activity;
import android.content.DialogInterface;
import android.support.v7.app.AlertDialog;
import android.view.LayoutInflater;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import org.matrix.androidsdk.HomeserverConnectionConfig;
import org.matrix.androidsdk.ssl.Fingerprint;

import java.util.HashMap;
import java.util.HashSet;

public class UnrecognizedCertHandler {
    private static final String LOG_TAG = "UnrecognizedCertHandler";

    private static HashMap<String, HashSet<Fingerprint>> ignoredFingerprints = new HashMap<String, HashSet<Fingerprint>>();
    private static HashSet<String> openDialogIds = new HashSet<String>();

    public static void show(final HomeserverConnectionConfig hsConfig, final Fingerprint unrecognizedFingerprint, boolean existing, final Callback callback) {
        final Activity activity = VectorApp.getCurrentActivity();
        if (activity == null) return;

        final String dialogId;
        if (hsConfig.getCredentials() != null) {
            dialogId = hsConfig.getCredentials().userId;
        } else {
            dialogId = hsConfig.getHomeserverUri().toString();
        }


        if (openDialogIds.contains(dialogId)) {
            Log.i(LOG_TAG, "Not opening dialog " + dialogId + " as one is already open.");
            return;
        }

        if (hsConfig.getCredentials() != null) {
            HashSet<Fingerprint> f = ignoredFingerprints.get(hsConfig.getCredentials().userId);
            if (f != null && f.contains(unrecognizedFingerprint)) {
                callback.onIgnore();
                return;
            }
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(activity);

        LayoutInflater inflater = activity.getLayoutInflater();

        View layout = inflater.inflate(R.layout.ssl_fingerprint_prompt, null);

        TextView sslFingerprintTitle = (TextView) layout.findViewById(R.id.ssl_fingerprint_title);
        sslFingerprintTitle.setText(
                String.format(activity.getString(R.string.ssl_fingerprint_hash), unrecognizedFingerprint.getType().toString())
        );

        TextView sslFingerprint = (TextView) layout.findViewById(R.id.ssl_fingerprint);
        sslFingerprint.setText(unrecognizedFingerprint.getBytesAsHexString());

        TextView sslUserId = (TextView) layout.findViewById(R.id.ssl_user_id);
        if (hsConfig.getCredentials() != null) {
            sslUserId.setText(
                    activity.getString(R.string.username) + ":  " + hsConfig.getCredentials().userId
            );
        } else {
            sslUserId.setText(
                    activity.getString(R.string.hs_url) + ":  " + hsConfig.getHomeserverUri().toString()
            );
        }

        TextView sslExpl = (TextView) layout.findViewById(R.id.ssl_explanation);
        if (existing) {
            if (hsConfig.getAllowedFingerprints().size() > 0) {
                sslExpl.setText(activity.getString(R.string.ssl_expected_existing_expl));
            } else {
                sslExpl.setText(activity.getString(R.string.ssl_unexpected_existing_expl));
            }
        } else {
            sslExpl.setText(activity.getString(R.string.ssl_cert_new_account_expl));
        }

        builder.setView(layout);
        builder.setTitle(R.string.ssl_could_not_verify);

        builder.setPositiveButton(R.string.ssl_trust, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                hsConfig.getAllowedFingerprints().add(unrecognizedFingerprint);
                callback.onAccept();
            }
        });

        if (existing) {
            builder.setNegativeButton(R.string.ssl_remain_offline, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    if (hsConfig.getCredentials() != null) {
                        HashSet<Fingerprint> f = ignoredFingerprints.get(hsConfig.getCredentials().userId);
                        if (f == null) {
                            f = new HashSet<Fingerprint>();
                            ignoredFingerprints.put(hsConfig.getCredentials().userId, f);
                        }

                        f.add(unrecognizedFingerprint);
                    }
                    callback.onIgnore();
                }
            });

            builder.setNeutralButton(R.string.ssl_logout_account, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    callback.onReject();
                }
            });
        } else {
            builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    callback.onReject();
                }
            });
        }

        final AlertDialog dialog = builder.create();

        final EventEmitter.Listener<Activity> destroyListener = new EventEmitter.Listener<Activity>() {
            @Override
            public void onEventFired(EventEmitter<Activity> emitter, Activity destroyedActivity) {
                if (activity == destroyedActivity) {
                    Log.e(LOG_TAG, "Dismissed!");
                    openDialogIds.remove(dialogId);
                    dialog.dismiss();
                    emitter.unregister(this);
                }
            }
        };

        final EventEmitter<Activity> emitter = VectorApp.getInstance().getOnActivityDestroyedListener();
        emitter.register(destroyListener);

        dialog.setOnDismissListener(new AlertDialog.OnDismissListener() {
            public void onDismiss(DialogInterface dialog) {
                Log.e(LOG_TAG, "Dismissed!");
                openDialogIds.remove(dialogId);
                emitter.unregister(destroyListener);
            }
        });

        dialog.show();
        openDialogIds.add(dialogId);
    }

    public interface Callback {
        void onAccept();
        void onIgnore();
        void onReject();
    }
}
