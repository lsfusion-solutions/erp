package equ.clt.handler.mertech;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.common.io.LittleEndianDataInputStream;
import com.google.common.io.LittleEndianDataOutputStream;
import equ.api.scales.ScalesInfo;
import equ.api.scales.ScalesItem;
import equ.api.scales.TransactionScalesInfo;
import equ.clt.handler.MultithreadScalesHandler;
import equ.clt.handler.TCPPort;
import lsfusion.base.ExceptionUtils;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import javax.naming.CommunicationException;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import lsfusion.base.BaseUtils;

public class MertechPMHandler extends MultithreadScalesHandler {
    
    private class ProductFileClass {
        @JsonProperty("categories")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        List<Category> categories; //Список категорий
        @JsonProperty("messages")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        List<Message> messages; //Список сообщений (для состава или описания товара)
        @JsonProperty("products")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        List<Product> products; //Список товаров
    }

    private class Category {
        @JsonProperty("idCategory")
        Integer idCategory; //ID категории
        @JsonProperty("name")
        String name; //Название категории
        
        Category(Integer idCategory, String name) {
            this.idCategory = idCategory;
            this.name = name;
        }
    }
    
    private class Message {
        @JsonProperty("id")
        int id; //ID сообщения
        @JsonProperty("value")
        String value; //Текст сообщения (для состава или описания товара)
        @JsonProperty("deleted")
        boolean deleted; //Признак удалёного элемента
    
        Message(int id, String value, boolean deleted) {
            this.id = id;
            this.value = value;
            this.deleted = deleted;
        }
    }
    
    private class Product {
        //Идентификаторы
        @JsonProperty("id")
        String id; //ID товара
        @JsonProperty("name")
        String name; //Название
        @JsonProperty("code")
        String code; //Код товара
        @JsonProperty("pluNumber")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        String pluNumber; //? ПЛУ товара
        @JsonProperty("buttonNumber")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        Integer buttonNumber; //? Номер кнопки
        
        //Цены
        @JsonProperty("price")
        Double price; //Цена
        @JsonProperty("discountPrice")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        Double discountPrice; //? Цена со скидкой
        
        //Датирование
        @JsonProperty("manufactureDate")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        String manufactureDate; //? Дата производства. Формат "DD-MM-YY"
        @JsonProperty("sellByDate")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        String sellByDate; //? Дата срока годности. Формат "DD-MM-YY"
        @JsonProperty("shelfLife")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        Integer shelfLife; //? Срок годности
        @JsonProperty("shelfLifeType")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        String shelfLifeType; //? Тип срока годности
        //Характеристики
        @JsonProperty("productType")
        String productType; //Тип продукта
        @JsonProperty("pieceWeight")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        Double pieceWeight; //? Вес 1 штуки

        @JsonProperty("category")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        Integer category; //? ID категории

        @JsonProperty("message")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        Integer message; //? ID сообщения (для состава или описания товара)

        @JsonProperty("wrappingType")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        Integer wrappingType; //? Тип упаковки для ленты Мёбиуса
        
        @JsonProperty("deleted")
        boolean deleted; //Признак удалёного элемента
        
        //Этикетка
        @JsonProperty("labelTemplate")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        Integer labelTemplate; //? Приоритетный шаблон этикетки
        
        @JsonProperty("labelDiscountTemplate")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        Integer labelDiscountTemplate; //? Приоритетный шаблон этикетки, если указана цена со скидкой
        
        //Штрихкоды
        @JsonProperty("barcodePrefixType")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        String barcodePrefixType; //? Приоритетный тип префикса штрихкода
        @JsonProperty("barcodeStructure")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        String barcodeStructure; //? JSON приоритетных структур штрихкодов
    }
    
    private static class Result {
        protected boolean error;
        protected int errorCode;
        protected String errorMessage;
        
        Result(boolean error, int errorCode, String errorMessage) {
            this.error = error;
            this.errorCode = errorCode;
            this.errorMessage = errorMessage;
        }
        
