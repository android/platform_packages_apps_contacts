/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.contacts.voicemail;

import static com.android.contacts.CallDetailActivity.EXTRA_VOICEMAIL_START_PLAYBACK;
import static com.android.contacts.CallDetailActivity.EXTRA_VOICEMAIL_URI;

import android.app.Activity;
import android.app.Fragment;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.database.Cursor;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.VoicemailContract;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;

import com.android.common.io.MoreCloseables;
import com.android.contacts.ProximitySensorAware;
import com.android.contacts.R;
import com.android.contacts.util.AsyncTaskExecutors;
import com.android.ex.variablespeed.MediaPlayerProxy;
import com.android.ex.variablespeed.VariableSpeed;
import com.google.common.base.Preconditions;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.NotThreadSafe;

/**
 * Displays and plays back a single voicemail.
 * <p>
 * When the Activity containing this Fragment is created, voicemail playback
 * will begin immediately. The Activity is expected to be started via an intent
 * containing a suitable voicemail uri to playback.
 * <p>
 * This class is not thread-safe, it is thread-confined. All calls to all public
 * methods on this class are expected to come from the main ui thread.
 */
@NotThreadSafe
public class VoicemailPlaybackFragment extends Fragment {
    private static final String TAG = "VoicemailPlayback";
    private static final int NUMBER_OF_THREADS_IN_POOL = 2;
    private static final String[] HAS_CONTENT_PROJECTION = new String[] {
        VoicemailContract.Voicemails.HAS_CONTENT,
    };

    private VoicemailPlaybackPresenter mPresenter;
    private ScheduledExecutorService mScheduledExecutorService;
    private View mPlaybackLayout;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        mPlaybackLayout = inflater.inflate(R.layout.playback_layout, null);
        return mPlaybackLayout;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mScheduledExecutorService = createScheduledExecutorService();
        Bundle arguments = getArguments();
        Preconditions.checkNotNull(arguments, "fragment must be started with arguments");
        Uri voicemailUri = arguments.getParcelable(EXTRA_VOICEMAIL_URI);
        Preconditions.checkNotNull(voicemailUri, "fragment must contain EXTRA_VOICEMAIL_URI");
        boolean startPlayback = arguments.getBoolean(EXTRA_VOICEMAIL_START_PLAYBACK, false);
        PowerManager powerManager =
                (PowerManager) getActivity().getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wakeLock =
                powerManager.newWakeLock(
                        PowerManager.SCREEN_DIM_WAKE_LOCK, getClass().getSimpleName());
        mPresenter = new VoicemailPlaybackPresenter(createPlaybackViewImpl(),
                createMediaPlayer(mScheduledExecutorService), voicemailUri,
                mScheduledExecutorService, startPlayback,
                AsyncTaskExecutors.createAsyncTaskExecutor(), wakeLock);
        mPresenter.onCreate(savedInstanceState);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        mPresenter.onSaveInstanceState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onDestroy() {
        mPresenter.onDestroy();
        mScheduledExecutorService.shutdown();
        super.onDestroy();
    }

    @Override
    public void onPause() {
        mPresenter.onPause();
        super.onPause();
    }

    private PlaybackViewImpl createPlaybackViewImpl() {
        return new PlaybackViewImpl(new ActivityReference(), getActivity().getApplicationContext(),
                mPlaybackLayout);
    }

    private MediaPlayerProxy createMediaPlayer(ExecutorService executorService) {
        return VariableSpeed.createVariableSpeed(executorService);
    }

    private ScheduledExecutorService createScheduledExecutorService() {
        return Executors.newScheduledThreadPool(NUMBER_OF_THREADS_IN_POOL);
    }

    /**
     * Formats a number of milliseconds as something that looks like {@code 00:05}.
     * <p>
     * We always use four digits, two for minutes two for seconds.  In the very unlikely event
     * that the voicemail duration exceeds 99 minutes, the display is capped at 99 minutes.
     */
    private static String formatAsMinutesAndSeconds(int millis) {
        int seconds = millis / 1000;
        int minutes = seconds / 60;
        seconds -= minutes * 60;
        if (minutes > 99) {
            minutes = 99;
        }
        return String.format("%02d:%02d", minutes, seconds);
    }

