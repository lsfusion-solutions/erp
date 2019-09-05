package equ.clt.handler.cas;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;

public class DataSocket {
    String ip;
    Integer port;
    Socket socket = null;
    DataOutputStream outputStream = null;
    DataInputStream inputStream = null;

    public DataSocket(String ip, Integer port) {
        this.ip = ip;
        this.port = port;
    }

    public void open() throws IOException {
        socket = new Socket(InetAddress.getByName(ip), port);
        outputStream = new DataOutputStream(socket.getOutputStream());
        inputStream = new DataInputStream(socket.getInputStream());
    }

    public void close() throws IOException {
        if (socket != null) {
            socket.close();
            socket = null;
        }
    }
}