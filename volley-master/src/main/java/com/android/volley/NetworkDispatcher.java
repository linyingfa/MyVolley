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

import android.annotation.TargetApi;
import android.net.TrafficStats;
import android.os.Build;
import android.os.Process;
import android.os.SystemClock;
import android.support.annotation.VisibleForTesting;

import java.util.concurrent.BlockingQueue;

/**
 * Provides a thread for performing network dispatch from a queue of requests.
 * <p>
 * <p>Requests added to the specified queue are processed from the network via a specified {@link
 * Network} interface. Responses are committed to cache, if eligible, using a specified {@link
 * Cache} interface. Valid responses and errors are posted back to the caller via a {@link
 * ResponseDelivery}.
 */
public class NetworkDispatcher extends Thread {

    /**
     * The queue of requests to service.
     */
    private final BlockingQueue<Request<?>> mQueue;
    /**
     * The network interface for processing requests.
     */
    private final Network mNetwork;
    /**
     * The cache to write to.
     */
    private final Cache mCache;
    /**
     * For posting responses and errors.
     */
    private final ResponseDelivery mDelivery;
    /**
     * Used for telling us to die.
     */
    private volatile boolean mQuit = false;

    /**
     * Creates a new network dispatcher thread. You must call {@link #start()} in order to begin
     * processing.
     *
     * @param queue    Queue of incoming requests for triage
     * @param network  Network interface to use for performing requests
     * @param cache    Cache interface to use for writing responses to cache
     * @param delivery Delivery interface to use for posting responses
     */
    public NetworkDispatcher(BlockingQueue<Request<?>> queue, Network network, Cache cache, ResponseDelivery delivery) {
        mQueue = queue;
        mNetwork = network;
        mCache = cache;
        mDelivery = delivery;
    }

    /**
     * Forces this dispatcher to quit immediately. If any requests are still in the queue, they are
     * not guaranteed to be processed.
     */
    public void quit() {
        mQuit = true;
        interrupt();
    }

    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    private void addTrafficStatsTag(Request<?> request) {
        // Tag the request (if API >= 14)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            TrafficStats.setThreadStatsTag(request.getTrafficStatsTag());
        }
    }

    @Override
    public void run() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
        while (true) {
            try {
                /**
                 * 在 NetworkDispatcher 同样使用了 while(true)，说明网络请求线程也是不断运行的。
                 * 然后从网络队列里面取出 Request，再调用 Network 的 performRequest() 方法去发送网络请求。
                 * Network 其实是一个接口，这里具体的实现是 BasicNetwork,看下它的 performRequest() 方法实现
                 */
                processRequest();
            } catch (InterruptedException e) {
                // We may have been interrupted because it was time to quit.
                if (mQuit) {
                    return;
                }
            }
        }
    }

    // Extracted to its own method to ensure locals have a constrained liveness scope by the GC.
    // This is needed to avoid keeping previous request references alive for an indeterminate amount
    // of time. Update consumer-proguard-rules.pro when modifying this. See also
    // https://github.com/google/volley/issues/114
    private void processRequest() throws InterruptedException {
        // Take a request from the queue.
        Request<?> request = mQueue.take();
        processRequest(request);
    }

    @VisibleForTesting
    void processRequest(Request<?> request) {//process处理
        long startTimeMs = SystemClock.elapsedRealtime();
        try {
            request.addMarker("network-queue-take");
            // If the request was cancelled already, do not perform the
            // network request.
            // 如果 Request 已经取消了，那就不执行网络请求
            if (request.isCanceled()) {
                request.finish("network-discard-cancelled");
                request.notifyListenerResponseNotUsable();
                return;
            }

            addTrafficStatsTag(request);

            // Perform the network request.
            // 执行网络请求
            NetworkResponse networkResponse = mNetwork.performRequest(request);
            request.addMarker("network-http-complete");
            // If the server returned 304 AND we delivered a response already,
            // we're done -- don't deliver a second identical response.
            // 如果服务器返回 304，而且我们已经分发过该 Request 的结果，那就不用进行第二次分发了
            //（这里补充一下，304 代表服务器上的结果跟上次访问的结果是一样的，也就是说数据没有变化）
            if (networkResponse.notModified && request.hasHadResponseDelivered()) {
                request.finish("not-modified");
                request.notifyListenerResponseNotUsable();
                return;
            }
            // Parse the response here on the worker thread.
            // 在子线程解析返回的结果
            Response<?> response = request.parseNetworkResponse(networkResponse);
            request.addMarker("network-parse-complete");
            // Write to cache if applicable.
            // TODO: Only update cache metadata instead of entire record for 304s.
            if (request.shouldCache() && response.cacheEntry != null) {
                mCache.put(request.getCacheKey(), response.cacheEntry);
                request.addMarker("network-cache-written");
            }
            // Post the response back.
            // 分发响应结果
            request.markDelivered();
            mDelivery.postResponse(request, response);
            request.notifyListenerResponseReceived(response);
        } catch (VolleyError volleyError) {
            volleyError.setNetworkTimeMs(SystemClock.elapsedRealtime() - startTimeMs);
            parseAndDeliverNetworkError(request, volleyError);
            request.notifyListenerResponseNotUsable();
        } catch (Exception e) {
            VolleyLog.e(e, "Unhandled exception %s", e.toString());
            VolleyError volleyError = new VolleyError(e);
            volleyError.setNetworkTimeMs(SystemClock.elapsedRealtime() - startTimeMs);
            mDelivery.postError(request, volleyError);
            request.notifyListenerResponseNotUsable();
        }
    }

    private void parseAndDeliverNetworkError(Request<?> request, VolleyError error) {
        error = request.parseNetworkError(error);
        mDelivery.postError(request, error);
    }
}
