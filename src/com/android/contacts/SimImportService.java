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

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.OperationApplicationException;
import android.os.AsyncTask;
import android.os.IBinder;
import android.os.RemoteException;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import android.util.TimingLogger;

import com.android.contacts.activities.PeopleActivity;
import com.android.contacts.database.SimContactDao;
import com.android.contacts.model.SimCard;
import com.android.contacts.model.SimContact;
import com.android.contacts.model.account.AccountWithDataSet;
import com.android.contacts.util.ContactsNotificationChannelsUtil;
import com.android.contactsbind.FeedbackHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Imports {@link SimContact}s from a background thread
 */
public class SimImportService extends Service {

    private static final String TAG = "SimImportService";

    /**
     * Wrapper around the service state for testability
     */
    public interface StatusProvider {

        /**
         * Returns whether there is any imports still pending
         *
         * <p>This should be called from the UI thread</p>
         */
        boolean isRunning();

        /**
         * Returns whether an import for sim has been requested
         *
         * <p>This should be called from the UI thread</p>
         */
        boolean isImporting(SimCard sim);
    }

    public static final String EXTRA_ACCOUNT = "account";
    public static final String EXTRA_SIM_CONTACTS = "simContacts";
    public static final String EXTRA_SIM_SUBSCRIPTION_ID = "simSubscriptionId";
    public static final String EXTRA_RESULT_CODE = "resultCode";
    public static final String EXTRA_RESULT_COUNT = "count";
    public static final String EXTRA_OPERATION_REQUESTED_AT_TIME = "requestedTime";

    public static final String BROADCAST_SERVICE_STATE_CHANGED =
            SimImportService.class.getName() + "#serviceStateChanged";
    public static final String BROADCAST_SIM_IMPORT_COMPLETE =
            SimImportService.class.getName() + "#simImportComplete";

    public static final int RESULT_UNKNOWN = 0;
    public static final int RESULT_SUCCESS = 1;
    public static final int RESULT_FAILURE = 2;

    // VCardService uses jobIds for it's notifications which count up from 0 so we just use a
    // bigger number to prevent overlap.
    private static final int NOTIFICATION_ID = 100;

    private ExecutorService mExecutor = Executors.newSingleThreadExecutor();

    // Keeps track of current tasks. This is only modified from the UI thread.
    private static List<ImportTask> sPending = new ArrayList<>();

    private static StatusProvider sStatusProvider = new StatusProvider() {
        @Override
        public boolean isRunning() {
            return !sPending.isEmpty();
        }

        @Override
        public boolean isImporting(SimCard sim) {
            return SimImportService.isImporting(sim);
        }
    };

    /**
     * Returns whether an import for sim has been requested
     *
     * <p>This should be called from the UI thread</p>
     */
    private static boolean isImporting(SimCard sim) {
        for (ImportTask task : sPending) {
            if (task.getSim().equals(sim)) {
                return true;
            }
        }
        return false;
    }

    public static StatusProvider getStatusProvider() {
        return sStatusProvider;
    }

