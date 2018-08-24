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

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * An interface for a cache keyed by a String with a byte array as data.
 * Volley 提供了一个 Cache 作为缓存的接口，封装了缓存的实体 Entry，以及一些常规的增删查操作。
 */
public interface Cache {
    /**
     * Retrieves an entry from the cache.
     *
     * @param key Cache key
     * @return An {@link Entry} or null in the event of a cache miss
     */
    Entry get(String key);

    /**
     * Adds or replaces an entry to the cache.
     *
     * @param key   Cache key
     * @param entry Data to store and metadata for cache coherency, TTL, etc.
     */
    void put(String key, Entry entry);

    /**
     * Performs any potentially long-running actions needed to initialize the cache; will be called
     * from a worker thread.
     */
    void initialize();

    /**
     * Invalidates an entry in the cache.
     *
     * @param key        Cache key
     * @param fullExpire True to fully expire the entry, false to soft expire
     */
    void invalidate(String key, boolean fullExpire);

    /**
     * Removes an entry from the cache.
     *
     * @param key Cache key
     */
    void remove(String key);

    /**
     * Empties the cache.
     */
    void clear();

    /**
     * Data and metadata for an entry returned by the cache.
     * Entry 里面主要是放网络响应的原始数据 data、跟缓存相关的属性以及对应的响应头，作为缓存的一个实体。
     */
    class Entry {
        /**
         * The data returned from cache.
         */
        public byte[] data;

        /**
         * ETag for cache coherency.
         */
        public String etag;

        /**
         * Date of this response as reported by the server.
         */
        public long serverDate;

        /**
         * The last modified date for the requested object.
         */
        public long lastModified;

        /**
         * TTL for this record.
         */
        public long ttl;

        /**
         * Soft TTL for this record.
         */
        public long softTtl;

        /**
         * Response headers as received from server; must be non-null. Should not be mutated
         * directly.
         * <p>
         * <p>Note that if the server returns two headers with the same (case-insensitive) name,
         * this map will only contain the one of them. {@link #allResponseHeaders} may contain all
         * headers if the {@link Cache} implementation supports it.
         */
        public Map<String, String> responseHeaders = Collections.emptyMap();

        /**
         * All response headers. May be null depending on the {@link Cache} implementation. Should
         * not be mutated directly.
         */
        public List<Header> allResponseHeaders;

        /**
         * True if the entry is expired.
         */
        /**
         * 判断 Entry 是否过期.
         */
        public boolean isExpired() {
            return this.ttl < System.currentTimeMillis();
        }

        /**
         * True if a refresh is needed from the original data source.
         */
        /**
         * 判断 Entry 是否需要刷新.
         */
        public boolean refreshNeeded() {
            return this.softTtl < System.currentTimeMillis();
        }
    }
}
