package equ.clt.handler.cas;

import equ.api.scales.ScalesItemInfo;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.concurrent.*;

public class CL7000Handler extends CL5000JHandler {

    public CL7000Handler(FileSystemXmlApplicationContext springContext) {
        super(springContext, 400);
    }

    @Override
    protected String getLogPrefix() {
        return "CL7000: ";
    }

    @Override
    protected String sendTouchPlu(DataSocket socket, int pluNumber) throws IOException {
        Short dept = 1;
        String imageName = pluNumber + ".jpg";

        //10 + 2 bytes; 01.57,2 - ptype = 1, stype = W, data size = 2
        String deptRecord = "F=01.57,2:";
        //10 + 4 bytes; 02.4C,4 - ptype = 2, stype = L, data size = 4
        String pluRecord = "F=02.4C,4:";
        //10 + imageName length bytes; 65.53 - ptype = 101, stype = S
        String imageRecord = "F=65.53," + imageName.length() + ":" + imageName;

        Integer dataBlocksSize = deptRecord.length() + 2 + pluRecord.length() + 4 + imageRecord.length();

        //18 bytes
        String headerRecord = "W02A" + fillZeroes(Integer.toHexString(pluNumber), 5) + "," + fillZeroes(dept, 2) + "L" + fillZeroes(Integer.toHexString(dataBlocksSize), 4) + ":";

        ByteBuffer bytes = ByteBuffer.allocate(headerRecord.length() + dataBlocksSize + 1);
        bytes.order(ByteOrder.LITTLE_ENDIAN);

        bytes.put(getBytes(headerRecord));

        bytes.put(getBytes(deptRecord));
        bytes.putShort(dept);

        bytes.put(getBytes(pluRecord));
        bytes.putInt(pluNumber);

        bytes.put(getBytes(imageRecord));

        byte bcc = 0;
        for(int i = headerRecord.length(); i < headerRecord.length() + dataBlocksSize; i++) {
            bcc = (byte) (bcc ^ bytes.get(i));
        }

        bytes.put(bcc);

        return sendCommandTouch(socket, bytes.array()).error;
    }

    @Override
    protected String sendTouchSpeedKeys(DataSocket socket, List<ScalesItemInfo> itemsList) throws IOException {
        CL7000Reply speedKeys = readTouchSpeedKeys(socket);

        if(speedKeys.error != null) {
            return speedKeys.error;
        } else {

            //todo: temp log
            processTransactionLogger.info(getLogPrefix() + String.format("speedKeys read data (%s bytes): %s", speedKeys.data.length, Hex.encodeHexString(speedKeys.data)));

            ByteBuffer speedKeysByteBuffer = ByteBuffer.allocate(800);
            speedKeysByteBuffer.order(ByteOrder.LITTLE_ENDIAN);

            //парсим считанные клавиши (BIG_ENDIAN) и записываем в массив (LITTLE_ENDIAN)
            ByteBuffer readByteBuffer = ByteBuffer.wrap(ArrayUtils.subarray(speedKeys.data, 0, 800));
            while(readByteBuffer.remaining() > 0) {
                speedKeysByteBuffer.putInt(readByteBuffer.getInt());
            }

            for(ScalesItemInfo item : itemsList) {
                int pluNumber = getPluNumber(item.pluNumber, getBarcode(item));
                if(pluNumber <= 200) {
                    speedKeysByteBuffer.position((pluNumber - 1) * 4);
                    speedKeysByteBuffer.putInt(pluNumber);
                    //todo: temp log
                    processTransactionLogger.info(getLogPrefix() + "speedKeys write data: pluNumber " + pluNumber);
                }
            }

            //todo: temp log
            processTransactionLogger.info(getLogPrefix() + String.format("speedKeys write data (%s bytes): %s", speedKeysByteBuffer.array().length, Hex.encodeHexString(speedKeysByteBuffer.array())));

            return sendSpeedKeys(socket, speedKeysByteBuffer.array());
        }
    }

