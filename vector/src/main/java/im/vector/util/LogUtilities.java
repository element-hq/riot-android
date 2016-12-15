/*
 * Copyright 2014 OpenMarket Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package im.vector.util;

import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;

/**
 * Manages all the logs which are sent to us when a user sends a bug report.
 */
public class LogUtilities {

    private static final String LOG_TAG = "LogUtilities";

    // the current log directory
    private static File mLogDirectory = null;

    private static final String[] LOGCAT_CMD = new String[] {
             "logcat", ///< Run 'logcat' command
             "-d",  ///< Dump the log rather than continue outputting it
             "-v", // formatting
             "threadtime", // include timestamps
             "AndroidRuntime:E " + ///< Pick all AndroidRuntime errors (such as uncaught exceptions)
             "communicatorjni:V " + ///< All communicatorjni logging
             "libcommunicator:V " + ///< All libcommunicator logging
             "DEBUG:V " + ///< All DEBUG logging - which includes native land crashes (seg faults, etc)
             "*:S" ///< Everything else silent, so don't pick it..
             };

    private static final String[]  LOGCAT_CMD_DEBUG = new String[] {
            "logcat",
            "-d",
            "-v",
            "threadtime",
            "Retrofit:S",
            "ProgressBar:S",
            "AbsListView:S",
            "dalvikvm:S",
            "OpenGLRenderer:S",
            "NativeCrypto:S",
            "VelocityTracker:S",
            "MaliEGL:S",
            "GraphicBuffer:S",
            "WifiStateMachine:S",
            "ActivityThread:S",
            "PowerManagerService:S",
            "BufferQueue:S",
            "KeyguardUpdateMonitor:S",
            "wpa_supplicant:S",
            "ANRManager:S",
            "InputReader:S",
            "PowerUI:S",
            "BatteryService:S",
            "qdhwcomposer:S",
            "ServiceDumpSys:S",
            "DisplayPowerController:S",
            "View:S",
            "ListView:S",
            "Posix:S",
            "chatty:S",
            "ViewRootImpl:S",
            "TextView:S",
            "MotionRecognitionManager:S",
            "DisplayListCanvas:S",
            "AudioManager:S",
            "irsc_util:S",
            "QCamera2HWI:S",
            "audio_hw_primary:S",
            "msm8974_platform:S",
            "ACDB-LOADER:S",
            "platform_parser:S",
            "audio_hw_ssr:S",
            "audio_hw_spkr_prot:S",
            "Thermal-Lib:S",
            "AudioFlinger:S",
            "EffectDiracSound:S",
            "BufferProvider:S",
            "MonoPipe:S",
            "bt_a2dp_hw:S",
            "r_submix:S",
            "AudioPolicyManagerCustom:S",
            "RadioService:S",
            "mediaserver:S",
            "ListenService:S",
            "InstallerConnection:S",
            "SystemServer:S",
            "SystemServiceManager:S",
            "BaseMiuiBroadcastManager:S",
            "BatteryStatsImpl:S",
            "IntentFirewall:S",
            "ServiceThread:S",
            "AppOps:S",
            "DisplayManagerService:S",
            "SELinuxMMAC:S",
            "PackageManager:S",
            "PackageParser:S",
            "PreinstallApp:S",
            "VoldConnector:S",
            "SoundTriggerHelper:S",
            "AutomaticBrightnessController:S",
            "KeyguardServiceDelegate:S",
            "VoiceInteractionManagerService:S",
            "SystemServer:S",
            "UsbAlsaManager:S",
            "Telecom:S",
            "LocationManagerInjector:S",
            "LocationPolicy:S",
            "MmsServiceBroker:S",
            "MountService:S",
            "ACodec:S",
            "OMXNodeInstance:S",
            "MM_OSAL:S",
            "OMXNodeInstance:S",
            "SoftMPEG4Encoder:S",
            "audio_hw_extn:S",
            "audio_hw_fm:S",
            "ContextImpl:S",
            "ActiveAndroid:S",
            "bt_a2dp_hw:S",
            "BroadcastQueueInjector:S",
            "AutoStartManagerService:S",
            "Ext4Crypt:S",
            "MccTable:S",
            "DiracAPI:S",
            "skia:S",
            "libc-netbsd:S",
            "chromium:S",
            "v8:S",
            "PreferenceGroup:S",
            "Preference:S",
            "*:*"
    };

    private static final int BUFFER_SIZE = 1024;

