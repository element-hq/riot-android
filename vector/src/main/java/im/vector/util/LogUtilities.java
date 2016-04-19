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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;

/** Manages all the logs which are sent to us when a user sends a bug report.
 */
public class LogUtilities {

    private static File mLogDirectory = null;

    public static final String[] LOGCAT_CMD = new String[] { 
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

    public static final String[]  LOGCAT_CMD_DEBUG = new String[] {
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
            "*:*"
    };

    private static final int BUFFER_SIZE = 1024;

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
        }
        finally {
            if (reader != null) {
                try {
                    reader.close();
                }
                catch (IOException e) {}
            }
        }
        return response;
    }

    /**
     *
     * @return the logcat error
     */
    public static String getLogCatError() {
        return getLog(LOGCAT_CMD);
    }

    public static String getLogCatDebug() {
        return getLog(LOGCAT_CMD_DEBUG);
    }

    // general method to store several logs

    public static void setLogDirectory(File logDirectory) {
        mLogDirectory = logDirectory;
    }

    private static File ensureLogDirectoryExists() throws IOException {
        if (mLogDirectory == null) {
            return null;
        }
        if (!mLogDirectory.exists()) {
            mLogDirectory.mkdirs();
        }
        return mLogDirectory;
    }

    public static void storeLogcat() {
        LogUtilities.rotateLogs();

        File cacheDirectory;
        try {
            cacheDirectory = LogUtilities.ensureLogDirectoryExists();
        }
        catch (IOException e) {
            return;
        }

        File file = new File(cacheDirectory, "logcat.0");
        FileOutputStream stream = null;
        try {
            stream = new FileOutputStream(file);
            stream.write(LogUtilities.getLogCatDebug().getBytes());
        }
        catch (FileNotFoundException e) {
        }
        catch (IOException e) {
        }
        finally {
            try {
                stream.close();
            }
            catch (Exception e) {}
        }
    }

    public static ArrayList<File> getLogsFileList() {
        ArrayList<File> list = new ArrayList<File>();

        try {
            File logDir = LogUtilities.ensureLogDirectoryExists();

            File log1 = new File(logDir, "logcat.0");
            if (log1.exists()) {
                list.add(log1);
            }

            File log2 = new File(logDir, "logcat.1");
            if (log2.exists()) {
                list.add(log2);
            }

            File log3 = new File(logDir, "logcat.2");
            if (log3.exists()) {
                list.add(log3);
            }

            File log4 = new File(logDir, "logcat.3");
            if (log4.exists()) {
                list.add(log4);
            }

        } catch (Exception e) {

        }

        return list;
    }

    private static void rotateLogs() {
        try {
            File logDir = LogUtilities.ensureLogDirectoryExists();

            File log1 = new File(logDir, "logcat.0");
            File log2 = new File(logDir, "logcat.1");
            File log3 = new File(logDir, "logcat.2");
            File log4 = new File(logDir, "logcat.3");

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
        catch (IOException e) {}
    }
}
