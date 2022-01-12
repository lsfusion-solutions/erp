package equ.clt.handler.dibal;

import com.google.common.primitives.Bytes;
import equ.api.MachineryInfo;
import equ.api.scales.ScalesInfo;
import equ.api.scales.ScalesItem;
import equ.api.scales.TransactionScalesInfo;
import equ.api.stoplist.StopListInfo;
import equ.clt.handler.MultithreadScalesHandler;
import equ.clt.handler.TCPPort;
import lsfusion.base.ExceptionUtils;
import org.json.JSONObject;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import javax.naming.CommunicationException;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.ServerSocket;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static equ.clt.handler.HandlerUtils.*;

public class DibalD500Handler extends MultithreadScalesHandler {

    protected FileSystemXmlApplicationContext springContext;

    public DibalD500Handler(FileSystemXmlApplicationContext springContext) {
        this.springContext = springContext;
    }

    @Override
    protected String getLogPrefix() {
        return "Dibal D500: ";
    }

    private String openPort(TCPPort port, String ip) {
        try {
            processTransactionLogger.info(getLogPrefix() + "Connecting..." + ip);
            port.open();
        } catch (Exception e) {
            processTransactionLogger.error("Error: ", e);
            return e.getMessage();
        }
        return null;
    }

    protected List<ScalesInfo> getEnabledScalesList(TransactionScalesInfo transaction, List<MachineryInfo> succeededScalesList) {
        List<ScalesInfo> enabledScalesList = new ArrayList<>();
        for (ScalesInfo scales : transaction.machineryInfoList) {
            if (scales.succeeded)
                succeededScalesList.add(scales);
            else if (scales.enabled)
                enabledScalesList.add(scales);
        }
        if (enabledScalesList.isEmpty())
            for (ScalesInfo scales : transaction.machineryInfoList) {
                if (!scales.succeeded)
                    enabledScalesList.add(scales);
            }
        return enabledScalesList;
    }

    private void sendCommand(TCPPort port, byte[] command) throws IOException {
        try {
            port.getOutputStream().write(command);
        } finally {
            port.getOutputStream().flush();
        }
    }

    private void loadItem(TCPPort port, Set<String> loadedGroups, ScalesItem item) throws IOException {
        if(item.pluNumber != null) {
            JSONObject infoJSON = getExtInfo(item.info);
            String idItemGroup = infoJSON != null ? infoJSON.getString("numberGroup") : "1";
            String nameItemGroup = infoJSON != null ? infoJSON.getString("nameGroup") : "Все";
            Integer tareWeight = infoJSON != null ? infoJSON.optInt("tareWeight") : 0;

            List<byte[]> itemData = new ArrayList<>();

            itemData.add(getItemL2Bytes(item));
            itemData.add(getItemH3Bytes(item, idItemGroup, tareWeight));

            if (idItemGroup != null && !loadedGroups.contains(idItemGroup)) {
                itemData.add(getItemGroupZSBytes(idItemGroup, nameItemGroup));
                itemData.add(getItemGroupSTBytes(idItemGroup, false));
                loadedGroups.add(idItemGroup);
            }

            itemData.addAll(getImageData(item, idItemGroup));


            sendCommand(port, Bytes.concat(itemData.toArray(new byte[0][])));
        }
    }

    //implement in D900
    List<byte[]> getImageData(ScalesItem item, String idItemGroup) {
        return new ArrayList<>();
    }



    private JSONObject getExtInfo(String extInfo) {
        return extInfo != null ? new JSONObject(extInfo).optJSONObject("Dibal") : null;
    }

    private void clearItems(TCPPort port) throws IOException {
        sendCommand(port, getClearPBBytes());
            ServerSocket serverSocket = new ServerSocket(3001, 1000, Inet4Address.getByName(Inet4Address.getLocalHost().getHostAddress()));
            serverSocket.setSoTimeout(60000);
            serverSocket.accept(); // Блокирует выполнение, пока не придёт ответ.
    }

