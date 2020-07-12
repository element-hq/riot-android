package im.vector.directory.role;

import android.app.Activity;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Filter;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import im.vector.R;
import im.vector.directory.role.model.DropDownItem;


public class DropDownAdapter extends ArrayAdapter<DropDownItem> {
    private final Context mContext;
    private final List<DropDownItem> dropDownItems;
    private final List<DropDownItem> dropDownItemsAll;
    private final int mLayoutResourceId;

    public DropDownAdapter(Context context, int resource) {
        super(context, resource);
        this.mContext = context;
        this.mLayoutResourceId = resource;
        this.dropDownItems = new ArrayList<>();
        this.dropDownItemsAll = new ArrayList<>();
    }

    public void addData(List<DropDownItem> data) {
        dropDownItemsAll.clear();
        dropDownItems.clear();
        dropDownItemsAll.addAll(data);
        dropDownItems.addAll(data);
    }

    public int getCount() {
        return dropDownItems.size();
    }

    public DropDownItem getItem(int position) {
        return dropDownItems.get(position);
    }

    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        try {
            if (convertView == null) {
                LayoutInflater inflater = ((Activity) mContext).getLayoutInflater();
                convertView = inflater.inflate(mLayoutResourceId, parent, false);
            }
            DropDownItem dropDownItem = getItem(position);
            TextView name = (TextView) convertView.findViewById(R.id.text);
            name.setText(dropDownItem.name);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return convertView;
    }

    @Override
    public Filter getFilter() {
        return new Filter() {
            @Override
            public String convertResultToString(Object resultValue) {
                return ((DropDownItem) resultValue).name;
            }

            @Override
            protected FilterResults performFiltering(CharSequence constraint) {
                FilterResults filterResults = new FilterResults();
                List<DropDownItem> dropDownSuggestion = new ArrayList<>();
                if (constraint != null) {
                    for (DropDownItem dropDownItem : dropDownItemsAll) {
                        if (dropDownItem.name.toLowerCase().startsWith(constraint.toString().toLowerCase())) {
                            dropDownSuggestion.add(dropDownItem);
                        }
                    }
                    filterResults.values = dropDownSuggestion;
                    filterResults.count = dropDownSuggestion.size();
                }
                return filterResults;
            }

            @Override
            protected void publishResults(CharSequence constraint, FilterResults results) {
                dropDownItems.clear();
                if (results != null && results.count > 0) {
                    // avoids unchecked cast warning when using mDepartments.addAll((ArrayList<Department>) results.values);
                    for (Object object : (List<?>) results.values) {
                        if (object instanceof DropDownItem) {
                            dropDownItems.add((DropDownItem) object);
                        }
                    }
                    notifyDataSetChanged();
                } else if (constraint == null) {
                    // no filter, add entire original list back in
                    dropDownItems.addAll(dropDownItemsAll);
                    notifyDataSetInvalidated();
                }
            }
        };
    }
}


