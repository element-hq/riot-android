/*
 * Copyright 2017 Vector Creations Ltd
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

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import im.vector.R;
import im.vector.settings.VectorLocale;

public class LanguagesAdapter extends RecyclerView.Adapter<LanguagesAdapter.LanguageViewHolder> implements Filterable {

    private final List<Locale> mLocalesList;
    private final List<Locale> mFilteredLocalesList;

    private final OnSelectLocaleListener mListener;

    /*
     * *********************************************************************************************
     * Constructor
     * *********************************************************************************************
     */

    public LanguagesAdapter(final List<Locale> locales, final OnSelectLocaleListener listener) {
        mLocalesList = locales;
        mFilteredLocalesList = new ArrayList<>(locales);
        mListener = listener;
    }

    /*
     * *********************************************************************************************
     * RecyclerView.Adapter methods
     * *********************************************************************************************
     */

    @Override
    public LanguageViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
        final LayoutInflater layoutInflater = LayoutInflater.from(viewGroup.getContext());
        final View itemView = layoutInflater.inflate(R.layout.item_locale, viewGroup, false);
        return new LanguagesAdapter.LanguageViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(LanguagesAdapter.LanguageViewHolder viewHolder, int position) {
        if (position < mFilteredLocalesList.size()) {
            viewHolder.populateViews(mFilteredLocalesList.get(position));
        }
    }

    @Override
    public int getItemCount() {
        return mFilteredLocalesList.size();
    }

    @Override
    public Filter getFilter() {
        return new Filter() {
            @Override
            protected FilterResults performFiltering(CharSequence constraint) {
                mFilteredLocalesList.clear();
                final FilterResults results = new FilterResults();

                if (TextUtils.isEmpty(constraint)) {
                    mFilteredLocalesList.addAll(mLocalesList);
                } else {
                    final String filterPattern = constraint.toString().trim();
                    Pattern pattern = Pattern.compile(Pattern.quote(filterPattern), Pattern.CASE_INSENSITIVE);

                    for (Locale locale : mLocalesList) {
                        if (pattern.matcher(VectorLocale.INSTANCE.localeToLocalisedString(locale)).find()) {
                            mFilteredLocalesList.add(locale);
                        }
                    }
                }

                results.values = mFilteredLocalesList;
                results.count = mFilteredLocalesList.size();

                return results;
            }

            @Override
            protected void publishResults(CharSequence constraint, FilterResults results) {
                notifyDataSetChanged();
            }
        };
    }

    /*
     * *********************************************************************************************
     * View holders
     * *********************************************************************************************
     */

    class LanguageViewHolder extends RecyclerView.ViewHolder {
        private final TextView vLocaleNameTextView;

        private LanguageViewHolder(final View itemView) {
            super(itemView);
            vLocaleNameTextView = itemView.findViewById(R.id.locale_text_view);
        }

        private void populateViews(final Locale locale) {
            vLocaleNameTextView.setText(VectorLocale.INSTANCE.localeToLocalisedString(locale));
            itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mListener.onSelectLocale(locale);
                }
            });
        }
    }

    /*
     * *********************************************************************************************
     * Inner classes
     * *********************************************************************************************
     */

    public interface OnSelectLocaleListener {
        void onSelectLocale(Locale locale);
    }
}