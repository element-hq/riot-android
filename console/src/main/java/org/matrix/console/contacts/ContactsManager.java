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

package org.matrix.console.contacts;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.provider.ContactsContract;
import android.text.TextUtils;

import org.matrix.console.ga.Analytics;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;

/**
 * Manage the local contacts
 */
public class ContactsManager {

    public static interface ContactsManagerListener {
        /**
         * Called when the contacts list have been
         */
        public void onRefresh();
    }

    private static Collection<Contact> mContactsList = null;
    private static ArrayList<ContactsManagerListener> mListeners = null;

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
            mListeners = new ArrayList<ContactsManagerListener>();
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
     * @return a list of contacts.
     */
    public static void refreshLocalContactsSnapshot (Context context) {
        long startTime = System.currentTimeMillis();

        ContentResolver cr = context.getContentResolver();
        HashMap<String, Contact> dict = new HashMap<String, Contact>();

        // get the names
        Cursor namesCur = cr.query(ContactsContract.Data.CONTENT_URI,
                new String[]{ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
                        ContactsContract.CommonDataKinds.StructuredName.CONTACT_ID,
                        ContactsContract.Contacts.PHOTO_THUMBNAIL_URI
                },
                ContactsContract.Data.MIMETYPE + " = ?",
                new String[] { ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE }, null);


        if (namesCur != null) {
            while (namesCur.moveToNext()) {
                String displayName = namesCur.getString(namesCur.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY));
                String contactId = namesCur.getString(namesCur.getColumnIndex(ContactsContract.CommonDataKinds.StructuredName.CONTACT_ID));
                String thumbnailUri = namesCur.getString(namesCur.getColumnIndex(ContactsContract.CommonDataKinds.StructuredName.PHOTO_THUMBNAIL_URI));

                Contact contact = dict.get(contactId);

                if (null == contact) {
                    contact = new Contact();
                    dict.put(contactId, contact);
                }

                if (null != displayName) {
                    contact.mDisplayName = displayName;
                }

                if (null != thumbnailUri) {
                    contact.mThumbnailUri = thumbnailUri;
                }

                if (null != contactId) {
                    contact.mContactId = contactId;
                }
            }
            namesCur.close();
        }

        // get the phonenumbers
        Cursor phonesCur = cr.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                new String[]{ContactsContract.CommonDataKinds.Phone.DATA, // actual number
                        ContactsContract.CommonDataKinds.Phone.CONTACT_ID
                },
                null,null, null);

        if (null != phonesCur) {
            while (phonesCur.moveToNext()) {
                String phone = phonesCur.getString(phonesCur.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DATA));

                if (!TextUtils.isEmpty(phone)) {
                    String contactId = phonesCur.getString(phonesCur.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID));

                    Contact contact = dict.get(contactId);
                    if (null == contact) {
                        contact = new Contact();
                        dict.put(contactId, contact);
                    }

                    contact.mPhoneNumbers.add(phone);
                }
            }
            phonesCur.close();
        }

        // get the emails
        Cursor emailsCur = cr.query(ContactsContract.CommonDataKinds.Email.CONTENT_URI,
                new String[]{ContactsContract.CommonDataKinds.Email.DATA, // actual email
                        ContactsContract.CommonDataKinds.Email.CONTACT_ID},
                null, null, null);


        if (emailsCur != null) {
            while (emailsCur.moveToNext()) {
                String email = emailsCur.getString(emailsCur.getColumnIndex(ContactsContract.CommonDataKinds.Email.DATA));
                if (!TextUtils.isEmpty(email)) {
                    String contactId = emailsCur.getString(emailsCur.getColumnIndex(ContactsContract.CommonDataKinds.Email.CONTACT_ID));

                    Contact contact = dict.get(contactId);
                    if (null == contact) {
                        contact = new Contact();
                        dict.put(contactId, contact);
                    }

                    contact.mEmails.add(email);
                }
            }
            emailsCur.close();
        }

        mContactsList = dict.values();

        Analytics.sendEvent("Contacts", "Refresh", mContactsList.size() + " Contacts", System.currentTimeMillis() - startTime);

        if (null != mListeners) {
            for(ContactsManagerListener listener : mListeners) {
                try {
                    listener.onRefresh();

                } catch (Exception e) {
                }
            }
        }
    }
}