    private void clearGroups(TCPPort port) throws IOException {
        List<byte[]> clearGroupsData = new ArrayList<>();
        for(int i = 1; i < 254; i++) {
            clearGroupsData.add(getItemGroupSTBytes(String.valueOf(i), true));
        }
        sendCommand(port, Bytes.concat(clearGroupsData.toArray(new byte[0][])));
    }

    private byte[] getClearPBBytes() {
        ByteBuffer bytes = ByteBuffer.allocate(130);

        //6 bytes
        bytes.put(getCommandPrefixBytes("PB"));

        //request, 3 bytes (009 = Delete PLU)
        bytes.put(getBytes("009"));

        //request type, 2 bytes (00 = All)
        bytes.put(getBytes("00"));

        //answer, 2 bytes (00 = No, 01 = End)
        bytes.put(getBytes("00"));

        bytes.put(getBytes(fillZeroes(117)));

        return bytes.array();
    }

    private byte[] getItemL2Bytes(ScalesItem item) {
        ByteBuffer bytes = ByteBuffer.allocate(130);

        //6 bytes
        bytes.put(getCommandPrefixBytes("L2"));

        //key, 1 byte (A Creation, B Deletion, M Modification)
        bytes.put(getBytes("M"));

        //code, 6 bytes
        bytes.put(getBytes(prependZeroes(item.idBarcode, 6)));

        //quick code, 3 bytes
        bytes.put(getBytes(prependZeroes(item.pluNumber, 3)));

        //name, 24 bytes
        bytes.put(getNameBytes(item.name, 0));

        //name two, 24 bytes
        bytes.put(getNameBytes(item.name, 1));

        //name three, 24 bytes
        bytes.put(getNameBytes(item.name, 2));

        //price, 8 bytes
        bytes.put(getBytes(prependZeroes(safeMultiply(item.price, 100).intValue(), 8)));

        //offer price, 8 bytes
        bytes.put(getBytes(fillZeroes(8)));

        //cost price, 8 bytes
        bytes.put(getBytes(fillZeroes(8)));

        //direct key order, 3 bytes
        bytes.put(getBytes(fillSpaces(3)));

        //reference, 9 bytes
        bytes.put(getBytes(fillZeroes(9)));

        //free, 6 bytes
        bytes.put(getBytes(fillSpaces(6)));

        return bytes.array();
    }

