/*
 * Copyright 2018 New Vector Ltd
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

package im.vector.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import org.matrix.androidsdk.MXSession;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import im.vector.R;
import im.vector.VectorApp;
import im.vector.util.SlashCommandsParser;

/**
 * This class describes a list of auto-completed slash commands
 */
public class AutoCompletedCommandLineAdapter extends ArrayAdapter<String> {

    // the context
    private final Context mContext;

    // the layout inflater
    private final LayoutInflater mLayoutInflater;

    // the layout to draw items
    private final int mLayoutResourceId;

    // the session
    private final MXSession mSession;

    // the filter
    private android.widget.Filter mFilter;

    // cannot use the parent list
    private List<String> mCommandLines = new ArrayList<>();

    /**
     * Comparators
     */
    private static final Comparator<String> mCommandLinesComparator = new Comparator<String>() {
        @Override
        public int compare(String command1, String command2) {
            return command1.compareToIgnoreCase(command2);
        }
    };

    /**
     * Construct an adapter which will display a list of slash commands
     *
     * @param context           Activity context
     * @param layoutResourceId  The resource ID of the layout for each item.
     * @param session           The session
     * @param commandLines      The command lines list
     */
    public AutoCompletedCommandLineAdapter(Context context, int layoutResourceId, MXSession session, Collection<String> commandLines) {
        super(context, layoutResourceId);
        mContext = context;
        mLayoutResourceId = layoutResourceId;
        mLayoutInflater = LayoutInflater.from(mContext);
        mSession = session;
        addAll(commandLines);
        mCommandLines = new ArrayList<>(commandLines);
    }

    /**
     * Get the updated view for a specified position.
     *
     * @param position      the position
     * @param convertView   the convert view
     * @param parent        the parent view
     *
     * @return the view
     */
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = mLayoutInflater.inflate(mLayoutResourceId, parent, false);
        }

        String command = getItem(position);
        String parameter = SlashCommandsParser.getSlashCommandParam(command);
        String description = SlashCommandsParser.getSlashCommandDescription(command);

        TextView tvCommandName = convertView.findViewById(R.id.item_command_auto_complete_name);
        TextView tvCommandParameter = convertView.findViewById(R.id.item_command_auto_complete_parameter);
        TextView tvCommandDescription = convertView.findViewById(R.id.item_command_auto_complete_description);

        tvCommandName.setText(command);
        tvCommandParameter.setText(parameter);
        tvCommandDescription.setText(description);

        return convertView;
    }

    @Override
    public android.widget.Filter getFilter() {
        if (mFilter == null) {
            mFilter = new AutoCompletedCommandFilter();
        }
        return mFilter;
    }

    private class AutoCompletedCommandFilter extends android.widget.Filter {
        @Override
        protected FilterResults performFiltering(CharSequence prefix) {
            FilterResults results = new FilterResults();
            List<String> newValues;

            if (prefix == null || prefix.length() == 0) {
                newValues = new ArrayList<>();
            } else {
                newValues = new ArrayList<>();
                String prefixString = prefix.toString().toLowerCase(VectorApp.getApplicationLocale());

                if (prefixString.startsWith("/")) {
                    for (String command : mCommandLines) {
                        if ((null != command) && command.toLowerCase(VectorApp.getApplicationLocale()).startsWith(prefixString)) {
                            newValues.add(command);
                        }
                    }
                }
            }

            //Collections.sort(newValues, mCommandLinesComparator);

            results.values = newValues;
            results.count = newValues.size();

            return results;
        }

        @Override
        protected void publishResults(CharSequence constraint, FilterResults results) {
            clear();
            addAll((List<String>) results.values);
            if (results.count > 0) {
                notifyDataSetChanged();
            } else {
                notifyDataSetInvalidated();
            }
        }

        @Override
        public CharSequence convertResultToString(Object resultValue) {
            String commandLine = (String) resultValue;
            return commandLine;
        }
    }
}