    @Override
    protected String deleteTouchSpeedKeys(DataSocket socket) throws IOException {
        //после стандартной очистки требуется некоторое время
        try {
            Thread.sleep(5000);
        } catch (InterruptedException ignored) {
        }

        //CL7000Reply speedKeys = readTouchSpeedKeys(socket);

        //if(speedKeys.error != null) {
        //    return speedKeys.error;
        //} else {

            //ByteBuffer speedKeysByteBuffer = ByteBuffer.wrap(ArrayUtils.subarray(speedKeys.data, 0, 800));
            ByteBuffer speedKeysByteBuffer = ByteBuffer.allocate(800);
            //speedKeysByteBuffer.order(ByteOrder.LITTLE_ENDIAN);

            //for(int i = 0; i < 200; i++) {
            //    speedKeysByteBuffer.putInt(0);
            //}
            return sendSpeedKeys(socket, speedKeysByteBuffer.array());
        //}
    }

    private String sendSpeedKeys(DataSocket socket, byte[] speedKeys) throws IOException {
        ByteBuffer bytes = ByteBuffer.allocate(821);
        bytes.order(ByteOrder.LITTLE_ENDIAN);
        bytes.put(getBytes("W04F020415,0000L320:")); //self key type = 02, scale type = 04, data size = 320

        bytes.put(speedKeys);

        byte bcc = 0;
        for (int i = 20; i < bytes.position(); i++) {
            bcc = (byte) (bcc ^ bytes.get(i));
        }

        bytes.put(bcc);

        return sendCommandTouch(socket, bytes.array()).error;
    }

    private CL7000Reply readTouchSpeedKeys(DataSocket socket) throws IOException {
        String record = "R04F15,00";
        ByteBuffer bytes = ByteBuffer.allocate(record.length() + 1);
        bytes.order(ByteOrder.LITTLE_ENDIAN);
        bytes.put(getBytes(record));
        bytes.put((byte) 0x0a);
        return sendCommandTouch(socket, bytes.array());
    }

    private CL7000Reply sendCommandTouch(DataSocket socket, byte[] bytes) throws IOException {
        socket.outputStream.write(bytes);
        byte[] result = receiveReplyTouch(socket);
        if (result[0] == 'E') {
            return new CL7000Reply(getErrorMessageTouch(new String(result).substring(1, 3)));
        } else {
            return new CL7000Reply(result);
        }
    }

    private byte[] receiveReplyTouch(DataSocket socket) {
        try {
            final Future<byte[]> future = Executors.newSingleThreadExecutor().submit((Callable) () -> {
                byte[] buffer = new byte[1024];
                socket.inputStream.read(buffer);
                return ArrayUtils.subarray(buffer, ArrayUtils.lastIndexOf(buffer, (byte) ':') + 1, buffer.length);
            });

            byte[] result;
            try {
                result = future.get(30000, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                processTransactionLogger.error("CL5000J: receive reply error", e);
                future.cancel(true);
                result = "E02".getBytes();
            }
            return result;
        } catch (Exception e) {
            processTransactionLogger.error("CL5000J: receive reply error", e);
            return "E01".getBytes();
        }
    }

    private static String fillZeroes(Object value, int len) {
        String result = String.valueOf(value);
        while(result.length() < len) {
            result = "0" + result;
        }
        return result;
    }

    private String getErrorMessageTouch(String errorNumber) {
        switch(errorNumber) {
            case "02": return "TimeoutException. Check logs";
            case "01": return "Exception occurred. Check logs";
            case "82": return "Mismatch Receive Data or Invalid Value";
            default: return "Unknown error " + errorNumber;
        }
    }

    private class CL7000Reply {
        String error;
        byte[] data;

        public CL7000Reply(String error) {
            this.error = error;
        }

        public CL7000Reply(byte[] data) {
            this.data = data;
        }
    }

}