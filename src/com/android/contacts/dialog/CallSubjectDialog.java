/*
 * Copyright (C) 2015 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.contacts.dialog;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.QuickContactBadge;
import android.widget.TextView;

import com.android.contacts.CallUtil;
import com.android.contacts.ContactPhotoManager;
import com.android.contacts.R;
import com.android.contacts.compat.CompatUtils;
import com.android.contacts.compat.PhoneAccountSdkCompat;
import com.android.contacts.compat.telecom.TelecomManagerCompat;
import com.android.contacts.util.UriUtils;
import com.android.phone.common.animation.AnimUtils;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

/**
 * Implements a dialog which prompts for a call subject for an outgoing call.  The dialog includes
 * a pop up list of historical call subjects.
 */
public class CallSubjectDialog extends Activity {
    private static final String TAG = "CallSubjectDialog";
    private static final int CALL_SUBJECT_LIMIT = 16;
    private static final int CALL_SUBJECT_HISTORY_SIZE = 5;

    private static final int REQUEST_SUBJECT = 1001;

    public static final String PREF_KEY_SUBJECT_HISTORY_COUNT = "subject_history_count";
    public static final String PREF_KEY_SUBJECT_HISTORY_ITEM = "subject_history_item";

    /**
     * Activity intent argument bundle keys:
     */
    public static final String ARG_PHOTO_ID = "PHOTO_ID";
    public static final String ARG_PHOTO_URI = "PHOTO_URI";
    public static final String ARG_CONTACT_URI = "CONTACT_URI";
    public static final String ARG_NAME_OR_NUMBER = "NAME_OR_NUMBER";
    public static final String ARG_IS_BUSINESS = "IS_BUSINESS";
    public static final String ARG_NUMBER = "NUMBER";
    public static final String ARG_DISPLAY_NUMBER = "DISPLAY_NUMBER";
    public static final String ARG_NUMBER_LABEL = "NUMBER_LABEL";
    public static final String ARG_PHONE_ACCOUNT_HANDLE = "PHONE_ACCOUNT_HANDLE";

    private int mAnimationDuration;
    private Charset mMessageEncoding;
    private View mBackgroundView;
    private View mDialogView;
    private QuickContactBadge mContactPhoto;
    private TextView mNameView;
    private TextView mNumberView;
    private EditText mCallSubjectView;
    private TextView mCharacterLimitView;
    private View mHistoryButton;
    private View mSendAndCallButton;
    private ListView mSubjectList;

    private int mLimit = CALL_SUBJECT_LIMIT;
    private int mPhotoSize;
    private SharedPreferences mPrefs;
    private List<String> mSubjectHistory;

    private long mPhotoID;
    private Uri mPhotoUri;
    private Uri mContactUri;
    private String mNameOrNumber;
    private boolean mIsBusiness;
    private String mNumber;
    private String mDisplayNumber;
    private String mNumberLabel;
    private PhoneAccountHandle mPhoneAccountHandle;

