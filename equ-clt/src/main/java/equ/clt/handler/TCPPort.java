package equ.clt.handler;

import lsfusion.base.Pair;

import javax.naming.CommunicationException;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

import static equ.clt.handler.HandlerUtils.defaultConnectTimeout;
import static equ.clt.handler.HandlerUtils.openSocket;
import static equ.clt.handler.HandlerUtils.parseHostPort;

public class TCPPort {
    private Socket socket = null;
    private String ipAddress;
    private int ipPort;
    private InputStream inStream;
    private BufferedInputStream bisStream;
    private OutputStream outStream;

    public TCPPort(String var1, int var2) {
        this.ipAddress = var1;
        this.ipPort = var2;
    }

    public static TCPPort fromAddress(String address, int defaultPort) {
        Pair<String, Integer> hostPort = parseHostPort(address, defaultPort);
        return new TCPPort(hostPort.first, hostPort.second);
    }

    public void open() throws CommunicationException {
        try {
            this.socket = openSocket(this.ipAddress, this.ipPort, defaultConnectTimeout, 0);
            this.socket.setSendBufferSize(1);
            this.inStream = this.socket.getInputStream();
            this.bisStream = new BufferedInputStream(this.socket.getInputStream());
            this.outStream = this.socket.getOutputStream();
        } catch (Exception var2) {
            throw new CommunicationException(var2.toString());
        }
    }

    public void close() throws CommunicationException {
        try {
            if(this.bisStream != null)
                bisStream.close();
            if(this.socket != null) {
                this.socket.close();
                this.socket = null;
            }

        } catch (Exception var2) {
            throw new CommunicationException(var2.toString());
        }
    }

    public InputStream getInputStream() {
        return this.inStream;
    }

    public BufferedInputStream getBisStream() {
        return bisStream;
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
        return this.ipAddress;
    }

    public int getPort() {
        return this.ipPort;
    }
    
    public void setSoTimeout(int timeout) throws CommunicationException {
        try {
            if (this.socket != null)
                this.socket.setSoTimeout(timeout);
        } catch (Exception e) {
            throw new CommunicationException(e.toString());
        }
    }
}
