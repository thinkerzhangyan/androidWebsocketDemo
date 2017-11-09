# Android WebSocket Demo

Android使用WebSocket进行长链接的一个简单的Demo，可以用来收发消息，里面用到了**autobahn**的jar包。

基本操作都在WebSocketClient这个类中，这个类对**autobahn**的jar包中的WebSocketConnection进行了封装，简化了使用。

在学习使用**autobahn**的WebSocket的过程中，查阅资料发现网上的用法基本一致：都是创建一个Service，然后在Service中利用WebSocketConnection建立长连接进行通信，获取到服务器推送的消息之后，利用接口回调的方式或者利用EventBus等将消息传递给Activity，使用比较麻烦，而且有时候会涉及到Service的保活问题。

在WebSocketClient这个类中对WebSocketConnection的相关操作必须在一个具有Looper的线程中使用，在WebSocketClient内部利用HandlerThread和Handler将WebSocketConnection的相关操作都放置到一个HandlerThread中执行，方便了开发者使用，具体的实现细节见代码。

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

// WebSocketClient.WebSocketClientEvents接口中的方法，会在WebSocketClient中回调，每个方//法的具体含义参照代码注释
private class MySocketClientEvents implements WebSocketClient.WebSocketClientEvents {

        @Override
        public void onWebSocketMessage(final String msg) {

            if (mWebSocketClient.getState() != WebSocketClient.WebSocketConnectionState.REGISTERED) {
                Log.e(TAG, "Got WebSocket message in non registered state.");
                return;
            }

            if (msg.length() > 0) {
                Log.d(TAG, msg);
                Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
            }

        }

        @Override
        public void onWebSocketClose() {
            Log.d(TAG, "onWebSocketClose()");
            Toast.makeText(getApplicationContext(),"和房间连接断开", Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onWebSocketError(String description) {
            Log.d(TAG, "onWebSocketError："+description);
            Toast.makeText(getApplicationContext(), "socket出错", Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onConnectSuccess(String wsUrl) {
            Log.d(TAG, "onConnectSuccess："+wsUrl);
            Toast.makeText(getApplicationContext(),"和房间连接成功", Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onRegisterSuccess(String wsUrl, String rooomId, String clientId) {
            Log.d(TAG, "onRegisterSuccess："+wsUrl+rooomId+clientId);
            Toast.makeText(getApplicationContext(), "注册房间成功", Toast.LENGTH_SHORT).show();
        }

}
```