    /**
     * An object that can provide us with an Activity.
     * <p>
     * Fragments suffer the drawback that the Activity they belong to may sometimes be null. This
     * can happen if the Fragment is detached, for example. In that situation a call to
     * {@link Fragment#getString(int)} will throw and {@link IllegalStateException}. Also, calling
     * {@link Fragment#getActivity()} is dangerous - it may sometimes return null. And thus blindly
     * calling a method on the result of getActivity() is dangerous too.
     * <p>
     * To work around this, I have made the {@link PlaybackViewImpl} class static, so that it does
     * not have access to any Fragment methods directly. Instead it uses an application Context for
     * things like accessing strings, accessing system services. It only uses the Activity when it
     * absolutely needs it - and does so through this class. This makes it easy to see where we have
     * to check for null properly.
     */
    private final class ActivityReference {
        /** Gets this Fragment's Activity: <b>may be null</b>. */
        public final Activity get() {
            return getActivity();
        }
    }

    /**  Methods required by the PlaybackView for the VoicemailPlaybackPresenter. */
    private static final class PlaybackViewImpl implements VoicemailPlaybackPresenter.PlaybackView {
        private final ActivityReference mActivityReference;
        private final Context mApplicationContext;
        private final SeekBar mPlaybackSeek;
        private final ImageButton mStartStopButton;
        private final ImageButton mPlaybackSpeakerphone;
        private final ImageButton mRateDecreaseButton;
        private final ImageButton mRateIncreaseButton;
        private final TextViewWithMessagesController mTextController;

        public PlaybackViewImpl(ActivityReference activityReference, Context applicationContext,
                View playbackLayout) {
            Preconditions.checkNotNull(activityReference);
            Preconditions.checkNotNull(applicationContext);
            Preconditions.checkNotNull(playbackLayout);
            mActivityReference = activityReference;
            mApplicationContext = applicationContext;
            mPlaybackSeek = (SeekBar) playbackLayout.findViewById(R.id.playback_seek);
            mStartStopButton = (ImageButton) playbackLayout.findViewById(
                    R.id.playback_start_stop);
            mPlaybackSpeakerphone = (ImageButton) playbackLayout.findViewById(
                    R.id.playback_speakerphone);
            mRateDecreaseButton = (ImageButton) playbackLayout.findViewById(
                    R.id.rate_decrease_button);
            mRateIncreaseButton = (ImageButton) playbackLayout.findViewById(
                    R.id.rate_increase_button);
            mTextController = new TextViewWithMessagesController(
                    (TextView) playbackLayout.findViewById(R.id.playback_position_text),
                    (TextView) playbackLayout.findViewById(R.id.playback_speed_text));
            setContentDescriptions();
        }

        private void setContentDescriptions() {
            mStartStopButton.setContentDescription(getString(R.string.play_button_description));
            mPlaybackSpeakerphone.setContentDescription(getString(R.string.speaker_phone_on_description));
            mRateDecreaseButton.setContentDescription(getString(R.string.rate_decrease_description));
            mRateIncreaseButton.setContentDescription(getString(R.string.rate_increase_description));
            mPlaybackSeek.setContentDescription(getString(R.string.playback_seek_description));
        }

        @Override
        public void finish() {
            Activity activity = mActivityReference.get();
            if (activity != null) {
                activity.finish();
            }
        }

        @Override
        public void runOnUiThread(Runnable runnable) {
            Activity activity = mActivityReference.get();
            if (activity != null) {
                activity.runOnUiThread(runnable);
            }
        }

        @Override
        public Context getDataSourceContext() {
            return mApplicationContext;
        }

        @Override
        public void setRateDecreaseButtonListener(View.OnClickListener listener) {
            mRateDecreaseButton.setOnClickListener(listener);
        }

        @Override
        public void setRateIncreaseButtonListener(View.OnClickListener listener) {
            mRateIncreaseButton.setOnClickListener(listener);
        }

        @Override
        public void setStartStopListener(View.OnClickListener listener) {
            mStartStopButton.setOnClickListener(listener);
        }

