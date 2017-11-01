# androidWebsocketDemo
android使用websocket进行长链接的一个简单的demo，可以用来收发消息或别的操作，里面用到了**autobahn**的jar包。
## 基本操作都在WebSocketService 这个类中，websocketHost要填写自己服务器的，ws开头的url。
## 发送消息时的注意
使用json进行数据传输，关于json格式的定义，根据后台给出的要求而定。
