/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.cellbroadcastservice;

import android.app.AppOpsManager;
import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.os.Binder;
import android.os.Process;
import android.provider.Telephony.CellBroadcasts;
import android.telephony.Rlog;
import android.text.TextUtils;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;

import java.util.Arrays;

/**
 * The content provider that provides access of cell broadcast message to application.
 * Permission {@link android.permission.READ_CELL_BROADCASTS} is required for querying the cell
 * broadcast message. Only phone process has the permission to write/update the database via this
 * provider.
 */
public class CellBroadcastProvider extends ContentProvider {
    /** Interface for read/write permission check. */
    public interface PermissionChecker {
        /** Return {@code True} if the caller has the permission to write/update the database. */
        boolean hasWritePermission();

        /** Return {@code True} if the caller has the permission to query the complete database. */
        boolean hasReadPermission();

        /**
         * Return {@code True} if the caller has the permission to query the database for
         * cell broadcast message history.
         */
        boolean hasReadPermissionForHistory();
    }

    private static final String TAG = CellBroadcastProvider.class.getSimpleName();

    private static final boolean DBG = Log.isLoggable(TAG, Log.DEBUG);

    /** Database name. */
    private static final String DATABASE_NAME = "cellbroadcasts.db";

    /** Database version. */
    private static final int DATABASE_VERSION = 2;

