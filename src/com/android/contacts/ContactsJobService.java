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

import android.app.job.JobParameters;
import android.app.job.JobService;

/**
 * Service to run {@link android.app.job.JobScheduler} jobs.
 */
public class ContactsJobService extends JobService {

    public static final int DYNAMIC_SHORTCUTS_JOB_ID = 1;

    @Override
    public boolean onStartJob(JobParameters jobParameters) {
        switch (jobParameters.getJobId()) {
            case DYNAMIC_SHORTCUTS_JOB_ID:
                DynamicShortcuts.updateFromJob(this, jobParameters);
                return true;
            default:
                break;
        }
        return false;
    }

    @Override
    public boolean onStopJob(JobParameters jobParameters) {
        return false;
    }
}
