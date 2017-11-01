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
package com.android.contacts.activities;

import android.app.Fragment;
import android.app.FragmentManager;
import android.os.Bundle;

import com.android.contacts.AppCompatContactsActivity;
import com.android.contacts.R;
import com.android.contacts.SimImportFragment;
import com.android.contacts.model.SimCard;

/**
 * Host activity for SimImportFragment
 *
 * Initially SimImportFragment was a DialogFragment but there were accessibility issues with
 * that so it was changed to an activity
 */
public class SimImportActivity extends AppCompatContactsActivity {

    public static final String EXTRA_SUBSCRIPTION_ID = "extraSubscriptionId";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.sim_import_activity);
        final FragmentManager fragmentManager = getFragmentManager();
        Fragment fragment = fragmentManager.findFragmentByTag("SimImport");
        if (fragment == null) {
            fragment = SimImportFragment.newInstance(getIntent().getIntExtra(EXTRA_SUBSCRIPTION_ID,
                    SimCard.NO_SUBSCRIPTION_ID));
            fragmentManager.beginTransaction().add(R.id.root, fragment, "SimImport").commit();
        }
    }
}
