package im.vector.keymanager;

import android.content.Context;
import android.net.Uri;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModelProviders;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.core.Log;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

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
                    FileOutputStream outputStream = null;
                    try {
                        outputStream = activity.getApplicationContext().openFileOutput("recovery-key-" + session.getMyUserId(), Context.MODE_PRIVATE);
                        outputStream.write(recoveryKey[0].getBytes());
                        outputStream.close();
                    } catch (FileNotFoundException e) {
                        Log.e("File not found: ", e.getMessage());
                        e.printStackTrace();
                    } catch (IOException e) {
                        Log.e("IO Exception: ", e.getMessage());
                        e.printStackTrace();
                    }
                }

                @Override
                public void onRecoveryKeyFailed(Exception e) {
                    Log.v("RecoveryKey generation ", "Failed! " + e.getMessage());
                }
            });
        }
    }
}
