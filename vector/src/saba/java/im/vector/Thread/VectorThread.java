package im.vector.Thread;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.media.MediaRecorder;
import android.net.Uri;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

import im.vector.BuildConfig;

import static androidx.core.content.FileProvider.getUriForFile;

public class VectorThread extends Thread {

    private static File newFile;

    public static MediaRecorder getAudioRecorder() {
        return audioRecorder;
    }

    private static MediaRecorder audioRecorder;

    public void startVoiceRecorder(Context context) {

        audioRecorder = new MediaRecorder();
        try {
            @SuppressLint("SimpleDateFormat") SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss");
            String currentTime = sdf.format(new Date());
            File voicePath = new File(context.getApplicationContext().getFilesDir(), "ext_share");
            newFile = new File(voicePath, currentTime + ".aac");
            String outputFile = newFile.getAbsolutePath();
            audioRecorder.setOutputFile(outputFile);
            audioRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            audioRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            audioRecorder.setAudioEncoder(MediaRecorder.OutputFormat.AMR_NB);
            audioRecorder.setAudioEncodingBitRate(16 * 44100);
            audioRecorder.setAudioSamplingRate(44100);
            audioRecorder.prepare();
            audioRecorder.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    public Intent stopVoiceRecorder(Context context) {
        audioRecorder.stop();
        audioRecorder.reset();
        Uri contentUri = getUriForFile(context.getApplicationContext(),
                BuildConfig.APPLICATION_ID + ".fileProvider",
                newFile);
        Intent intent = new Intent();
        intent.setData(contentUri);
        return intent;
    }

    public void resetVoiceRecorder() {
        if (audioRecorder != null) {
            audioRecorder.reset();
        }
    }

    public void deleteFile() {
        if (newFile.exists()) {
            newFile.delete();
        }
    }
}
