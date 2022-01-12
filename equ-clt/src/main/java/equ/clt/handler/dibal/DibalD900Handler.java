package equ.clt.handler.dibal;

import equ.api.MachineryInfo;
import equ.api.scales.ScalesInfo;
import equ.api.scales.ScalesItem;
import equ.api.scales.TransactionScalesInfo;
import equ.api.stoplist.StopListInfo;
import equ.clt.handler.MultithreadScalesHandler;
import equ.clt.handler.TCPPort;
import lsfusion.base.ExceptionUtils;
import lsfusion.base.Pair;
import org.apache.commons.lang3.ArrayUtils;
import org.json.JSONObject;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import javax.naming.CommunicationException;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.ServerSocket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static equ.clt.handler.HandlerUtils.*;

public class DibalD900Handler extends MultithreadScalesHandler {

    protected FileSystemXmlApplicationContext springContext;

    public DibalD900Handler(FileSystemXmlApplicationContext springContext) {
        this.springContext = springContext;
    }

    @Override
    protected String getLogPrefix() {
        return "Dibal D900: ";
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

    private void loadItem(TCPPort port, ScalesItem item) throws IOException {
        JSONObject infoJSON = getExtInfo(item.info);
        String idItemGroup = infoJSON != null ? infoJSON.getString("numberGroup") : "1";
        String nameItemGroup = infoJSON != null ? infoJSON.getString("nameGroup") : "Все";
        Integer tareWeight = infoJSON != null ? infoJSON.optInt("tareWeight") : 0;

        sendCommand(port, getItemL2Bytes(item));
        sendCommand(port, getItemH3Bytes(item, idItemGroup, tareWeight));

        if(nameItemGroup != null) {
            sendCommand(port, getItemGroupZSBytes(idItemGroup, nameItemGroup));
            sendCommand(port, getItemGroupSTBytes(idItemGroup));
            if(item.groupImage != null) {
                for (byte[] command : createImageCommands(item.groupImage.getBytes(), idItemGroup)) {
                    sendCommand(port, command);
                }
            }
        }

        if(item.itemImage != null) {
            for (byte[] command : createImageCommands(item.itemImage.getBytes(), String.valueOf(item.pluNumber + 100))) {
                sendCommand(port, command);
            }
        }
    }

    private JSONObject getExtInfo(String extInfo) {
        return extInfo != null ? new JSONObject(extInfo).optJSONObject("Dibal") : null;
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

    private byte[] getItemGroupSTBytes(String idItemGroup) {
        ByteBuffer bytes = ByteBuffer.allocate(130);

        //6 bytes
        bytes.put(getCommandPrefixBytes("ST"));

        //code, 2 bytes
        bytes.put(getBytes(prependZeroes(idItemGroup, 2)));

        //prefix, 3 bytes
        bytes.put(getBytes(fillSpaces(3)));

        //direct key, 3 bytes
        bytes.put(getBytes(prependZeroes(idItemGroup, 3)));

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
        return new DibalD900SendTransactionTask(transaction, scales);
    }

    class DibalD900SendTransactionTask extends SendTransactionTask {

        public DibalD900SendTransactionTask(TransactionScalesInfo transaction, ScalesInfo scales) {
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
                        sendCommand(port, getClearPBBytes());
                        try {
                            ServerSocket serverSocket = new ServerSocket(3001, 1000, Inet4Address.getByName(Inet4Address.getLocalHost().getHostAddress()));
                            serverSocket.setSoTimeout(60000);
                            serverSocket.accept(); // Блокирует выполнение, пока не придёт ответ.
                            cleared = true;
                            // Нам неважно, что пришло в ответ, главное, что мы его дождались - значит, очистка выполнилась
                        } catch (IOException e) {
                            logError(localErrors, String.format(getLogPrefix() + "Clearing items failed. IP %s, transaction %s, error", scales.port, transaction.id, e.getMessage()), e);
                        }
                    }

                    if(cleared || !needToClear) {
                        processTransactionLogger.info(getLogPrefix() + "Sending items..." + scales.port);
                        int count = 0;
                        for (ScalesItem item : transaction.itemsList) {
                            if (!Thread.currentThread().isInterrupted()) {
                                processTransactionLogger.info(String.format(getLogPrefix() + "IP %s, Transaction #%s, sending item #%s (barcode %s) of %s", scales.port, transaction.id, ++count, item.idBarcode, transaction.itemsList.size()));
                                loadItem(port, item);
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

    // ---------------- sending images ---------------- //

    private List<byte[]> createImageCommands(byte[] imageBytes, String article) {
        // ширина - 4 байта с 18 по 21 включая
        int width = convertByteArrayToInt(new byte[]{imageBytes[18], imageBytes[19], imageBytes[20], imageBytes[21]});

        // высота - 4 байта с 18 по 21 включая
        int height = convertByteArrayToInt(new byte[]{imageBytes[22], imageBytes[23], imageBytes[24], imageBytes[25]});

        //конвертирование пикселей из 32 битных в 16 битные
        Pair<List<Character>, int[]> convertedBytesAndImageColorsExists = createConvertPixelsFrom32To16Bit(imageBytes, width);
        List<Character> image16Bytes = convertedBytesAndImageColorsExists.first; //картинка, каждый пиксел сконвертирован в 16 битный
        int[] colorsExistArray = convertedBytesAndImageColorsExists.second; //массив существования цветов, цвет - индекс в массиве, значение - количество повторов

        List<Color> colors = createColorsListFromExistsList(colorsExistArray);
        sortColorsByDescendingRepetitionsCount(colors);

        List<Color> pallete = createPalette(colors);
        int[] colorsMapping = createMappingBetweenPalleteAndColors(pallete);

        List<Byte> indexedPixels = convertPixelsColorsToPalleteColorsIndexes(pallete, image16Bytes, colorsMapping);
        List<Byte> palleteBytes = convertPalleteColorsToBytes(pallete);

        List<Byte> resultImage = new ArrayList<>();
        resultImage.addAll(Arrays.asList(ArrayUtils.toObject(convertIntToByteArray(width))));
        resultImage.addAll(Arrays.asList(ArrayUtils.toObject(convertIntToByteArray(height))));
        resultImage.addAll(palleteBytes);
        resultImage.addAll(indexedPixels);


        return createRegistersFromImage(resultImage, article);
    }

    private int convertByteArrayToInt(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        return buffer.getInt();
    }

    private byte[] convertIntToByteArray(int value) {
        ByteBuffer buffer = ByteBuffer.allocate(4);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(value);
        return buffer.array();
    }

    // конвертирование всех пикселей картинки из 32 битных в 16 битные
    private Pair<List<Character>, int[]> createConvertPixelsFrom32To16Bit(byte[] imageBytes, int width) {
        // глубина цвета, картинка должна быть rgb обычная 32 - разрядная
        int numBytesColor = convertByteArrayToInt(new byte[]{imageBytes[28], imageBytes[29], 0, 0}) / 8;
        // в файле картинки в каждой строке данных есть байты отступа, равные 0, которые дополняют строку
        // до количества байт, которое будет делиться на 4. весы эти байты ломают, их надо пропустить
        int skipPaddingBytes = (width * numBytesColor) % 4;

        List<Character> image16Bytes = new ArrayList<>(); //картинка, каждый пиксель сконвертирован в 16 битный
        List<Character> image16BytesOneRow = new ArrayList<>(); //одна строка сконвертированная
        int[] colorsExistArray = new int[65536]; // массив существования цветов, цвет - индекс в массиве, значение - количество повторов

        int startImage = convertByteArrayToInt(new byte[]{imageBytes[10], imageBytes[11], imageBytes[12], imageBytes[13]});
        if (startImage == 0) {
            startImage = 54;
        }
        int columnIndex = 0;
        while (startImage + 2 < imageBytes.length) {
            if (columnIndex * numBytesColor + skipPaddingBytes >= width * numBytesColor) {
                image16Bytes.addAll(0, image16BytesOneRow);
                image16BytesOneRow.clear();

                startImage += skipPaddingBytes;
                columnIndex = 0;

                continue;
            }

            char pix = (char) ((((imageBytes[startImage + 2] & 0xFF) >> 3) << 11) | (((imageBytes[startImage + 1] & 0xFF) >> 2) << 5) | ((imageBytes[startImage] & 0xFF) >> 3));
            image16BytesOneRow.add(pix);

            colorsExistArray[pix] += 1;

            startImage += numBytesColor;
            columnIndex++;
        }
        return Pair.create(image16Bytes, colorsExistArray);
    }

    private List<Color> createColorsListFromExistsList(int[] colorsExistArray) {
        List<Color> colors = new ArrayList<>();
        for (int j = 0; j < 65536; j++) {
            if (colorsExistArray[j] != 0) {
                char colorCode = (char) j;
                colors.add(new Color(colorCode, (byte) ((colorCode >>> 11) & 0x1F), (byte) ((colorCode >>> 5) & 0x3F), (byte) (colorCode & 0x1F), colorsExistArray[j]));
            }
        }
        return colors;
    }

    private void sortColorsByDescendingRepetitionsCount(List<Color> colors) {
        colors.sort((o1, o2) -> o1 != null && o2 != null ? (o2.repeated - o1.repeated) : 1);
    }

    private double getColorsDistance(double redColor, double greenColor, double blueColor, Color paletteColor) {
        double redColorDistance = Math.pow(redColor - paletteColor.red, 2.0);
        double greenColorDistance = Math.pow(greenColor - paletteColor.green, 2.0);
        double blueColorDistance = Math.pow(blueColor - paletteColor.blue, 2.0);

        return Math.sqrt(redColorDistance + greenColorDistance + blueColorDistance);
    }

    private byte getNearestColor(List<Color> pallete, Character color) {
        byte greenColor = (byte) ((color >>> 5) & 0x3F);
        byte redColor = (byte) ((color >>> 11) & 0x1F);
        byte blueColor = (byte) (color & 0x1F);

        double minDistance = 1000000.0;
        byte nearestPaletteColorIndex = 0;

        for (int i = 0; i < pallete.size(); i++) {
            if (color == pallete.get(i).colorCode) {
                return (byte) i;
            } else {
                double colorsDistance = getColorsDistance(redColor, greenColor, blueColor, pallete.get(i));

                if (colorsDistance < minDistance) {
                    minDistance = colorsDistance;
                    nearestPaletteColorIndex = (byte) i;
                }
            }
        }
        return nearestPaletteColorIndex;
    }


    private boolean isSimilarColor(List<Color> pallete, Color color, double criticalDistance) {
        for (Color value : pallete) {
            double redColorDistance = Math.pow(color.red - value.red, 2.0);
            double greenColorDistance = Math.pow(color.green - value.green, 2.0);
            double blueColorDistance = Math.pow(color.blue - value.blue, 2.0);

            double colorsDistance = Math.sqrt(redColorDistance + greenColorDistance + blueColorDistance);

            if (colorsDistance < criticalDistance) {
                return true;
            }
        }

        return false;
    }


    private List<Color> createNewPalleteList(List<Color> colors) {
        List<Color> pallete = new ArrayList<>();

        //Если в изображении есть белый цвет, то добавляем его в палитру
        if (colors.stream().anyMatch(color -> color.colorCode == (char) 0xFFFF)) {
            pallete.add(new Color((char) 0xFFFF, (byte) 0x1F, (byte) 0x3F, (byte) 0x1F, 1));
        }

        //Если в изображении есть чёрный цвет, то добавляем его в палитру
        if (colors.stream().anyMatch(color -> color.colorCode == (char) 0x0000)) {
            pallete.add(new Color((char) 0x0000, (byte) 0, (byte) 0, (byte) 0, 1));
        }
        return pallete;
    }


    private List<Color> createPalette(List<Color> colors) {
        List<Color> pallete = new ArrayList<>();
        if (colors.size() > 255) {
            double distance;

            //Определение критического значения дистанции между цветами
            if (colors.size() < 500) {
                distance = 1.0;
            } else if (colors.size() < 1000) {
                distance = 1.5;
            } else if (colors.size() < 1250) {
                distance = 1.8;
            } else {
                distance = 2.0;
            }

            while (distance < 7.5) {
                pallete = createNewPalleteList(colors);

                //Формируем палитру из цветов по заданному значению расстояния
                for (Color color : colors) {
                    if (color.colorCode != 0 && color.colorCode != (char) 0xFFFF) {
                        if (!isSimilarColor(pallete, color, distance)) {
                            pallete.add(color);
                        }
                    }

                    if (pallete.size() >= 256) {
                        break;
                    }
                }

                //Если не удалось вместить цвета в палитру при таком критическом значении расстояния
                if (pallete.size() >= 256) {
                    //Создаём новую палитру
                    distance += 0.1;
                } else {
                    //Получена корректная палитра цветов
                    break;
                }
            }

            //Если палитра не заполнена до конца, то дополняем её цветами, которых в ней нет
            if (pallete.size() < 255) {
                for (int i = 0; i < colors.size(); i++) {
                    int finalI = i;
                    if (pallete.stream().noneMatch(color -> color.colorCode == colors.get(finalI).colorCode)) {
                        pallete.add(colors.get(i));
                    }

                    if (pallete.size() >= 256) {
                        break;
                    }
                }
            }

            //Для прозрачности
            pallete.set(255, new Color((char) 0xFFFF, (byte) 0x1F, (byte) 0x3F, (byte) 0x1F, 1));
        } else {
            pallete = createNewPalleteList(colors);

            //добавляем в палитру все цвета
            pallete.addAll(colors);

            //если цветов в палитре не хватает, то добавляем белый до конца
            if (colors.size() < 256) {
                for (int j = pallete.size(); j < 256; j++) {
                    pallete.add(new Color((char) 0xFFFF, (byte) 0x1F, (byte) 0x3F, (byte) 0x1F, 1));
                }
            }
        }
        return pallete;
    }

    //Создание тблицы соответствия между цветами и их индексами в палитре
    private int[] createMappingBetweenPalleteAndColors(List<Color> pallete) {
        int[] colorsMapping = new int[65536];

        //Устанавливаем соответсвие между цветом и его индексов в палитре цветов
        for (int i = 0; i < pallete.size(); i++) {
            colorsMapping[pallete.get(i).colorCode] = i;
        }

        //Белый цвет должен быть 256-ым в палитре
        if (colorsMapping[0xFFFF] == 0) {
            colorsMapping[0xFFFF] = 255;
        }
        return colorsMapping;
    }

    //Замена цветов пикселей на индекты цветов в палитре
    private List<Byte> convertPixelsColorsToPalleteColorsIndexes(List<Color> pallete, List<Character> image16Bytes, int[] ColorsMapping) {
        List<Byte> indexedPixels = new ArrayList<>();

        for (Character item : image16Bytes) {
            if (ColorsMapping[item] == 0) {
                ColorsMapping[item] = getNearestColor(pallete, item);
            }
            indexedPixels.add((byte) ColorsMapping[item]);
        }

        return indexedPixels;
    }

    //Преобразование цветов палитры из класс Color в массив байт
    private List<Byte> convertPalleteColorsToBytes(List<Color> pallete) {
        ByteBuffer buffer = ByteBuffer.allocate(pallete.size() * 2);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        for (Color color : pallete) {
            buffer.putChar(color.colorCode);
        }
        return Arrays.asList(ArrayUtils.toObject(buffer.array()));
    }

    // разбить картинку на регистры в соответствии с правилами
    private List<byte[]> createRegistersFromImage(List<Byte> imageBytes, String article) {
        List<byte[]> listMessages = new ArrayList<>();
        int countMessages = (imageBytes.size() / 118) + 1;
        int nextByteInBmp = 0;
        int numMessage = 0;
        for (int i = 0; i < countMessages; i++) {
            String numMessageStr = prependZeroes(numMessage, 2);
            String posByte = getPosByte(i, countMessages);
            String startData = prependZeroes("01"/*scaleId*/, 2) + "DL" + numMessageStr + "" + posByte + "2" + prependZeroes(article, 4);
            byte[] startDataBytes = startData.getBytes();

            byte[] message = new byte[130];
            System.arraycopy(startDataBytes, 0, message, 0, startDataBytes.length);

            for (int j = startDataBytes.length; j < 118 + startDataBytes.length; j++) {
                if (posByte.equals("3") && imageBytes.size() >= nextByteInBmp) {
                    message[j] = 48;
                    continue;
                }
                message[j] = imageBytes.get(nextByteInBmp);
                nextByteInBmp++;
            }

            listMessages.add(message);

            numMessage++;
            if (numMessage == 100) numMessage = 0;
        }

        return listMessages;
    }

    private String prependZeroes(Object value, int length) {
        return prepend(value, '0', length);
    }

    private String prepend(Object value, Character c, int length) {
        String result = value == null ? "" : String.valueOf(value);
        if (result.length() > length) result = result.substring(0, length);
        while (result.length() < length) {
            result = c + result;
        }
        return result;
    }

    // получить байт в команде, означающий первое, последнее или промежуточное сообщение формируется
    private String getPosByte(int numMessage, int countMessages) {
        String posByte;
        if (numMessage == 0) {
            posByte = "1";
        } else if (numMessage != countMessages - 1) {
            posByte = "2";
        } else {
            posByte = "3";
        }

        return posByte;
    }

    private class Color {
        char colorCode;
        byte green;
        byte red;
        byte blue;
        int repeated;

        public Color(char colorCode, byte red, byte green, byte blue, int repeated) {
            this.colorCode = colorCode;
            this.green = green;
            this.red = red;
            this.blue = blue;
            this.repeated = repeated;
        }
    }
}