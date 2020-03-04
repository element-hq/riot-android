package im.vector.keymanager;

import android.content.Context;
import android.net.Uri;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModelProviders;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.core.Log;
import org.matrix.androidsdk.core.callback.SimpleApiCallback;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;

import im.vector.activity.CommonActivityUtils;
import im.vector.callback.OnRecoveryKeyListener;
import im.vector.fragments.keysbackup.setup.KeysBackupSetupSharedViewModel;
import im.vector.sharedpreferences.BatnaSharedPreferences;
import im.vector.ui.arch.LiveEvent;


// This class is used for save and restoring backup keys
public class KeyManager {

    private static final String HAS_RUN_BEFORE = "hasRunBefore";
    public static final String RECOVERY_KEY_EXISTS = "recoveryKeyExists";

    // This method saves backup key on internal storage
    public static void getKeyBackup(AppCompatActivity activity, MXSession session) {
        BatnaSharedPreferences batnaSharedPreferences = new BatnaSharedPreferences(activity.getApplicationContext());
        if (!batnaSharedPreferences.getBooleanData(HAS_RUN_BEFORE)) {
            KeysBackupSetupSharedViewModel viewModel = ViewModelProviders.of(activity).get(KeysBackupSetupSharedViewModel.class);
            MutableLiveData<Boolean> mutableLiveData = new MutableLiveData<>();
            mutableLiveData.setValue(true);
            viewModel.setShowManualExport(mutableLiveData);
            viewModel.initSession(session);
            LiveEvent<String> liveEvent = new LiveEvent<>(KeysBackupSetupSharedViewModel.NAVIGATE_MANUAL_EXPORT);
            MutableLiveData<LiveEvent<String>> liveEventMutableLiveData = new MutableLiveData<>();
            liveEventMutableLiveData.postValue(liveEvent);
            viewModel.setNavigateEvent(liveEventMutableLiveData);
            final String[] recoveryKey = new String[1];
            viewModel.prepareRecoveryKey(activity.getApplicationContext(), session, null, new OnRecoveryKeyListener() {
                @Override
                public void onRecoveryKeyGenerated() {
                    recoveryKey[0] = viewModel.getMegolmBackupCreationInfo().getRecoveryKey();
                    ByteArrayInputStream stream = new ByteArrayInputStream(recoveryKey[0].getBytes());
                    String url = viewModel.session.getMediaCache().saveMedia(stream, "recovery-key" + System.currentTimeMillis() + ".txt", "text/plain");
                    try {
                        stream.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    CommonActivityUtils.saveMediaIntoDownloads(activity.getApplicationContext(),
                            new File(Uri.parse(url).getPath()), "Messenger-recovery-key.txt", "text/plain", new SimpleApiCallback<String>() {
                                @Override
                                public void onSuccess(String info) {
                                    Log.v("keybackup: ", "saved in: " + Uri.parse(url).getPath());
                                    viewModel.setCopyHasBeenMade(true);
                                    batnaSharedPreferences.saveBooleanData(HAS_RUN_BEFORE, true);
                                }
                            });
                }

                @Override
                public void onRecoveryKeyFailed(Exception e) {
                    Log.v("RecoveryKey generation ", "Failed! " + e.getMessage());
                }
            });
        }
    }
}
