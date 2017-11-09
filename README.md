
# Android WebSocket Demo

Android使用WebSocket进行长链接的一个简单的Demo，可以用来收发消息，里面用到了**autobahn**的jar包。

基本操作都在WebSocketClient这个类中，这个类对**autobahn**的jar包中的WebSocketConnection进行了封装，简化了使用。

使用示范：
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

在使用**autobahn**的jar包中的WebSocketConnection的过程中碰到的**问题：**

1.连接建立后，总是立马断开。

后来发现是jar包的问题，将autobahn-0.5的jar包更换为现在的jar包后，问题解决。

2.连接建立并注册房间成功后，只能收到服务器推送的几条消息，而且连接并未断开（并未回调关闭方法，其实连接已经断开了）。

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

3.在学习使用**autobahn**的WebSocket的过程中，查阅资料发现网上的用法基本一致：都是创建一个Service，然后在Service中利用WebSocketConnection建立长连接进行通信，获取到服务器推送的消息之后，利用接口回调的方式或者利用EventBus等将消息传递给Activity，这种方式使用比较麻烦，而且有时候会涉及到Service的保活问题。

原因：之所以使用这种方式，说是因为在Activity中使用连接会断开，但是经过本人测试，在Activity中使用并未发现连接断开的问题，所以不推荐使用建立Service的方式，而推荐直接在Activity中建立连接，此外推荐在子线程中利用WebSocketConnection进行相关操作，主要是担心会阻塞主线程（最初遇到过问题，但是后来又测试发现没问题，所以不确定）。

4.在子线程中使用WebSocketConnection建立连接，出错。

原因：**WebSocketConnection的相关方法必须在一个具有Looper的线程中使用。**

在WebSocketClient内部利用HandlerThread和Handler将WebSocketConnection的相关操作都放置到一个HandlerThread中执行，方便了开发者使用，具体的实现细节见代码。

在使用过程中还**没有解决的问题：**

1. 断开连接的时候onClose方法并不会被调用，原因未知，如果不回调onClose方法，怎么做断线重连。
2. WebSocketConnection是否会阻塞主线程，是否可以在主线程中使用，代码没看懂，最初遇到过问题，但是后来又试的时候，发现没有问题，目前不确定。
3. 为什么一定要在具有Looper的线程中才能调用WebSocketConnection的相关方法。
4. 为什么不发送消息连接就会断开。
