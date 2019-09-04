/*
 * Copyright (C) 2010 The Android Open Source Project
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
package com.android.contacts.list;

import android.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.PorterDuff;
import android.os.Bundle;
import androidx.core.content.ContextCompat;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.android.contacts.R;
import com.android.contacts.compat.ProviderStatusCompat;
import com.android.contacts.interactions.ImportDialogFragment;
import com.android.contacts.util.ImplicitIntentsUtil;

/**
 * Fragment shown when contacts are unavailable. It contains provider status
 * messaging as well as instructions for the user.
 */
public class ContactsUnavailableFragment extends Fragment implements OnClickListener {

    private View mView;
    private ImageView mImageView;
    private TextView mMessageView;
    private Button mAddAccountButton;
    private Button mImportContactsButton;
    private ProgressBar mProgress;
    private View mButtonsContainer;

    private Integer mProviderStatus;

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mView = inflater.inflate(R.layout.contacts_unavailable_fragment, null);

        mImageView = (ImageView) mView.findViewById(R.id.empty_image);
        final LinearLayout.LayoutParams layoutParams =
                (LinearLayout.LayoutParams) mImageView.getLayoutParams();
        final int screenHeight = getResources().getDisplayMetrics().heightPixels;
        final int topMargin =
                screenHeight / getResources()
                        .getInteger(R.integer.contacts_no_account_empty_image_margin_divisor)
                - getResources()
                        .getDimensionPixelSize(R.dimen.contacts_no_account_empty_image_offset);
        layoutParams.setMargins(0, topMargin, 0, 0);
        layoutParams.gravity = Gravity.CENTER_HORIZONTAL;
        mImageView.setLayoutParams(layoutParams);

        mMessageView = (TextView) mView.findViewById(R.id.message);
        mAddAccountButton = (Button) mView.findViewById(R.id.add_account_button);
        mAddAccountButton.setOnClickListener(this);
        mAddAccountButton.getBackground().setColorFilter(ContextCompat.getColor(getContext(), R
                .color.primary_color), PorterDuff.Mode.SRC_ATOP);
        mImportContactsButton = (Button) mView.findViewById(R.id.import_contacts_button);
        mImportContactsButton.setOnClickListener(this);
        mImportContactsButton.getBackground().setColorFilter(ContextCompat.getColor(getContext(),
                R.color.primary_color), PorterDuff.Mode.SRC_ATOP);
        mProgress = (ProgressBar) mView.findViewById(R.id.progress);

        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            mButtonsContainer = mView.findViewById(R.id.buttons_container);
        }

        if (mProviderStatus != null) {
            updateStatus(mProviderStatus);
        }

        return mView;
    }

    public void updateStatus(int providerStatus) {
        mProviderStatus = providerStatus;
        if (mView == null) {
            // The view hasn't been inflated yet.
            return;
        }
        if (providerStatus == ProviderStatusCompat.STATUS_EMPTY) {
            updateViewsForEmptyStatus();
        } else if (providerStatus == ProviderStatusCompat.STATUS_BUSY
                || providerStatus == ProviderStatusCompat.STATUS_CHANGING_LOCALE) {
            updateViewsForBusyStatus();
        }
    }

    /**
     * Update views in the fragment when provider status is empty.
     */
    private void updateViewsForEmptyStatus() {
        mMessageView.setVisibility(View.VISIBLE);
        updateButtonVisibility(View.VISIBLE);
        mProgress.setVisibility(View.GONE);
    }

    /**
     * Update views in the fragment when provider status is busy.
     */
    private void updateViewsForBusyStatus() {
        mMessageView.setVisibility(View.GONE);
        mImageView.setVisibility(View.GONE);
        updateButtonVisibility(View.GONE);
        mProgress.setVisibility(View.VISIBLE);
    }

    @Override
    public void onClick(View v) {
        final int id = v.getId();
        if (id == R.id.add_account_button) {
            final Intent intent = ImplicitIntentsUtil.getIntentForAddingGoogleAccount();
            ImplicitIntentsUtil.startActivityOutsideApp(getActivity(), intent);

        } else if (id == R.id.import_contacts_button) {
            ImportDialogFragment.show(getFragmentManager());

        }
    }

    private void updateButtonVisibility(int visibility) {
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            mAddAccountButton.setVisibility(visibility);
            mImportContactsButton.setVisibility(visibility);
            mButtonsContainer.setVisibility(visibility);
        } else {
            mAddAccountButton.setVisibility(visibility);
            mImportContactsButton.setVisibility(visibility);
        }
    }

    @Override
    public Context getContext() {
        return getActivity();
    }
}
