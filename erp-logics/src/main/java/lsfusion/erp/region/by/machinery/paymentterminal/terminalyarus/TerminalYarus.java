package lsfusion.erp.region.by.machinery.paymentterminal.terminalyarus;

import com.google.common.base.Throwables;
import org.apache.commons.codec.binary.Hex;
import org.apache.log4j.EnhancedPatternLayout;
import org.apache.log4j.FileAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import javax.naming.CommunicationException;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;

public class TerminalYarus {

    public static int counter = 0;

    static Logger logger;
    static {
        try {
            logger = Logger.getLogger("terminalLog");
            logger.setLevel(Level.INFO);
            FileAppender fileAppender = new FileAppender(new EnhancedPatternLayout("%d{DATE} %5p %c{1} - %m%n%throwable{1000}"),
                    "logs/terminal.log");
            logger.removeAllAppenders();
            logger.addAppender(fileAppender);

        } catch (Exception ignored) {
        }
    }

    public static String operation(String host, Integer port, int type, BigDecimal sum, String comment) throws RuntimeException {

        try {

            UDPPort udpPort = new UDPPort(host, port, 1000);

            udpPort.open();

            int attempt = 0;
            int result = -1;

            logger.info(String.format("Send: host %s, port %s, type %s, sum %s, comment '%s'", host, port, type, sum, comment));

            long un = 0;
            //-1 нет связи, -2 пришёл "левый" пакет, ждём дальше
            while((result == -1 && attempt < 5) || result == -2) {
                un = sendOperation(udpPort, type, sum, comment);
                result = receiveReply(udpPort);
                attempt++;
            }

            while(result == 1 && un != 0) {
                sendCheckStatus(udpPort, un);
                result = receiveReply(udpPort);
            }

            return getResultDescription(result);
        } catch (Exception e) {
            return e.getMessage();
        }

    }

    public static long sendOperation(UDPPort port, int type, BigDecimal sum, String comment) throws CommunicationException {//comment = "Тестовая печать";
        long un = System.currentTimeMillis();

        byte[] commentBytes = comment.getBytes(Charset.forName("cp1251"));

        ByteBuffer byteBuffer = ByteBuffer.allocate(19 + commentBytes.length);
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
        byteBuffer.putInt(++counter); //4 bytes
        byteBuffer.putLong(un); //8 bytes
        byteBuffer.put((byte) type); //1 byte
        byteBuffer.putInt((int) (sum.multiply(new BigDecimal(100)).doubleValue())); //4 bytes
        byteBuffer.putShort((short) 933); //2 bytes
        byteBuffer.put(commentBytes);

        logger.info(String.format("Sent bytes (operation): host %s, port %s, bytes %s", port.getAddress(), port.ipPort, Hex.encodeHexString(byteBuffer.array())));

        port.sendCommand(byteBuffer.array());
        return un;
    }

    public static void sendCheckStatus(UDPPort port, long un) throws CommunicationException {

        ByteBuffer byteBuffer = ByteBuffer.allocate(12);
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
        byteBuffer.putInt(++counter);
        byteBuffer.putLong(un);
        logger.info(String.format("Sent bytes (checkStatus): host %s, port %s, bytes %s", port.getAddress(), port.ipPort, Hex.encodeHexString(byteBuffer.array())));

        port.sendCommand(byteBuffer.array());
    }

    public static byte receiveReply(UDPPort port) {
        try {

            Thread.sleep(500);

            byte[] response = port.receiveCommand();

            logger.info(String.format("Received bytes: host %s, port %s, bytes %s", port.getAddress(), port.ipPort, Hex.encodeHexString(response)));

            ByteBuffer byteBuffer = ByteBuffer.wrap(response);
            byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
            int resultCounter = byteBuffer.getInt();
            if(resultCounter == counter) {
                byte resultCode = byteBuffer.get();
                logger.info("Terminal reply code: " + resultCode);
                switch (resultCode) {
                    case 0:
                    case 1:
                        byte[] un = new byte[8];
                        byteBuffer.get(un);
                        break;
                    case 2:
                        un = new byte[8];
                        byteBuffer.get(un);
                        byte[] bankReply = new byte[12];
                        byteBuffer.get(bankReply);
                        logger.info("Bank reply: " + new String(bankReply));
                        break;
                }
                return resultCode;
            } else return -2;
        }catch (SocketTimeoutException e) {
            logger.error("Receive reply error: ", e);
            return -1;
        } catch (IOException | InterruptedException e) {
            logger.error("Receive reply error: ", e);
            throw Throwables.propagate(e);
        }
    }

    private static String getResultDescription(int result) {
        switch (result) {
            case -1:
                return "Нет связи с терминалом";
            case 0:
                return "Операция не выполнена"; //кнопка cancel или недостаточно средств
            case 1:
                return "Не завершена предыдущая операция";
            case 2:
                return null; //"Операция выполнена
            case 3:
                return "Операция, выполняемая кассиром, не завершена";
            default:
                return "Код ошибки:" + result;
        }
    }

    private static class UDPPort {
        private DatagramSocket socket = null;
        private InetAddress ipAddress;
        private int ipPort;
        private int timeout;

        public UDPPort(String ipAddress, int ipPort, int timeout) throws UnknownHostException {
            this.ipAddress = InetAddress.getByName(ipAddress);
            this.ipPort = ipPort;
            this.timeout = timeout;
        }

        public void open() throws CommunicationException {
            try {
                socket = new DatagramSocket();
                socket.setSoTimeout(timeout);
            } catch (Exception e) {
                throw new CommunicationException(e.toString());
            }
        }

        public void close() throws CommunicationException {
            try {
                if(socket != null) {
                    socket.close();
                    socket = null;
                }
            } catch (Exception e) {
                throw new CommunicationException(e.toString());
            }
        }

        public void sendCommand(byte[] bytes) throws CommunicationException {
            try {
                DatagramPacket packet = new DatagramPacket(bytes, bytes.length, ipAddress, ipPort);
                socket.send(packet);
            } catch (IOException e) {
                throw new CommunicationException("Send command exception " + e);
            }
        }

        public byte[] receiveCommand() throws IOException {
            byte[] response = new byte[25];
            DatagramPacket packet = new DatagramPacket(response, response.length);
            socket.receive(packet);
            return packet.getData();
        }

        public String toString() {
            return ipAddress + ", " + ipPort;
        }

        protected void finalize() throws Throwable {
            try {
                close();
            } finally {
                super.finalize();
            }
        }

        public String getAddress() {
            return ipAddress.getHostAddress();
        }
    }
}