        public boolean success() {
            return !error;
        }
        
        @Override
        public String toString() {
            return "success: " + !error + ", errorCode: " + errorCode + ", errorMessage: " + (StringUtils.isEmpty(errorMessage) ? "null" : errorMessage);
        }
        
        static class Success extends Result {
            public Success() {
                super(false, 0, null);
            }
        }
        
        static class Status extends Result {
            int statusCode;
            String statusMessage;
            
            public Status(int statusCode, String message) {
                super(false, 0, null);
                this.statusCode = statusCode;
                this.statusMessage = message;
            }
            public String toString() {
                return "statusCode: " + statusCode + ", statusMessage: " + (StringUtils.isEmpty(statusMessage) ? "null" : statusMessage);
            }
        }
    
        static class Data extends Result {
            Object result;
            
            public Data(Object result) {
                super(false, 0, null);
                this.result = result;
            }
            public String toString() {
                return String.valueOf(result);
            }
        }
        
        static class Error extends Result {
            public Error(int errorCode, String errorMessage) {
                super(true, errorCode, errorMessage);
            }
            public Error(int errorCode) {
                super(true, errorCode, errorString(errorCode));
            }
            public String toString() {
                return "resultCode: " + this.errorCode + ", errorMessage: " + (StringUtils.isEmpty(errorMessage) ? "null" : errorMessage);
            }
        }
    }
    
    private class Packet {
        int timeout = 5000;
        byte[] commandId;
        ByteBuffer commandBytes;
        byte resultCode;
    }
    
    protected final static Logger mertechLogger = Logger.getLogger("MertechLogger");
    
    private static final byte stx = 0x02;
    private static final byte[] productFile = new byte[] {(byte)0xff, (byte)0x13};
    //private static final byte[] clearPluAndMessage = new byte[] {(byte)0x18}; // очистка базы товаров и сообщений
    //private static final byte[] clearPlu = new byte[] {(byte)0xB9}; // очистка базы товаров
    //private static final byte[] clearMessage = new byte[] {(byte)0xBA}; // очистка базы сообщений
    private static final byte[] setBarcodePrefix = new byte[] {(byte)0x77}; // Задать значение префикса
    
    private TCPPort port;
    private MertechSettings mertechSettings;
    
    protected FileSystemXmlApplicationContext springContext;
    
    public MertechPMHandler(FileSystemXmlApplicationContext springContext) {
        this.springContext = springContext;
    }
    
