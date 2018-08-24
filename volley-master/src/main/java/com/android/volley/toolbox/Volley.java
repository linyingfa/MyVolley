/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.volley.toolbox;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.http.AndroidHttpClient;
import android.os.Build;

import com.android.volley.Network;
import com.android.volley.RequestQueue;

import java.io.File;


/**
 * 对外暴露的 API，主要作用是构建 RequestQueue
 */
public class Volley {

    /**
     * Default on-disk cache directory.
     */
    private static final String DEFAULT_CACHE_DIR = "volley";

    /**
     * Creates a default instance of the worker pool and calls {@link RequestQueue#start()} on it.
     * 这个方法中先判断 stack 是否为 null，如果是的话，这里会根据 Android 手机的系统版本号来进行相应的处理，
     * 当 SDK >= 9，则创建一个 HurlStack 实例，否则创建一个 HttpClientStack 实例，
     * 实际上 HurlStack 内部使用的是 HttpURLConnction 进行网络请求，而 HttpClientStack 则是使用 HttpClient 进行网络请求，
     * 这里之所以要这么处理，主要是因为在 Android  2.3（SDK = 9）之前，HttpURLConnection 存在一个很严重的问题，
     * 所以这时候用 HttpClient 来进行网络请求会比较合适
     *
     * @param context A {@link Context} to use for creating the cache dir.
     * @param stack   A {@link BaseHttpStack} to use for the network, or null for default.
     * @return A started {@link RequestQueue} instance.
     */
    public static RequestQueue newRequestQueue(Context context, BaseHttpStack stack) {//TODO 2
        BasicNetwork network;
        if (stack == null) {//这个方法中先判断 stack 是否为 null，如果是的话，这里会根据 Android 手机的系统版本号来进行相应的处理，
            if (Build.VERSION.SDK_INT >= 9) {
                //当 SDK >= 9，则创建一个 HurlStack 实例，否则创建一个 HttpClientStack 实例，
                //TODO  实际上 HurlStack 内部使用的是 HttpURLConnction 进行网络请求
                network = new BasicNetwork(new HurlStack());
            } else {
                // Prior to Gingerbread, HttpUrlConnection was unreliable.
                // See: http://android-developers.blogspot.com/2011/09/androids-http-clients.html
                // At some point in the future we'll move our minSdkVersion past Froyo and can
                // delete this fallback (along with all Apache HTTP code).
                String userAgent = "volley/0";
                try {
                    String packageName = context.getPackageName();
                    PackageInfo info = context.getPackageManager().getPackageInfo(packageName, /* flags= */ 0);
                    userAgent = packageName + "/" + info.versionCode;
                } catch (NameNotFoundException e) {
                }
                //而 HttpClientStack 则是使用 HttpClient 进行网络请求
                network = new BasicNetwork(new HttpClientStack(AndroidHttpClient.newInstance(userAgent)));
            }
        } else {//TODO stack !=null
            network = new BasicNetwork(stack);
        }

        //拿到 Stack 的实例之后将其构建成一个 Network 对象，它是用于根据传入的 Stack 对象来处理网络请求的
        return newRequestQueue(context, network);
    }

    /**
     * Creates a default instance of the worker pool and calls {@link RequestQueue#start()} on it.
     *
     * @param context A {@link Context} to use for creating the cache dir.
     * @param stack   An {@link HttpStack} to use for the network, or null for default.
     * @return A started {@link RequestQueue} instance.
     * @deprecated Use {@link #newRequestQueue(Context, BaseHttpStack)} instead to avoid depending
     * on Apache HTTP. This method may be removed in a future release of Volley.
     */
    @Deprecated
    @SuppressWarnings("deprecation")
    public static RequestQueue newRequestQueue(Context context, HttpStack stack) {//TODO 2
        if (stack == null) {
            return newRequestQueue(context, (BaseHttpStack) null);
        }
        return newRequestQueue(context, new BasicNetwork(stack));
    }

    /**
     * 拿到 Stack 的实例之后将其构建成一个 Network 对象，
     * 它是用于根据传入的 Stack 对象来处理网络请求的，
     * 紧接着构建出一个 RequestQueue 对象，并调用 start() 方法。
     */
    private static RequestQueue newRequestQueue(Context context, Network network) {// TODO 3
        //可以看到我们先通过 context.getCacheDir() 获取缓存路径，
        // 然后创建我们缓存所需的目录 cacheDir，这其实就是在 DiskBaseCache 中的 mRootDirectory
        File cacheDir = new File(context.getCacheDir(), DEFAULT_CACHE_DIR);
        //然后将其传入 DiskBaseCache 只有一个参数的构造器中，创建了 DiskBaseCache 的实例，默认的内存缓存空间是 5M.
        RequestQueue queue = new RequestQueue(new DiskBasedCache(cacheDir), network);
        queue.start();
        return queue;
    }

    /**
     * Creates a default instance of the worker pool and calls {@link RequestQueue#start()} on it.
     *
     * @param context A {@link Context} to use for creating the cache dir.
     * @return A started {@link RequestQueue} instance.
     */
    public static RequestQueue newRequestQueue(Context context) {// TODO 1
        //这个方法只有一行代码，只是调用了 newRequestQueue() 的方法重载，并给第二个参数传入 null，
        // 那我们看下带有两个参数的 newRequestQueue 方法中的代码
        return newRequestQueue(context, (BaseHttpStack) null);
    }
}