    /** URI matcher for ContentProvider queries. */
    private static final UriMatcher sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);

    /** URI matcher type to get all cell broadcasts. */
    private static final int ALL = 0;

    /**
     * URI matcher type for get all message history, this is used primarily for default
     * cellbroadcast app or messaging app to display message history. some information is not
     * exposed for messaging history, e.g, messages which are out of broadcast geometrics will not
     * be delivered to end users thus will not be returned as message history query result.
     */
    private static final int MESSAGE_HISTORY = 1;

    /** MIME type for the list of all cell broadcasts. */
    private static final String LIST_TYPE = "vnd.android.cursor.dir/cellbroadcast";

    /** Table name of cell broadcast message. */
    @VisibleForTesting
    public static final String CELL_BROADCASTS_TABLE_NAME = "cell_broadcasts";

    /** Authority string for content URIs. */
    @VisibleForTesting
    public static final String AUTHORITY = "cellbroadcasts";

    /** Content uri of this provider. */
    public static final Uri CONTENT_URI = Uri.parse("content://cellbroadcasts");

    /**
     * Local definition of the subId column name.
     * The value should match CellBroadcasts.SUB_ID, but we don't use it here because it's hidden
     * and deprecated, and slot_index should be enough in the future.
     */
    private static final String SUB_ID = "sub_id";

    /**
     * Local definition of the query columns for instantiating
     * {@link android.telephony.SmsCbMessage} objects.
     */
    public static final String[] QUERY_COLUMNS = {
        CellBroadcasts._ID,
        CellBroadcasts.SLOT_INDEX,
        CellBroadcasts.GEOGRAPHICAL_SCOPE,
        CellBroadcasts.PLMN,
        CellBroadcasts.LAC,
        CellBroadcasts.CID,
        CellBroadcasts.SERIAL_NUMBER,
        CellBroadcasts.SERVICE_CATEGORY,
        CellBroadcasts.LANGUAGE_CODE,
        CellBroadcasts.MESSAGE_BODY,
        CellBroadcasts.MESSAGE_FORMAT,
        CellBroadcasts.MESSAGE_PRIORITY,
        CellBroadcasts.ETWS_WARNING_TYPE,
        CellBroadcasts.CMAS_MESSAGE_CLASS,
        CellBroadcasts.CMAS_CATEGORY,
        CellBroadcasts.CMAS_RESPONSE_TYPE,
        CellBroadcasts.CMAS_SEVERITY,
        CellBroadcasts.CMAS_URGENCY,
        CellBroadcasts.CMAS_CERTAINTY,
        CellBroadcasts.RECEIVED_TIME,
        CellBroadcasts.MESSAGE_BROADCASTED,
        CellBroadcasts.GEOMETRIES,
        CellBroadcasts.MAXIMUM_WAIT_TIME
    };

    @VisibleForTesting
    public PermissionChecker mPermissionChecker;

    /** The database helper for this content provider. */
    @VisibleForTesting
    public SQLiteOpenHelper mDbHelper;

    static {
        sUriMatcher.addURI(AUTHORITY, null, ALL);
        sUriMatcher.addURI(AUTHORITY, "history", MESSAGE_HISTORY);
    }

    public CellBroadcastProvider() {}

    @VisibleForTesting
    public CellBroadcastProvider(PermissionChecker permissionChecker) {
        mPermissionChecker = permissionChecker;
    }

    @Override
    public boolean onCreate() {
        mDbHelper = new CellBroadcastDatabaseHelper(getContext());
        mPermissionChecker = new CellBroadcastPermissionChecker();
        setAppOps(AppOpsManager.OP_READ_CELL_BROADCASTS, AppOpsManager.OP_NONE);
        return true;
    }

    /**
     * Return the MIME type of the data at the specified URI.
     *
     * @param uri the URI to query.
     * @return a MIME type string, or null if there is no type.
     */
    @Override
    public String getType(Uri uri) {
        int match = sUriMatcher.match(uri);
        switch (match) {
            case ALL:
                return LIST_TYPE;
            default:
                return null;
        }
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {
        checkReadPermission(uri);

        if (DBG) {
            Rlog.d(TAG, "query:"
                    + " uri = " + uri
                    + " projection = " + Arrays.toString(projection)
                    + " selection = " + selection
                    + " selectionArgs = " + Arrays.toString(selectionArgs)
                    + " sortOrder = " + sortOrder);
        }
        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        qb.setStrict(true); // a little protection from injection attacks
        qb.setTables(CELL_BROADCASTS_TABLE_NAME);

        String orderBy;
        if (!TextUtils.isEmpty(sortOrder)) {
            orderBy = sortOrder;
        } else {
            orderBy = CellBroadcasts.RECEIVED_TIME + " DESC";
        }

        int match = sUriMatcher.match(uri);
        switch (match) {
            case ALL:
                return getReadableDatabase().query(
                        CELL_BROADCASTS_TABLE_NAME, projection, selection, selectionArgs,
                        null /* groupBy */, null /* having */, orderBy);
            case MESSAGE_HISTORY:
                // limit projections to certain columns. limit result to broadcasted messages only.
                qb.appendWhere(CellBroadcasts.MESSAGE_BROADCASTED  + "=1");
                return qb.query(getReadableDatabase(), projection, selection, selectionArgs, null,
                        null, orderBy);
            default:
                throw new IllegalArgumentException(
                        "Query method doesn't support this uri = " + uri);
        }
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        checkWritePermission();

        if (DBG) {
            Rlog.d(TAG, "insert:"
                    + " uri = " + uri
                    + " contentValue = " + values);
        }

        switch (sUriMatcher.match(uri)) {
            case ALL:
                long row = getWritableDatabase().insertOrThrow(CELL_BROADCASTS_TABLE_NAME, null,
                        values);
                if (row > 0) {
                    Uri newUri = ContentUris.withAppendedId(CONTENT_URI, row);
                    getContext().getContentResolver()
                            .notifyChange(CONTENT_URI, null /* observer */);
                    return newUri;
                } else {
                    Rlog.e(TAG, "Insert record failed because of unknown reason, uri = " + uri);
                    return null;
                }
            default:
                throw new IllegalArgumentException(
                        "Insert method doesn't support this uri = " + uri);
        }
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        checkWritePermission();

        if (DBG) {
            Rlog.d(TAG, "delete:"
                    + " uri = " + uri
                    + " selection = " + selection
                    + " selectionArgs = " + Arrays.toString(selectionArgs));
        }

        switch (sUriMatcher.match(uri)) {
            case ALL:
                return getWritableDatabase().delete(CELL_BROADCASTS_TABLE_NAME,
                        selection, selectionArgs);
            default:
                throw new IllegalArgumentException(
                        "Delete method doesn't support this uri = " + uri);
        }
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        checkWritePermission();

        if (DBG) {
            Rlog.d(TAG, "update:"
                    + " uri = " + uri
                    + " values = {" + values + "}"
                    + " selection = " + selection
                    + " selectionArgs = " + Arrays.toString(selectionArgs));
        }

        switch (sUriMatcher.match(uri)) {
            case ALL:
                int rowCount = getWritableDatabase().update(
                        CELL_BROADCASTS_TABLE_NAME,
                        values,
                        selection,
                        selectionArgs);
                if (rowCount > 0) {
                    getContext().getContentResolver().notifyChange(uri, null /* observer */);
                }
                return rowCount;
            default:
                throw new IllegalArgumentException(
                        "Update method doesn't support this uri = " + uri);
        }
    }

    /**
     * Returns a string used to create the cell broadcast table. This is exposed so the unit test
     * can construct its own in-memory database to match the cell broadcast db.
     */
    @VisibleForTesting
    public static String getStringForCellBroadcastTableCreation(String tableName) {
        return "CREATE TABLE " + tableName + " ("
                + CellBroadcasts._ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + SUB_ID + " INTEGER,"
                + CellBroadcasts.SLOT_INDEX + " INTEGER DEFAULT 0,"
                + CellBroadcasts.GEOGRAPHICAL_SCOPE + " INTEGER,"
                + CellBroadcasts.PLMN + " TEXT,"
                + CellBroadcasts.LAC + " INTEGER,"
                + CellBroadcasts.CID + " INTEGER,"
                + CellBroadcasts.SERIAL_NUMBER + " INTEGER,"
                + CellBroadcasts.SERVICE_CATEGORY + " INTEGER,"
                + CellBroadcasts.LANGUAGE_CODE + " TEXT,"
                + CellBroadcasts.MESSAGE_BODY + " TEXT,"
                + CellBroadcasts.MESSAGE_FORMAT + " INTEGER,"
                + CellBroadcasts.MESSAGE_PRIORITY + " INTEGER,"
                + CellBroadcasts.ETWS_WARNING_TYPE + " INTEGER,"
                + CellBroadcasts.CMAS_MESSAGE_CLASS + " INTEGER,"
                + CellBroadcasts.CMAS_CATEGORY + " INTEGER,"
                + CellBroadcasts.CMAS_RESPONSE_TYPE + " INTEGER,"
                + CellBroadcasts.CMAS_SEVERITY + " INTEGER,"
                + CellBroadcasts.CMAS_URGENCY + " INTEGER,"
                + CellBroadcasts.CMAS_CERTAINTY + " INTEGER,"
                + CellBroadcasts.RECEIVED_TIME + " BIGINT,"
                + CellBroadcasts.MESSAGE_BROADCASTED + " BOOLEAN DEFAULT 0,"
                + CellBroadcasts.GEOMETRIES + " TEXT,"
                + CellBroadcasts.MAXIMUM_WAIT_TIME + " INTEGER);";
    }

    private SQLiteDatabase getWritableDatabase() {
        return mDbHelper.getWritableDatabase();
    }

    private SQLiteDatabase getReadableDatabase() {
        return mDbHelper.getReadableDatabase();
    }

    private void checkWritePermission() {
        if (!mPermissionChecker.hasWritePermission()) {
            throw new SecurityException(
                    "No permission to write CellBroadcast provider");
        }
    }

    private void checkReadPermission(Uri uri) {
        int match = sUriMatcher.match(uri);
        switch (match) {
            case ALL:
                if (!mPermissionChecker.hasReadPermission()) {
                    throw new SecurityException(
                            "No permission to read CellBroadcast provider");
                }
                break;
            case MESSAGE_HISTORY:
                // TODO: if we plan to allow apps to query db in framework, we should migrate data
                // first before deprecating app's database. otherwise users will lose all history.
                if (!mPermissionChecker.hasReadPermissionForHistory()) {
                    throw new SecurityException(
                            "No permission to read CellBroadcast provider for message history");
                }
                break;
            default:
                return;
        }
    }

    private class CellBroadcastDatabaseHelper extends SQLiteOpenHelper {
        CellBroadcastDatabaseHelper(Context context) {
            super(context, DATABASE_NAME, null /* factory */, DATABASE_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL(getStringForCellBroadcastTableCreation(CELL_BROADCASTS_TABLE_NAME));
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            if (DBG) {
                Rlog.d(TAG, "onUpgrade: oldV=" + oldVersion + " newV=" + newVersion);
            }
            if (newVersion == 2) {
                db.execSQL("ALTER TABLE " + CELL_BROADCASTS_TABLE_NAME + " ADD COLUMN "
                        + CellBroadcasts.SLOT_INDEX + " INTEGER DEFAULT 0;");
                Rlog.d(TAG, "add slotIndex column");
            }
        }
    }

    private class CellBroadcastPermissionChecker implements PermissionChecker {
        @Override
        public boolean hasWritePermission() {
            // Only the phone and network statck process has the write permission to modify this
            // provider.
            return Binder.getCallingUid() == Process.PHONE_UID
                    || Binder.getCallingUid() == Process.NETWORK_STACK_UID;
        }

        @Override
        public boolean hasReadPermission() {
            // Only the phone and network stack process has the read permission to query data from
            // this provider.
            return Binder.getCallingUid() == Process.PHONE_UID
                    || Binder.getCallingUid() == Process.NETWORK_STACK_UID;
        }

        @Override
        public boolean hasReadPermissionForHistory() {
            int status = getContext().checkCallingOrSelfPermission(
                    "android.permission.RECEIVE_EMERGENCY_BROADCAST");
            if (status == PackageManager.PERMISSION_GRANTED) {
                return true;
            }
            return false;
        }
    }
}
