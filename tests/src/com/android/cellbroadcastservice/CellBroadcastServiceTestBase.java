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

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;

import android.content.Context;
import android.content.res.Resources;
import android.os.Handler;
import android.os.IPowerManager;
import android.os.PowerManager;
import android.telephony.SubscriptionManager;
import android.test.mock.MockContentResolver;
import android.testing.TestableLooper;

import junit.framework.TestCase;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;

/**
 * This is the test base class can be extended by all cell broadcast service unit test classes.
 */
public class CellBroadcastServiceTestBase extends TestCase {

    @Mock
    Context mMockedContext;

    @Mock
    Resources mMockedResources;

    @Mock
    SubscriptionManager mMockedSubscriptionManager;

    final MockContentResolver mMockedContentResolver = new MockContentResolver();

    private final HashMap<InstanceKey, Object> mOldInstances = new HashMap<>();

    private final LinkedList<InstanceKey> mInstanceKeys = new LinkedList<>();

    private static class InstanceKey {
        final Class mClass;
        final String mInstName;
        final Object mObj;

        InstanceKey(final Class c, final String instName, final Object obj) {
            mClass = c;
            mInstName = instName;
            mObj = obj;
        }

        @Override
        public int hashCode() {
            return (mClass.getName().hashCode() * 31 + mInstName.hashCode()) * 31;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null || obj.getClass() != getClass()) {
                return false;
            }

            InstanceKey other = (InstanceKey) obj;
            return (other.mClass == mClass && other.mInstName.equals(mInstName)
                    && other.mObj == mObj);
        }
    }

    protected void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        doReturn(mMockedContentResolver).when(mMockedContext).getContentResolver();
        doReturn(mMockedResources).when(mMockedContext).getResources();

        // Can't directly mock power manager because it's final.
        PowerManager powerManager = new PowerManager(mMockedContext, mock(IPowerManager.class),
                new Handler(TestableLooper.get(CellBroadcastServiceTestBase.this).getLooper()));
        doReturn(powerManager).when(mMockedContext).getSystemService(Context.POWER_SERVICE);
        doReturn(mMockedSubscriptionManager).when(mMockedContext)
                .getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
        doReturn(new int[]{1}).when(mMockedSubscriptionManager).getSubscriptionIds(anyInt());
    }

    protected void tearDown() throws Exception {
        restoreInstances();
    }

    void putResources(int id, Object value) {
        if (value instanceof String[]) {
            doReturn(value).when(mMockedResources).getStringArray(eq(id));
        } else if (value instanceof Boolean) {
            doReturn(value).when(mMockedResources).getBoolean(eq(id));
        } else if (value instanceof Integer) {
            doReturn(value).when(mMockedResources).getInteger(eq(id));
        } else if (value instanceof Integer[]) {
            doReturn(value).when(mMockedResources).getIntArray(eq(id));
        } else if (value instanceof String) {
            doReturn(value).when(mMockedResources).getString(eq(id));
        }
    }

    synchronized void replaceInstance(final Class c, final String instanceName,
                                              final Object obj, final Object newValue)
            throws Exception {
        Field field = c.getDeclaredField(instanceName);
        field.setAccessible(true);

        InstanceKey key = new InstanceKey(c, instanceName, obj);
        if (!mOldInstances.containsKey(key)) {
            mOldInstances.put(key, field.get(obj));
            mInstanceKeys.add(key);
        }
        field.set(obj, newValue);
    }

    private synchronized void restoreInstances() throws Exception {
        Iterator<InstanceKey> it = mInstanceKeys.descendingIterator();

        while (it.hasNext()) {
            InstanceKey key = it.next();
            Field field = key.mClass.getDeclaredField(key.mInstName);
            field.setAccessible(true);
            field.set(key.mObj, mOldInstances.get(key));
        }

        mInstanceKeys.clear();
        mOldInstances.clear();
    }
}