    /**
     * Handles changes to the text in the subject box.  Ensures the character limit is updated.
     */
    private final TextWatcher mTextWatcher = new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            // no-op
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            updateCharacterLimit();
        }

        @Override
        public void afterTextChanged(Editable s) {
            // no-op
        }
    };

    /**
     * Click listener which handles user clicks outside of the dialog.
     */
    private View.OnClickListener mBackgroundListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            finish();
        }
    };

    /**
     * Handles displaying the list of past call subjects.
     */
    private final View.OnClickListener mHistoryOnClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            hideSoftKeyboard(CallSubjectDialog.this, mCallSubjectView);
            showCallHistory(mSubjectList.getVisibility() == View.GONE);
        }
    };

    /**
     * Handles starting a call with a call subject specified.
     */
    private final View.OnClickListener mSendAndCallOnClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            String subject = mCallSubjectView.getText().toString();
            Intent intent = CallUtil.getCallWithSubjectIntent(mNumber, mPhoneAccountHandle,
                    subject);

            TelecomManagerCompat.placeCall(
                    CallSubjectDialog.this,
                    (TelecomManager) getSystemService(Context.TELECOM_SERVICE),
                    intent);

            mSubjectHistory.add(subject);
            saveSubjectHistory(mSubjectHistory);
            finish();
        }
    };

    /**
     * Handles auto-hiding the call history when user clicks in the call subject field to give it
     * focus.
     */
    private final View.OnClickListener mCallSubjectClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (mSubjectList.getVisibility() == View.VISIBLE) {
                showCallHistory(false);
            }
        }
    };

    /**
     * Item click listener which handles user clicks on the items in the list view.  Dismisses
     * the activity, returning the subject to the caller and closing the activity with the
     * {@link Activity#RESULT_OK} result code.
     */
    private AdapterView.OnItemClickListener mItemClickListener =
            new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> arg0, View view, int position, long arg3) {
                    mCallSubjectView.setText(mSubjectHistory.get(position));
                    showCallHistory(false);
                }
            };

    /**
     * Show the call subject dialog given a phone number to dial (e.g. from the dialpad).
     *
     * @param activity The activity.
     * @param number The number to dial.
     */
    public static void start(Activity activity, String number) {
        start(activity,
                -1 /* photoId */,
                null /* photoUri */,
                null /* contactUri */,
                number /* nameOrNumber */,
                false /* isBusiness */,
                number /* number */,
                null /* displayNumber */,
                null /* numberLabel */,
                null /* phoneAccountHandle */);
    }

    /**
     * Creates a call subject dialog.
     *
     * @param activity The current activity.
     * @param photoId The photo ID (used to populate contact photo).
     * @param photoUri The photo Uri (used to populate contact photo).
     * @param contactUri The Contact URI (used so quick contact can be invoked from contact photo).
     * @param nameOrNumber The name or number of the callee.
     * @param isBusiness {@code true} if a business is being called (used for contact photo).
     * @param number The raw number to dial.
     * @param displayNumber The number to dial, formatted for display.
     * @param numberLabel The label for the number (if from a contact).
     * @param phoneAccountHandle The phone account handle.
     */
    public static void start(Activity activity, long photoId, Uri photoUri, Uri contactUri,
            String nameOrNumber, boolean isBusiness, String number, String displayNumber,
            String numberLabel, PhoneAccountHandle phoneAccountHandle) {
        Bundle arguments = new Bundle();
        arguments.putLong(ARG_PHOTO_ID, photoId);
        arguments.putParcelable(ARG_PHOTO_URI, photoUri);
        arguments.putParcelable(ARG_CONTACT_URI, contactUri);
        arguments.putString(ARG_NAME_OR_NUMBER, nameOrNumber);
        arguments.putBoolean(ARG_IS_BUSINESS, isBusiness);
        arguments.putString(ARG_NUMBER, number);
        arguments.putString(ARG_DISPLAY_NUMBER, displayNumber);
        arguments.putString(ARG_NUMBER_LABEL, numberLabel);
        arguments.putParcelable(ARG_PHONE_ACCOUNT_HANDLE, phoneAccountHandle);
        start(activity, arguments);
    }

    /**
     * Shows the call subject dialog given a Bundle containing all the arguments required to
     * display the dialog (e.g. from Quick Contacts).
     *
     * @param activity The activity.
     * @param arguments The arguments bundle.
     */
    public static void start(Activity activity, Bundle arguments) {
        Intent intent = new Intent(activity, CallSubjectDialog.class);
        intent.putExtras(arguments);
        activity.startActivity(intent);
    }

    /**
     * Creates the dialog, inflating the layout and populating it with the name and phone number.
     *
     * @param savedInstanceState The last saved instance state of the Fragment,
     * or null if this is a freshly created Fragment.
     *
     * @return Dialog instance.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mAnimationDuration = getResources().getInteger(R.integer.call_subject_animation_duration);
        mPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        mPhotoSize = getResources().getDimensionPixelSize(
                R.dimen.call_subject_dialog_contact_photo_size);
        readArguments();
        loadConfiguration();
        mSubjectHistory = loadSubjectHistory(mPrefs);

        setContentView(R.layout.dialog_call_subject);
        getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        mBackgroundView = findViewById(R.id.call_subject_dialog);
        mBackgroundView.setOnClickListener(mBackgroundListener);
        mDialogView = findViewById(R.id.dialog_view);
        mContactPhoto = (QuickContactBadge) findViewById(R.id.contact_photo);
        mNameView = (TextView) findViewById(R.id.name);
        mNumberView = (TextView) findViewById(R.id.number);
        mCallSubjectView = (EditText) findViewById(R.id.call_subject);
        mCallSubjectView.addTextChangedListener(mTextWatcher);
        mCallSubjectView.setOnClickListener(mCallSubjectClickListener);
        InputFilter[] filters = new InputFilter[1];
        filters[0] = new InputFilter.LengthFilter(mLimit);
        mCallSubjectView.setFilters(filters);
        mCharacterLimitView = (TextView) findViewById(R.id.character_limit);
        mHistoryButton = findViewById(R.id.history_button);
        mHistoryButton.setOnClickListener(mHistoryOnClickListener);
        mHistoryButton.setVisibility(mSubjectHistory.isEmpty() ? View.GONE : View.VISIBLE);
        mSendAndCallButton = findViewById(R.id.send_and_call_button);
        mSendAndCallButton.setOnClickListener(mSendAndCallOnClickListener);
        mSubjectList = (ListView) findViewById(R.id.subject_list);
        mSubjectList.setOnItemClickListener(mItemClickListener);
        mSubjectList.setVisibility(View.GONE);

        updateContactInfo();
        updateCharacterLimit();
    }

    /**
     * Populates the contact info fields based on the current contact information.
     */
    private void updateContactInfo() {
        if (mContactUri != null) {
            setPhoto(mPhotoID, mPhotoUri, mContactUri, mNameOrNumber, mIsBusiness);
        } else {
            mContactPhoto.setVisibility(View.GONE);
        }
        mNameView.setText(mNameOrNumber);
        if (!TextUtils.isEmpty(mNumberLabel) && !TextUtils.isEmpty(mDisplayNumber)) {
            mNumberView.setVisibility(View.VISIBLE);
            mNumberView.setText(getString(R.string.call_subject_type_and_number,
                    mNumberLabel, mDisplayNumber));
        } else {
            mNumberView.setVisibility(View.GONE);
            mNumberView.setText(null);
        }
    }

    /**
     * Reads arguments from the fragment arguments and populates the necessary instance variables.
     */
    private void readArguments() {
        Bundle arguments = getIntent().getExtras();
        if (arguments == null) {
            Log.e(TAG, "Arguments cannot be null.");
            return;
        }
        mPhotoID = arguments.getLong(ARG_PHOTO_ID);
        mPhotoUri = arguments.getParcelable(ARG_PHOTO_URI);
        mContactUri = arguments.getParcelable(ARG_CONTACT_URI);
        mNameOrNumber = arguments.getString(ARG_NAME_OR_NUMBER);
        mIsBusiness = arguments.getBoolean(ARG_IS_BUSINESS);
        mNumber = arguments.getString(ARG_NUMBER);
        mDisplayNumber = arguments.getString(ARG_DISPLAY_NUMBER);
        mNumberLabel = arguments.getString(ARG_NUMBER_LABEL);
        mPhoneAccountHandle = arguments.getParcelable(ARG_PHONE_ACCOUNT_HANDLE);
    }

    /**
     * Updates the character limit display, coloring the text RED when the limit is reached or
     * exceeded.
     */
    private void updateCharacterLimit() {
        String subjectText = mCallSubjectView.getText().toString();
        final int length;

        // If a message encoding is specified, use that to count bytes in the message.
        if (mMessageEncoding != null) {
            length = subjectText.getBytes(mMessageEncoding).length;
        } else {
            // No message encoding specified, so just count characters entered.
            length = subjectText.length();
        }

        mCharacterLimitView.setText(
                getString(R.string.call_subject_limit, length, mLimit));
        if (length >= mLimit) {
            mCharacterLimitView.setTextColor(getResources().getColor(
                    R.color.call_subject_limit_exceeded));
        } else {
            mCharacterLimitView.setTextColor(getResources().getColor(
                    R.color.dialtacts_secondary_text_color));
        }
    }

    /**
     * Sets the photo on the quick contact photo.
     *
     * @param photoId
     * @param photoUri
     * @param contactUri
     * @param displayName
     * @param isBusiness
     */
    private void setPhoto(long photoId, Uri photoUri, Uri contactUri, String displayName,
            boolean isBusiness) {
        mContactPhoto.assignContactUri(contactUri);
        if (CompatUtils.isLollipopCompatible()) {
            mContactPhoto.setOverlay(null);
        }

        int contactType;
        if (isBusiness) {
            contactType = ContactPhotoManager.TYPE_BUSINESS;
        } else {
            contactType = ContactPhotoManager.TYPE_DEFAULT;
        }

        String lookupKey = null;
        if (contactUri != null) {
            lookupKey = UriUtils.getLookupKeyFromUri(contactUri);
        }

        ContactPhotoManager.DefaultImageRequest
                request = new ContactPhotoManager.DefaultImageRequest(
                displayName, lookupKey, contactType, true /* isCircular */);

        if (photoId == 0 && photoUri != null) {
            ContactPhotoManager.getInstance(this).loadPhoto(mContactPhoto, photoUri,
                    mPhotoSize, false /* darkTheme */, true /* isCircular */, request);
        } else {
            ContactPhotoManager.getInstance(this).loadThumbnail(mContactPhoto, photoId,
                    false /* darkTheme */, true /* isCircular */, request);
        }
    }

    /**
     * Loads the subject history from shared preferences.
     *
     * @param prefs Shared preferences.
     * @return List of subject history strings.
     */
    public static List<String> loadSubjectHistory(SharedPreferences prefs) {
        int historySize = prefs.getInt(PREF_KEY_SUBJECT_HISTORY_COUNT, 0);
        List<String> subjects = new ArrayList(historySize);

        for (int ix = 0 ; ix < historySize; ix++) {
            String historyItem = prefs.getString(PREF_KEY_SUBJECT_HISTORY_ITEM + ix, null);
            if (!TextUtils.isEmpty(historyItem)) {
                subjects.add(historyItem);
            }
        }

        return subjects;
    }

    /**
     * Saves the subject history list to shared prefs, removing older items so that there are only
     * {@link #CALL_SUBJECT_HISTORY_SIZE} items at most.
     *
     * @param history The history.
     */
    private void saveSubjectHistory(List<String> history) {
        // Remove oldest subject(s).
        while (history.size() > CALL_SUBJECT_HISTORY_SIZE) {
            history.remove(0);
        }

        SharedPreferences.Editor editor = mPrefs.edit();
        int historyCount = 0;
        for (String subject : history) {
            if (!TextUtils.isEmpty(subject)) {
                editor.putString(PREF_KEY_SUBJECT_HISTORY_ITEM + historyCount,
                        subject);
                historyCount++;
            }
        }
        editor.putInt(PREF_KEY_SUBJECT_HISTORY_COUNT, historyCount);
        editor.apply();
    }

    /**
     * Hide software keyboard for the given {@link View}.
     */
    public void hideSoftKeyboard(Context context, View view) {
        InputMethodManager imm = (InputMethodManager) context.getSystemService(
                Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(view.getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
        }
    }

    /**
     * Hides or shows the call history list.
     *
     * @param show {@code true} if the call history should be shown, {@code false} otherwise.
     */
    private void showCallHistory(final boolean show) {
        // Bail early if the visibility has not changed.
        if ((show && mSubjectList.getVisibility() == View.VISIBLE) ||
                (!show && mSubjectList.getVisibility() == View.GONE)) {
            return;
        }

        final int dialogStartingBottom = mDialogView.getBottom();
        if (show) {
            // Showing the subject list; bind the list of history items to the list and show it.
            ArrayAdapter<String> adapter = new ArrayAdapter<String>(CallSubjectDialog.this,
                    R.layout.call_subject_history_list_item, mSubjectHistory);
            mSubjectList.setAdapter(adapter);
            mSubjectList.setVisibility(View.VISIBLE);
        } else {
            // Hiding the subject list.
            mSubjectList.setVisibility(View.GONE);
        }

        // Use a ViewTreeObserver so that we can animate between the pre-layout and post-layout
        // states.
        final ViewTreeObserver observer = mBackgroundView.getViewTreeObserver();
        observer.addOnPreDrawListener(
                new ViewTreeObserver.OnPreDrawListener() {
                    @Override
                    public boolean onPreDraw() {
                        // We don't want to continue getting called.
                        if (observer.isAlive()) {
                            observer.removeOnPreDrawListener(this);
                        }

                        // Determine the amount the dialog has shifted due to the relayout.
                        int shiftAmount = dialogStartingBottom - mDialogView.getBottom();

                        // If the dialog needs to be shifted, do that now.
                        if (shiftAmount != 0) {
                            // Start animation in translated state and animate to translationY 0.
                            mDialogView.setTranslationY(shiftAmount);
                            mDialogView.animate()
                                    .translationY(0)
                                    .setInterpolator(AnimUtils.EASE_OUT_EASE_IN)
                                    .setDuration(mAnimationDuration)
                                    .start();
                        }

                        if (show) {
                            // Show the subhect list.
                            mSubjectList.setTranslationY(mSubjectList.getHeight());

                            mSubjectList.animate()
                                    .translationY(0)
                                    .setInterpolator(AnimUtils.EASE_OUT_EASE_IN)
                                    .setDuration(mAnimationDuration)
                                    .setListener(new AnimatorListenerAdapter() {
                                        @Override
                                        public void onAnimationEnd(Animator animation) {
                                            super.onAnimationEnd(animation);
                                        }

                                        @Override
                                        public void onAnimationStart(Animator animation) {
                                            super.onAnimationStart(animation);
                                            mSubjectList.setVisibility(View.VISIBLE);
                                        }
                                    })
                                    .start();
                        } else {
                            // Hide the subject list.
                            mSubjectList.setTranslationY(0);

                            mSubjectList.animate()
                                    .translationY(mSubjectList.getHeight())
                                    .setInterpolator(AnimUtils.EASE_OUT_EASE_IN)
                                    .setDuration(mAnimationDuration)
                                    .setListener(new AnimatorListenerAdapter() {
                                        @Override
                                        public void onAnimationEnd(Animator animation) {
                                            super.onAnimationEnd(animation);
                                            mSubjectList.setVisibility(View.GONE);
                                        }

                                        @Override
                                        public void onAnimationStart(Animator animation) {
                                            super.onAnimationStart(animation);
                                        }
                                    })
                                    .start();
                        }
                        return true;
                    }
                }
        );
    }

    /**
     * Loads the message encoding and maximum message length from the phone account extras for the
     * current phone account.
     */
    private void loadConfiguration() {
        // Only attempt to load configuration from the phone account extras if the SDK is N or
        // later.  If we've got a prior SDK the default encoding and message length will suffice.
        int sdk = android.os.Build.VERSION.SDK_INT;
        if(sdk <= android.os.Build.VERSION_CODES.M) {
            return;
        }

        if (mPhoneAccountHandle == null) {
            return;
        }

        TelecomManager telecomManager =
                (TelecomManager) getSystemService(Context.TELECOM_SERVICE);
        final PhoneAccount account = telecomManager.getPhoneAccount(mPhoneAccountHandle);

        Bundle phoneAccountExtras = PhoneAccountSdkCompat.getExtras(account);
        if (phoneAccountExtras == null) {
            return;
        }

        // Get limit, if provided; otherwise default to existing value.
        mLimit = phoneAccountExtras
                .getInt(PhoneAccountSdkCompat.EXTRA_CALL_SUBJECT_MAX_LENGTH, mLimit);

        // Get charset; default to none (e.g. count characters 1:1).
        String charsetName = phoneAccountExtras.getString(
                PhoneAccountSdkCompat.EXTRA_CALL_SUBJECT_CHARACTER_ENCODING);

        if (!TextUtils.isEmpty(charsetName)) {
            try {
                mMessageEncoding = Charset.forName(charsetName);
            } catch (java.nio.charset.UnsupportedCharsetException uce) {
                // Character set was invalid; log warning and fallback to none.
                Log.w(TAG, "Invalid charset: " + charsetName);
                mMessageEncoding = null;
            }
        } else {
            // No character set specified, so count characters 1:1.
            mMessageEncoding = null;
        }
    }
}