    private byte[] getItemH3Bytes(ScalesItem item, String idItemGroup, int tareWeight) {
        ByteBuffer bytes = ByteBuffer.allocate(130);

        //6 bytes
        bytes.put(getCommandPrefixBytes("H3"));

        //code, 6 bytes
        bytes.put(getBytes(prependZeroes(item.idBarcode, 6)));

        //type of article, 1 byte (0 Weight, 1 Unit, 2 Fixed Weight, 3 Devolution, 4 Percentual Tare, 5 Counting)
        bytes.put(getBytes(isWeight(item, 0) ? "0" : "1"));

        //price per kg, 1 byte (0 Kg price, 1 100g price, 2 500g price)
        bytes.put(getBytes("0"));

        //best before, 6 bytes
        bytes.put(getBytes(item.expiryDate != null ? item.expiryDate.format(DateTimeFormatter.ofPattern("ddMMyy")) : "000000"));

        //extra date, 6 bytes
        bytes.put(getBytes(fillZeroes(6)));

        //packing date, 6 bytes
        bytes.put(getBytes(fillZeroes(6)));

        //tare, 5 bytes
        bytes.put(getBytes(prependZeroes(tareWeight, 5)));

        //percentage tare, 2 bytes (0-99)
        bytes.put(getBytes("00"));

        //label format, 2 bytes (0-60)
        bytes.put(getBytes("00"));

        //ean 13 format, 2 bytes
        bytes.put(getBytes("00"));

        //ean 128 format, 2 bytes
        bytes.put(getBytes("00"));

        //section, 4 bytes
        bytes.put(getBytes(prependZeroes(idItemGroup, 4)));

        //vat, 2 bytes (номер группы, не ставка НДС)
        bytes.put(getBytes("00"));

        //logo, 3 bytes
        bytes.put(getBytes(fillZeroes(3)));

        //product class, 2 bytes (0: Normal Item, 1-10: Animal class)
        bytes.put(getBytes("00"));

        //quick animal number, 3 bytes (1-99)
        bytes.put(getBytes(fillZeroes(3)));

        //rentability code, 1 byte
        bytes.put(getBytes("0"));

        //recipe, 3 bytes
        bytes.put(getBytes(fillZeroes(3)));

        //alter price, 1 byte (0 Allow, 1 Don't allow)
        bytes.put(getBytes("0"));

        //ean scanner, 13 bytes
        bytes.put(getBytes(fillSpaces(13)));

        //color logo (lsb), 4 bytes (image id)
        bytes.put(getBytes(prependZeroes(item.pluNumber + 100, 4)));

        //exact best before hour, 4 bytes
        bytes.put(getBytes(fillZeroes(4)));

        //lot number (msb), 3 bytes
        bytes.put(getBytes(fillZeroes(3)));

        //color logo (most signif), 2 bytes
        bytes.put(getBytes("00"));

        //batch promotion number, 2 bytes
        bytes.put(getBytes("00"));

        //weight for piece (g), 6 bytes (Active for counting article type)
        bytes.put(getBytes(fillZeroes(6)));

        //freeze date, 6 bytes
        bytes.put(getBytes(fillZeroes(6)));

        //label 2 format, 2 bytes (0-60)
        bytes.put(getBytes("00"));

        //color, 2 bytes (1- Red, 2- Green, 3- Blue, 4- Yellow, 5- Purple, 6- Cyan, 7- White, 8- Gray, 9- Black)
        bytes.put(getBytes("00"));

        //stock notify, 1 byte (0..1)
        bytes.put(getBytes("0"));

        //advertising image, 6 bytes (Default 0)
        bytes.put(getBytes(fillZeroes(6)));

        //printing image, 3 bytes
        bytes.put(getBytes("000"));

        //batch promotion number, 2 bytes
        bytes.put(getBytes("00"));

        //extended text number, 6 bytes
        bytes.put(getBytes(fillZeroes(6)));

        //free, 4 bytes
        bytes.put(getBytes(fillZeroes(4)));

        return bytes.array();
    }

    private byte[] getItemGroupZSBytes(String idItemGroup, String nameItemGroup) {
        ByteBuffer bytes = ByteBuffer.allocate(130);

        //6 bytes
        bytes.put(getCommandPrefixBytes("ZS"));

        //number section, 2 bytes
        bytes.put(getBytes(prependZeroes(idItemGroup, 2)));

        //name section, 20 bytes
        bytes.put(getBytes(appendSpaces(nameItemGroup, 20)));

        //number logo display (lsb), 3 bytes
        bytes.put(getBytes(prependZeroes(idItemGroup, 3)));

        //next section (Number section, name section, number logo display are repeated for another 3 sections), 75 bytes
        bytes.put(getBytes(fillSpaces(75)));

        //number logo display 1, 3 bytes
        bytes.put(getBytes("000"));

        bytes.put(getBytes(fillSpaces(21)));

        return bytes.array();
    }

    private byte[] getItemGroupSTBytes(String idItemGroup, boolean delete) {
        ByteBuffer bytes = ByteBuffer.allocate(130);

        //6 bytes
        bytes.put(getCommandPrefixBytes("ST"));

        //code, 2 bytes
        bytes.put(getBytes(prependZeroes(idItemGroup, 2)));

        //prefix, 3 bytes
        bytes.put(getBytes(fillSpaces(3)));

        //direct key, 3 bytes
        bytes.put(getBytes(delete ? "255" : prependZeroes(idItemGroup, 3)));

        bytes.put(getBytes(fillSpaces(116)));

        return bytes.array();
    }

