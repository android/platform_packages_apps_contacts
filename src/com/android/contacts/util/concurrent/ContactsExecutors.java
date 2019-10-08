// Copyright 2016 Google Inc. All Rights Reserved.
package com.android.contacts.util.concurrent;

import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;

import com.google.common.util.concurrent.ForwardingFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;

import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Callable;
import java.util.concurrent.Delayed;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RunnableScheduledFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Provides some common executors for use with {@link Futures}
 */
public class ContactsExecutors {

    private ContactsExecutors() {}

    private static final int CPU_COUNT = Runtime.getRuntime().availableProcessors();
    private static final int CORE_POOL_SIZE = CPU_COUNT + 1;

    // AsyncTask.THREAD_POOL_EXECUTOR is a ThreadPoolExecutor so we should end up always using that
    // but we have a fallback in case the platform implementation changes in some future release.
    private static final ListeningExecutorService DEFAULT_THREAD_POOL_EXECUTOR =
            (AsyncTask.THREAD_POOL_EXECUTOR instanceof ExecutorService) ?
                    MoreExecutors.listeningDecorator(
                            (ExecutorService) AsyncTask.THREAD_POOL_EXECUTOR) :
                    MoreExecutors.listeningDecorator(
                            Executors.newFixedThreadPool(CORE_POOL_SIZE));

    // We initialize this lazily since in some cases we may never even read from the SIM card
    private static ListeningExecutorService sSimExecutor;

    /**
     * Returns the default thread pool that can be used for background work.
     */
    public static ListeningExecutorService getDefaultThreadPoolExecutor() {
        return DEFAULT_THREAD_POOL_EXECUTOR;
    }

    /**
     * Creates an executor that runs commands on the application UI thread
     */
    public static ScheduledExecutorService newUiThreadExecutor() {
        return newHandlerExecutor(new Handler(Looper.getMainLooper()));
    }

    /**
     * Create an executor that posts commands to the provided handler
     */
    public static ScheduledExecutorService newHandlerExecutor(final Handler handler) {
        return new HandlerExecutorService(handler);
    }

    /**
     * Returns an ExecutorService that can be used to read from the SIM card.
     *
     * <p>See b/32831092</p>
     * <p>A different executor than {@link ContactsExecutors#getDefaultThreadPoolExecutor()} is
     * provided for this case because reads of the SIM card can block for long periods of time
     * and if they do we might exhaust our thread pool. Additionally it appears that reading from
     * the SIM provider from multiple threads concurrently can cause problems.
     * </p>
     */
    public synchronized static ListeningExecutorService getSimReadExecutor() {
        if (sSimExecutor == null) {
            final ThreadPoolExecutor executor = new ThreadPoolExecutor(
                    1, 1, 30, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());
            executor.allowCoreThreadTimeOut(true);
            sSimExecutor = MoreExecutors.listeningDecorator(executor);
        }
        return sSimExecutor;
    }

    /**
     * Wrapper around a handler that implements a subset of the ScheduledExecutorService
     *
     * <p>This class is useful for testability because Handler can't be mocked since it's
     * methods are final. It might be better to just use Executors.newSingleThreadScheduledExecutor
     * in the cases where we need to run some time based tasks.
     * </p>
     */
    private static class HandlerExecutorService extends AbstractExecutorService
            implements ScheduledExecutorService {
        private final Handler mHandler;

        private HandlerExecutorService(Handler handler) {
            mHandler = handler;
        }

        @NonNull
        @Override
        public ScheduledFuture<?> schedule(final Runnable command, long delay, TimeUnit unit) {
            final HandlerFuture<Void> future = HandlerFuture
                    .fromRunnable(mHandler, delay, unit, command);
            mHandler.postDelayed(future, unit.toMillis(delay));
            return future;
        }

        @NonNull
        @Override
        public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
            final HandlerFuture<V> future = new HandlerFuture<>(mHandler, delay, unit, callable);
            mHandler.postDelayed(future, unit.toMillis(delay));
            return future;
        }

        @NonNull
        @Override
        public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay,
                long period, TimeUnit unit) {
            throw new UnsupportedOperationException();
        }

        @NonNull
        @Override
        public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay,
                long delay, TimeUnit unit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void shutdown() {
        }

        @Override
        public List<Runnable> shutdownNow() {
            return null;
        }

        @Override
        public boolean isShutdown() {
            return false;
        }

        @Override
        public boolean isTerminated() {
            return false;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void execute(Runnable command) {
            mHandler.post(command);
        }
    }

    private static class HandlerFuture<T> extends ForwardingFuture<T> implements
            RunnableScheduledFuture<T> {

        private final Handler mHandler;
        private final long mDelayMillis;
        private final Callable<T> mTask;
        private final SettableFuture<T> mDelegate = SettableFuture.create();

        private final AtomicLong mStart = new AtomicLong(-1);

        private HandlerFuture(Handler handler, long delay, TimeUnit timeUnit, Callable<T> task) {
            mHandler = handler;
            mDelayMillis = timeUnit.toMillis(delay);
            mTask = task;
        }

        @Override
        public boolean isPeriodic() {
            return false;
        }

        @Override
        public long getDelay(TimeUnit unit) {
            long start = mStart.get();
            if (start < 0) {
                return mDelayMillis;
            }
            long remaining = mDelayMillis - (System.currentTimeMillis() - start);
            return TimeUnit.MILLISECONDS.convert(remaining, unit);
        }

        @Override
        public int compareTo(Delayed o) {
            return Long.compare(getDelay(TimeUnit.MILLISECONDS),
                    o.getDelay(TimeUnit.MILLISECONDS));
        }

        @Override
        protected Future<T> delegate() {
            return mDelegate;
        }

        @Override
        public boolean cancel(boolean b) {
            mHandler.removeCallbacks(this);
            return super.cancel(b);
        }

        @Override
        public void run() {
            if (!mStart.compareAndSet(-1, System.currentTimeMillis())) {
                // Already started
                return;
            }
            try {
                mDelegate.set(mTask.call());
            } catch (Exception e) {
                mDelegate.setException(e);
            }
        }

        public static HandlerFuture<Void> fromRunnable(Handler handler, long delay, TimeUnit unit,
                final Runnable command) {
            return new HandlerFuture<>(handler, delay, unit, new Callable<Void>() {
                @Override
                public Void call() throws Exception {
                    command.run();
                    return null;
                }
            });
        }
    }
}
