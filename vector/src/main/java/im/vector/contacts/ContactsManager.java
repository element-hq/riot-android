/*
 * Copyright 2015 OpenMarket Ltd
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

package im.vector.contacts;

import android.Manifest;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Build;
import android.preference.PreferenceManager;
import android.provider.ContactsContract;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.text.TextUtils;
import android.util.Log;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.User;

import im.vector.Matrix;
import im.vector.VectorApp;
import im.vector.activity.VectorSettingsActivity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Set;

/**
 * Manage the local contacts
 */
public class ContactsManager {
    private static final String LOG_TAG = "ContactsManager";

    public interface ContactsManagerListener {
        /**
         * Called when the contacts list have been
         */
        void onRefresh();

        /**
         * Called when an user presence has been updated
         */
        void onContactPresenceUpdate(Contact contact, String matrixId);
    }

    private static Collection<Contact> mContactsList = null;
    private static ArrayList<ContactsManagerListener> mListeners = null;

    // retriever listener
    private static final PIDsRetriever.PIDsRetrieverListener mPIDsRetrieverListener = new PIDsRetriever.PIDsRetrieverListener() {
        /**
         * Warn the listeners about a contact information update.
         * @param contact the contact
         * @param mxid the mxid
         */
        private void onContactPresenceUpdate(Contact contact, Contact.MXID mxid ) {
            if(null != mListeners) {
                for (ContactsManagerListener listener : mListeners) {
                    try {
                        listener.onContactPresenceUpdate(contact, mxid.mMatrixId);
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "onContactPresenceUpdate failed " + e.getLocalizedMessage());
                    }
                }
            }
        }

