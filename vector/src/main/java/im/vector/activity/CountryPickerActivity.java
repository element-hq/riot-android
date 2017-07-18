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

package im.vector.activity;

import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SearchView;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Filter;

import im.vector.R;
import im.vector.adapters.CountryAdapter;
import im.vector.util.CountryPhoneData;
import im.vector.util.PhoneNumberUtils;
import im.vector.util.ThemeUtils;

public class CountryPickerActivity extends VectorAppCompatActivity implements CountryAdapter.OnSelectCountryListener, SearchView.OnQueryTextListener {

    public static final String EXTRA_IN_WITH_INDICATOR = "EXTRA_IN_WITH_INDICATOR";

    public static final String EXTRA_OUT_COUNTRY_NAME = "EXTRA_OUT_COUNTRY_NAME";
    public static final String EXTRA_OUT_COUNTRY_CODE = "EXTRA_OUT_COUNTRY_CODE";
    public static final String EXTRA_OUT_CALLING_CODE = "EXTRA_OUT_CALLING_CODE";

    private RecyclerView mCountryRecyclerView;
    private View mCountryEmptyView;
    private CountryAdapter mCountryAdapter;
    private SearchView mSearchView;

    private boolean mWithIndicator;

     /*
     * *********************************************************************************************
     * Static methods
     * *********************************************************************************************
     */

    public static Intent getIntent(final Context context, final boolean withIndicator) {
        final Intent intent = new Intent(context, CountryPickerActivity.class);
        intent.putExtra(EXTRA_IN_WITH_INDICATOR, withIndicator);
        return intent;
    }

    /*
    * *********************************************************************************************
    * Activity lifecycle
    * *********************************************************************************************
    */

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_country_picker);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
            if (getSupportActionBar() != null) {
                getSupportActionBar().setDisplayShowHomeEnabled(true);
                getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            }
        }

        final Intent intent = getIntent();
        mWithIndicator = intent.getBooleanExtra(EXTRA_IN_WITH_INDICATOR, false);

        initViews();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_country_picker, menu);

        final MenuItem searchItem = menu.findItem(R.id.action_search);
        if (searchItem != null) {
            SearchManager searchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
            mSearchView = (SearchView) MenuItemCompat.getActionView(searchItem);
            mSearchView.setMaxWidth(Integer.MAX_VALUE);
            mSearchView.setSubmitButtonEnabled(false);
            mSearchView.setQueryHint(getString(R.string.search_hint));
            mSearchView.setSearchableInfo(searchManager.getSearchableInfo(getComponentName()));
            mSearchView.setOnQueryTextListener(this);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            setResult(RESULT_CANCELED);
            finish();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mSearchView != null) {
            mSearchView.setOnQueryTextListener(null);
        }
    }

    /*
    * *********************************************************************************************
    * UI
    * *********************************************************************************************
    */

    private void initViews() {
        mCountryEmptyView = findViewById(R.id.country_empty_view);

        mCountryRecyclerView = (RecyclerView) findViewById(R.id.country_recycler_view);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setOrientation(LinearLayoutManager.VERTICAL);
        mCountryRecyclerView.setLayoutManager(layoutManager);
        mCountryAdapter = new CountryAdapter(PhoneNumberUtils.getCountriesWithIndicator(), mWithIndicator, this);
        mCountryRecyclerView.setAdapter(mCountryAdapter);
    }

    private void filterCountries(final String pattern) {
        mCountryAdapter.getFilter().filter(pattern, new Filter.FilterListener() {
            @Override
            public void onFilterComplete(int count) {
                mCountryEmptyView.setVisibility(count > 0 ? View.GONE : View.VISIBLE);
            }
        });
    }

    /*
    * *********************************************************************************************
    * Listener
    * *********************************************************************************************
    */

    @Override
    public void onSelectCountry(CountryPhoneData country) {
        Intent intent = new Intent();
        intent.putExtra(EXTRA_OUT_COUNTRY_NAME, country.getCountryName());
        intent.putExtra(EXTRA_OUT_COUNTRY_CODE, country.getCountryCode());
        intent.putExtra(EXTRA_OUT_CALLING_CODE, country.getCallingCode());
        setResult(RESULT_OK, intent);
        finish();
    }

    @Override
    public boolean onQueryTextChange(String newText) {
        filterCountries(newText);
        return true;
    }

    @Override
    public boolean onQueryTextSubmit(String query) {
        return true;
    }

}
