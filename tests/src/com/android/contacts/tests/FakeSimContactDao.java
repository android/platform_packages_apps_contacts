/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.contacts.tests;

import android.content.ContentProviderResult;
import android.content.OperationApplicationException;
import android.os.RemoteException;

import com.android.contacts.database.SimContactDao;
import com.android.contacts.model.SimCard;
import com.android.contacts.model.SimContact;
import com.android.contacts.model.account.AccountWithDataSet;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Fake implementation of SimContactDao for testing
 */
public class FakeSimContactDao extends SimContactDao {

    public boolean canReadSimContacts = true;
    public List<SimCard> simCards;
    public Map<SimCard, ArrayList<SimContact>> simContacts;
    public ContentProviderResult[] importResult;
    public Map<AccountWithDataSet, Set<SimContact>> existingSimContacts;

    public FakeSimContactDao() {
        simCards = new ArrayList<>();
        simContacts = new HashMap<>();
        importResult = new ContentProviderResult[0];
        existingSimContacts = new HashMap<>();
    }

    @Override
    public boolean canReadSimContacts() {
        return canReadSimContacts;
    }

    @Override
    public List<SimCard> getSimCards() {
        return simCards;
    }

    @Override
    public ArrayList<SimContact> loadContactsForSim(SimCard sim) {
        return simContacts.get(sim);
    }

    @Override
    public ContentProviderResult[] importContacts(List<SimContact> contacts,
            AccountWithDataSet targetAccount)
            throws RemoteException, OperationApplicationException {
        return importResult;
    }

    @Override
    public void persistSimStates(List<SimCard> simCards) {
        this.simCards = simCards;
    }

    @Override
    public SimCard getSimBySubscriptionId(int subscriptionId) {
        if (subscriptionId == SimCard.NO_SUBSCRIPTION_ID) {
            return simCards.get(0);
        }
        for (SimCard sim : simCards) {
            if (sim.getSubscriptionId() == subscriptionId) {
                return sim;
            }
        }
        return null;
    }

    @Override
    public Map<AccountWithDataSet, Set<SimContact>> findAccountsOfExistingSimContacts(
            List<SimContact> contacts) {
        return existingSimContacts;
    }

    public FakeSimContactDao addSim(SimCard sim, SimContact... contacts) {
        simCards.add(sim);
        simContacts.put(sim, new ArrayList<>(Arrays.asList(contacts)));
        return this;
    }

    public static FakeSimContactDao singleSimWithContacts(SimCard sim, SimContact... contacts) {
        return new FakeSimContactDao().addSim(sim, contacts);
    }

    public static FakeSimContactDao noSim() {
        FakeSimContactDao result = new FakeSimContactDao();
        result.canReadSimContacts = false;
        return result;
    }
}
