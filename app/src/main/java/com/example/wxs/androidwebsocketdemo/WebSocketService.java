package com.example.wxs.androidwebsocketdemo;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.net.URI;

import de.tavendo.autobahn.WebSocket;
import de.tavendo.autobahn.WebSocketConnection;
import de.tavendo.autobahn.WebSocketOptions;

/**
 *
 *
 * Created by wxs on 16/8/17.
 */
public class WebSocketService extends Service {

    private static final String TAG = WebSocketService.class.getSimpleName();

    public static final String WEBSOCKET_ACTION = "WEBSOCKET_ACTION";

    private BroadcastReceiver connectionReceiver;
    private static boolean isClosed = true;
    private static WebSocketConnection webSocketConnection;
    private static WebSocketOptions options = new WebSocketOptions();
    private static boolean isExitApp = false;
    private static String websocketHost = "ws://10.135.30.170:8089/ws"; //websocket服务端的url,,,ws是协议,和http一样,我写的时候是用的我们公司的服务器所以这里不能贴出来


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        if (connectionReceiver == null) {
            connectionReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
                    NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();

                    if (networkInfo == null || !networkInfo.isAvailable()) {
                        Toast.makeText(getApplicationContext(), "网络已断开，请重新连接", Toast.LENGTH_SHORT).show();
                    } else {
                        if (webSocketConnection != null) {
                            webSocketConnection.disconnect();
                        }
                        if (isClosed) {
                            webSocketConnect();
                        }
                    }

                }
            };

            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
            registerReceiver(connectionReceiver, intentFilter);


        }
        return super.onStartCommand(intent, flags, startId);

    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public static void closeWebsocket(boolean exitApp) {
        isExitApp = exitApp;
        if (webSocketConnection != null && webSocketConnection.isConnected()) {
            webSocketConnection.disconnect();
            webSocketConnection = null;
        }
    }

    public static void webSocketConnect(){
        webSocketConnection = new WebSocketConnection();
        try {
            webSocketConnection.connect(new URI(websocketHost), new WebSocket.WebSocketConnectionObserver() {

                @Override
                public void onOpen() {
                    Log.d(TAG, "WebSocket connection opened to: " + websocketHost);

                    JSONObject json = new JSONObject();
                    try {
                        json.put("cmd", "register");
                        json.put("roomid","1");
                        json.put("clientid","123");
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    webSocketConnection.sendTextMessage(json.toString());
                }

                @Override
                public void onClose(WebSocketCloseNotification code, String reason) {
                    Log.d(TAG, "WebSocket connection closed. Code: " + code + ". Reason: " + reason +
                            ". State: " + reason);

                }

                @Override
                public void onTextMessage(String payload) {
                    Log.d(TAG, "recevie message: " + payload);
                    final String message = payload;

                }

                @Override
                public void onRawTextMessage(byte[] payload) {}

                @Override
                public void onBinaryMessage(byte[] payload) {}
                    });

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void sendMsg(String s) {
        Log.d(TAG, "sendMsg = " + s);
        JSONObject json = new JSONObject();
        try {
            json.put("cmd", "send");
            json.put("msg",s);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        String msg = json.toString();
        if (!TextUtils.isEmpty(msg))
            if (webSocketConnection != null) {
                webSocketConnection.sendTextMessage(msg);
            }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (connectionReceiver != null) {
            unregisterReceiver(connectionReceiver);
        }
    }


}
