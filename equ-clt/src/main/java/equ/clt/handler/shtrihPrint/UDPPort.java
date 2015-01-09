package equ.clt.handler.shtrihPrint;

import javax.naming.CommunicationException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;

public class UDPPort {
    private DatagramSocket socket = null;
    private InetAddress ipAddress;
    private int ipPort;
    private int timeout;
    private InputStream inStream;
    private OutputStream outStream;

    public UDPPort(String ipAddress, int ipPort, int timeout) throws UnknownHostException {
        this.ipAddress = InetAddress.getByName(ipAddress);
        this.ipPort = ipPort;
        this.timeout = timeout;
    }

    public void open() throws CommunicationException {
        try {
            this.socket = new DatagramSocket();
            this.socket.setSoTimeout(this.timeout);
        } catch (Exception var2) {
            throw new CommunicationException(var2.toString());
        }
    }

    public void close() throws CommunicationException {
        try {
            if(this.socket != null) {
                this.socket.close();
                this.socket = null;
            }

        } catch (Exception var2) {
            throw new CommunicationException(var2.toString());
        }
    }

    public void sendCommand(byte[] var1) throws CommunicationException {
        try {
            DatagramPacket var2 = new DatagramPacket(var1, var1.length, this.ipAddress, this.ipPort);
            this.socket.send(var2);
        } catch (IOException var3) {
            throw new CommunicationException("Send command exception " + var3);
        }
    }

    public void receiveCommand(byte[] var1) throws CommunicationException {
        try {
            this.timeout = this.socket.getSoTimeout();
            DatagramPacket var2 = new DatagramPacket(var1, var1.length);
            this.socket.receive(var2);
            var1 = Arrays.copyOf(var2.getData(), var2.getLength());
        } catch (IOException var3) {
            throw new CommunicationException("Receive command exception " + var3.getMessage() + " (Timeout=" + this.timeout + " ms)");
        }
    }

    public InputStream getInputStream() {
        return this.inStream;
    }

    public OutputStream getOutputStream() {
        return this.outStream;
    }

    public String toString() {
        return this.ipAddress + ", " + this.ipPort;
    }

    protected void finalize() throws Throwable {
        try {
            this.close();
        } finally {
            super.finalize();
        }

    }

    public String getAddress() {
        return this.ipAddress.getHostAddress();
    }

    public int getPort() {
        return this.ipPort;
    }
}