    private byte[] getCommandPrefixBytes(String commandKey) {
        ByteBuffer bytes = ByteBuffer.allocate(6);

        //Scales number, 2 bytes
        bytes.put(getBytes("01"));

        //key, 2 bytes
        bytes.put(getBytes(commandKey));

        //group, 2 bytes
        bytes.put(getBytes("50"));

        return bytes.array();
    }

    protected void logError(List<String> errors, String errorText, Throwable t) {
        errors.add(errorText + (t == null ? "" : ('\n' + ExceptionUtils.getStackTraceString(t))));
        processTransactionLogger.error(errorText, t);
    }

    @Override
    public void sendStopListInfo(StopListInfo stopListInfo, Set<MachineryInfo> machineryInfoList) {
    }

    @Override
    protected SendTransactionTask getTransactionTask(TransactionScalesInfo transaction, ScalesInfo scales) {
        return new DibalD500SendTransactionTask(transaction, scales);
    }

    class DibalD500SendTransactionTask extends SendTransactionTask {

        public DibalD500SendTransactionTask(TransactionScalesInfo transaction, ScalesInfo scales) {
            super(transaction, scales);
        }

        @Override
        protected SendTransactionResult run() {
            boolean needToClear = !transaction.itemsList.isEmpty() && transaction.snapshot && !scales.cleared;
            List<String> localErrors = new ArrayList<>();
            TCPPort port = new TCPPort(scales.port, 3000);
            String openPortResult = openPort(port, scales.port);
            boolean cleared = false;
            if (openPortResult != null) {
                localErrors.add(openPortResult + ", transaction: " + transaction.id + ";");
            } else {
                try {
                    if (needToClear) {
                        processTransactionLogger.info(getLogPrefix() + "Clearing items..." + scales.port);
                        try {
                            clearItems(port);
                            clearGroups(port);
                            cleared = true; // Если не упала ошибка - значит, очистка выполнилась
                        } catch (IOException e) {
                            logError(localErrors, String.format(getLogPrefix() + "Clearing items failed. IP %s, transaction %s, error", scales.port, transaction.id, e.getMessage()), e);
                        }
                    }

                    if(cleared || !needToClear) {
                        processTransactionLogger.info(getLogPrefix() + "Sending items..." + scales.port);
                        int count = 0;
                        Set<String> loadedGroups = new HashSet<>();
                        for (ScalesItem item : transaction.itemsList) {
                            if (!Thread.currentThread().isInterrupted()) {
                                processTransactionLogger.info(String.format(getLogPrefix() + "IP %s, Transaction #%s, sending item #%s (barcode %s) of %s", scales.port, transaction.id, ++count, item.idBarcode, transaction.itemsList.size()));
                                loadItem(port, loadedGroups, item);
                            } else break;
                        }
                    }

                } catch (Exception e) {
                    logError(localErrors, String.format(getLogPrefix() + "IP %s error, transaction %s;", scales.port, transaction.id), e);
                } finally {
                    processTransactionLogger.info(getLogPrefix() + "Finally disconnecting..." + scales.port);
                    try {
                        port.close();
                    } catch (CommunicationException e) {
                        logError(localErrors, String.format(getLogPrefix() + "IP %s close port error ", scales.port), e);
                    }
                }
            }
            processTransactionLogger.info(getLogPrefix() + "Completed ip: " + scales.port);
            return new SendTransactionResult(scales, localErrors, cleared);
        }
    }

    private byte[] getNameBytes(String name, int index) {
        int start = index * 24;
        return getBytes(appendSpaces(name.length() > start ? name.substring(start, Math.min(name.length(), start + 24)) : "", 24));
    }

    private byte[] getBytes(String value) {
        return value.getBytes(Charset.forName("cp1251"));
    }
}