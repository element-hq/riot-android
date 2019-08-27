/*
 * Copyright 2017 Vector Creations Ltd
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
import java.util.regex.Pattern;

import im.vector.R;
import im.vector.util.CountryPhoneData;

public class CountryAdapter extends RecyclerView.Adapter<CountryAdapter.CountryViewHolder> implements Filterable {

    private final List<CountryPhoneData> mHumanCountryData;
    private final List<CountryPhoneData> mFilteredList;

    // Set whether we display indicators (ex: +33) or not
    private final boolean mWithIndicator;

    private final OnSelectCountryListener mListener;

    /*
     * *********************************************************************************************
     * Constructor
     * *********************************************************************************************
     */

    public CountryAdapter(final List<CountryPhoneData> countries, final boolean withIndicator, final OnSelectCountryListener listener) {
        mHumanCountryData = countries;
        // Init filtered list with a copy of countries list
        mFilteredList = new ArrayList<>(countries);
        mWithIndicator = withIndicator;
        mListener = listener;
    }

    /*
     * *********************************************************************************************
     * RecyclerView.Adapter methods
     * *********************************************************************************************
     */

    @Override
    public CountryViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
        final LayoutInflater layoutInflater = LayoutInflater.from(viewGroup.getContext());
        final View itemView = layoutInflater.inflate(R.layout.item_country, viewGroup, false);
        return new CountryAdapter.CountryViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(CountryAdapter.CountryViewHolder viewHolder, int position) {
        if (position < mFilteredList.size()) {
            viewHolder.populateViews(mFilteredList.get(position));
        }
    }

    @Override
    public int getItemCount() {
        return mFilteredList.size();
    }

    @Override
    public Filter getFilter() {
        return new Filter() {
            @Override
            protected FilterResults performFiltering(CharSequence constraint) {
                mFilteredList.clear();
                final FilterResults results = new FilterResults();

                if (TextUtils.isEmpty(constraint)) {
                    mFilteredList.addAll(mHumanCountryData);
                } else {
                    final String filterPattern = constraint.toString().trim();

                    for (final CountryPhoneData country : mHumanCountryData) {
                        if (Pattern.compile(Pattern.quote(filterPattern), Pattern.CASE_INSENSITIVE)
                                .matcher(country.getCountryName() + country.getCallingCode())
                                .find()) {
                            mFilteredList.add(country);
                        }
                    }
                }
                results.values = mFilteredList;
                results.count = mFilteredList.size();

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

    class CountryViewHolder extends RecyclerView.ViewHolder {
        final TextView vCountryName;
        final TextView vCallingCode;

        private CountryViewHolder(final View itemView) {
            super(itemView);
            vCountryName = itemView.findViewById(R.id.country_name);
            vCallingCode = itemView.findViewById(R.id.country_calling_code);
        }

        private void populateViews(final CountryPhoneData country) {
            vCountryName.setText(country.getCountryName());
            if (mWithIndicator) {
                vCallingCode.setText(country.getFormattedCallingCode());
            }
            itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mListener.onSelectCountry(country);
                }
            });
        }
    }

    /*
     * *********************************************************************************************
     * Inner classes
     * *********************************************************************************************
     */

    public interface OnSelectCountryListener {
        void onSelectCountry(CountryPhoneData country);
    }
}