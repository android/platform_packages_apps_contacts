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

package com.android.contacts;

import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;

import com.android.contacts.activities.AppCompatTransactionSafeActivity;
import com.android.contacts.testing.InjectedServices;

/**
 * A common superclass for Contacts activities that handles application-wide services, copied from
 * {@link com.android.contacts.ContactsActivity}, which will be deprecated after Kitkat backporting
 * is done.
 */
public abstract class AppCompatContactsActivity extends AppCompatTransactionSafeActivity
        implements ContactSaveService.Listener {

    private ContentResolver mContentResolver;

    @Override
    public ContentResolver getContentResolver() {
        if (mContentResolver == null) {
            InjectedServices services = ContactsApplication.getInjectedServices();
            if (services != null) {
                mContentResolver = services.getContentResolver();
            }
            if (mContentResolver == null) {
                mContentResolver = super.getContentResolver();
            }
        }
        return mContentResolver;
    }

    @Override
    public SharedPreferences getSharedPreferences(String name, int mode) {
        InjectedServices services = ContactsApplication.getInjectedServices();
        if (services != null) {
            SharedPreferences prefs = services.getSharedPreferences();
            if (prefs != null) {
                return prefs;
            }
        }

        return super.getSharedPreferences(name, mode);
    }

    @Override
    public Object getSystemService(String name) {
        Object service = super.getSystemService(name);
        if (service != null) {
            return service;
        }

        return getApplicationContext().getSystemService(name);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ContactSaveService.registerListener(this);
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void onDestroy() {
        ContactSaveService.unregisterListener(this);
        super.onDestroy();
    }

    @Override
    public void onServiceCompleted(Intent callbackIntent) {
        onNewIntent(callbackIntent);
    }

    /**
     * Convenient version of {@link FragmentManager#findFragmentById(int)}, which throws
     * an exception if the fragment doesn't exist.
     */
    @SuppressWarnings("unchecked")
    public <T extends Fragment> T getFragment(int id) {
        T result = (T)getFragmentManager().findFragmentById(id);
        if (result == null) {
            throw new IllegalArgumentException("fragment 0x" + Integer.toHexString(id)
                    + " doesn't exist");
        }
        return result;
    }

    /**
     * Convenient version of {@link #findViewById(int)}, which throws
     * an exception if the view doesn't exist.
     */
    @SuppressWarnings("unchecked")
    public <T extends View> T getView(int id) {
        T result = (T)findViewById(id);
        if (result == null) {
            throw new IllegalArgumentException("view 0x" + Integer.toHexString(id)
                    + " doesn't exist");
        }
        return result;
    }

    protected static void showFragment(FragmentTransaction ft, Fragment f) {
        if ((f != null) && f.isHidden()) ft.show(f);
    }

    protected static void hideFragment(FragmentTransaction ft, Fragment f) {
        if ((f != null) && !f.isHidden()) ft.hide(f);
    }
}
