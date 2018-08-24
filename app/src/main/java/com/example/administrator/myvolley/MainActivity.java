package com.example.administrator.myvolley;

import android.graphics.Bitmap;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.ImageView;

import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.ImageRequest;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;


public class MainActivity extends AppCompatActivity {

    public static class Run implements Runnable {
        private Student student;

        public void setStudent(Student student) {
            this.student = student;
        }

        @Override
        public void run() {
            synchronized (student) {
                for (int i = 0; i < 5; i++) {
                    System.out.println(Thread.currentThread().getName() + " synchronized loop " + i);
                }
            }
        }
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Run t1 = new Run();
        Student student1 = new Student("A");
        Student student2 = new Student("B");
        Thread ta = new Thread(t1, "A");
        Thread tb = new Thread(t1, "B");
        ta.start();
        tb.start();

    }


    //        Volley 中比较重要的类,在这先把 Volley 中比较重要的类说一下，到时候看源码能更加明白：
//        类名                  作用
//        Volley                对外暴露的 API，主要作用是构建 RequestQueue
//        Request               所有网络请求的抽象类，StringRequest、JsonRequest、ImageRequest 都是它的子类
//        RequestQueue          存放请求的队列，里面包括 CacheDispatcher、NetworkDispatcher 和 ResponseDelivery
//        Response              封装一个解析后的结果以便分发
//        CacheDispatcher       用于执行缓存队列请求的线程
//        NetworkDispatcher     用户执行网络队列请求的线程
//        Cache                 缓存请求结果，Volley 默认使用的是基于 sdcard 的 DiskBaseCache
//        HttpStack             处理 Http 请求，并返回请求结果
//        Network               调用 HttpStack 处理请求，并将结果转换成可被 ResponseDelivery 处理的 NetworkResponse
//        ResponseDelivery      返回结果的分发接口
    private void showUseVolley() {
        /**

         1.需要注意的是在我标注的第一个地方，调用了 Stack 的 executeRequest() 方法，
         这里的 Stack 就是之前调用 Volley.newRequestQueue() 所创建的实例，
         前面也说过了这个对象的内部是使用了 HttpURLConnection 或 HttpClient（已弃用）来进行网络请求。
         网络请求结束后将返回的数据封装成一个 NetworkResponse 对象进行返回。

         2.在 NetworkDispatcher 接收到了这个 NetworkResponse 对象之后，又会调用 Request 的 parseNetworkResponse() 方法来对结果进行解析，
         然后将数据写入到缓存，最后调用 ExecutorDelivery 的 postResponse() 方法来回调解析后的数据，如下所示：

         @Override public void postResponse(Request<?> request, Response<?> response, Runnable runnable) {
         request.markDelivered();
         request.addMarker("post-response");
         mResponsePoster.execute(new ResponseDeliveryRunnable(request, response, runnable));
         }

         在 mResponsePoster（一个 Executor 的实例对象） 的 execute() 方法中传入了一个 ResponseDeliveryRunnable 对象，
         execute() 方法默认是在主线程中执行的，这样就保证了 ResponseDeliveryRunnable 的 run() 方法也是在主线程当中运行的，
         我们看下 run() 方法里面的逻辑：

         @SuppressWarnings("unchecked")
         @Override
         //         public void run() {
         //             // 如果 Request 被取消了，调用 finish() 方法，结束该请求，不进行传递
         //             if (mRequest.isCanceled()) {
         //                mRequest.finish("canceled-at-delivery");
         //                return;
         //             }
         //             if (mResponse.isSuccess()) { // 根据响应的结果来进行不同的分发
         //                 mRequest.deliverResponse(mResponse.result);
         //             } else {
         //             mRequest.deliverError(mResponse.error);
         //             }
         //             if (mRunnable != null) {  // 如果传入的 mRunnable 不为 null，则运行
         //             mRunnable.run();
         //            }
         //         }


         可以看到当 Response.isSuccess() 为 true 的话，调用 Resquest 的 deliverResponse() 方法，
         对结果进行回调，deliverResponse() 方法是每一个具体的 Request 子类都必须实现的抽象类，
         来看下我们最熟悉的 StringRequest 中的 deliverResponse() 方法



         @Override
         //         protected void deliverResponse(String response) {
         //            Response.Listener<String> listener;
         //             synchronized (mLock) {
         //             listener = mListener;
         //             }
         //             if (listener != null) {
         //            listener.onResponse(response);
         //            }
         //         }

         ，在 deliverResponse() 方法中，调用 listener.onResponse() 方法进行回调，
         这个 listener 正是我们构建 StringRequest 时传入的 Listener，也就是说将返回的结果回调到我们在外部调用的地方。

         //         StringRequest stringRequest = new StringRequest(url, new Response.Listener<String>() {
         //             @Override
         //             public void onResponse(String s) {
         //                 // TODO：
         //             }
         //         }, new Response.ErrorListener() {
         //             @Override
         //             public void onErrorResponse(VolleyError error) {
         //             // TODO：
         //             }
         //         });


         */

        //1、通过 Volley.newRequestQueue(Context) 获取一个 RequestQueue
        RequestQueue requestQueue = Volley.newRequestQueue(this);
        //2、传入 URL 构建 Request，并实现相应的回调
        StringRequest stringRequest = new StringRequest("", new Response.Listener<String>() {
            @Override
            public void onResponse(String s) {
                // TODO：
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                // TODO：
            }
        });
        //3、将 Request 加入到 RequestQueue 中
        //Request 被添加到缓存队列中后，在后台等待的缓存线程就要开始运行起来了,看下 CacheDispatcher 的 run()
        requestQueue.add(stringRequest);
    }


    private void showUseVolleyImageView() {

        //ImageRequest 和 StringRequest 以及 JsonRequest 都是继承自 Request，
        // 因此他们的用法也基本是相同的，首先需要获取一个 RequestQueue 对象：
        RequestQueue mQueue = Volley.newRequestQueue(this);
        //1、图片的 URL 地址
        String URL = "http://ww4.sinaimg.cn/large/610dc034gw1euxdmjl7j7j20r2180wts.jpg";
        //2、图片请求成功的回调，这里我们将返回的 Bitmap 设置到 ImageView 中
        ImageRequest imageRequest = new ImageRequest(URL, new Response.Listener<Bitmap>() {
            @Override
            public void onResponse(Bitmap response) {
//                imageView.setImageBitmap(response);
            }//3、4 分别用于指定允许图片最大的宽度和高度，如果指定的网络图片的宽度或高度大于这里的值，
            // 就会对图片进行压缩，指定为 0 的话，表示不管图片有多大，都不进行压缩
        }, 0, 0, ImageView.ScaleType.CENTER_CROP, Bitmap.Config.RGB_565, new Response.ErrorListener() {
            @Override//5、指定图片的属性，Bitmap.Config 下的几个常量都可以使用，
            // 其中 ARGB_8888 可以展示最好的颜色属性，每个图片像素像素占 4 个字节，RGB_565 表示每个图片像素占 2 个字节
            public void onErrorResponse(VolleyError error) {
                //6、图片请求失败的回调
            }
        });
        mQueue.add(imageRequest);
    }


}
