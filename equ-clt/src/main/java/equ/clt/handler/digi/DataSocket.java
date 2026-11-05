package equ.clt.handler.digi;

import equ.clt.handler.ScalesAvailability;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

import static equ.clt.handler.HandlerUtils.openSocket;

public class DataSocket {
    String ip;
    Socket socket = null;
    DataOutputStream outputStream = null;
    DataInputStream inputStream = null;
    private boolean connectionOpened = false;

    public DataSocket(String ip) {
        this.ip = ip;
    }

    public void open() throws IOException {
        try {
            //connection in LAN is established in milliseconds or not at all; the scales can process a command long
            socket = openSocket(ip, getPort(ip), 10000, 60000);
        } catch (IOException e) {
            ScalesAvailability.connectionFailed(ip);
            throw e;
        }
        ScalesAvailability.connectionOpened(ip);
        connectionOpened = true;
        outputStream = new DataOutputStream(socket.getOutputStream());
        inputStream = new DataInputStream(socket.getInputStream());
    }

    //close can be called several times
    public void close() throws IOException {
        try {
            try {
                if (outputStream != null) {
                    outputStream.close();
                    outputStream = null;
                }
                if (inputStream != null) {
                    inputStream.close();
                    inputStream = null;
                }
            } finally { //the socket is closed even if closing of a stream failed
                if (socket != null) {
                    socket.close();
                    socket = null;
                }
            }
        } finally {
            if (connectionOpened) {
                connectionOpened = false;
                ScalesAvailability.connectionClosed(ip);
            }
        }
    }

    private int getPort(String ip) {
        String[] splittedIP = ip.split("\\.");
        return 2000 + Integer.parseInt(splittedIP[splittedIP.length - 1]);
    }
}
