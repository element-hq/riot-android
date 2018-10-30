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
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import org.matrix.androidsdk.MXSession;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import im.vector.R;
import im.vector.settings.VectorLocale;
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
    private List<SlashCommandsParser.SlashCommand> mCommandLines = new ArrayList<>();

    /**
     * Construct an adapter which will display a list of slash commands
     *
     * @param context          Activity context
     * @param layoutResourceId The resource ID of the layout for each item.
     * @param session          The session
     * @param slashCommands    The command lines list
     */
    public AutoCompletedCommandLineAdapter(Context context,
                                           int layoutResourceId,
                                           MXSession session,
                                           Collection<SlashCommandsParser.SlashCommand> slashCommands) {
        super(context, layoutResourceId);
        mContext = context;
        mLayoutResourceId = layoutResourceId;
        mLayoutInflater = LayoutInflater.from(mContext);
        mSession = session;
        mCommandLines = new ArrayList<>(slashCommands);
    }

    /**
     * Get the updated view for a specified position.
     *
     * @param position    the position
     * @param convertView the convert view
     * @param parent      the parent view
     * @return the view
     */
    public View getView(int position, View convertView, ViewGroup parent) {
        CommandViewHolder viewHolder;

        if (convertView == null) {
            // inflate the layout
            convertView = mLayoutInflater.inflate(mLayoutResourceId, parent, false);
            viewHolder = new CommandViewHolder(convertView);
            convertView.setTag(viewHolder);
        } else {
            viewHolder = (CommandViewHolder) convertView.getTag();
        }

        SlashCommandsParser.SlashCommand slashCommand = SlashCommandsParser.SlashCommand.get(getItem(position));

        if (null != slashCommand) {
            viewHolder.tvCommandName.setText(slashCommand.getCommand());
            viewHolder.tvCommandParameter.setText(slashCommand.getParam());
            viewHolder.tvCommandDescription.setText(slashCommand.getDescription());
        }
        return convertView;
    }

    static class CommandViewHolder {

        @BindView(R.id.item_command_auto_complete_name)
        TextView tvCommandName;

        @BindView(R.id.item_command_auto_complete_parameter)
        TextView tvCommandParameter;

        @BindView(R.id.item_command_auto_complete_description)
        TextView tvCommandDescription;

        public CommandViewHolder(View itemView) {
            ButterKnife.bind(this, itemView);
        }
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
            List<String> newValues = new ArrayList<>();

            if (!TextUtils.isEmpty(prefix)) {
                String prefixString = prefix.toString().toLowerCase(VectorLocale.INSTANCE.getApplicationLocale());

                if (prefixString.startsWith("/")) {
                    for (SlashCommandsParser.SlashCommand slashCommand : mCommandLines) {
                        if ((null != slashCommand.getCommand())
                                && slashCommand.getCommand()
                                .toLowerCase(VectorLocale.INSTANCE.getApplicationLocale())
                                .startsWith(prefixString)) {
                            newValues.add(slashCommand.getCommand());
                        }
                    }
                }
            }
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
            return (String) resultValue;
        }
    }
}