        @Override
        public void onPIDsRetrieved(final String accountId, final Contact contact, final boolean has3PIDs) {
            // privacy
            // Log.d(LOG_TAG, "onPIDsRetrieved : the contact " + contact + " retrieves its 3PIds.");

            if (has3PIDs) {
                MXSession session = Matrix.getInstance(VectorApp.getInstance().getApplicationContext()).getSession(accountId);

                if (null != session) {
                    Set<String> medias = contact.getMatrixIdMedias();

                    // privacy
                    //Log.d(LOG_TAG, "medias " + medias);

                    for(String media : medias) {
                        final Contact.MXID mxid = contact.getMXID(media);

                        onContactPresenceUpdate(contact, mxid);

                        mxid.mUser = session.getDataHandler().getUser(mxid.mMatrixId);

                        // if the user is not known, get its presence
                        if (null == mxid.mUser) {
                            session.getPresenceApiClient().getPresence(mxid.mMatrixId, new ApiCallback<User>() {
                                @Override
                                public void onSuccess(User user) {
                                    Log.d(LOG_TAG, "retrieve the presence of " + mxid.mMatrixId + " :"  + user);
                                    mxid.mUser = user;
                                    onContactPresenceUpdate(contact, mxid);
                                }

                                /**
                                 * Error method
                                 * @param errorMessage the error description
                                 */
                                private void onError(String errorMessage) {
                                    Log.e(LOG_TAG, "cannot retrieve the presence of " + mxid.mMatrixId + " :"  + errorMessage);
                                    onContactPresenceUpdate(contact, mxid);
                                }

                                @Override
                                public void onNetworkError(Exception e) {
                                    onError(e.getLocalizedMessage());
                                }

                                @Override
                                public void onMatrixError(MatrixError e) {
                                    onError(e.getLocalizedMessage());
                                }

                                @Override
                                public void onUnexpectedError(Exception e) {
                                    onError(e.getLocalizedMessage());
                                }
                            });
                        }
                    }
                }
            }
        }
    };

    /**
     * Refresh the local contacts list snapshot.
     * @param context the context.
     * @return a local contacts list
     */
    public static Collection<Contact> getLocalContactsSnapshot(Context context) {
        if (null == mContactsList) {
            refreshLocalContactsSnapshot(context);
        }
        return mContactsList;
    }

    /**
     * reset
     */
    public static void reset() {
        mListeners = null;
        mContactsList = null;
    }

    /**
     * Add a listener.
     * @param listener the listener to add.
     */
    public static void addListener(ContactsManagerListener listener) {
        if (null == mListeners) {
            mListeners = new ArrayList<>();
        }

        mListeners.add(listener);
    }

    /**
     * Remove a listener.
     * @param listener the listener to remove.
     */
    public static void removeListener(ContactsManagerListener listener) {
        if (null != mListeners) {
            mListeners.remove(listener);
        }
    }

    /**
     * List the local contacts.
     * @param context the context.
     */
    public static void refreshLocalContactsSnapshot (Context context) {
        ContentResolver cr = context.getContentResolver();
        HashMap<String, Contact> dict = new HashMap<>();

        // test if the user allows to access to the contact
        if (isContactBookAccessAllowed(context)) {
            // get the names
            Cursor namesCur = null;

            try {
                namesCur = cr.query(ContactsContract.Data.CONTENT_URI,
                        new String[]{ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
                                ContactsContract.CommonDataKinds.StructuredName.CONTACT_ID,
                                ContactsContract.Contacts.PHOTO_THUMBNAIL_URI
                        },
                        ContactsContract.Data.MIMETYPE + " = ?",
                        new String[]{ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE}, null);
            } catch (Exception e) {
                Log.e(LOG_TAG, "## refreshLocalContactsSnapshot(): Exception - Contact names query Msg=" + e.getMessage());
            }

            if (namesCur != null) {
                try {
                    while (namesCur.moveToNext()) {
                        String displayName = namesCur.getString(namesCur.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY));
                        String contactId = namesCur.getString(namesCur.getColumnIndex(ContactsContract.CommonDataKinds.StructuredName.CONTACT_ID));
                        String thumbnailUri = namesCur.getString(namesCur.getColumnIndex(ContactsContract.CommonDataKinds.StructuredName.PHOTO_THUMBNAIL_URI));

                        if (null != contactId) {
                            Contact contact = dict.get(contactId);

                            if (null == contact) {
                                contact = new Contact(contactId);
                                dict.put(contactId, contact);
                            }

                            if (null != displayName) {
                                contact.setDisplayName(displayName);
                            }

                            if (null != thumbnailUri) {
                                contact.setThumbnailUri(thumbnailUri);
                            }
                        }
                    }
                } catch (Exception e) {
                    Log.e(LOG_TAG, "## refreshLocalContactsSnapshot(): Exception - Contact names query2 Msg=" + e.getMessage());
                }

                namesCur.close();
            }

            // get the phonenumbers
            Cursor phonesCur = null;

            try {
                phonesCur = cr.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                        new String[]{ContactsContract.CommonDataKinds.Phone.DATA, // actual number
                                ContactsContract.CommonDataKinds.Phone.CONTACT_ID
                        },
                        null, null, null);
            } catch (Exception e) {
                Log.e(LOG_TAG, "## refreshLocalContactsSnapshot(): Exception - Phone numbers query Msg=" + e.getMessage());
            }

            if (null != phonesCur) {
                try {
                    while (phonesCur.moveToNext()) {
                        String phone = phonesCur.getString(phonesCur.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DATA));

                        if (!TextUtils.isEmpty(phone)) {
                            String contactId = phonesCur.getString(phonesCur.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID));

                            if (null != contactId) {
                                Contact contact = dict.get(contactId);
                                if (null == contact) {
                                    contact = new Contact(contactId);
                                    dict.put(contactId, contact);
                                }

                                contact.addPhonenumber(phone);
                            }
                        }
                    }
                } catch (Exception e) {
                    Log.e(LOG_TAG, "## refreshLocalContactsSnapshot(): Exception - Phone numbers query2 Msg=" + e.getMessage());
                }

                phonesCur.close();
            }

            // get the emails
            Cursor emailsCur = null;

            try {
                emailsCur = cr.query(ContactsContract.CommonDataKinds.Email.CONTENT_URI,
                        new String[]{ContactsContract.CommonDataKinds.Email.DATA, // actual email
                                ContactsContract.CommonDataKinds.Email.CONTACT_ID},
                        null, null, null);
            } catch (Exception e) {
                Log.e(LOG_TAG, "## refreshLocalContactsSnapshot(): Exception - Emails query Msg=" + e.getMessage());
            }

            if (emailsCur != null) {
                try {
                    while (emailsCur.moveToNext()) {
                        String email = emailsCur.getString(emailsCur.getColumnIndex(ContactsContract.CommonDataKinds.Email.DATA));
                        if (!TextUtils.isEmpty(email)) {
                            String contactId = emailsCur.getString(emailsCur.getColumnIndex(ContactsContract.CommonDataKinds.Email.CONTACT_ID));

                            if (null != contactId) {
                                Contact contact = dict.get(contactId);
                                if (null == contact) {
                                    contact = new Contact(contactId);
                                    dict.put(contactId, contact);
                                }

                                contact.addEmailAdress(email);
                            }
                        }
                    }
                } catch (Exception e) {
                    Log.e(LOG_TAG, "## refreshLocalContactsSnapshot(): Exception - Emails query2 Msg=" + e.getMessage());
                }

                emailsCur.close();
            }
        }

        PIDsRetriever.getIntance().setPIDsRetrieverListener(mPIDsRetrieverListener);

        mContactsList = dict.values();

        if (null != mListeners) {
            for(ContactsManagerListener listener : mListeners) {
                try {
                    listener.onRefresh();
                } catch (Exception e) {
                    Log.e(LOG_TAG, "refreshLocalContactsSnapshot : onRefresh failed" + e.getLocalizedMessage());
                }
            }
        }
    }

    //================================================================================
    // Contacts book management (for android < M devices)
    //================================================================================
    public static final String CONTACTS_BOOK_ACCESS_KEY = "CONTACT_BOOK_ACCESS_KEY";

    /**
     * Tells if the contacts book access has been requested.
     * @param activity the calling activity
     * @return true it was requested once
     */
    public static boolean isContactBookAccessRequested(Activity activity) {
        if (Build.VERSION.SDK_INT >= 23) {
            return (PackageManager.PERMISSION_GRANTED != ContextCompat.checkSelfPermission(activity.getApplicationContext(), Manifest.permission.READ_CONTACTS)) &&
                    !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.READ_CONTACTS);
        } else {
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(activity);
            return preferences.contains(CONTACTS_BOOK_ACCESS_KEY);
        }
    }

    /**
     * Update the contacts book access.
     * @param activity the calling activity.
     * @param isAllowed true to allowed the contacts book access.
     */
    public static void setIsContactBookAccessAllowed(Activity activity, boolean isAllowed) {
        if (Build.VERSION.SDK_INT < 23) {
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(activity);
            SharedPreferences.Editor editor = preferences.edit();
            editor.clear();
            editor.putBoolean(CONTACTS_BOOK_ACCESS_KEY, isAllowed);
            editor.commit();
        }
    }

    /**
     * Tells if the contacts book access has been granted
     * @param context the context
     * @return true if it was granted.
     */
    public static boolean isContactBookAccessAllowed(Context context) {
        if (Build.VERSION.SDK_INT >= 23) {
            return (PackageManager.PERMISSION_GRANTED == ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS));
        } else {
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
            return preferences.getBoolean(CONTACTS_BOOK_ACCESS_KEY, false);
        }
    }
}