        @Override
        public void setSpeakerphoneListener(View.OnClickListener listener) {
            mPlaybackSpeakerphone.setOnClickListener(listener);
        }

        @Override
        public void setRateDisplay(float rate, int stringResourceId) {
            mTextController.setTemporaryText(
                    mApplicationContext.getString(stringResourceId), 1, TimeUnit.SECONDS);
        }

        @Override
        public void setPositionSeekListener(SeekBar.OnSeekBarChangeListener listener) {
            mPlaybackSeek.setOnSeekBarChangeListener(listener);
        }

        @Override
        public void playbackStarted() {
            mStartStopButton.setImageResource(R.drawable.ic_hold_pause_holo_dark);
            mStartStopButton.setContentDescription(getString(R.string.stop_button_description));
        }

        @Override
        public void playbackStopped() {
            mStartStopButton.setImageResource(R.drawable.ic_play);
            mStartStopButton.setContentDescription(getString(R.string.play_button_description));
        }

        @Override
        public void enableProximitySensor() {
            // Only change the state if the activity is still around.
            Activity activity = mActivityReference.get();
            if (activity != null && activity instanceof ProximitySensorAware) {
                ((ProximitySensorAware) activity).enableProximitySensor();
            }
        }

        @Override
        public void disableProximitySensor() {
            // Only change the state if the activity is still around.
            Activity activity = mActivityReference.get();
            if (activity != null && activity instanceof ProximitySensorAware) {
                ((ProximitySensorAware) activity).disableProximitySensor(true);
            }
        }

        @Override
        public void registerContentObserver(Uri uri, ContentObserver observer) {
            mApplicationContext.getContentResolver().registerContentObserver(uri, false, observer);
        }

        @Override
        public void unregisterContentObserver(ContentObserver observer) {
            mApplicationContext.getContentResolver().unregisterContentObserver(observer);
        }

        @Override
        public void setClipPosition(int clipPositionInMillis, int clipLengthInMillis) {
            int seekBarPosition = Math.max(0, clipPositionInMillis);
            int seekBarMax = Math.max(seekBarPosition, clipLengthInMillis);
            if (mPlaybackSeek.getMax() != seekBarMax) {
                mPlaybackSeek.setMax(seekBarMax);
            }
            mPlaybackSeek.setProgress(seekBarPosition);
            mTextController.setPermanentText(
                    formatAsMinutesAndSeconds(seekBarMax - seekBarPosition));
        }

        private String getString(int resId) {
            return mApplicationContext.getString(resId);
        }

        @Override
        public void setIsBuffering() {
            disableUiElements();
            mTextController.setPermanentText(getString(R.string.voicemail_buffering));
        }

        @Override
        public void setIsFetchingContent() {
            disableUiElements();
            mTextController.setPermanentText(getString(R.string.voicemail_fetching_content));
        }

        @Override
        public void setFetchContentTimeout() {
            disableUiElements();
            mTextController.setPermanentText(getString(R.string.voicemail_fetching_timout));
        }

        @Override
        public int getDesiredClipPosition() {
            return mPlaybackSeek.getProgress();
        }

        @Override
        public void disableUiElements() {
            mRateIncreaseButton.setEnabled(false);
            mRateDecreaseButton.setEnabled(false);
            mStartStopButton.setEnabled(false);
            mPlaybackSpeakerphone.setEnabled(false);
            mPlaybackSeek.setProgress(0);
            mPlaybackSeek.setEnabled(false);
        }

        @Override
        public void playbackError(Exception e) {
            disableUiElements();
            mTextController.setPermanentText(getString(R.string.voicemail_playback_error));
            Log.e(TAG, "Could not play voicemail", e);
        }

        @Override
        public void enableUiElements() {
            mRateIncreaseButton.setEnabled(true);
            mRateDecreaseButton.setEnabled(true);
            mStartStopButton.setEnabled(true);
            mPlaybackSpeakerphone.setEnabled(true);
            mPlaybackSeek.setEnabled(true);
        }

        @Override
        public void sendFetchVoicemailRequest(Uri voicemailUri) {
            Intent intent = new Intent(VoicemailContract.ACTION_FETCH_VOICEMAIL, voicemailUri);
            mApplicationContext.sendBroadcast(intent);
        }

