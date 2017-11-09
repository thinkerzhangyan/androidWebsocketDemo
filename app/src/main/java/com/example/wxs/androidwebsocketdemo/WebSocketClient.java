/*
 * WebSocket的使用帮助类，对websocket autobahn jar中的WebSocketConnection的相关操作进行了封装，
 * 外界在构造方法中传入自己创建的实现了的WebSocketClientEvents接口的对象，该对象的相关方法会在相应
 * 方法中被回调，对外开放了connectToRoom方法，disconnectFromRoom方法和sendMessage方法，WebSocket
 * 的相关操作必须在一个具有Looper的子线程中运行，否则会报错，原因未知。
 */
package com.example.wxs.androidwebsocketdemo;

import android.os.Handler;
import android.os.HandlerThread;
import android.text.TextUtils;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedList;
import java.util.Timer;
import java.util.TimerTask;

import de.tavendo.autobahn.WebSocket.WebSocketConnectionObserver;
import de.tavendo.autobahn.WebSocketConnection;
import de.tavendo.autobahn.WebSocketException;


/**
 *@author zhangyan
 *@date 2017/11/8
 */
public class WebSocketClient implements WebSocket{

    private static final String TAG = WebSocketClient.class.getSimpleName();

    private static final int CLOSE_TIMEOUT = 1000;

    /**
    *外界通过构造方法传入的实现了WebSocketClientEvents接口的方法的对象，该对象的方法会在WebSocketObserver的相关方法中被调用。
    */
    private final WebSocketClientEvents mWebSocketClientEvents;

    /**
    *包含HandlerThread的Looper的Handler，将WebSocketConnection相关的操作都借助于其Post方法，传递到HandlerThread中去执行，
     * WebSocketConnection的相关方法如果在主线程和没有Looper的子线程中调用的时候会报错。
    */
    private final Handler mHandler;

    private WebSocketConnection mWebSocketConnection;

    private WebSocketObserver mWebSocketObserver;
    /**
    *房间的网络地址
    */
    private String wsServerUrl;
    /**
    *房间ID
    */
    private String roomID;
    /**
    *用户ID
    */
    private String clientID;
    /**
    *连接状态
    */
    private WebSocketConnectionState state;
    private final Object closeEventLock = new Object();
    private boolean closeEvent;
    /**
    *发送心跳包的Timer
    */
    private Timer mTimer = new Timer();
    /**
    *缓存消息的Queue
    */
    private final LinkedList<String> wsSendQueue;


    /**
    *状态类
    */
    public enum WebSocketConnectionState {
        NEW, CONNECTED, REGISTERED, CLOSED, ERROR
    }


    public WebSocketClient(WebSocketClientEvents events) {

        final HandlerThread handlerThread = new HandlerThread(TAG);
        handlerThread.start();
        mHandler = new Handler(handlerThread.getLooper());

        this.mWebSocketClientEvents = events;
        wsSendQueue = new LinkedList<>();

        state = WebSocketConnectionState.NEW;
    }


