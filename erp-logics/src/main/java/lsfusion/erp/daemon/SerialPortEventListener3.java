package lsfusion.erp.daemon;

import purejavacomm.SerialPort;
import purejavacomm.SerialPortEvent;

public interface SerialPortEventListener3 {

    void serialEvent(SerialPortEvent event, SerialPort serialPort);
}
