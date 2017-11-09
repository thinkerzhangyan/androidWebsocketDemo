package com.example.wxs.androidwebsocketdemo;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity implements View.OnClickListener{


    private static final String TAG = MainActivity.class.getSimpleName();

    private Button connectBtn;
    private Button disconnectBtn;
    private TextView messageTv;
    private EditText sendMsgEdit;
    private Button sendMsgBtn;

    private WebSocketClient mWebSocketClient;

    //websocket服务端的url,,,ws是协议,和http一样
    private String wsUrl = "ws://10.135.30.170:8080/ws";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        findViews();
        initViews();

        mWebSocketClient = new WebSocketClient(new MySocketClientEvents());

    }

    @Override
    public void onClick(View v) {
        switch (v.getId()){
            case R.id.connect_btn:
                mWebSocketClient.connectToRoom(wsUrl,"988863197","5");
                break;

            case R.id.disconnect_btn:
                mWebSocketClient.disconnectFromRoom(true);
                break;

            case R.id.send_msg_btn:
                mWebSocketClient.sendMessage(sendMsgEdit.getText().toString());
                break;
        }
    }


    private void findViews(){
        connectBtn = (Button)findViewById(R.id.connect_btn);
        disconnectBtn = (Button)findViewById(R.id.disconnect_btn);
        messageTv = (TextView)findViewById(R.id.message_tv);
        sendMsgEdit = (EditText)findViewById(R.id.send_msg_edit);
        sendMsgBtn = (Button)findViewById(R.id.send_msg_btn);
    }

    private void initViews(){
        connectBtn.setOnClickListener(this);
        disconnectBtn.setOnClickListener(this);
        sendMsgBtn.setOnClickListener(this);
    }

    @Override
    public void onBackPressed() {
        mWebSocketClient.disconnectFromRoom(true);
        super.onBackPressed();
    }


    // WebSocketClient.WebSocketClientEvents接口中的方法，会在WebSocketClient中回调
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
}