    private static String errorString(int code) {
        switch (code) {
            case 0:
                return "";
            case 17:
                return "Ошибка в значении тары";
            case 110:
                return "Неверное количество строк у параметра „Название магазина“";
            case 120:
                return "Неизвестная команда";
            case 122:
                return "Неверный пароль";
            case 124:
                return "Неверное значение параметра";
            case 128:
                return "Неверный номер ПЛУ";
            case 129:
                return "Папка не существует";
            case 130:
                return "Неверный код товара";
            case 136:
                return "Неверный номер изображения";
            case 140:
                return "Пустое ПЛУ";
            case 161:
                return "Размер изображения превышает допустимый предел";
            case 171:
                return "Запрашиваемый файл пуст";
            case 172:
                return "В процессе выполнения";
            case 173:
                return "Камера распознавания выключена";
            case 174:
                return "Сбой работы с бэкапом";
            case 175:
                return "Не было запроса на создание файла бэкапа";
            case 176:
                return "Не хватает места на диске для создания бэкапа";
            case 177:
                return "Несоответствие версий БД";
            case 178:
                return "Несоответствие версий Service";
            case 179:
                return "Несоответствие прав доступа";
            case 180:
                return "Несоответствие версий камеры распознавания";
            case 181:
                return "Бэкап не содержит файл конфигурации";
            case 182:
                return "Бэкап не содержит папку MertechScale";
            case 183:
                return "Бэкап не содержит данные о товарах";
            case 184:
                return "Бэкап не содержит данные об этикетках";
            case 185:
                return "Бэкап не содержит файла камеры распознавания";
            case 186:
                return "Бэкап не содержит папки сценариев";
            case 187:
                return "Фасовщик с таким номером не найден";
            case 188:
                return "Устройство не проинициализировано";
            case 189:
                return "Старая версия прошивки без возможности сделать резервную копию";
            case 190:
                return "Камера распознавания не содержит данных для создания резервной копии";
            case 191:
                return "Отсутствует один из обязательных элементов этикетки";
            case 192:
                return "Сервис распознавания отключен";
            case 193:
                return "Процесс распознавания ещё не начинался";
            case 194:
                return "Процесс распознавания ещё не закончился";
            case 195:
                return "Нет данных об этикетке";
            case 196:
                return "Категория не найдена";
            case 197:
                return "Категория для переназначения не найдена";
            case 198:
                return "Штрихкод не поддерживается";
            case 199:
                return "Предыдущий запрос ещё не завершён";
            case 200:
                return "Нельзя зашифровать текущий сценарий";
            case 201:
                return "Ошибка шифрования сценария";
            case 202:
                return "Сценарий уже зашифрован";
            case 203:
                return "Нехватает свободного места на диске";
            
            case -1:
                return "Нет подключения";
            case -2:
                return "Выполнение предыдущей команды ещё не закончено";
            case -3:
                return "Данные ещё не готовы для получения";
            case -4:
                return "Обработка файла на весах завершилась без ошибок";
            case -5:
                return "Обработка файла на весах завершилась с ошибкой";
            case -6:
                return "Неизвестный этап загрузки файла";
            case -7:
                return "Не задан обязательный параметр";
            case -8:
                return "Параметр задан не того типа";
            case -101:
                return "Неверная длина ответа";
            case -102:
                return "Ответ не содержит STX";
            case -103:
                return "Длина ответа не соответствует заявленной";
            case -104:
                return "Неверное значение параметра";
            case -105:
                return "Хэш-данные не совпадают";
            case -106:
                return "Неверное значение использования группового кода";
            case -107:
                return "На весах более новая версия протокола";
            case -108:
                return "На весах устаревшая версия протокола";
            case -109:
                return "IP адрес уже добавлен в список";
            case -110:
                return "Функционал не для группового режима";
            case -111:
                return "Ошибка выполнения группового запроса";
            case -112:
                return "Файл получен";
            case -113:
                return "Неверный файл";
            case -114:
                return "Идёт процесс распознавания";
            case -1000:
                return "Неизвестная ошибка";
            default:
                return "Неизвестная ошибка";
        }
    }
    
    @Override
    protected String getLogPrefix() {
        return "Mertech PM: ";
    }
    
    protected byte[] getPassword() {
        return "1234".getBytes();
    }
    
    private Result sendPacket(Packet packet) {
        
        //04000000 02 02 ff17
        
        int commandBytesCount = packet.commandBytes == null ? 0 : packet.commandBytes.array().length;
        
        int totalLength = 2 + packet.commandId.length + commandBytesCount;
        int length = packet.commandId.length + commandBytesCount;
        byte byteLength = length > 255 ? (byte)0xff : (byte)length;
        
        ByteBuffer bytes = ByteBuffer.allocate(4 + totalLength);
        bytes.order(ByteOrder.LITTLE_ENDIAN);
        
        bytes.putInt(totalLength); // 4 bytes, длина всей последующей команды в порядке LittleEndian
        bytes.put(stx); // 1 byte, байт инициализирующий команду (STX)
        bytes.put(byteLength);
        bytes.put(packet.commandId);//1-2 bytes, код команды
        if (packet.commandBytes != null)
            bytes.put(packet.commandBytes.array());
        
        try {
            mertechLogger.info(getLogPrefix() + String.format("ip: %s >> %s", port.getAddress(), Hex.encodeHexString(bytes.array())));
            
            port.getOutputStream().write(bytes.array());
            port.getOutputStream().flush();
        } catch (IOException e) {
            packet.commandBytes = null;
            return new Result.Error(packet.resultCode, e.getMessage());
        }
        
        return readPacket(packet);
    }
    
