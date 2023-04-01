package cn.byyddyh.spoofingdetection.sockets;

import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;

public class SocketClient {
    private Socket client;
    private Context context;
    private int port;           //IP
    private String site;            //端口
    public static Handler mHandler;
    private boolean isClient = false;
    private PrintWriter out;
    private String str;


    /**
     * @effect 开启线程建立连接开启客户端
     */
    public void openClientThread() {
        /*
         * connect()步骤
         */
        Thread thread = new Thread(() -> {
            try {
                /*
                 *  connect()步骤
                 * */
                client = new Socket(site, port);
                // client.setSoTimeout ( 5000 );//设置超时时间
                isClient = true;
                forOut();
                forIn();
                Log.i("hahah", "site=" + site + " ,port=" + port);
            } catch (UnknownHostException e) {
                e.printStackTrace();
                Log.i("socket", "6");
            } catch (IOException e) {
                e.printStackTrace();
                Log.i("socket", "7");
            }

        });
        thread.start();
    }

    /**
     * 调用时向类里传值
     */
    public void clintValue(Context context, String site, int port) {
        this.context = context;
        this.site = site;
        this.port = port;
    }

    /**
     * @effect 得到输出字符串
     */
    public void forOut() {
        try {
            out = new PrintWriter(client.getOutputStream());
        } catch (IOException e) {
            e.printStackTrace();
            Log.i("socket", "8");
        }
    }

    /**
     * @steps read();
     * @effect 得到输入字符串
     */
    public void forIn() {

        while (isClient) {
            try {
                InputStream in = client.getInputStream();
                /*得到的是16进制数，需要进行解析*/
                byte[] bt = new byte[50];
                in.read(bt);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    str = new String(bt, StandardCharsets.UTF_8);
                }
            } catch (IOException e) {
                Log.d("SocketClient 异常", e.getMessage());
            }
            if (str != null) {
                Message msg = new Message();
                msg.obj = str;
                mHandler.sendMessage(msg);
            }

        }
    }

    /**
     * @steps write();
     * @effect 发送消息
     */
    public void sendMsg(final String str) {
        new Thread(() -> {
            if (client != null) {
                out.print(str);
                out.flush();
                Log.i("SocketClient Send over", out + "");
            } else {
                isClient = false;
                Toast.makeText(context, "网络连接失败", Toast.LENGTH_LONG).show();
            }
        }).start();

    }
}