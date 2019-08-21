package c2.myapplication;

import androidx.appcompat.app.AppCompatActivity;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import com.asus.robotframework.API.MotionControl;
import com.asus.robotframework.API.RobotAPI;
import com.asus.robotframework.API.RobotFace;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Enumeration;

public class MainActivity extends Activity {

    public Thread thread;
    public BufferedReader br;
    public BufferedWriter bw;
    public Handler handler, handler2;
    public TextView info, msg, clientinfo, ipInfo;
    public String message = "";
    public ServerSocket serverSocket;
    public Socket s;
    public Runnable runnable;
    public SocketServerReplyThread socketServerReplyThread;
    public Boolean threadFlag,socketServerReplyThreadFlag;
    public RobotAPI mRobotAPI;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mRobotAPI = new RobotAPI(getApplicationContext(), null);
        info = (TextView) findViewById(R.id.portInfo);
        ipInfo = (TextView) findViewById(R.id.ipInfo);
        msg = (TextView) findViewById(R.id.msg);
        clientinfo = (TextView)findViewById(R.id.clientinfo);

        ipInfo.setText(getIpAddress());
        clientinfo.setText("Client State : False");

        startThread();

        handler2 = new Handler();
        runnable = new Runnable() {
            @Override
            public void run() {
                handler2.postDelayed(this, 1500);
                Log.e("text","test");
                try{
                    bw = new BufferedWriter( new OutputStreamWriter(s.getOutputStream()));
                    // 寫入訊息
                    bw.write("ClientState\n");
                    // 立即發送
                    bw.flush();
                    MainActivity.this.runOnUiThread(new Runnable() {

                        @Override
                        public void run() {
                            clientinfo.setText("Client Connect");
                        }
                    });
                }
                catch (Exception e){
                    MainActivity.this.runOnUiThread(new Runnable() {

                        @Override
                        public void run() {
                            clientinfo.setText("Client State : False");
                        }
                    });
                    try {
                        Thread.sleep(1500); //1000為1秒
                    } catch (InterruptedException e2) {
                        e2.printStackTrace();
                    }
                    try{
                        s.close();
                    }
                    catch (Exception e2){}
                    stopCheck();
                }
            }
        };
        handler2.postDelayed(runnable, 3500);
    }

    public void stopCheck(){
        handler2.removeCallbacks(runnable);
    }

    public void startThread(){
        socketServerReplyThreadFlag = true;
        threadFlag = true;
        thread = new Thread(new SocketServerThread());
        thread.start();
    }

    public void stopThread(View v){
        try {
            bw = new BufferedWriter(new OutputStreamWriter(
                    s.getOutputStream()));
            bw.write("ServerClose");
            bw.flush();
        }catch (Exception e){}
        stopCheck();
        thread.interrupt();
        socketServerReplyThreadFlag = false;
        threadFlag = false;
        clientinfo.setText("Client State : False");
        info.setText("Server Stop");
        msg.setText("");
        try {
            if(s != null)
                s.close();
            if(serverSocket != null)
                serverSocket.close();
        }catch (Exception e){}
    }

    public void Listen(View v){
        startThread();
    }

    private class SocketServerThread extends Thread {

        String receiveMsg;

        @Override
        public void run() {
            if(!thread.isInterrupted())
                try {
                    serverSocket = new ServerSocket(5050);
                    MainActivity.this.runOnUiThread(new Runnable() {

                        @Override
                        public void run() {
                            info.setText("I'm waiting here: "
                                    + serverSocket.getLocalPort());
                            msg.append("Server Start\n");
                        }
                    });

                    while(threadFlag) {
                        Log.e("text", "before accept");
                        s = serverSocket.accept();
                        Log.e("text", "accept");

                        socketServerReplyThread = new SocketServerReplyThread(s);
                        socketServerReplyThread.run();

                        message = s.getInetAddress() + " : connect\n";

                        MainActivity.this.runOnUiThread(new Runnable() {

                            @Override
                            public void run() {
                                msg.append(message);
                            }
                        });
                    }
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }finally {
                    try{
                        s.close();
                        serverSocket.close();
                    }catch (Exception e){}
                }
        }

    }

    private class SocketServerReplyThread extends Thread {

        private Socket hostThreadSocket;
        String receiveMsg;

        SocketServerReplyThread(Socket socket) {
            hostThreadSocket = socket;
        }

        @Override
        public void run() {
            MainActivity.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    clientinfo.setText("Client Connect");
                }
            });

            try {
                br = new BufferedReader(new InputStreamReader(
                        hostThreadSocket.getInputStream()));

                while (hostThreadSocket.isConnected() & socketServerReplyThreadFlag) {
                    Log.e("text","connect");
                    // 取得網路訊息
                    receiveMsg =  br.readLine();
                    // 如果不是空訊息則
                    if(receiveMsg!=null){
                        Log.e("text", receiveMsg);
                        if (receiveMsg.equals("ClientDisconnect")) {
                            Log.e("text","client disconnect");
                            MainActivity.this.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    msg.append(hostThreadSocket.getInetAddress().toString()+" disconnect\n");
                                    clientinfo.setText("Client State : False");
                                }
                            });
                            break;
                        }
                        else if (receiveMsg.equals("ProudFace")){
                            RobotFaceChange();
                        }
                        else if (receiveMsg.equals("HeadUP")){
                            RobotHeadMove();
                        }
                        else if (!receiveMsg.equals("ServerState") ) {
                            // 顯示新的訊息
                            MainActivity.this.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    msg.append(receiveMsg+"\n");
                                }
                            });
                            bw = new BufferedWriter(new OutputStreamWriter(
                                    hostThreadSocket.getOutputStream()));
                            bw.write("Receive : "+receiveMsg+"\n");
                            bw.flush();
                        }
                        else{
                            receiveMsg = "";
                        }
                    }

                }
            } catch (IOException e) {
                // TODO Auto-generated catch block
//                e.printStackTrace();
//                message = "Something wrong! " + e.toString() + "\n";
            }
            finally {
                try{
                    hostThreadSocket.close();
                    s.close();
                }catch (Exception e){}

            }

//            MainActivity.this.runOnUiThread(new Runnable() {
//
//                @Override
//                public void run() {
//                    msg.append(message);
//                }
//            });
        }

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            bw = new BufferedWriter(new OutputStreamWriter(
                    s.getOutputStream()));
            bw.write("ServerClose");
            bw.flush();
        }catch (Exception e){}
    }

    private void RobotFaceChange(){
        mRobotAPI.robot.setExpression(RobotFace.PROUD);
    }

    private void RobotHeadMove(){
        mRobotAPI.motion.moveHead(0,50, MotionControl.SpeedLevel.Head.L2);
    }

    private String getIpAddress() {
        String ip = "";
        try {
            Enumeration<NetworkInterface> enumNetworkInterfaces = NetworkInterface
                    .getNetworkInterfaces();
            while (enumNetworkInterfaces.hasMoreElements()) {
                NetworkInterface networkInterface = enumNetworkInterfaces
                        .nextElement();
                Enumeration<InetAddress> enumInetAddress = networkInterface
                        .getInetAddresses();
                while (enumInetAddress.hasMoreElements()) {
                    InetAddress inetAddress = enumInetAddress.nextElement();

                    if (inetAddress.isSiteLocalAddress()) {
                        ip += "SiteLocalAddress: "
                                + inetAddress.getHostAddress() + "\n";
                    }

                }

            }

        } catch (SocketException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            ip += "Something Wrong! " + e.toString() + "\n";
        }

        return ip;
    }

}
