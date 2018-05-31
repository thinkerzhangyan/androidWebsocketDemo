# Android WebSocket Demo

Android使用websocket创建长连接实现推送的一个简单的[Demo][1]。Demo中使用了autobahn这个开源库，基本操作都在WebSocketClient这个类中，这个类根据项目中的业务需求对**autobahn**的jar包中的WebSocketConnection方法进行了简单的封装。

## 使用示范

```
WebSocketClient  mWebSocketClient = new WebSocketClient(new MySocketClientEvents());

//建立连接，第一个参数是websocket服务端的url,ws是协议,和http一样；第二个参数是房间ID，
//第三个参数，是用户ID
mWebSocketClient.connectToRoom("ws://10.135.30.170:8080/ws","988863197","5");
//断开连接
mWebSocketClient.disconnectFromRoom(true);
//发送消息
mWebSocketClient.sendMessage("我是消息");

// WebSocketClient.WebSocketClientEvents接口中的方法，会在WebSocketClient中回调，每个方法的具体含义参照代码注释
private class MySocketClientEvents implements WebSocketClient.WebSocketClientEvents {
        @Override
        public void onWebSocketMessage(final String msg) {
        }

        @Override
        public void onWebSocketClose() {
        }

        @Override
        public void onWebSocketError(String description) {
        }

        @Override
        public void onConnectSuccess(String wsUrl) {
        }

        @Override
        public void onRegisterSuccess(String wsUrl, String rooomId, String clientId) {
        }
}
```

## 使用**autobahn**的过程中碰到的问题

1 . 连接建立后，总是立马断开。

后来发现是jar包的问题，将autobahn-0.5的jar包更换为现在的jar包后，问题解决。

2 . 连接建立并注册房间成功后，只能收到服务器推送的几条消息，而且连接并未断开（并未回调关闭方法，其实连接已经断开了）。

原因：如果连接建立后，长时间不发送消息或者发送消息不规律的话，ws连接虽然开着，但其实已经断开了，而且不会回调连接关闭的方法，为了防止连接断开，需要在WebSocketClient中加入心跳机制。代码中用如下的方式实现了心跳机制：
```
//心跳指令
final JSONObject jsonHeart = new JSONObject();
try {
    jsonHeart.put("cmd", "heart");
    jsonHeart.put("msg","heart");
} catch (JSONException e) {
    reportError("WebSocket heart JSON error: " + e.getMessage());
}
mTimer.schedule(new TimerTask() {
    @Override
    public void run() {
    //发送信息有时候会异常，不过不多...
    try {
        Log.d(TAG, "WebSocket heart beat！");
        mWebSocketConnection.sendTextMessage(jsonHeart.toString());
     }catch (Exception e){
        e.printStackTrace();
    }

  }
} , 1000, 60 * 1000);
```
每间隔一分钟发送心跳包（这个心跳间隔还可以改善，目前暂定一分钟），另外经过测试发现，只要发送消息即可，服务器返不返回心跳包对连接的断开没有影响，这并不符合正常的心跳机制。正常的心跳机制：每间隔一段时间发送心跳包，服务器在收到心跳包的时候，将心跳包返回给客户端，如果超过了一定的时间，客户端没收到服务器返回的心跳包，就认为连接断开了，就会进行断线重连。

学习推送原理的时候，搞明白了原因，运营商NAT服务器的原因，建议阅读：[推送][3]中的第一个面试问题。

3 . 在子线程中使用WebSocketConnection建立连接，出错。

原因：子线程中没有创建Looper，而WebSocketConnection的相关方法必须在一个具有Looper的线程中使用。这是因为在WebSocketConnection的构造方法里面创建了Handler，创建Handler的时候，会获取当前线程的Looper，如果获取不到，会报错。

为了防止使用出错，在WebSocketClient内部利用HandlerThread和Handler将WebSocketConnection的相关操作都放置到一个HandlerThread中执行，方便了开发者使用，具体的实现细节见代码。

4 . 断开连接的时候onClose方法并不会被调用。

开源库本身的原因，查看代码发现，onClose只有在连接失败的时候才会被回调，连接成功后，再断开连接的时候，并不会回调。。。。这感觉很坑爹，如果你想要在这个方法里面关闭和回收资源的话是不可能的，只能自己对断开连接的方法进行封装，然后在断开连接的方法里面调用onClose方法，具体的代码实现见[Demo][2]。

5 . WebSocketConnection是不会阻塞主线程，可以在主线程中使用。

第一个版本的时候，遇到过问题，而且查阅资料的时候也说会有问题。换包后问题消失。

查阅代码发现，确实不会阻塞主线程。连接方法是在子线程中进行的，读取和发送数据也是在子线程中进行的，所以并不会阻塞主线程。

6 . 在学习使用**autobahn**的WebSocket的过程中，查阅资料发现网上的用法基本一致：都是创建一个Service，然后在Service中利用WebSocketConnection建立长连接进行通信，获取到服务器推送的消息之后，利用接口回调的方式或者利用EventBus等将消息传递给Activity，这种方式使用比较麻烦，而且有时候会涉及到Service的保活问题。

原因：之所以使用这种方式，说是因为在Activity中使用连接会断开，但是经过本人测试，在Activity中使用并未发现连接断开的问题，所以不推荐使用建立Service的方式，而推荐直接在Activity中建立连接，此外推荐在子线程中利用WebSocketConnection进行相关操作，主要是担心会阻塞主线程（最初遇到过问题，但是后来又测试发现没问题，所以不确定）。

参考资料：

[android中使用WebSocket传递数据][4]

[Android上WebRTC介绍][5]

[移动端直播开发（四）播放与弹幕评论][6]

[Android中应用WebSocket完成群聊和新闻推送功效(不应用WebView)][7]

[分享下android的websocket解决方案][8]

[AsyncHttpClient/async-http-client][9]

[AndroidAsync][10]

[WebSocket安卓客户端实现详解(一)--连接建立与重连][11]

[深入浅出OkHttp Websocket--使用篇][12]

[Android 关于WebSocket的应用][13]

[android 使用 websocket 进行长链接的一个简单的 demo，可以用来收发消息或别的操作，里面用到了 autobahn 的 jar 包][14]

[websocket autobahn jar包的用法][15]

[Android WebSocket通信通过Service来绑定][16]

[Android WebSocket连接不成功][17]


  [1]: https://github.com/thinkerzhangyan/androidWebsocketDemo
  [2]: https://github.com/thinkerzhangyan/androidWebsocketDemo
  [3]: https://www.zybuluo.com/946898963/note/376465
  [4]: http://www.jianshu.com/p/ee5bdb999df6
  [5]: http://www.jianshu.com/p/5a67272d7055
  [6]: http://www.jianshu.com/p/121cb5d1594d
  [7]: http://www.1jtx.com/Android/30517.html
  [8]: https://ask.dcloud.net.cn/article/1103
  [9]: https://github.com/AsyncHttpClient/async-http-client
  [10]: https://github.com/koush/AndroidAsync
  [11]: http://blog.csdn.net/zly921112/article/details/72973054
  [12]: https://rabtman.com/2017/01/21/okhttp_ws_use/
  [13]: http://blog.csdn.net/coffeeco/article/details/13276437
  [14]: http://blog.csdn.net/u014608640/article/details/53063813
  [15]: http://blog.csdn.net/u014492513/article/details/52473286
  [16]: http://blog.csdn.net/u012162503/article/details/51770127
  [17]: http://blog.csdn.net/xgangzai/article/details/72675080   
