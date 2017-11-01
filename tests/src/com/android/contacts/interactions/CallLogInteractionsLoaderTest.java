package com.android.contacts.interactions;

import android.content.ContentValues;
import android.provider.CallLog.Calls;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;

import java.util.ArrayList;
import java.util.List;

/**
 * Tests {@link CallLogInteractionsLoader}
 */
@SmallTest
public class CallLogInteractionsLoaderTest extends AndroidTestCase {

    public void testCallLogInteractions_pruneDuplicates_containsDuplicates() {
        List<ContactInteraction> interactions = new ArrayList<>();
        int maxToRetrieve = 5;

        ContentValues interactionOneValues = new ContentValues();
        interactionOneValues.put(Calls.DATE, 1L);
        interactions.add(new CallLogInteraction(interactionOneValues));

        ContentValues interactionTwoValues = new ContentValues();
        interactionTwoValues.put(Calls.DATE, 1L);
        interactions.add(new CallLogInteraction(interactionTwoValues));

        interactions = CallLogInteractionsLoader.pruneDuplicateCallLogInteractions(interactions,
                maxToRetrieve);
        assertEquals(1, interactions.size());
    }

    public void testCallLogInteractions_pruneDuplicates_containsNoDuplicates() {
        List<ContactInteraction> interactions = new ArrayList<>();
        int maxToRetrieve = 5;

        ContentValues interactionOneValues = new ContentValues();
        interactionOneValues.put(Calls.DATE, 1L);
        interactions.add(new CallLogInteraction(interactionOneValues));

        ContentValues interactionTwoValues = new ContentValues();
        interactionTwoValues.put(Calls.DATE, 5L);
        interactions.add(new CallLogInteraction(interactionTwoValues));

        interactions = CallLogInteractionsLoader.pruneDuplicateCallLogInteractions(interactions,
                maxToRetrieve);
        assertEquals(2, interactions.size());
    }

    public void testCallLogInteractions_maxToRetrieve() {
        List<ContactInteraction> interactions = new ArrayList<>();
        int maxToRetrieve = 1;

        ContentValues interactionOneValues = new ContentValues();
        interactionOneValues.put(Calls.DATE, 1L);
        interactions.add(new CallLogInteraction(interactionOneValues));

        ContentValues interactionTwoValues = new ContentValues();
        interactionTwoValues.put(Calls.DATE, 5L);
        interactions.add(new CallLogInteraction(interactionTwoValues));

        interactions = CallLogInteractionsLoader.pruneDuplicateCallLogInteractions(interactions,
                maxToRetrieve);
        assertEquals(1, interactions.size());
    }
}
