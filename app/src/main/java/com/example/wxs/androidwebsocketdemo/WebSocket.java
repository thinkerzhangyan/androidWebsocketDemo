package com.example.wxs.androidwebsocketdemo;

/**
*@des WebSocketClient的接口
*@author zhangyan
*@date 2017/11/8
*/
public interface WebSocket {

    /**
    * 连接到房间的方法
    */
    void connectToRoom(final String wsUrl, final String roomId, final String clientId);

    /**
    *从房间断开连接方法
    */
    void disconnectFromRoom(final boolean waitForComplete);

    /**
     *发送消息的方法
     */
    void sendMessage(final String msg);

}
