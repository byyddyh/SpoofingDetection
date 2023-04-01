package cn.byyddyh.spoofingdetection.sockets;

import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Created by kys-29 on 2016/9/21.
 */
public class SocketServer {
    private ServerSocket server;
    private Socket socket;
    private InputStream in;
    private String str = null;
    public static Handler ServerHandler;

    /**
     * @param port 端口号
     * @steps bind();绑定端口号
     * @effect 初始化服务端
     */
    public SocketServer(int port) {
        try {
            server = new ServerSocket(port);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    /**
     * @steps listen();
     * @effect socket监听数据
     */
    public void beginListen() {
        new Thread(() -> {
            try {
                /* accept();
                 * 接受请求
                 * */
                socket = server.accept();
                try {
                    /*得到输入流*/
                    in = socket.getInputStream();
                    /*
                     * 实现数据循环接收
                     * */
                    while (!socket.isClosed()) {
                        byte[] bt = new byte[256];
                        in.read(bt);
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                            str = new String(bt, StandardCharsets.UTF_8);                   // 编码方式  解决收到数据乱码
                        }
                        if (!str.equals("exit")) {
//                            returnMessage(str);
                        } else {
                            break;                                                      // 跳出循环结束socket数据接收
                        }
                        Log.d("SocketServer 接收到数据", str);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    socket.isClosed();
                }
            } catch (IOException e) {
                e.printStackTrace();
                socket.isClosed();
            }
        }).start();
    }

    /**
     * @steps write();
     * @effect socket服务端发送信息
     */
    public void sendMessage(final String chat) {
        Thread thread = new Thread(() -> {
            try {
                PrintWriter out = new PrintWriter(socket.getOutputStream());
                out.print(chat);
                out.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        thread.start();
    }

//    /**
//     * @steps read();
//     * @effect socket服务端得到返回数据并发送到主界面
//     */
//    public void returnMessage(String chat) {
//        Message msg = new Message();
//        msg.obj = chat;
//        ServerHandler.sendMessage(msg);
//    }

}
