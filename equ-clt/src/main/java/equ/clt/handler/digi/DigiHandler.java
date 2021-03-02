package equ.clt.handler.digi;

import equ.api.MachineryInfo;
import equ.api.scales.ScalesInfo;
import equ.api.scales.ScalesItem;
import equ.api.scales.TransactionScalesInfo;
import equ.clt.handler.MultithreadScalesHandler;
import lsfusion.base.ExceptionUtils;
import lsfusion.base.Pair;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.text.WordUtils;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static lsfusion.base.BaseUtils.nvl;

public class DigiHandler extends MultithreadScalesHandler {

    protected static short cmdWrite = 0xF1;
    protected static short cmdCls = 0xF2;
    protected static short filePLU = 0x25;
    protected static short fileIngredient = 0x3A;
    protected static short fileKeyAssignment = 0x41;
    protected static short fileDF = 0xDF;

    //включить для вывода в лог отправляемых запросов
    private boolean debugMode = false;

    protected FileSystemXmlApplicationContext springContext;

    public DigiHandler(FileSystemXmlApplicationContext springContext) {
        this.springContext = springContext;
    }

    @Override
    protected String getLogPrefix() {
        return "Digi SM300: ";
    }

    @Override
    protected SendTransactionTask getTransactionTask(TransactionScalesInfo transaction, ScalesInfo scales) {
        return new DigiSendTransactionTask(transaction, scales);
    }

    class DigiSendTransactionTask extends SendTransactionTask {
        private Integer maxLineLength;
        private Integer maxNameLength;
        private Integer maxNameLinesCount;
        private Integer fontSize;
        private boolean clearImages;

        public DigiSendTransactionTask(TransactionScalesInfo transaction, ScalesInfo scales) {
            super(transaction, scales);
            initSettings();
        }

        @Override
        protected Pair<List<String>, Boolean> run() throws Exception {
            List<String> localErrors = new ArrayList<>();
            boolean cleared = false;
            DataSocket socket = new DataSocket(scales.port);
            try {
                socket.open();
                int globalError = 0;
                boolean needToClear = !transaction.itemsList.isEmpty() && transaction.snapshot && !scales.cleared;
                if (needToClear)
                    cleared = clearFiles(socket, localErrors, clearImages);

                if (cleared || !needToClear) {
                    processTransactionLogger.info(getLogPrefix() + "Sending items..." + scales.port);
                    if (localErrors.isEmpty()) {
                        int count = 0;
                        for (ScalesItem item : transaction.itemsList) {
                            count++;
                            if (!Thread.currentThread().isInterrupted() && globalError < 3) {
                                processTransactionLogger.info(String.format(getLogPrefix() + "IP %s, Transaction #%s, sending item #%s (barcode %s) of %s", scales.port, transaction.id, count, item.idBarcode, transaction.itemsList.size()));
                                Integer pluNumber = getPlu(item);
                                if(item.idBarcode.length() <= 5) {
                                    if (!sendPLU(socket, localErrors, item, pluNumber)
                                            || !sendIngredient(socket, localErrors, item, pluNumber)
                                            || !sendKeyAssignment(socket, localErrors, item, pluNumber))
                                        globalError++;
                                } else {
                                    processTransactionLogger.info(String.format(getLogPrefix() + "Sending item %s to scales %s failed: incorrect barcode %s", pluNumber, scales.port, item.idBarcode));
                                }
                            } else break;
                        }
                    }
                    socket.close();
                }

            } catch (Exception e) {
                logError(localErrors, String.format(getLogPrefix() + "IP %s error, transaction %s;", scales.port, transaction.id), e);
            } finally {
                processTransactionLogger.info(getLogPrefix() + "Finally disconnecting..." + scales.port);
                socket.close();
            }
            processTransactionLogger.info(getLogPrefix() + "Completed ip: " + scales.port);
            return Pair.create(localErrors, cleared);
        }
        //------------------------------------ methods to override ------------------------------------//

        protected void initSettings() {
            DigiSettings digiSettings = springContext.containsBean("digiSettings") ? (DigiSettings) springContext.getBean("digiSettings") : new DigiSettings();
            maxLineLength = nvl(digiSettings.getMaxLineLength(), 50);
            maxNameLength = digiSettings.getMaxNameLength();
            maxNameLinesCount = digiSettings.getMaxNameLinesCount();
            fontSize = nvl(digiSettings.getFontSize(), 4);
            clearImages = digiSettings.isClearImages();
        }

