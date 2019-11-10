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
import android.telephony.SmsCbMessage;
import android.test.suitebuilder.annotation.SmallTest;
import android.testing.AndroidTestingRunner;
import android.util.Log;

import com.android.cellbroadcastservice.CbGeoUtils.Circle;
import com.android.cellbroadcastservice.CbGeoUtils.Polygon;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@RunWith(AndroidTestingRunner.class)
public class GsmSmsCbMessageTest extends CellBroadcastServiceTestBase {

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

        Log.d("GsmSmsCbMessageTest", "msg=" + msg);

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
}
