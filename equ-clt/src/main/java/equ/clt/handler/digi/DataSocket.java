package equ.clt.handler.digi;

import javax.naming.CommunicationException;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

public class DataSocket {
    String ip;
    Socket socket = null;
    DataOutputStream outputStream = null;
    DataInputStream inputStream = null;

    public DataSocket(String ip) {
        this.ip = ip;
    }

    public void open() throws IOException {
        socket = new Socket();
        socket.setSoTimeout(60000);
        socket.connect(new InetSocketAddress(ip, getPort(ip)), 60000);
        outputStream = new DataOutputStream(socket.getOutputStream());
        inputStream = new DataInputStream(socket.getInputStream());
    }

    public void close() throws IOException {
        if(outputStream != null) {
            outputStream.close();
            outputStream = null;
        }
        if(inputStream != null) {
            inputStream.close();
            inputStream = null;
        }
        if (socket != null) {
            socket.close();
            socket = null;
        }
    }

    private int getPort(String ip) {
        String[] splittedIP = ip.split("\\.");
        return 2000 + Integer.parseInt(splittedIP[splittedIP.length - 1]);
    }
}