    private Result readPacket(Packet packet) {
        
        packet.commandBytes = null;
        
        try {
            port.setSoTimeout(packet.timeout);
        } catch (Exception e) {
            return new Result.Error(-1, e.getMessage());
        }
        
        byte[] buffer = new byte[1];
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        
        long started = System.currentTimeMillis();
        
        while (true) {
            
            try {
                int bytes = port.getInputStream().read(buffer, 0, 1);
                
                long elapsed = System.currentTimeMillis() - started;
                
                if (elapsed > packet.timeout) {
                    return new Result.Error(-1, "socket read timeout");
                }
                
                if ( bytes > 0) {
                    
                    started = System.currentTimeMillis();
                    
                    result.write(buffer[0]);
                    
                    if (result.size() > 4 && result.toByteArray()[4] == stx) {
                        
                        LittleEndianDataInputStream stream = new LittleEndianDataInputStream(new ByteArrayInputStream(result.toByteArray()));
                        
                        int totalLength = stream.readInt();
                        
                        if (result.size() - 4 == totalLength) {
    
                            mertechLogger.info(getLogPrefix() + String.format("ip: %s, << %s", port.getAddress(), Hex.encodeHexString(result.toByteArray())));
                            
                            // 32020000 02 ff ff17 00 2b027b22616e64726f696456657273696f6e223a22362e302e31222c22696d616765457874656e73696f6e5072696f72697479223a226a70672f6a7065672f706e672f626d70222c226d616e756661637475726572223a224d657274656368222c226e6574776f726b496e666f223a5b7b2269704c697374223a5b223139322e3136382e34322e3735225d2c226d6163223a2262302d35382d36372d38302d63332d6638222c226e616d65223a2265746830222c2275736564223a747275657d2c7b2269704c697374223a5b5d2c226d6163223a2264342d39632d64642d39352d38312d3535222c226e616d65223a22776c616e30222c2275736564223a747275657d5d2c2270726f746f636f6c56657273696f6e223a22302e3136222c2272657461696c426f74496e666f223a7b22636c69656e744964223a2230303030222c22656e67696e6556657273696f6e223a22392e322e30222c226669726d7761726556657273696f6e223a22372e392e312e3135227d2c227265766973696f6e223a2231222c227363616c6541707056657273696f6e223a22312e322e33222c227363616c654d6f64656c223a224d2d45522037323520504d2d31352e32222c2273646b56657273696f6e223a223233222c2273657269616c4e756d626572223a223231423630383437222c22736572766963654170704275696c6444617465223a2232392e30332e3234222c227365727669636541707056657273696f6e223a22312e322e352e353632227d
                            stream.readByte(); // stx
                            stream.readByte(); // length
                            stream.readByte(); // 1-st byte commandId
                            //int offset = 7;
                            if (packet.commandId.length == 2) {
                                stream.readByte(); // 2-nd byte commandId
                                //offset = 8;
                            }
                            
                            packet.resultCode = stream.readByte(); // result code
                            byte[] commandBuffer = new byte[totalLength];
                            int readCount = stream.read(commandBuffer, 0, stream.available());
                            if (readCount > 0)
                                packet.commandBytes = ByteBuffer.wrap(commandBuffer, 0, readCount);
                            
                            return packet.resultCode == 0 ? new Result.Success() : new Result.Error(packet.resultCode);
                        }
                    }
                    
                }
            } catch (IOException e) {
                return new Result.Error(-1, e.getMessage());
            }
        }
    }
    
