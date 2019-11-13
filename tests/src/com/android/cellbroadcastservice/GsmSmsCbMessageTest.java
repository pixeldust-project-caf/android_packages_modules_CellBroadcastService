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

import android.telephony.CbGeoUtils;
import android.telephony.SmsCbCmasInfo;
import android.telephony.SmsCbEtwsInfo;
import android.telephony.SmsCbMessage;
import android.test.suitebuilder.annotation.SmallTest;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;
import android.util.Log;

import com.android.cellbroadcastservice.CbGeoUtils.Circle;
import com.android.cellbroadcastservice.CbGeoUtils.Polygon;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class GsmSmsCbMessageTest extends CellBroadcastServiceTestBase {

    private static final String TAG = "GsmSmsCbMessageTest";

    @Before
    public void setUp() throws Exception {
        super.setUp();
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    @SmallTest
    public void testGetEtwsPrimaryMessage() {
        String testMessage1 = "Testmessage1";
        String testMessage2 = "Testmessage2";
        String testMessage3 = "Testmessage3";
        String testMessage4 = "Testmessage4";
        String testMessage5 = "Testmessage5";

        putResources(R.string.etws_primary_default_message_earthquake, testMessage1);
        putResources(R.string.etws_primary_default_message_tsunami, testMessage2);
        putResources(R.string.etws_primary_default_message_earthquake_and_tsunami, testMessage3);
        putResources(R.string.etws_primary_default_message_test, testMessage4);
        putResources(R.string.etws_primary_default_message_others, testMessage5);

        String message = GsmSmsCbMessage.getEtwsPrimaryMessage(mMockedContext,
                SmsCbEtwsInfo.ETWS_WARNING_TYPE_EARTHQUAKE);
        Log.d("GsmSmsCbMessageTest", "earthquake message=" + message);
        assertEquals(testMessage1, message);

        message = GsmSmsCbMessage.getEtwsPrimaryMessage(mMockedContext,
                SmsCbEtwsInfo.ETWS_WARNING_TYPE_TSUNAMI);
        Log.d("GsmSmsCbMessageTest", "tsunami message=" + message);
        assertEquals(testMessage2, message);

        message = GsmSmsCbMessage.getEtwsPrimaryMessage(mMockedContext,
                SmsCbEtwsInfo.ETWS_WARNING_TYPE_EARTHQUAKE_AND_TSUNAMI);
        Log.d("GsmSmsCbMessageTest", "earthquake and tsunami message=" + message);
        assertEquals(testMessage3, message);

        message = GsmSmsCbMessage.getEtwsPrimaryMessage(mMockedContext,
                SmsCbEtwsInfo.ETWS_WARNING_TYPE_TEST_MESSAGE);
        Log.d("GsmSmsCbMessageTest", "test message=" + message);
        assertEquals(testMessage4, message);

        message = GsmSmsCbMessage.getEtwsPrimaryMessage(mMockedContext,
                SmsCbEtwsInfo.ETWS_WARNING_TYPE_OTHER_EMERGENCY);
        Log.d("GsmSmsCbMessageTest", "others message=" + message);
        assertEquals(testMessage5, message);
    }

    @Test
    @SmallTest
    public void testCreateMessageFromBinary() throws Exception {
        final byte[] pdu = hexStringToBytes("0111130F6A0101C8329BFD06559BD429E8FE96B3C92C101D9D9"
                + "E83D27350B22E1C7EAFF234BDFCADB962AE9A6BCE06A1DCE57B0AD40241C3E73208147B81622E000"
                + "0000000000000000000000000000000000000000000000039EA013028B53640A4BF600063204C8FC"
                + "D063F341AF67167E683CF01215F1E40100C053028B53640A4BF600063204C8FCD063F341AF67167E"
                + "683CF01215F1E40100C053028B53640A4BF600063204C8FCD063F341AF67167E683CF01215F1E401"
                + "00C053028B53640A4BF600063204C8FCD063F341AF67167E683CF01215F1E40100C053028B53640A"
                + "4BF600063204C8FCD063F341AF67167E683CF01215F1E40100C053028B53640A4BF600063204C8FC"
                + "D063F341AF67167E683CF01215F1E40100C053028B53640A4BF600063204C8FCD063F341AF67167E"
                + "683CF01215F1E40100C053028B53640A4BF600063204C8FCD063F341AF67167E683CF01215F1E401"
                + "00C053028B53640A4BF600063204C8FCD063F341AF67167E683CF01215F1E40100C053028B53640A"
                + "4BF600063204C8FCD063F341AF67167E683CF01215F1E40100C053028B53640A4BF600063204C8FC"
                + "D063F341AF67167E683CF01215F1E40100C053028B53640A4BF600063204C8FCD063F341AF67167E"
                + "683CF01215F1E40100C053028B53640A4BF600063204C8FCD063F341AF67167E683CF01215F1E401"
                + "00C053028B53640A4BF600063204C8FCD063F341AF67167E683CF01215F1E40100C053028B53640A"
                + "4BF600063204C8FCD063F341AF67167E683CF01215F1E40100C053028B53640A4BF600063");
        SmsCbHeader header = new SmsCbHeader(pdu);

        byte[][] pdus = new byte[1][];
        pdus[0] = pdu;

        SmsCbMessage msg = GsmSmsCbMessage.createSmsCbMessage(mMockedContext, header, null, pdus,
                0);

        Log.d(TAG, "msg=" + msg);

        assertEquals(SmsCbMessage.GEOGRAPHICAL_SCOPE_CELL_WIDE_IMMEDIATE,
                msg.getGeographicalScope());
        assertEquals(3946, msg.getSerialNumber());
        assertEquals(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_IMMEDIATE_OBSERVED,
                msg.getServiceCategory());
        assertEquals("en", msg.getLanguageCode());
        assertEquals("Hello UMTS world, this is IuBC§Write§5.1.5.sl (new) - Page  1/ 1.",
                msg.getMessageBody());
        assertEquals(SmsCbMessage.MESSAGE_PRIORITY_EMERGENCY, msg.getMessagePriority());

        SmsCbCmasInfo cmasInfo = msg.getCmasWarningInfo();
        assertEquals(SmsCbCmasInfo.CMAS_CLASS_EXTREME_THREAT, cmasInfo.getMessageClass());
        assertEquals(SmsCbCmasInfo.CMAS_CATEGORY_UNKNOWN, cmasInfo.getCategory());
        assertEquals(SmsCbCmasInfo.CMAS_RESPONSE_TYPE_UNKNOWN, cmasInfo.getResponseType());
        assertEquals(SmsCbCmasInfo.CMAS_URGENCY_IMMEDIATE, cmasInfo.getUrgency());
        assertEquals(SmsCbCmasInfo.CMAS_CERTAINTY_OBSERVED, cmasInfo.getCertainty());

        List<CbGeoUtils.Geometry> geometries = msg.getGeometries();
        for (int i = 0; i < 15; i++) {
            assertEquals(1546.875, ((Circle) geometries.get(i * 2)).getRadius());
            assertEquals(37.41462707519531, ((Circle) geometries.get(i * 2)).getCenter().lat);
            assertEquals(-122.08093643188477, ((Circle) geometries.get(i * 2)).getCenter().lng);
            assertEquals(11.109967231750488,
                    ((Polygon) geometries.get(i * 2 + 1)).getVertices().get(0).lat);
            assertEquals(22.219934463500977,
                    ((Polygon) geometries.get(i * 2 + 1)).getVertices().get(0).lng);
            assertEquals(33.32998752593994, 44,
                    ((Polygon) geometries.get(i * 2 + 1)).getVertices().get(1).lat);
            assertEquals(44.43995475769043,
                    ((Polygon) geometries.get(i * 2 + 1)).getVertices().get(1).lng);
            assertEquals(55.549964904785156,
                    ((Polygon) geometries.get(i * 2 + 1)).getVertices().get(2).lat);
            assertEquals(-56.560020446777344,
                    ((Polygon) geometries.get(i * 2 + 1)).getVertices().get(2).lng);
        }
    }

    @Test
    @SmallTest
    public void testCreateTriggerMessage() throws Exception {
        final byte[] pdu = hexStringToBytes("0001113001010010C0111204D2");
        GsmSmsCbMessage.GeoFencingTriggerMessage triggerMessage =
                GsmSmsCbMessage.createGeoFencingTriggerMessage(pdu);

        Log.d(TAG, "trigger message=" + triggerMessage);

        assertEquals(1, triggerMessage.type);
        assertEquals(1, triggerMessage.cbIdentifiers.size());
        assertEquals(1234, triggerMessage.cbIdentifiers.get(0).serialNumber);
        assertEquals(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_PRESIDENTIAL_LEVEL,
                triggerMessage.cbIdentifiers.get(0).messageIdentifier);
    }
}