    /**
     * Retrieves the logs from a dedicated command.
     * @param cmd the command to execute.
     * @return the logs.
     */
    private static String getLog(String[] cmd) {
        Process logcatProc;
        try {
            logcatProc = Runtime.getRuntime().exec(cmd);
        }
        catch (IOException e1) {
            return "";
        }

        BufferedReader reader = null;
        String response = "";
        try {
            String separator = System.getProperty("line.separator");
            StringBuilder sb = new StringBuilder();
            reader = new BufferedReader(new InputStreamReader(logcatProc.getInputStream()), BUFFER_SIZE);
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
                sb.append(separator);
            }
            response = sb.toString();
        }
        catch (IOException e) {
            Log.e(LOG_TAG, "getLog fails with " + e.getLocalizedMessage());
        }
        finally {
            if (reader != null) {
                try {
                    reader.close();
                }
                catch (IOException e) {
                    Log.e(LOG_TAG, "getLog fails with " + e.getLocalizedMessage());
                }
            }
        }
        return response;
    }

    /**
     * @return the error logcat command line.
     */
    public static String getLogCatError() {
        return getLog(LOGCAT_CMD);
    }

    /**
     * @return the debug logcat command line.
     */
    public static String getLogCatDebug() {
        return getLog(LOGCAT_CMD_DEBUG);
    }

    /**
     * Set the Logcat directory.
     * @param logDirectory the new directory file.
     */
    public static void setLogDirectory(File logDirectory) {
        mLogDirectory = logDirectory;
    }

    /**
     * Check if the log directory exists.
     * Create it if it s not created
     * @return the log directory file.
     */
    public static File ensureLogDirectoryExists() {
        if (mLogDirectory == null) {
            return null;
        }
        if (!mLogDirectory.exists()) {
            mLogDirectory.mkdirs();
        }
        return mLogDirectory;
    }

    /**
     * Store the current logs.
     * The previous ones are rotated.
     */
    public static void storeLogcat() {
        LogUtilities.rotateLogs();

        File cacheDirectory = LogUtilities.ensureLogDirectoryExists();
        File file = new File(cacheDirectory, "logcat.txt");
        FileOutputStream stream = null;
        try {
            stream = new FileOutputStream(file);
            stream.write(LogUtilities.getLogCatDebug().getBytes());
        }
        catch (Exception e) {
            Log.e(LOG_TAG, "storeLogcat fails with " + e.getLocalizedMessage());
        }
        finally {
            try {
                stream.close();
            }
            catch (Exception e) {
                Log.e(LOG_TAG, "storeLogcat fails with " + e.getLocalizedMessage());
            }
        }
    }

    /**
     * @return the stored log files.
     */
    public static ArrayList<File> getLogsFileList() {
        ArrayList<File> list = new ArrayList<>();

        try {
            File logDir = LogUtilities.ensureLogDirectoryExists();

            File log1 = new File(logDir, "logcat.txt");
            if (log1.exists()) {
                list.add(log1);
            }

            File log2 = new File(logDir, "prev_logcat_1.txt");
            if (log2.exists()) {
                list.add(log2);
            }

            File log3 = new File(logDir, "prev_logcat_2.txt");
            if (log3.exists()) {
                list.add(log3);
            }

            File log4 = new File(logDir, "prev_logcat_3.txt");
            if (log4.exists()) {
                list.add(log4);
            }

        } catch (Exception e) {
            Log.e(LOG_TAG, "getLogsFileList fails with " + e.getLocalizedMessage());
        }

        return list;
    }

    /**
     * Rotate the log files ie the drop the oldest log.
     */
    private static void rotateLogs() {
        try {
            File logDir = LogUtilities.ensureLogDirectoryExists();

            File log1 = new File(logDir, "logcat.txt");
            File log2 = new File(logDir, "prev_logcat_1.txt");
            File log3 = new File(logDir, "prev_logcat_2.txt");
            File log4 = new File(logDir, "prev_logcat_3.txt");

            if (log4.exists()) {
                log4.delete();
            }

            if (log3.exists()) {
                log3.renameTo(log4);
            }

            if (log2.exists()) {
                log2.renameTo(log3);
            }

            if (log1.exists()) {
                log1.renameTo(log2);
            }
        }
        catch (Exception e) {
            Log.e(LOG_TAG, "rotateLogs fails " + e.getLocalizedMessage());
        }
    }
}