    public Result sendProductFile(ScalesInfo scales, TransactionScalesInfo transaction, boolean needToClear) {
        
        File tmpFile = null;
        
        try {
    
            port.open();
            
            Result result;
    
            if (!(result = sendBarcodePrefexes(scales)).success()) {
                return result;
            }
            
            result = makeProductsFile(transaction);
            if (!(result instanceof Result.Data))
                return result;
    
            tmpFile = File.createTempFile("products", ".json");
            
            FileOutputStream fos = new FileOutputStream(tmpFile);
            Result.Data data = (Result.Data)result;
            String json = data.toString();
            fos.write(json.getBytes());
            fos.close();
    
            File copyFile = new File(tmpFile.getParent() + "/mertech_products.json");
            FileUtils.copyFile(tmpFile, copyFile);
            
            if ((result = sendHashProductFile(tmpFile.getPath(), needToClear)).success()) {
                
                try (FileInputStream fis = new FileInputStream(tmpFile)) {
                    byte[] buffer = new byte[6000];
                    int read;
                    int offset = 0;
                    while ((read = fis.read(buffer, 0, buffer.length)) > 0) {
                        if (!(result = sendPartProductFile(fis.available() == 0, offset, read, buffer)).success()) {
                            fis.close();
                            if (tmpFile.exists())
                                tmpFile.delete();
                            return  result;
                        }
                        offset += read;
                    }
    
                    fis.close();
                    
                    if (tmpFile.exists())
                        tmpFile.delete();
                    
                    while (true) {
                        result = checkProductFile();
                        if (result.success()) {
                            if (result instanceof Result.Status) {
                                Result.Status status = (Result.Status) result;
                                switch (status.statusCode) {
                                    case 0:
                                        return new Result.Success();
                                    case 1:
                                        Thread.sleep(1000);
                                        continue;
                                    case 2:
                                        return new Result.Error(-1, status.statusMessage);
                                }
                            }
                        }
                        else
                            return result;
                    }
                }
            }
    
            if (tmpFile.exists())
                tmpFile.delete();
            
            return result;
            
        } catch (Exception e) {
    
            if (tmpFile != null && tmpFile.exists())
                tmpFile.delete();
            
            return new Result.Error(-1, e.getMessage());
            
        } finally {
            
            try {
                port.close();
            } catch (CommunicationException ignored) {}
        }
    }
    
    public Result sendBarcodePrefexes(ScalesInfo scales) {
        
        Result result = new Result.Success();
        // Шлем префикс для весового штрихкода
        if (!StringUtils.isBlank(scales.weightCodeGroupScales)) {
            int weightCodeGroupScales = Integer.parseInt(scales.weightCodeGroupScales);
            if ((result = sendBarcodePrefix((byte)0x00, (byte)weightCodeGroupScales)).success()) {
                if (!StringUtils.isBlank(scales.pieceCodeGroupScales)) {
                    int pieceCodeGroupScales = Integer.parseInt(scales.pieceCodeGroupScales);
                    result = sendBarcodePrefix((byte)0x01, (byte)pieceCodeGroupScales);
                }
            }
        }
        
        return result;
    }
    
    public Result sendHashProductFile(String filePath, boolean needToClear) {
        
        try {
            
            Packet packet = new Packet();
            packet.commandId = productFile;
            
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            LittleEndianDataOutputStream outputStream = new LittleEndianDataOutputStream(baos);
            
            outputStream.write(getPassword()); // пароль
            outputStream.write(0x02); //  код этапа отправки хэш-данных файла
            
            Path path = Paths.get(filePath);
            try (InputStream is = Files.newInputStream(path)) {
                outputStream.write(DigestUtils.md5(is)); // hash-данные файла
            }
            
            outputStream.write(0x04); // параметр: Размер файла
            outputStream.writeLong( Files.size(path)); // Значение параметра: Размер файла в байтах
            outputStream.write(0x01); // параметр: Тип экспорта товаров
            outputStream.write(needToClear ? 0x00 : 0x01); // Значение параметра: 0 - с предварительной очисткой базы, 1 - без предварительной очистки базы
            
            outputStream.flush();
            
            packet.commandBytes = ByteBuffer.allocate(baos.size());
            packet.commandBytes.put(baos.toByteArray());
            
            return sendPacket(packet);
            
        } catch (Exception e) {
            System.out.println(String.format("error: %s", e));
            return new Result.Error(-1, e.getMessage());
        }
    }
    