    /**
     * Starts an import of the contacts from the sim into the target account
     *
     * @param context context to use for starting the service
     * @param subscriptionId the subscriptionId of the SIM card that is being imported. See
     *                       {@link android.telephony.SubscriptionInfo#getSubscriptionId()}.
     *                       Upon completion the SIM for that subscription ID will be marked as
     *                       imported
     * @param contacts the contacts to import
     * @param targetAccount the account import the contacts into
     */
    public static void startImport(Context context, int subscriptionId,
            ArrayList<SimContact> contacts, AccountWithDataSet targetAccount) {
        context.startService(new Intent(context, SimImportService.class)
                .putExtra(EXTRA_SIM_CONTACTS, contacts)
                .putExtra(EXTRA_SIM_SUBSCRIPTION_ID, subscriptionId)
                .putExtra(EXTRA_ACCOUNT, targetAccount));
    }


    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, final int startId) {
        ContactsNotificationChannelsUtil.createDefaultChannel(this);
        final ImportTask task = createTaskForIntent(intent, startId);
        if (task == null) {
            new StopTask(this, startId).executeOnExecutor(mExecutor);
            return START_NOT_STICKY;
        }
        sPending.add(task);
        task.executeOnExecutor(mExecutor);
        notifyStateChanged();
        return START_REDELIVER_INTENT;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mExecutor.shutdown();
    }

    private ImportTask createTaskForIntent(Intent intent, int startId) {
        final AccountWithDataSet targetAccount = intent.getParcelableExtra(EXTRA_ACCOUNT);
        final ArrayList<SimContact> contacts =
                intent.getParcelableArrayListExtra(EXTRA_SIM_CONTACTS);

        final int subscriptionId = intent.getIntExtra(EXTRA_SIM_SUBSCRIPTION_ID,
                SimCard.NO_SUBSCRIPTION_ID);
        final SimContactDao dao = SimContactDao.create(this);
        final SimCard sim = dao.getSimBySubscriptionId(subscriptionId);
        if (sim != null) {
            return new ImportTask(sim, contacts, targetAccount, dao, startId);
        } else {
            return null;
        }
    }

    private Notification getCompletedNotification() {
        final Intent intent = new Intent(this, PeopleActivity.class);
        final NotificationCompat.Builder builder = new NotificationCompat.Builder(
                this, ContactsNotificationChannelsUtil.DEFAULT_CHANNEL);
        builder.setOngoing(false)
                .setAutoCancel(true)
                .setContentTitle(this.getString(R.string.importing_sim_finished_title))
                .setColor(this.getResources().getColor(R.color.dialtacts_theme_color))
                .setSmallIcon(R.drawable.quantum_ic_done_vd_theme_24)
                .setContentIntent(PendingIntent.getActivity(this, 0, intent, 0));
        return builder.build();
    }

    private Notification getFailedNotification() {
        final Intent intent = new Intent(this, PeopleActivity.class);
        final NotificationCompat.Builder builder = new NotificationCompat.Builder(
                this, ContactsNotificationChannelsUtil.DEFAULT_CHANNEL);
        builder.setOngoing(false)
                .setAutoCancel(true)
                .setContentTitle(this.getString(R.string.importing_sim_failed_title))
                .setContentText(this.getString(R.string.importing_sim_failed_message))
                .setColor(this.getResources().getColor(R.color.dialtacts_theme_color))
                .setSmallIcon(R.drawable.quantum_ic_error_vd_theme_24)
                .setContentIntent(PendingIntent.getActivity(this, 0, intent, 0));
        return builder.build();
    }

    private Notification getImportingNotification() {
        final NotificationCompat.Builder builder = new NotificationCompat.Builder(
                this, ContactsNotificationChannelsUtil.DEFAULT_CHANNEL);
        final String description = getString(R.string.importing_sim_in_progress_title);
        builder.setOngoing(true)
                .setProgress(/* current */ 0, /* max */ 100, /* indeterminate */ true)
                .setContentTitle(description)
                .setColor(this.getResources().getColor(R.color.dialtacts_theme_color))
                .setSmallIcon(android.R.drawable.stat_sys_download);
        return builder.build();
    }

    private void notifyStateChanged() {
        LocalBroadcastManager.getInstance(this).sendBroadcast(
                new Intent(BROADCAST_SERVICE_STATE_CHANGED));
    }

    // Schedule a task that calls stopSelf when it completes. This is used to ensure that the
    // calls to stopSelf occur in the correct order (because this service uses a single thread
    // executor this won't run until all work that was requested before it has finished)
    private static class StopTask extends AsyncTask<Void, Void, Void> {
        private Service mHost;
        private final int mStartId;

        private StopTask(Service host, int startId) {
            mHost = host;
            mStartId = startId;
        }

        @Override
        protected Void doInBackground(Void... params) {
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
            mHost.stopSelf(mStartId);
        }
    }

    private class ImportTask extends AsyncTask<Void, Void, Boolean> {
        private final SimCard mSim;
        private final List<SimContact> mContacts;
        private final AccountWithDataSet mTargetAccount;
        private final SimContactDao mDao;
        private final NotificationManager mNotificationManager;
        private final int mStartId;
        private final long mStartTime;

        public ImportTask(SimCard sim, List<SimContact> contacts, AccountWithDataSet targetAccount,
                SimContactDao dao, int startId) {
            mSim = sim;
            mContacts = contacts;
            mTargetAccount = targetAccount;
            mDao = dao;
            mNotificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            mStartId = startId;
            mStartTime = System.currentTimeMillis();
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            startForeground(NOTIFICATION_ID, getImportingNotification());
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            final TimingLogger timer = new TimingLogger(TAG, "import");
            try {
                // Just import them all at once.
                // Experimented with using smaller batches (e.g. 25 and 50) so that percentage
                // progress could be displayed however this slowed down the import by over a factor
                // of 2. If the batch size is over a 100 then most cases will only require a single
                // batch so we don't even worry about displaying accurate progress
                mDao.importContacts(mContacts, mTargetAccount);
                mDao.persistSimState(mSim.withImportedState(true));
                timer.addSplit("done");
                timer.dumpToLog();
            } catch (RemoteException|OperationApplicationException e) {
                FeedbackHelper.sendFeedback(SimImportService.this, TAG,
                        "Failed to import contacts from SIM card", e);
                return false;
            }
            return true;
        }

        public SimCard getSim() {
            return mSim;
        }

        @Override
        protected void onPostExecute(Boolean success) {
            super.onPostExecute(success);
            stopSelf(mStartId);

            Intent result;
            final Notification notification;
            if (success) {
                result = new Intent(BROADCAST_SIM_IMPORT_COMPLETE)
                        .putExtra(EXTRA_RESULT_CODE, RESULT_SUCCESS)
                        .putExtra(EXTRA_RESULT_COUNT, mContacts.size())
                        .putExtra(EXTRA_OPERATION_REQUESTED_AT_TIME, mStartTime)
                        .putExtra(EXTRA_SIM_SUBSCRIPTION_ID, mSim.getSubscriptionId());

                notification = getCompletedNotification();
            } else {
                result = new Intent(BROADCAST_SIM_IMPORT_COMPLETE)
                        .putExtra(EXTRA_RESULT_CODE, RESULT_FAILURE)
                        .putExtra(EXTRA_OPERATION_REQUESTED_AT_TIME, mStartTime)
                        .putExtra(EXTRA_SIM_SUBSCRIPTION_ID, mSim.getSubscriptionId());

                notification = getFailedNotification();
            }
            LocalBroadcastManager.getInstance(SimImportService.this).sendBroadcast(result);

            sPending.remove(this);

            // Only notify of completion if all the import requests have finished. We're using
            // the same notification for imports so in the rare case that a user has started
            // multiple imports the notification won't go away until all of them complete.
            if (sPending.isEmpty()) {
                stopForeground(false);
                mNotificationManager.notify(NOTIFICATION_ID, notification);
            }
            notifyStateChanged();
        }
    }
}
