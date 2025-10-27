package equ.clt.handler.cas;

import equ.api.scales.ScalesItem;
import lsfusion.base.file.RawFileData;
import lsfusion.base.file.WriteUtils;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.ArrayUtils;
import org.json.JSONObject;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;
import java.util.concurrent.*;

import static equ.clt.handler.HandlerUtils.prependZeroes;
import static equ.clt.handler.HandlerUtils.trim;

public class CL7000Handler extends CL5000JHandler {

    public CL7000Handler(FileSystemXmlApplicationContext springContext) {
        super(springContext, 400);
    }

    @Override
    protected String getLogPrefix() {
        return "CL7000: ";
    }

    @Override
    protected String sendTouchPlu(DataSocket socket, RawFileData itemImage, int pluNumber) throws IOException {
        Short dept = 1;
        String imageName = pluNumber + ".jpg";

        if(itemImage != null) {
            //todo: refactor RawFileData> -> NamedFileData after upgrading equ to 6.1
            WriteUtils.storeFileToFTP("cas:cascl7200@" + socket.ip + "/image/" + imageName, itemImage, null);
        }

        //10 + 2 bytes; 01.57,2 - ptype = 1, stype = W, data size = 2
        String deptRecord = "F=01.57,2:";
        //10 + 4 bytes; 02.4C,4 - ptype = 2, stype = L, data size = 4
        String pluRecord = "F=02.4C,4:";
        //10 + imageName length bytes; 65.53 - ptype = 101, stype = S
        String imageRecord = "F=65.53," + imageName.length() + ":" + imageName;

        Integer dataBlocksSize = deptRecord.length() + 2 + pluRecord.length() + 4 + imageRecord.length();

        //18 bytes
        String headerRecord = "W02A" + prependZeroes(Integer.toHexString(pluNumber), 5) + "," + prependZeroes(dept, 2) + "L" + prependZeroes(Integer.toHexString(dataBlocksSize), 4) + ":";

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

    private JSONObject getExtInfo(String extInfo) {
        return getExtInfo(extInfo, "CL7000");
    }

    @Override
    protected String sendTouchSpeedKeys(DataSocket socket, List<ScalesItem> itemsList) throws IOException {

        Map<Integer, String> groups = new HashMap<>();
        for (ScalesItem item : itemsList) {
            JSONObject info = getExtInfo(item.info);
            if (info != null) {
                int numberGroup = info.optInt("numberGroup");
                String nameGroup = trim(info.optString("nameGroup", "group " + numberGroup), 30);
                if (numberGroup >= 1 && numberGroup <= 4) {
                    groups.put(numberGroup, nameGroup);
                }
            }
        }

        for (Map.Entry<Integer, String> group : groups.entrySet()) {
            CL7000Reply reply = sendGroup(socket, group.getKey(), group.getValue(), false);
            if (reply.error != null) {
                return reply.error;
            }
        }

        //если нет групп товаров, все товары пишем в первую
        if (groups.isEmpty()) {
            groups.put(1, "");
        }

        for (Integer currentGroup : groups.keySet()) {

            String numberGroup = getHEXNumberGroup(currentGroup);

            CL7000Reply speedKeys = readTouchSpeedKeys(socket, numberGroup);

            if (speedKeys.error != null) {
                return speedKeys.error;
            } else {

                byte[] itemBytes = ArrayUtils.subarray(speedKeys.data, 0, 800);
                List<Integer> items = new ArrayList<>();
                for (int i = 0; i < 800; i = i + 4) {
                    int item = convertByteArrayToInt(ArrayUtils.subarray(itemBytes, i, i + 4));
                    if (item > 0) {
                        items.add(item);
                    }
                }

                for (ScalesItem item : itemsList) {
                    JSONObject info = getExtInfo(item.info);
                    int itemGroup = info != null ? info.optInt("numberGroup") : 1;
                    if (itemGroup == currentGroup) {
                        int pluNumber = getPluNumber(item.pluNumber, getBarcode(item));
                        if (!items.contains(pluNumber)) {
                            items.add(pluNumber);
                        }
                    }
                }

                Collections.sort(items);
                //load max 200 sorted items
                if (items.size() > 200)
                    items = items.subList(0, 200);

                ByteBuffer speedKeysByteBuffer = ByteBuffer.allocate(800);
                speedKeysByteBuffer.order(ByteOrder.LITTLE_ENDIAN);
                for (Integer item : items) {
                    speedKeysByteBuffer.putInt(item);
                }

                CL7000Reply reply = sendSpeedKeys(socket, numberGroup, speedKeysByteBuffer.array());
                if (reply.error != null) {
                    return reply.error;
                }
            }
        }
        return null;
    }

    private int convertByteArrayToInt(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        return buffer.getInt();
    }

    @Override
    protected String deleteTouchSpeedKeys(DataSocket socket) throws IOException {
        //после стандартной очистки требуется некоторое время
        try {
            Thread.sleep(5000);
        } catch (InterruptedException ignored) {
        }

        for (int i = 1; i <= 4; i++) {

            CL7000Reply reply = sendGroup(socket, i, "", true);
            if (reply.error != null) {
                return reply.error;
            }

            ByteBuffer speedKeysByteBuffer = ByteBuffer.allocate(800);
            reply = sendSpeedKeys(socket, getHEXNumberGroup(i), speedKeysByteBuffer.array());
            if (reply.error != null) {
                return reply.error;
            }
        }
        return null;
    }

    //groups 1, 2, 3, 4 -> HEX 15, 16, 17, 18
    private String getHEXNumberGroup(Integer currentGroup) {
        return Integer.toHexString(20 + currentGroup);
    }

    private CL7000Reply sendGroup(DataSocket socket, int numberGroup, String nameGroup, boolean disable) throws IOException {

        String body = "P=" + prependZeroes(Integer.toHexString(nameGroup.length()).toUpperCase(), 2) + "." + nameGroup + "B=" + (disable ? "0" : "1") + ".";
        String header = "W32F08,00" + numberGroup + "L00" + prependZeroes(Integer.toHexString(body.length()).toUpperCase(), 2) + ":";

        byte[] bodyBytes = getBytes(body);

        byte bcc = 0;
        for(int i = 0; i < body.length(); i++) {
            bcc = (byte) (bcc ^ bodyBytes[i]);
        }

        ByteBuffer bytes = ByteBuffer.allocate(header.length() + body.length() + 1);
        bytes.order(ByteOrder.LITTLE_ENDIAN);
        bytes.put(getBytes(header));
        bytes.put(bodyBytes);
        bytes.put(bcc);
        return sendCommandTouch(socket, bytes.array());
    }

    private CL7000Reply sendSpeedKeys(DataSocket socket, String numberGroup, byte[] speedKeys) throws IOException {
        ByteBuffer bytes = ByteBuffer.allocate(821);
        bytes.order(ByteOrder.LITTLE_ENDIAN);
        bytes.put(getBytes("W04F0204" + numberGroup + ",0000L320:")); //self key type = 02, scale type = 04, data size = 320

        bytes.put(speedKeys);

        byte bcc = 0;
        for (int i = 20; i < bytes.position(); i++) {
            bcc = (byte) (bcc ^ bytes.get(i));
        }

        bytes.put(bcc);

        return sendCommandTouch(socket, bytes.array());
    }

    private CL7000Reply readTouchSpeedKeys(DataSocket socket, String numberGroup) throws IOException {
        String record = "R04F" + numberGroup + ",00";
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
            return new CL7000Reply(getErrorMessageTouch(new String(result).substring(1, 3), result));
        } else {
            return new CL7000Reply(result);
        }
    }

    private byte[] receiveReplyTouch(DataSocket socket) {
        try {
            final Future<byte[]> future = Executors.newSingleThreadExecutor().submit((Callable) () -> {
                byte[] buffer = new byte[1024];
                //noinspection ResultOfMethodCallIgnored
                socket.inputStream.read(buffer);

                return ArrayUtils.subarray(buffer, ArrayUtils.indexOf(buffer, (byte) ':') + 1, buffer.length);
            });

            byte[] result;
            try {
                result = future.get(30000, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                casLogger.error("CL5000J: receive reply error", e);
                future.cancel(true);
                result = "E02".getBytes();
            }
            return result;
        } catch (Exception e) {
            casLogger.error("CL5000J: receive reply error", e);
            return "E01".getBytes();
        }
    }

    private String getErrorMessageTouch(String errorNumber, byte[] bytes) {
        switch(errorNumber) {
            case "02": return "TimeoutException. Check logs";
            case "01": return "Exception occurred. Check logs";
            case "82": return "Mismatch Receive Data or Invalid Value";
            default: return "Unknown error " + Hex.encodeHexString(bytes);
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