        protected boolean clearFiles(DataSocket socket, List<String> localErrors, boolean clearImages) throws IOException {
            return clearFile(socket, localErrors, scales.port, filePLU);
        }

        protected boolean sendPLU(DataSocket socket, List<String> localErrors, ScalesItem item, Integer plu) throws IOException {
            byte[] record = makePluRecord(item, getWeightCode(scales), getPieceCode(scales));
            processTransactionLogger.info(String.format(getLogPrefix() + "Sending plu file item %s to scales %s", plu, scales.port));
            int reply = sendRecord(socket, cmdWrite, filePLU, record);
            if (reply != 0) {
                logError(localErrors, String.format(getLogPrefix() + "Send item %s to scales %s failed. Error: %s", plu, scales.port, reply));
            }
            return reply == 0;
        }

        protected boolean sendIngredient(DataSocket socket, List<String> localErrors, ScalesItem item, Integer plu) throws IOException {
            return true;
        }

        protected boolean sendKeyAssignment(DataSocket socket, List<String> localErrors, ScalesItem item, Integer plu) throws IOException {
            return true;
        }

        protected Integer getMaxCompositionLinesCount() {
            return 9;
        }

        protected String getPluNumberForPluRecord(ScalesItem item) {
            return item.pluNumber != null ? String.valueOf(item.pluNumber) : item.idBarcode;
        }

        protected BigDecimal getTareWeight(ScalesItem item) {
            return null;
        }

        //------------------------------------ methods to override ------------------------------------//

