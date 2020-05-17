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

package com.android.cellbroadcastservice.tests;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doReturn;

import android.content.ContentValues;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.provider.Telephony;
import android.telephony.SmsCbCmasInfo;
import android.telephony.SmsCbLocation;
import android.telephony.SmsCbMessage;
import android.test.mock.MockContentProvider;
import android.test.mock.MockContentResolver;
import android.test.suitebuilder.annotation.SmallTest;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;
import android.text.format.DateUtils;

import com.android.cellbroadcastservice.CellBroadcastHandler;
import com.android.cellbroadcastservice.CellBroadcastProvider;
import com.android.cellbroadcastservice.SmsCbConstants;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;

import java.util.Map;

@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class CellBroadcastHandlerTest extends CellBroadcastServiceTestBase {

    private CellBroadcastHandler mCellBroadcastHandler;

    private TestableLooper mTestbleLooper;

    @Mock
    private Map<Integer, Resources> mMockedResourcesCache;

    private class CellBroadcastContentProvider extends MockContentProvider {
        @Override
        public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
                String sortOrder) {

            if (uri.compareTo(Telephony.CellBroadcasts.CONTENT_URI) == 0) {
                MatrixCursor mc = new MatrixCursor(CellBroadcastProvider.QUERY_COLUMNS);

                mc.addRow(new Object[]{
                        1,              // _ID
                        0,              // SLOT_INDEX
                        1,              // SUBSCRIPTION_ID
                        0,              // GEOGRAPHICAL_SCOPE
                        "311480",       // PLMN
                        0,              // LAC
                        0,              // CID
                        1234,           // SERIAL_NUMBER
                        SmsCbConstants.MESSAGE_ID_CMAS_ALERT_PRESIDENTIAL_LEVEL,
                        "en",           // LANGUAGE_CODE
                        1,              // DATA_CODING_SCHEME
                        "Test Message", // MESSAGE_BODY
                        1,              // MESSAGE_FORMAT
                        3,              // MESSAGE_PRIORITY
                        0,              // ETWS_WARNING_TYPE
                        SmsCbCmasInfo.CMAS_CLASS_PRESIDENTIAL_LEVEL_ALERT, // CMAS_MESSAGE_CLASS
                        0,              // CMAS_CATEGORY
                        0,              // CMAS_RESPONSE_TYPE
                        0,              // CMAS_SEVERITY
                        0,              // CMAS_URGENCY
                        0,              // CMAS_CERTAINTY
                        System.currentTimeMillis() - DateUtils.HOUR_IN_MILLIS * 2,
                        System.currentTimeMillis() - DateUtils.HOUR_IN_MILLIS * 2,
                        true,           // MESSAGE_BROADCASTED
                        true,           // MESSAGE_DISPLAYED
                        "",             // GEOMETRIES
                        5,              // MAXIMUM_WAIT_TIME
                });

                return mc;
            }

            return null;
        }

        @Override
        public int update(Uri url, ContentValues values, String where, String[] whereArgs) {
            return 1;
        }
    }

    @Before
    public void setUp() throws Exception {
        super.setUp();

        mTestbleLooper = TestableLooper.get(CellBroadcastHandlerTest.this);

        mCellBroadcastHandler = new CellBroadcastHandler("CellBroadcastHandlerUT",
                mMockedContext, mTestbleLooper.getLooper());
        ((MockContentResolver) mMockedContext.getContentResolver()).addProvider(
                Telephony.CellBroadcasts.CONTENT_URI.getAuthority(),
                new CellBroadcastContentProvider());
        doReturn(true).when(mMockedResourcesCache).containsKey(anyInt());
        doReturn(mMockedResources).when(mMockedResourcesCache).get(anyInt());
        replaceInstance(CellBroadcastHandler.class, "mResourcesCache", mCellBroadcastHandler,
                mMockedResourcesCache);
        putResources(com.android.cellbroadcastservice.R.integer.message_expiration_time,
                (int) DateUtils.DAY_IN_MILLIS);
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    private SmsCbMessage createSmsCbMessage(int serialNumber, int serviceCategory,
            String messageBody) {
        return new SmsCbMessage(SmsCbMessage.MESSAGE_FORMAT_3GPP,
                0, serialNumber, new SmsCbLocation("311480", 0, 0),
                serviceCategory, "en", messageBody, 3,
                null, null, 0, 1);
    }

    @Test
    @SmallTest
    public void testDuplicate() throws Exception {
        SmsCbMessage msg = createSmsCbMessage(1234, 4370, "msg");
        assertTrue(mCellBroadcastHandler.isDuplicate(msg));
    }

    @Test
    @SmallTest
    public void testNotDuplicateSerialDifferent() throws Exception {
        SmsCbMessage msg = createSmsCbMessage(1235, 4370, "msg");
        assertFalse(mCellBroadcastHandler.isDuplicate(msg));
    }

    @Test
    @SmallTest
    public void testNotDuplicateServiceCategoryDifferent() throws Exception {
        SmsCbMessage msg = createSmsCbMessage(1234, 4371, "msg");
        assertFalse(mCellBroadcastHandler.isDuplicate(msg));
    }

    @Test
    @SmallTest
    public void testNotDuplicateMessageBodyDifferent() throws Exception {
        putResources(com.android.cellbroadcastservice.R.bool.duplicate_compare_body, true);
        SmsCbMessage msg = createSmsCbMessage(1234, 4370, "msg");
        assertFalse(mCellBroadcastHandler.isDuplicate(msg));
    }

    public void testNotDuplicateCellLocationDifferent() throws Exception {
        SmsCbMessage msg = new SmsCbMessage(SmsCbMessage.MESSAGE_FORMAT_3GPP,
                0, 1234, new SmsCbLocation("311480", 0, 1),
                4370, "en", "Test Message", 3,
                null, null, 0, 1);
        assertFalse(mCellBroadcastHandler.isDuplicate(msg));
    }
}
