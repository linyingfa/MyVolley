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

package com.android.volley;

import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A request dispatch queue with a thread pool of dispatchers.
 * <p>
 * <p>Calling {@link #add(Request)} will enqueue the given Request for dispatch, resolving from
 * either cache or network on a worker thread, and then delivering a parsed response on the main
 * thread.
 */
public class RequestQueue {

    /**
     * Callback interface for completed requests.
     */
    // TODO: This should not be a generic class, because the request type can't be determined at
    // compile time, so all calls to onRequestFinished are unsafe. However, changing this would be
    // an API-breaking change. See also: https://github.com/google/volley/pull/109
    public interface RequestFinishedListener<T> {
        /**
         * Called when a request has finished processing.
         */
        void onRequestFinished(Request<T> request);
    }

    /**
     * Used for generating monotonically-increasing sequence numbers for requests.
     */
    private final AtomicInteger mSequenceGenerator = new AtomicInteger();

    /**
     * The set of all requests currently being processed by this RequestQueue. A Request will be in
     * this set if it is waiting in any queue or currently being processed by any dispatcher.
     */
    private final Set<Request<?>> mCurrentRequests = new HashSet<>();

    /**
     * The cache triage queue.
     * 优先队列
     */
    //优先级队列，缓存队列
    private final PriorityBlockingQueue<Request<?>> mCacheQueue = new PriorityBlockingQueue<>();

    /**
     * The queue of requests that are actually going out to the network.
     */
    //优先级队列，网络队列
    private final PriorityBlockingQueue<Request<?>> mNetworkQueue = new PriorityBlockingQueue<>();

    /**
     * Number of network request dispatcher threads to start.
     */
    private static final int DEFAULT_NETWORK_THREAD_POOL_SIZE = 4;

    /**
     * Cache interface for retrieving and storing responses.
     */
    private final Cache mCache;

    /**
     * Network interface for performing requests.
     */
    private final Network mNetwork;

    /**
     * Response delivery mechanism.
     */
    //响应传递机制。
    private final ResponseDelivery mDelivery;

    /**
     * The network dispatchers.
     */
    //网络调度员。分发器，是一个数组，说明会有多个网络请求
    private final NetworkDispatcher[] mDispatchers;

    /**
     * The cache dispatcher.
     */
    private CacheDispatcher mCacheDispatcher;

    private final List<RequestFinishedListener> mFinishedListeners = new ArrayList<>();

    /**
     * Creates the worker pool. Processing will not begin until {@link #start()} is called.
     *
     * @param cache          A Cache to use for persisting responses to disk
     * @param network        A Network interface for performing HTTP requests
     * @param threadPoolSize Number of network dispatcher threads to create
     * @param delivery       A ResponseDelivery interface for posting responses and errors
     */
    public RequestQueue(Cache cache, Network network, int threadPoolSize, ResponseDelivery delivery) {
        //this(cache, network, threadPoolSize, new ExecutorDelivery(new Handler(Looper.getMainLooper())));
        //父类=子类;多态
        mCache = cache;
        mNetwork = network;
        mDispatchers = new NetworkDispatcher[threadPoolSize];
        mDelivery = delivery;
    }

    /**
     * Creates the worker pool. Processing will not begin until {@link #start()} is called.
     *
     * @param cache          A Cache to use for persisting responses to disk
     * @param network        A Network interface for performing HTTP requests
     * @param threadPoolSize Number of network dispatcher threads to create
     */
    public RequestQueue(Cache cache, Network network, int threadPoolSize) {
        this(cache, network, threadPoolSize, new ExecutorDelivery(new Handler(Looper.getMainLooper())));
    }

    /**
     * Creates the worker pool. Processing will not begin until {@link #start()} is called.
     *
     * @param cache   A Cache to use for persisting responses to disk
     * @param network A Network interface for performing HTTP requests
     */
    public RequestQueue(Cache cache, Network network) {
        this(cache, network, DEFAULT_NETWORK_THREAD_POOL_SIZE);
    }

    //Starts the dispatchers in this queue.
    //开始
    public void start() {
        //先调用 stop() 方法将当前正在进行 Dispatcher 都停掉
        stop(); // Make sure any currently running dispatchers are stopped.
        // Create the cache dispatcher and start it.
        //然后创建了一个 CacheDispatcher 实例，并调用了它的 start() 方法
        //TODO  CacheDispatcher extends Thread
        mCacheDispatcher = new CacheDispatcher(mCacheQueue, mNetworkQueue, mCache, mDelivery);
        mCacheDispatcher.start();
        // Create network dispatchers (and corresponding threads) up to the pool size.
        for (int i = 0; i < mDispatchers.length; i++) {
            //接着在一个循环里去创建 NetworkDispatcher 的实例，分别调用它们的 start() 方法
            //这里的 CacheDispatcher 和 NetworkDispatcher 都是继承自 Thread 的，默认情况下 for 循环会执行四次，
            // 也就是说当调用了 Volley.newRequestQueue(context) 之后，就会有五个线程在后台运行，等待网络请求的到来，
            // 其中 CacheDispatcher 是缓存线程，NetworkDispatcher 是网络请求线程。
            //TODO NetworkDispatcher extends Thread
            NetworkDispatcher networkDispatcher = new NetworkDispatcher(mNetworkQueue, mNetwork, mCache, mDelivery);
            mDispatchers[i] = networkDispatcher;
            networkDispatcher.start();
        }
    }

    //Stops the cache and network dispatchers.
    public void stop() {
        if (mCacheDispatcher != null) {
            mCacheDispatcher.quit();//中断线程
        }
        for (final NetworkDispatcher mDispatcher : mDispatchers) {
            if (mDispatcher != null) {
                mDispatcher.quit();
            }
        }
    }

    /**
     * Gets a sequence number.
     */
    public int getSequenceNumber() {
        return mSequenceGenerator.incrementAndGet();
    }

    /**
     * Gets the {@link Cache} instance being used.
     */
    public Cache getCache() {
        return mCache;
    }

    /**
     * A simple predicate or filter interface for Requests, for use by {@link
     * RequestQueue#cancelAll(RequestFilter)}.
     */
    public interface RequestFilter {
        boolean apply(Request<?> request);
    }

    /**
     * Cancels all requests in this queue for which the given filter applies.
     *
     * @param filter The filtering function to use
     */
    public void cancelAll(RequestFilter filter) {
        //TODO synchronized防止并发啊,防止在取消过程中，有其他线程，修改mCurrentRequests
        synchronized (mCurrentRequests) {
            for (Request<?> request : mCurrentRequests) {
                if (filter.apply(request)) {
                    request.cancel();
                }
            }
        }
    }

    /**
     * Cancels all requests in this queue with the given tag. Tag must be non-null and equality is
     * by identity.
     */
    public void cancelAll(final Object tag) {
        if (tag == null) {
            throw new IllegalArgumentException("Cannot cancelAll with a null tag");
        }
        cancelAll(new RequestFilter() {
            @Override
            public boolean apply(Request<?> request) {
                return request.getTag() == tag;
            }
        });
    }

    /**
     * Adds a Request to the dispatch queue.
     *
     * @param request The request to service
     * @return The passed-in request
     */
    public <T> Request<T> add(Request<T> request) {
        // Tag the request as belonging to this queue and add it to the set of current requests.
        // 将 Request 标记为属于此队列，并将其放入 mCurrentRequests 中
        request.setRequestQueue(this);
        synchronized (mCurrentRequests) {
            mCurrentRequests.add(request);
        }

        // Process requests in the order they are added.
        // 让 Request 按照他们被添加的顺序执行
        request.setSequence(getSequenceNumber());
        request.addMarker("add-to-queue");

        // If the request is uncacheable, skip the cache queue and go straight to the network.
        // 如果请求不需要被缓存，就跳过缓存，直接进行网络请求
        if (!request.shouldCache()) {
            mNetworkQueue.add(request);
            return request;
        }
//        传入 Request 之后，会先判断该 Request 是否需要进行缓存，如果不需要就直接将其加入到网络请求队列，
//        需要缓存则加入缓存队列。默认情况下，每条请求都是应该缓存的，当然我们也可以调用 Request 的 setShouldCache() 方法来进行设置。
        mCacheQueue.add(request);
        return request;
    }

    /**
     * Called from {@link Request#finish(String)}, indicating that processing of the given request
     * has finished.
     */
    @SuppressWarnings("unchecked")
    // see above note on RequestFinishedListener
    <T> void finish(Request<T> request) {
        // Remove from the set of requests currently being processed.
        synchronized (mCurrentRequests) {
            mCurrentRequests.remove(request);
        }
        synchronized (mFinishedListeners) {
            for (RequestFinishedListener<T> listener : mFinishedListeners) {
                listener.onRequestFinished(request);
            }
        }
    }

    public <T> void addRequestFinishedListener(RequestFinishedListener<T> listener) {
        synchronized (mFinishedListeners) {
            mFinishedListeners.add(listener);
        }
    }

    /**
     * Remove a RequestFinishedListener. Has no effect if listener was not previously added.
     */
    public <T> void removeRequestFinishedListener(RequestFinishedListener<T> listener) {
        synchronized (mFinishedListeners) {
            mFinishedListeners.remove(listener);
        }
    }
}
