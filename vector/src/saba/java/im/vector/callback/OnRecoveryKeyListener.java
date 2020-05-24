package im.vector.callback;

public interface OnRecoveryKeyListener {

    void onRecoveryKeyGenerated();
    void onRecoveryKeyFailed(Exception e);
}
