package equ.clt.handler.mettlerToledo;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class TCPSocket {
    String ip;
    Integer port;
    Socket socket = null;
    DataOutputStream outputStream = null;
    private DataInputStream inputStream = null;

    public TCPSocket(String ip, Integer port) {
        this.ip = ip;
        this.port = port;
    }

    public void open() throws IOException {
        socket = new Socket();
        socket.setSoTimeout(60000);
        socket.connect(new InetSocketAddress(ip, port), 60000);
        outputStream = new DataOutputStream(socket.getOutputStream());
        inputStream = new DataInputStream(socket.getInputStream());
    }

    public String read(int timeout) throws IOException {
        int count = 0;
        try {
            while (inputStream.available() == 0 && count < timeout) {
                Thread.sleep(1000);
            }
        } catch (InterruptedException ignored) {
        }
        int available = inputStream.available();
        if (available == 0) {
            throw new RuntimeException("Socket read timeout");
        }
        byte[] buffer = new byte[available];
        inputStream.readFully(buffer);
        return new String(buffer).trim();
    }

    public void write(String data) throws IOException {
        byte[] commandBytes = data.getBytes(StandardCharsets.UTF_8);
        outputStream.write(commandBytes);
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
}