    public Result sendPartProductFile(boolean lastPart, int offset, int size, byte[] data) {
        
        try {
            
            Packet packet = new Packet();
            packet.commandId = productFile;
            
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            LittleEndianDataOutputStream outputStream = new LittleEndianDataOutputStream(baos);
            
            outputStream.write(getPassword()); // пароль
            
            outputStream.write(0x03); //  1 byte. Код этапа отправки порции файла
            
            outputStream.write(lastPart ? 0x01 : 0x00); // 1 byte, Флаг последней порции. 0 - не последняя, 1 - последняяпараметр: Размер файла
            
            outputStream.writeInt(offset); // 4 bytes. Смещение в файле
            
            outputStream.writeShort(size); // 2 bytes. Размер порции. Диапазон 1-60000
            
            outputStream.write(data, 0, size);
            
            outputStream.flush();
            
            packet.commandBytes = ByteBuffer.allocate(baos.size());
            packet.commandBytes.put(baos.toByteArray());
            
            return sendPacket(packet);
            
        } catch (Exception e) {
            System.out.println(String.format("error: %s", e));
            return new Result.Error(-1, e.getMessage());
        }
    }
    
    public Result checkProductFile() {
        
        try {
            
            Packet packet = new Packet();
            packet.commandId = productFile;
            
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            LittleEndianDataOutputStream outputStream = new LittleEndianDataOutputStream(baos);
            
            outputStream.write(getPassword()); // пароль
            
            outputStream.write(0x09); //  1 byte. Код этапа проверки отправляемого файла
            
            outputStream.flush();
            
            packet.commandBytes = ByteBuffer.allocate(baos.size());
            packet.commandBytes.put(baos.toByteArray());
            
            Result result = sendPacket(packet);
            if (packet.commandBytes != null && packet.commandBytes.remaining() > 0) {
                LittleEndianDataInputStream fis = new LittleEndianDataInputStream(new ByteArrayInputStream(packet.commandBytes.array()));
                byte status = fis.readByte();
                switch (status) {
                    case 0: return new Result.Status(status, null);
                    case 1: return new Result.Status(status, errorString(-2));
                    case 2: {
                        int messageLen = fis.readShort();
                        byte[] message = new byte[messageLen];
                        fis.read(message, 0, messageLen);
                        return new Result.Status(status, new String(message, StandardCharsets.UTF_8));
                    }
                }
            }
            
            return result;
            
        } catch (Exception e) {
            System.out.println(String.format("error: %s", e));
            return new Result.Error(-1, e.getMessage());
        }
    }
    
    private Result sendBarcodePrefix(byte type, byte value) {
        
        try {
            
            Packet packet = new Packet();
            packet.commandId = setBarcodePrefix;
            
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            LittleEndianDataOutputStream outputStream = new LittleEndianDataOutputStream(baos);
            
            outputStream.write(getPassword()); // пароль
            outputStream.write(type); // Тип задаваемого префикса: 0 – весовой товар, 1 – штучный товар, 2 – итоговая этикетка
            outputStream.write(value); // Значение префикса. Диапазон 0-99
            outputStream.flush();
    
            packet.commandBytes = ByteBuffer.allocate(baos.size());
            packet.commandBytes.put(baos.toByteArray());
            
            return sendPacket(packet);
            
        } catch (Exception e) {
            System.out.println(String.format("error: %s", e));
            return new Result.Error(-1, e.getMessage());
        }
    }
    
    
    private Result clear(byte[] commandId) {
        
        try {
            
            Packet packet = new Packet();
            packet.commandId = commandId;
            
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            LittleEndianDataOutputStream outputStream = new LittleEndianDataOutputStream(baos);
            
            outputStream.write(getPassword()); // пароль
            outputStream.flush();
            
            return sendPacket(packet);
            
        } catch (Exception e) {
            System.out.println(String.format("error: %s", e));
            return new Result.Error(-1, e.getMessage());
        }
    }
    