        private byte[] makePluRecord(ScalesItem item, String weightCode, String pieceCode) throws UnsupportedEncodingException {
            boolean hasDescription = item.description != null && !item.description.isEmpty();
            String[] splittedDescription = hasDescription ? item.description.split("@@") : null;

            String compositionMessage = splittedDescription != null ? splittedDescription[0] : null;
            boolean hasComposition = compositionMessage != null && !compositionMessage.isEmpty();
            List<String> compositionLines = new ArrayList<>();
            int compositionLength = 0;
            if(hasComposition) {
                for (String compositionLine : compositionMessage.split("\n")) {
                    while (compositionLine.length() > maxLineLength) {
                        compositionLines.add(compositionLine.substring(0, maxLineLength));
                        compositionLength += maxLineLength + 1;
                        compositionLine = compositionLine.substring(maxLineLength);
                    }
                    compositionLines.add(compositionLine);
                    compositionLength += compositionLine.length() + 1;
                }
            }

            String expiryMessage = splittedDescription != null && splittedDescription.length > 1 ? splittedDescription[1] : null;
            boolean hasExpiry = expiryMessage != null && !expiryMessage.isEmpty();
            List<String> expiryLines = new ArrayList<>();
            int expiryLength = 0;
            if(hasExpiry) {
                for (String expiryLine : expiryMessage.split("\n")) {
                    while (expiryLine.length() > maxLineLength) {
                        expiryLines.add(expiryLine.substring(0, maxLineLength));
                        expiryLength += maxLineLength + 1;
                        expiryLine = expiryLine.substring(maxLineLength);
                    }
                    expiryLines.add(expiryLine);
                    expiryLength += expiryLine.length() + 1;
                }
            }

            Integer maxCompositionLinesCount = getMaxCompositionLinesCount();
            if(maxCompositionLinesCount != null && compositionLines.size() > maxCompositionLinesCount)
                compositionLines = compositionLines.subList(0, maxCompositionLinesCount);

            List<String> headers = new ArrayList<>();
            for(String line : item.name.split("\n")) {
                headers.addAll(Arrays.asList(WordUtils.wrap(line, maxNameLength != null ? maxNameLength : line.length(), "\n", true).split("\n")));
            }

            if(maxNameLinesCount != null && headers.size() > maxNameLinesCount) {
                headers = headers.subList(0, maxNameLinesCount);
            }

            int headersLength = 3 * headers.size() - 1; //на первую строку + 2 байта, на остальные + 3 байта
            for(String header : headers) {
                headersLength += header.length();
            }

            int length = 36 + headersLength +
                    compositionLength + (compositionLines.isEmpty() ? 0 : compositionLines.size() * 2) +
                    expiryLength + (expiryLines.isEmpty() ? 0 : expiryLines.size() * 2);

            ByteBuffer bytes = ByteBuffer.allocate(length);
            bytes.order(ByteOrder.LITTLE_ENDIAN);

            // Номер PLU, 4 bytes
            bytes.put(getHexBytes(fillLeadingZeroes(getPluNumberForPluRecord(item), 8)));

            //Длина записи, заполняется в конце
            bytes.put((byte) 0);
            bytes.put((byte) 0);

            // 1-й байт 1-го статуса
            boolean pieceItem = item.shortNameUOM != null && item.shortNameUOM.toUpperCase().startsWith("ШТ");
            byte st1b1 = 0;
            if (pieceItem)
                st1b1 = setBit(st1b1, 0); //штучный
            st1b1 = setBit(st1b1, 2); //печатать дату продажи
            st1b1 = setBit(st1b1, 4); //печатать дату упаковки
            st1b1 = setBit(st1b1, 6); //печатать время упаковки
            bytes.put(st1b1);

            // 2-й байт 1-го статуса
            byte st1b2 = 0;
            bytes.put(st1b2);

            // 1-й байт 2-го статуса
            byte st2b1 = 0;
            st2b1 = setBit(st2b1, 0); //Формат 1-й этикетки Указан явно
            st2b1 = setBit(st2b1, 2); //Формат штрихкода Указан явно
            st2b1 = setBit(st2b1, 3); //Артикул товара Указан явно
            BigDecimal tareWeight = getTareWeight(item);
            if(tareWeight != null) {
                st2b1 = setBit(st2b1, 6); //Поле «Вес тары» Есть
            }
            bytes.put(st2b1);

            // 2-й байт 2-го статуса
            byte st2b2 = 0;
            st2b2 = setBit(st2b2, 1); //Поле «Номера спец.сообщения» Есть
            st2b2 = setBit(st2b2, 2); //Поле «Номер ингредиента» Есть
            st2b2 = setBit(st2b2, 5); //Поле «Название товара» Есть
            if (hasExpiry)
                st2b2 = setBit(st2b2, 6); //Поле «Текст встроенного в PLU ингредиента» Есть
            if (hasComposition)
                st2b2 = setBit(st2b2, 7); // Поле «Текст встроенного в PLU спец. сообщения» Есть
            bytes.put(st2b2);

            // 3-й байт 2-го статуса
            byte st2b3 = 0;
            st2b3 = setBit(st2b3, 0);
            bytes.put(st2b3);

            // Цена, 4 bytes
            int price = item.price == null ? 0 : item.price.multiply(new BigDecimal(100)).intValue();
            bytes.put(getHexBytes(fillLeadingZeroes(String.valueOf(price), 8)));

            // номер формата 1-й этикетки
            bytes.put((byte) 17);

            // номер формата штрихкода
            bytes.put((byte) 5);

            // данные штрихкода, 7 bytes
            String prefix = pieceCode != null && pieceItem ? pieceCode : weightCode;
            String barcode = fillTrailingZeroes(prefix + item.idBarcode, 13) + (pieceItem ? 2 : 1);
            bytes.put(getHexBytes(barcode));

            // срок продажи в днях, 2 bytes
            bytes.put(getHexBytes(fillLeadingZeroes(String.valueOf(item.daysExpiry == null ? 0 : item.daysExpiry), 4)));

            if(tareWeight != null) {
                //Вес тары в граммах, 2 bytes
                bytes.put(getHexBytes(fillLeadingZeroes(String.valueOf(tareWeight.intValue()), 4)));
            }

            // номер спец. сообщения
            bytes.put((byte) 0);

            // номер ингредиента
            bytes.put((byte) 0);

            for (int i = 0; i < headers.size(); i++) {
                String header = headers.get(i);

                //шрифт наименования
                bytes.put((byte) fontSize.intValue());
                //длина наименования
                bytes.put((byte) header.length());
                // Наименование товара
                bytes.put(getBytes(header));

                if(i < (headers.size() - 1)) {
                    //терминатор промежуточной строки
                    bytes.put((byte) 0x0D);
                } else {
                    //терминатор последней строки
                    bytes.put((byte) 0x0C);
                }
            }

            //todo: макс. длина строки состава и спец.сообщения - 51 символ, хорошо бы ещё и на это смотреть
            // Состав
            if (hasComposition) {
                for (int i = 0; i < compositionLines.size(); i++) {
                    bytes.put((byte) 2);
                    bytes.put((byte) compositionLines.get(i).length());
                    bytes.put(getBytes(compositionLines.get(i)));
                    bytes.put((byte) (i == compositionLines.size() - 1 ? 0x0C : 0x0D));
                }
            }

            // Специальное сообщение
            if (hasExpiry) {
                for (int i = 0; i < expiryLines.size(); i++) {
                    bytes.put((byte) 2);
                    bytes.put((byte) expiryLines.get(i).length());
                    bytes.put(getBytes(expiryLines.get(i)));
                    bytes.put((byte) (i == expiryLines.size() - 1 ? 0x0C : 0x0D));
                }
            }

            // Контрольная сумма
            bytes.put((byte) 0);

            //Длина записи
            bytes.put(4, (byte) (length >>> 8));
            bytes.put(5, (byte) length);

            return bytes.array();
        }

