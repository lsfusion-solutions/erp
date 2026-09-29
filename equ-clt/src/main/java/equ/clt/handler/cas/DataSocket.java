package equ.clt.handler.cas;

import lsfusion.base.Pair;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

import static equ.clt.handler.HandlerUtils.defaultConnectTimeout;
import static equ.clt.handler.HandlerUtils.openSocket;
import static equ.clt.handler.HandlerUtils.parseHostPort;

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

    public static DataSocket fromAddress(String address, int defaultPort) {
        Pair<String, Integer> hostPort = parseHostPort(address, defaultPort);
        return new DataSocket(hostPort.first, hostPort.second);
    }

    public void open() throws IOException {
        socket = openSocket(ip, port, defaultConnectTimeout, 0);
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