    //private Result clearPluAndMessage() {
    //    return clear(clearPluAndMessage);
    //}
    //private Result clearPlu() {
    //    return clear(clearPlu);
    //}
    //private Result clearMessage() {
    //    return clear(clearMessage);
    //}
    
    private Result makeProductsFile(TransactionScalesInfo transaction) {
        
        String barcodeStructureFile = mertechSettings.getBarcodeStructureFile();
        String barcodeStructureJSON = null;
        if (!StringUtils.isBlank(barcodeStructureFile)) {
            try {
                Path path = Paths.get(barcodeStructureFile);
                byte[] barcodeStructureData = Files.readAllBytes(path);
                barcodeStructureJSON = new String(barcodeStructureData, StandardCharsets.UTF_8);
            } catch (IOException e) {
                mertechLogger.error(getLogPrefix() + e.getMessage());
            }
        }
    
        ProductFileClass productFileClass = new ProductFileClass();
        
        List<Product> products = new ArrayList<>();
        List<Message> messages = new ArrayList<>();
        List<Category> categories = new ArrayList<>();
        
        for (ScalesItem item : transaction.itemsList) {
            
            int pluNumber = getPluNumber(item);
            String strPluNumber = String.valueOf(getPluNumber(item));
            
            Product product = new Product();
            product.id = strPluNumber; //ID товара
            product.name = item.name;
            product.code = item.idBarcode; //Код товара
            product.pluNumber = strPluNumber; //? ПЛУ товара
            product.buttonNumber = pluNumber; //? Номер кнопки
            //product.gtin = null; // ? GTIN
            
            //Цены
            Double price = item.price == null ? 0 : item.price.doubleValue();
            //Double retailPrice = item.retailPrice == null ? null : item.retailPrice.doubleValue();
            product.price = price; //Цена
            
            // Для демонстрации цены со скидкой
            double percentDiscount = BaseUtils.nvl(mertechSettings.getPercentDiscount(), 0.0);
            if (percentDiscount > 0) {
                product.discountPrice = price - (price * percentDiscount/100); //? Цена со скидкой
            }
            
            //if (retailPrice != null) {
            //    product.price = retailPrice; //? Цена
            //    product.discountPrice = price; //? Цена со скидкой
            //}
            
            //Датирование
            product.manufactureDate = null; //? Дата производства. Формат "DD-MM-YY"
            product.sellByDate = item.expiryDate == null ? null : item.expiryDate.format(DateTimeFormatter.ofPattern("dd-MM-yy")); //? Дата срока годности. Формат "DD-MM-YY"
    
            if (item.hoursExpiry != null) {
                product.shelfLife = item.hoursExpiry;
                product.shelfLifeType = "HOURS";
            }
            else if (item.daysExpiry != null) {
                product.shelfLife = item.daysExpiry;
                product.shelfLifeType = "DAYS";
            }
            
            //Характеристики
            product.productType = item.splitItem ? "WEIGHT" : "PIECE"; //Тип продукта
            product.pieceWeight = null; //? Вес 1 штуки
            
            product.category = null;
            
            if (item.idItemGroup != null) {
                try {
                    int idCategory = Integer.valueOf(item.idItemGroup);
                    
                    Category category = categories.stream()
                            .filter(it -> it.idCategory == idCategory)
                            .findAny()
                            .orElse(null);
                    
                    if (category == null)
                        categories.add(new Category(idCategory, item.nameItemGroup));
                    
                    product.category = idCategory;
                }
                catch (Exception ignored) {
                }
            }
            
            Integer idMessage = null;
            if (!StringUtils.isEmpty(item.description)) {
                idMessage = pluNumber;
                Message message = new Message(idMessage, item.description, false);
                messages.add(message);
            }
            product.message = idMessage; //? ID сообщения (для состава или описания товара)
            
            product.wrappingType = mertechSettings.getWrappingType();
            
            product.deleted = false; //Признак удалёного элемента
            
            //Этикетка
            product.labelTemplate = mertechSettings.getLabelTemplate(); //? Приоритетный шаблон этикетки
            product.labelDiscountTemplate = mertechSettings.getLabelDiscountTemplate(); //? Приоритетный шаблон этикетки, если указана цена со скидкой
            
            //Штрихкоды
            product.barcodePrefixType = StringUtils.isBlank(mertechSettings.getBarcodePrefixType()) ? null : mertechSettings.getBarcodePrefixType(); //? Приоритетный тип префикса штрихкода
            product.barcodeStructure = barcodeStructureJSON; //? JSON приоритетных структур штрихкодов
            
            products.add(product);
        }
        
        if (!products.isEmpty())
            productFileClass.products = products;
        if (!messages.isEmpty())
            productFileClass.messages = messages;
        if (!categories.isEmpty())
            productFileClass.categories = categories;
    
        ObjectMapper objectMapper = new ObjectMapper(); //.enable(SerializationFeature.INDENT_OUTPUT);
        objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        //SimpleDateFormat df = new SimpleDateFormat("dd-MM-yyyy hh:mm");
        //objectMapper.setDateFormat(df);
        
        try {
            String json = objectMapper.writeValueAsString(productFileClass);
            return new Result.Data(json);
        }
        catch (Exception e) {
            return new Result.Error(-1, e.getMessage());
        }
    }
    
