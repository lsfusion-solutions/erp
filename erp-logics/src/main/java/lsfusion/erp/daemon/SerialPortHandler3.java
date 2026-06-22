package lsfusion.erp.daemon;

import com.google.common.base.Throwables;
import lsfusion.interop.action.ClientActionDispatcher;
import purejavacomm.CommPort;
import purejavacomm.CommPortIdentifier;
import purejavacomm.NoSuchPortException;
import purejavacomm.PortInUseException;
import purejavacomm.SerialPort;
import purejavacomm.UnsupportedCommOperationException;

import java.util.HashMap;
import java.util.Map;
import java.util.TooManyListenersException;

import static lsfusion.base.BaseUtils.systemLogger;

public class SerialPortHandler3 {

    private static final Map<String, SerialPort> serialPortMap = new HashMap<>();

    public static void addSerialPort(ClientActionDispatcher dispatcher, String comPort, Integer baudRate, SerialPortEventListener3 listener)
            throws NoSuchPortException, PortInUseException, UnsupportedCommOperationException, TooManyListenersException {

        if (serialPortMap.isEmpty()) {
            dispatcher.addCleanListener(() -> {
                Throwable t = null;
                for (SerialPort port : serialPortMap.values()) {
                    try {
                        port.removeEventListener();
                        port.close();
                    } catch (Exception e) {
                        systemLogger.error("Error releasing serial port: ", e);
                        t = e;
                    }
                }
                serialPortMap.clear();
                if (t != null)
                    throw Throwables.propagate(t);
            });
        }

        final SerialPort serialPort = getSerialPort(comPort);

        serialPort.setSerialPortParams(baudRate, SerialPort.DATABITS_8, SerialPort.STOPBITS_1, SerialPort.PARITY_NONE);
        serialPort.addEventListener(event -> listener.serialEvent(event, serialPort));
        serialPort.notifyOnDataAvailable(true);

        serialPortMap.put(comPort, serialPort);
    }

    private static SerialPort getSerialPort(String comPort) throws NoSuchPortException, PortInUseException {
        SerialPort serialPort = serialPortMap.get(comPort);
        if (serialPort == null) {
            CommPortIdentifier portIdentifier = CommPortIdentifier.getPortIdentifier(comPort);
            if (portIdentifier.isCurrentlyOwned()) {
                throw new RuntimeException("Can't open port " + comPort + ". Try to close all other applications using this port and restart the client.");
            }
            CommPort commPort = portIdentifier.open("lsfusion", 2000);
            if (!(commPort instanceof SerialPort)) {
                commPort.close();
                throw new RuntimeException(comPort + " is not a serial port");
            }
            serialPort = (SerialPort) commPort;
        }
        return serialPort;
    }
}