        @Override
        public boolean queryHasContent(Uri voicemailUri) {
            ContentResolver contentResolver = mApplicationContext.getContentResolver();
            Cursor cursor = contentResolver.query(
                    voicemailUri, HAS_CONTENT_PROJECTION, null, null, null);
            try {
                if (cursor != null && cursor.moveToNext()) {
                    return cursor.getInt(cursor.getColumnIndexOrThrow(
                            VoicemailContract.Voicemails.HAS_CONTENT)) == 1;
                }
            } finally {
                MoreCloseables.closeQuietly(cursor);
            }
            return false;
        }

        private AudioManager getAudioManager() {
            return (AudioManager) mApplicationContext.getSystemService(Context.AUDIO_SERVICE);
        }

        @Override
        public boolean isSpeakerPhoneOn() {
            return getAudioManager().isSpeakerphoneOn();
        }

        @Override
        public void setSpeakerPhoneOn(boolean on) {
            getAudioManager().setSpeakerphoneOn(on);
            if (on) {
                mPlaybackSpeakerphone.setImageResource(R.drawable.ic_speakerphone_on);
                mPlaybackSpeakerphone.setContentDescription(getString(R.string.speaker_phone_on_description));
            } else {
                mPlaybackSpeakerphone.setImageResource(R.drawable.ic_speakerphone_off);
                mPlaybackSpeakerphone.setContentDescription(getString(R.string.speaker_phone_off_description));
            }
        }

        @Override
        public void setVolumeControlStream(int streamType) {
            Activity activity = mActivityReference.get();
            if (activity != null) {
                activity.setVolumeControlStream(streamType);
            }
        }
    }

    /**
     * Controls a TextView with dynamically changing text.
     * <p>
     * There are two methods here of interest,
     * {@link TextViewWithMessagesController#setPermanentText(String)} and
     * {@link TextViewWithMessagesController#setTemporaryText(String, long, TimeUnit)}.  The
     * former is used to set the text on the text view immediately, and is used in our case for
     * the countdown of duration remaining during voicemail playback.  The second is used to
     * temporarily replace this countdown with a message, in our case faster voicemail speed or
     * slower voicemail speed, before returning to the countdown display.
     * <p>
     * All the methods on this class must be called from the ui thread.
     */
    private static final class TextViewWithMessagesController {
        private static final float VISIBLE = 1;
        private static final float INVISIBLE = 0;
        private static final long SHORT_ANIMATION_MS = 200;
        private static final long LONG_ANIMATION_MS = 400;
        private final Object mLock = new Object();
        private final TextView mPermanentTextView;
        private final TextView mTemporaryTextView;
        @GuardedBy("mLock") private Runnable mRunnable;

        public TextViewWithMessagesController(TextView permanentTextView,
                TextView temporaryTextView) {
            mPermanentTextView = permanentTextView;
            mTemporaryTextView = temporaryTextView;
        }

        public void setPermanentText(String text) {
            mPermanentTextView.setText(text);
        }

        public void setTemporaryText(String text, long duration, TimeUnit units) {
            synchronized (mLock) {
                mTemporaryTextView.setText(text);
                mTemporaryTextView.animate().alpha(VISIBLE).setDuration(SHORT_ANIMATION_MS);
                mPermanentTextView.animate().alpha(INVISIBLE).setDuration(SHORT_ANIMATION_MS);
                mRunnable = new Runnable() {
                    @Override
                    public void run() {
                        synchronized (mLock) {
                            // We check for (mRunnable == this) becuase if not true, then another
                            // setTemporaryText call has taken place in the meantime, and this
                            // one is now defunct and needs to take no action.
                            if (mRunnable == this) {
                                mRunnable = null;
                                mTemporaryTextView.animate()
                                        .alpha(INVISIBLE).setDuration(LONG_ANIMATION_MS);
                                mPermanentTextView.animate()
                                        .alpha(VISIBLE).setDuration(LONG_ANIMATION_MS);
                            }
                        }
                    }
                };
                mTemporaryTextView.postDelayed(mRunnable, units.toMillis(duration));
            }
        }
    }
}