        private byte setBit(byte byteValue, int pos) {
            return (byte) (byteValue | (1 << pos));
        }

        /*private byte clearBit(byte byteValue, int pos) {
            return (byte) (byteValue & ~(1 << pos));
        }*/

        private String fillTrailingZeroes(String input, int length) {
            if (input == null)
                return null;
            while (input.length() < length)
                input = input + "0";
            return input;
        }

        private String getWeightCode(MachineryInfo scales) {
            String weightCode = scales instanceof ScalesInfo ? ((ScalesInfo) scales).weightCodeGroupScales : null;
            return weightCode == null ? "21" : weightCode;
        }

        private String getPieceCode(MachineryInfo scales) {
            return scales instanceof ScalesInfo ? ((ScalesInfo) scales).pieceCodeGroupScales : null;
        }

        protected byte[] getHexBytes(String value) {
            int len = value.length();
            byte[] data = new byte[len / 2];
            for (int i = 0; i < len; i += 2) {
                data[i / 2] = (byte) ((Character.digit(value.charAt(i), 16) << 4)
                        + Character.digit(value.charAt(i + 1), 16));
            }
            return data;
        }

        protected boolean clearFile(DataSocket socket, List<String> localErrors, String port, short file) throws IOException {
            processTransactionLogger.info(getLogPrefix() + String.format("Deleting file %s at scales %s", file, port));
            int reply = sendRecord(socket, cmdCls, file, new byte[0]);
            if (reply != 0)
                logError(localErrors, String.format("Deleting file %s at scales %s failed. Error: %s\n", file, port, reply));
            return reply == 0;
        }

        protected int sendRecord(DataSocket socket, short cmd, short file, byte[] record) throws IOException {
            ByteBuffer bytes = ByteBuffer.allocate(record.length + 2);
            bytes.order(ByteOrder.LITTLE_ENDIAN);
            bytes.put((byte) cmd);
            bytes.put((byte) file);
            bytes.put(record);
            return sendCommand(socket, bytes.array());
        }

        private int sendCommand(DataSocket socket, byte[] bytes) throws IOException {
            if(debugMode)
                processTransactionLogger.info(Hex.encodeHexString(bytes));
            socket.outputStream.write(bytes);
            socket.outputStream.flush();
            return receiveReply(socket);
        }

        private int receiveReply(DataSocket socket) {
            try {
                byte[] buffer = new byte[10];
                socket.inputStream.read(buffer);
                return buffer[0] == 6 ? 0 : buffer[0]; //это либо байт ошибки, либо первый байт хвоста (:)
            } catch (Exception e) {
                processTransactionLogger.error(getLogPrefix() + "ReceiveReply Error: ", e);
                return -1;
            }
        }

        protected Integer getPlu(ScalesItem item) {
            return item.pluNumber == null ? Integer.parseInt(item.idBarcode) : item.pluNumber;
        }

        protected byte[] getBytes(String value) throws UnsupportedEncodingException {
            return value.getBytes("cp866");
        }

        protected byte[] getBytes(String value, int length) throws UnsupportedEncodingException {
            ByteBuffer bytes = ByteBuffer.allocate(length);
            bytes.put(getBytes(value.substring(0, Math.min(value.length(), length))));
            return bytes.array();
        }

        protected void logError(List<String> errors, String errorText) {
            logError(errors, errorText, null);
        }

        protected void logError(List<String> errors, String errorText, Throwable t) {
            errors.add(errorText + (t == null ? "" : ('\n' + ExceptionUtils.getStackTraceString(t))));
            processTransactionLogger.error(errorText, t);
        }
    }
}