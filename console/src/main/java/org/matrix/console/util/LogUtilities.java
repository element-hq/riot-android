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

package org.matrix.console.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;

/** Manages all the logs which are sent to us when a user sends a bug report.
 */
public class LogUtilities {

    private static LogUtilities instance = null;

    private File mLogDirectory;

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
                "*:*"
    };

    private static final int BUFFER_SIZE = 1024;

    private static String getLog(String[] cmd) {
        Process logcatProc = null;
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
}