    /**
     *连接方法
     */
    @Override
    public void connectToRoom(final String wsUrl, final String roomId, final String clientId) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                connectToRoomInternal(wsUrl,roomId,clientId);
            }
        });
    }

    /**
     *断开连接的方法
     */
    @Override
    public void disconnectFromRoom(final boolean waitForComplete) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                disconnectFromRoomInternal(waitForComplete);
                mHandler.getLooper().quit();
            }
        });
    }

    /**
     *发送消息的方法
     */
    @Override
    public void sendMessage(final String message) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                sendMessageInternal(message);
            }
        });
    }

    /**
     *连接的私有方法
     */
    private void connectToRoomInternal(String wsUrl, String roomId, String clientId) {
        checkIfCalledOnValidThread();
        if (state != WebSocketConnectionState.NEW) {
            Log.e(TAG, "WebSocket is already connected.");
            return;
        }

        wsServerUrl = wsUrl;
        roomID = roomId;
        clientID = clientId;

        closeEvent = false;

        Log.d(TAG, "Connecting WebSocket to: " + wsUrl );
        mWebSocketConnection = new WebSocketConnection();
        mWebSocketObserver = new WebSocketObserver();
        try {
            mWebSocketConnection.connect(new URI(wsServerUrl), mWebSocketObserver);
        } catch (URISyntaxException e) {
            reportError("URI error: " + e.getMessage());
        } catch (WebSocketException e) {
            reportError("WebSocket connection error: " + e.getMessage());
        }
    }


    /**
     *断开连接的私有方法
     */
    private void disconnectFromRoomInternal(boolean waitForComplete) {

        checkIfCalledOnValidThread();
        Log.d(TAG, "Disconnect WebSocket. State: " + state);
        if (state == WebSocketConnectionState.REGISTERED) {

            state = WebSocketConnectionState.CONNECTED;
        }

        if (state == WebSocketConnectionState.CONNECTED ||
                state == WebSocketConnectionState.ERROR) {
            mWebSocketConnection.disconnect();
            state = WebSocketConnectionState.CLOSED;

            mTimer.cancel();
            mTimer = null;

            //TODO 正确位置应该是在Observer的onClose回调方法中，但是断开连接的时候Observer的onClose方法有时候不会调用，目前原因未知，为了让events.onWebSocketClose()被调用，暂时在disconnect中调用。
            mWebSocketClientEvents.onWebSocketClose();

            // Wait for websocket close event to prevent websocket library from
            // sending any pending messages to deleted looper thread.
            if (waitForComplete) {
                synchronized (closeEventLock) {
                    while (!closeEvent) {
                        try {
                            closeEventLock.wait(CLOSE_TIMEOUT);
                            break;
                        } catch (InterruptedException e) {
                            Log.e(TAG, "Wait error: " + e.toString());
                        }
                    }
                }
            }
        }
        Log.d(TAG, "Disconnecting WebSocket done.");
    }

    /**
     *发送消息的私有方法
     */
    private void sendMessageInternal(String message) {
        checkIfCalledOnValidThread();
        switch (state) {
            case NEW:
            case CONNECTED:
                // Store outgoing messages and send them after websocket client
                // is registered.
                Log.d(TAG, "WS ACC: " + message);
                wsSendQueue.add(message);
                return;
            case ERROR:
            case CLOSED:
                Log.e(TAG, "WebSocket send() in error or closed state : " + message);
                return;
            case REGISTERED:
                mWebSocketConnection.sendTextMessage(message);
                break;
        }
    }

    /**
    *房间注册方法，只有注册到房间的时候，才可以接收房间消息。
    */
    public void register(final String roomID, final String clientID) {
        checkIfCalledOnValidThread();
        if (state != WebSocketConnectionState.CONNECTED) {
            Log.w(TAG, "WebSocket register() in state " + state);
            return;
        }
        Log.d(TAG, "Registering WebSocket for room " + roomID + ". ClientID: " + clientID);
        JSONObject json = new JSONObject();
        try {
            json.put("cmd", "register");
            json.put("roomid", roomID);
            json.put("clientid", clientID);
            Log.d(TAG, "C->WSS: " + json.toString());
            mWebSocketConnection.sendTextMessage(json.toString());
            //应该根据返回的结果，来设置是否注册成功的标志位
            state = WebSocketConnectionState.REGISTERED;
            mWebSocketClientEvents.onRegisterSuccess(wsServerUrl, roomID, clientID);
            // 发送之前未成功发送出去的消息.
            for (String sendMessage : wsSendQueue) {
                if(!TextUtils.isEmpty(sendMessage))
                    sendMessage(sendMessage);
            }
            wsSendQueue.clear();
        } catch (JSONException e) {
            reportError("WebSocket register JSON error: " + e.getMessage());
        }
    }


    /**
    *报告错误的帮助方法
    */
    private void reportError(final String errorMessage) {
        Log.e(TAG, errorMessage);
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                if (state != WebSocketConnectionState.ERROR) {
                    state = WebSocketConnectionState.ERROR;
                    mWebSocketClientEvents.onWebSocketError(errorMessage);
                }
            }
        });
    }

    /**
     *状态获取方法
     */
    public WebSocketConnectionState getState() {
        return state;
    }

    /**
    *检查当前的操作是否在Handler所具有的Looper所在的线程
    */
    private void checkIfCalledOnValidThread() {
        if (Thread.currentThread() != mHandler.getLooper().getThread()) {
            throw new IllegalStateException("WebSocket method is not called on valid thread");
        }
    }

    /**
    *  WebSocketConnection的回调类，连接成功的时候会回调onOpen()方法，连接断开的时候会回调onClose方法（目前不回调，原因未知），
     * 接收到消息的时候会回调onTextMessage方法。
    */
    private class WebSocketObserver implements WebSocketConnectionObserver {

        @Override
        public void onOpen() {
            Log.d(TAG, "WebSocket connection opened to: " + wsServerUrl);
            mHandler.post(new Runnable() {
                @Override
                public void run() {

                    state = WebSocketConnectionState.CONNECTED;

                    mWebSocketClientEvents.onConnectSuccess(wsServerUrl);

                    if (roomID != null && clientID != null) {
                        register(roomID, clientID);
                    }


                    //心跳指令
                    final JSONObject jsonHeart = new JSONObject();
                    try {
                        jsonHeart.put("cmd", "heart");
                        jsonHeart.put("msg","heart");
                    } catch (JSONException e) {
                        reportError("WebSocket heart JSON error: " + e.getMessage());
                    }
                    //进过测试发现，利用websocket autobahn jar中的WebSocketConnection建立的WebSocket连接，WebSocket如果发送信息不规律或者长时间不发送，这个ws虽然开着，
                    //但是已经断开连接了,而且这种情况下WebSocketConnectionObserver的onClose方法也不会被调用，为了防止这种情况的出现，我们每间隔一分钟发送一次心跳包(心跳间隔还可以改善)，
                    //经过测试发现，只要发送心跳包就可以了，服务器返不返回心跳包的消息不影响连接情况。
                    //正常的心跳机制：每间隔一段时间发送心跳包，服务器在收到心跳包的时候，将心跳包返回给客户端，如果超过了一定的时间，客户端没收到服务器返回的心跳包，就认为连接断开了，就会进行断线重连。
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

                }
            });
        }

        //TODO 在断开连接的时候，有时候该回调方法并不会被调用，原因未知
        @Override
        public void onClose(WebSocketCloseNotification code, String reason) {
            Log.d(TAG, "WebSocket connection closed. Code: " + code + ". Reason: " + reason +
                    ". State: " + state);
            synchronized (closeEventLock) {
                closeEvent = true;
                closeEventLock.notify();
            }

            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (state != WebSocketConnectionState.CLOSED) {
                        state = WebSocketConnectionState.CLOSED;
                        mWebSocketClientEvents.onWebSocketClose();
                    }
                }
            });
        }

        @Override
        public void onTextMessage(String payload) {
            Log.d(TAG, "WSS->C: " + payload);
            final String message = payload;
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (state == WebSocketConnectionState.CONNECTED ||
                            state == WebSocketConnectionState.REGISTERED) {
                        mWebSocketClientEvents.onWebSocketMessage(message);
                    }
                }
            });
        }

        @Override
        public void onRawTextMessage(byte[] payload) {}

        @Override
        public void onBinaryMessage(byte[] payload) {}
    }

    /**
     *对外的接口
     */
    public interface WebSocketClientEvents {

        /**
         *接收到消息
         */
        void onWebSocketMessage(final String message);

        /**
         *连接断开
         */
        void onWebSocketClose();

        /**
         *连接出错
         */
        void onWebSocketError(final String description);

        /**
         *  连接成功
         */
        void onConnectSuccess(String wsUrl);

        /**
         *房间注册成功
         */
        void onRegisterSuccess(String wsUrl, String rooomId, String clientId);
    }
}
