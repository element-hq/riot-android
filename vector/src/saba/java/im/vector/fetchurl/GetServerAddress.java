package im.vector.fetchurl;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

/**
 * Created by Bakhtiari on August 09,2020
 * This class usage is to get server addresses from MDM application
 */

public class GetServerAddress {

    // MDM-agent App content provider uri address
    private final String URI_ADDRESS = "content://ir.batna.mdm.utils.provider.MyProvider/apps";
    private String url = null;
    Context context;

    public GetServerAddress(Context context) {
        this.context = context;
        getUrlFromProvider(context);
    }

    public String getUrl() {
        return this.url;
    }

    private void getUrlFromProvider(Context context) {

        Uri mUri = Uri.parse(URI_ADDRESS);
        Cursor mCursor = context.getContentResolver().query(mUri,
                null,
                null,
                null,
                null);

        if ((context.getContentResolver() != null) && (mCursor != null) && mCursor.moveToFirst()) {
            while (!mCursor.isAfterLast()) {
                this.url = mCursor.getString(mCursor.getColumnIndex("url1"));
                mCursor.moveToNext();
            }
            mCursor.close();
        }
    }
}