    private int getPluNumber(ScalesItem item) {
        return item.pluNumber != null ? item.pluNumber : Integer.parseInt(item.idBarcode);
    }
    
    @Override
    protected SendTransactionTask getTransactionTask(TransactionScalesInfo transaction, ScalesInfo scales) {
        return new MertechSendTransactionTask(transaction, scales);
    }
    
    private static boolean interrupted = false; //прерываем загрузку в рамках одной транзакции. Устанавливается при interrupt exception и сбрасывается при release
    
    class MertechSendTransactionTask extends SendTransactionTask {
        public MertechSendTransactionTask(TransactionScalesInfo transaction, ScalesInfo scales) {
            super(transaction, scales);
            initSettings();
        }
        
        @Override
        protected SendTransactionResult run() {
            
            String[] hostPort = scales.port.split(":");
            port = hostPort.length == 1 ? new TCPPort(scales.port, 1111) : new TCPPort(hostPort[0], Integer.parseInt(hostPort[1]));
    
            String error = null;
            boolean cleared = false;
            
            try {
                
                Result result;
                
                boolean needToClear = !transaction.itemsList.isEmpty() && transaction.snapshot && !scales.cleared;
                cleared = needToClear;
                
                mertechLogger.info(getLogPrefix() + String.format("transaction %s, ip %s, sending %s items...", transaction.id, scales.port, transaction.itemsList.size()));
                result = sendProductFile(scales, transaction, needToClear);
                
                if (!result.success()) {
                    error = result.errorMessage;
                    mertechLogger.error(getLogPrefix() + String.format("ip %s, error: %s", scales.port, error));
                }
            } catch (Throwable t) {
                interrupted = t instanceof InterruptedException;
                error = String.format(getLogPrefix() + "ip %s error, transaction %s: %s", scales.port, transaction.id, ExceptionUtils.getStackTraceString(t));
                mertechLogger.error(error, t);
            }
            mertechLogger.info(getLogPrefix() + "Completed ip: " + scales.port);
            return new SendTransactionResult(scales, error != null ? Collections.singletonList(error) : new ArrayList<>(), interrupted, cleared);
        }
    
        protected void initSettings() {
            mertechSettings = springContext.containsBean("mertechSettings") ? (MertechSettings) springContext.getBean("mertechSettings") : new MertechSettings();
        }
    }
}
