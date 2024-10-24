package equ.clt.handler.aclas;

import com.sun.jna.Library;
import com.sun.jna.Native;
import equ.api.scales.ScalesInfo;
import equ.api.scales.ScalesItem;
import equ.api.scales.TransactionScalesInfo;
import equ.clt.handler.MultithreadScalesHandler;
import lsfusion.base.ExceptionUtils;
import lsfusion.base.Pair;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.locks.ReentrantLock;

import static equ.clt.handler.HandlerUtils.safeDelete;
import static equ.clt.handler.HandlerUtils.safeMultiply;
import static lsfusion.base.BaseUtils.*;

public class AclasLS2Handler extends MultithreadScalesHandler {

    protected final static Logger aclasls2Logger = Logger.getLogger("AclasLS2Logger");

    private static int pluFile = 0x0000;
    private static int note1File = 0x000c;
    private static int note2File = 0x000d;
    private static int note3File = 0x000e;
    private static int note4File = 0x001c;
    private static int hotKeyFile = 0x0003;

    protected FileSystemXmlApplicationContext springContext;

    public AclasLS2Handler(FileSystemXmlApplicationContext springContext) {
        this.springContext = springContext;
    }

    private static boolean enableParallel = false;

    @Override
    public String getGroupId(TransactionScalesInfo transactionInfo) {
        if(enableParallel) {
            return super.getGroupId(transactionInfo);
        }
        return "aclasls2"; //параллелить нельзя, так как работаем с одной dll/so
    }

    protected String getLogPrefix() {
        return "Aclas LS-2: ";
    }

    @Override
    protected int getThreadPoolSize(Collection<Callable<SendTransactionResult>> taskList) {
        if(enableParallel) {
            return super.getThreadPoolSize(taskList);
        }
        return 1; //отключаем распараллеливание
    }

    private void init(String libraryDir, List<String> libraryNames) {
        if(libraryDir != null) {
            setLibraryPath(libraryDir, "jna.library.path");
            setLibraryPath(libraryDir, "java.library.path");
            AclasSDK.init(libraryNames);
        } else throw new RuntimeException("No libraryDir found in settings");
    }

    protected void setLibraryPath(String path, String property) {
        String libraryPath = System.getProperty(property);
        if (libraryPath == null) {
            System.setProperty(property, path);
        } else if (!libraryPath.contains(path)) {
            System.setProperty(property, path + ";" + libraryPath);
        }
    }

    private void release() {
        AclasSDK.release();
    }

    private int clearData(ScalesInfo scales, long sleep) throws IOException, InterruptedException {
        File clearFile = File.createTempFile("aclas", ".txt");
        try {
            BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(Files.newOutputStream(clearFile.toPath()), StandardCharsets.UTF_8));
            bw.write('\ufeff');
            bw.close();

            int result = AclasSDK.clearData(scales.port, clearFile.getAbsolutePath(), pluFile, sleep);
            if(result == 0) {
                result = AclasSDK.clearData(scales.port, clearFile.getAbsolutePath(), note1File, sleep);
            }
            if(result == 0) {
                result = AclasSDK.clearData(scales.port, clearFile.getAbsolutePath(), note2File, sleep);
            }
            if(result == 0) {
                result = AclasSDK.clearData(scales.port, clearFile.getAbsolutePath(), note3File, sleep);
            }
            if(result == 0) {
                result = AclasSDK.clearData(scales.port, clearFile.getAbsolutePath(), note4File, sleep);
            }
            //не работает, возвращает ошибку 1. Если реально понадобится очищать PLU, будем засылать файл с нулевыми ButtonValue
            //if(result == 0) {
            //    result = AclasSDK.clearData(scales.port, clearFile.getAbsolutePath(), hotKeyFile);
            //}

            return result;
        } finally {
            safeDelete(clearFile);
        }
    }

    private int loadData(ScalesInfo scales, TransactionScalesInfo transaction, boolean pluNumberAsPluId, boolean commaDecimalSeparator, boolean skipLoadHotKey, String overBarcodeTypeForPieceItems, long sleep) throws IOException, InterruptedException {
        int result = loadPLU(scales, transaction, pluNumberAsPluId, commaDecimalSeparator, overBarcodeTypeForPieceItems, sleep);
        if (result == 0) {
            result = loadNotes(scales, transaction, pluNumberAsPluId, sleep);
        }
        if (result == 0 && !skipLoadHotKey) {
            result = loadHotKey(scales, transaction, pluNumberAsPluId, sleep);
        }
        //load extra files
        if(result == 0 && !isRedundantString(transaction.info)) {
            JSONObject extraInfo = new JSONObject(transaction.info);
            JSONArray files = extraInfo.optJSONArray("files");
            if(files != null) {
                for(int i = 0; i < files.length(); i++) {
                    JSONObject fileObject = files.getJSONObject(i);
                    Integer dataType = fileObject.getInt("id");
                    String base64 = fileObject.getString("file");
                    String str = new String(Base64.decodeBase64(base64),StandardCharsets.UTF_8);//если не указать кодировку, то некорректно устанавливается кодировка при выгрузке из собранной jar
                    str = str.replace("{npp}", String.valueOf(scales.number));
                    File extraFile = null;
                    try {
                        extraFile = File.createTempFile("aclas", ".txt");
                        try(BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(Files.newOutputStream(extraFile.toPath()), StandardCharsets.UTF_8))) {
                            bw.write('\ufeff'+str);
                        }
                        result = AclasSDK.loadData(scales.port, extraFile.getAbsolutePath(), dataType, sleep);
                    } finally {
                        safeDelete(extraFile);
                    }
                }
            }
        }
        return result;
    }

    private int loadPLU(ScalesInfo scales, TransactionScalesInfo transaction, boolean pluNumberAsPluId, boolean commaDecimalSeparator, String overBarcodeTypeForPieceItems, long sleep) throws IOException, InterruptedException {
        File file = File.createTempFile("aclas", ".txt");
        try {
            try(BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(Files.newOutputStream(file.toPath()), StandardCharsets.UTF_8))) {
                bw.write('\ufeff');
                bw.write(StringUtils.join(Arrays.asList("ID", "ItemCode", "DepartmentID", "Name1", "Name2", "Price", "UnitID", "BarcodeType1", "FreshnessDate", "ValidDate", "PackageType", "Flag1", "Flag2", "IceValue", "TareValue").iterator(), "\t"));

                for (ScalesItem item : transaction.itemsList) {
                    boolean isWeight = isWeight(item, 1);
                    String barcodePrefix = (item.idBarcode.length() > 6) ? "" : (isWeight ? nvl(scales.weightCodeGroupScales, "22") : nvl(scales.pieceCodeGroupScales, "21"));
                    String name = escape(trimToEmpty(item.name));
                    String name1 = name.substring(0, Math.min(name.length(), 40));
                    String name2 = name.length() > 40 ? name.substring(40, Math.min(name.length(), 80)) : "";
                    if (item.price == null || item.price.compareTo(BigDecimal.ZERO) == 0) {
                        throw new RuntimeException("Zero price is not allowed");
                    }
                    String price = String.valueOf((double) safeMultiply(item.price, 100).intValue() / 100);
                    price = commaDecimalSeparator ? price.replace(".", ",") : price.replace(",", ".");
                    String unitID = isWeight ? "4" : "10";
                    String freshnessDate = item.hoursExpiry != null ? String.valueOf(item.hoursExpiry) : "0";
                    String packageType = isWeight ? "0" : "2";
                    String iceValue = item.extraPercent != null ? String.valueOf(safeMultiply(item.extraPercent, 10).intValue()) : "0";

                    Object id = pluNumberAsPluId && item.pluNumber != null ? item.pluNumber : item.idBarcode;
                    String barcodeType = !isWeight && overBarcodeTypeForPieceItems != null ? overBarcodeTypeForPieceItems : item.idBarcode.length() == 6 ? "6" : "7"; //7 - для 5-значных, 6 - для 6-значных

                    JSONObject infoJSON = getExtInfo(item.info, "AclasLS2");
                    String tareWeight = "0";
                    if (infoJSON != null && infoJSON.has("tareWeight")) {
                        tareWeight = infoJSON.optBigDecimal("tareWeight",BigDecimal.ZERO).toString();
                    }

                    bw.write(0x0d);
                    bw.write(0x0a);
                    bw.write(StringUtils.join(Arrays.asList(id, item.idBarcode, barcodePrefix, name1, name2, price, unitID, barcodeType, freshnessDate, freshnessDate, packageType, "60", "240", iceValue, tareWeight).iterator(), "\t"));

                }
            }

            return AclasSDK.loadData(scales.port, file.getAbsolutePath(), pluFile, sleep);
        } finally {
            safeDelete(file);
        }
    }

    private int loadNotes(ScalesInfo scales, TransactionScalesInfo transaction, boolean pluNumberAsPluId, long sleep) throws IOException, InterruptedException {
        List<List<List<Object>>> data = getDataNotes(transaction, pluNumberAsPluId);

        int result = 0;
        if(!data.get(0).isEmpty()) {
            result = loadNote(scales, data.get(0), sleep, note1File);
        }

        if(result == 0 && !data.get(1).isEmpty()) {
            result = loadNote(scales, data.get(1), sleep, note2File);
        }

        if(result == 0 && !data.get(2).isEmpty()) {
            result = loadNote(scales, data.get(2), sleep, note3File);
        }

        if(result == 0 && !data.get(3).isEmpty()) {
            result = loadNote(scales, data.get(3), sleep, note4File);
        }

        return result;
    }

    private int loadNote(ScalesInfo scales, List<List<Object>> noteData, long sleep, int noteFile) throws IOException, InterruptedException {
        File file = File.createTempFile("aclas", ".txt");
        try {
            try(BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(Files.newOutputStream(file.toPath()), StandardCharsets.UTF_8))) {
                bw.write('\ufeff');
                bw.write(StringUtils.join(Arrays.asList("PLUID", "Value").iterator(), "\t"));

                for (List<Object> noteEntry : noteData) {
                    bw.write(0x0d);
                    bw.write(0x0a);
                    bw.write(StringUtils.join(Arrays.asList(noteEntry.get(0), noteEntry.get(1)).iterator(), "\t"));
                }
            }

            return AclasSDK.loadData(scales.port, file.getAbsolutePath(), noteFile, sleep);
        } finally {
            safeDelete(file);
        }
    }

    private List<List<List<Object>>> getDataNotes(TransactionScalesInfo transaction, boolean pluNumberAsPluId) {
        List<List<Object>> dataNote1 = new ArrayList<>();
        List<List<Object>> dataNote2 = new ArrayList<>();
        List<List<Object>> dataNote3 = new ArrayList<>();
        List<List<Object>> dataNote4 = new ArrayList<>();

        for (ScalesItem item : transaction.itemsList) {
            Object id = pluNumberAsPluId && item.pluNumber != null ? item.pluNumber : item.idBarcode;
            String description = escape(trimToEmpty(item.description));

            dataNote1.add(Arrays.asList(id, description.substring(0, Math.min(description.length(), 1000))));

            if (description.length() > 1000) {
                dataNote2.add(Arrays.asList(id, description.substring(1000, Math.min(description.length(), 2000))));
            }

            if (description.length() > 2000) {
                dataNote3.add(Arrays.asList(id, description.substring(2000, Math.min(description.length(), 3000))));
            }

            if (description.length() > 3000) {
                dataNote4.add(Arrays.asList(id, description.substring(3000, Math.min(description.length(), 4000))));
            }
        }

        return Arrays.asList(dataNote1, dataNote2, dataNote3, dataNote4);
    }

    private int loadHotKey(ScalesInfo scales, TransactionScalesInfo transaction, boolean pluNumberAsPluId, long sleep) throws IOException, InterruptedException {
        File file = File.createTempFile("aclas", ".txt");
        try {
            try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(Files.newOutputStream(file.toPath()), StandardCharsets.UTF_8))) {
                bw.write('\ufeff');
                bw.write(StringUtils.join(Arrays.asList("ButtonIndex", "ButtonValue").iterator(), "\t"));

                for (ScalesItem item : transaction.itemsList) {
                    if (item.pluNumber != null) {
                        bw.write(0x0d);
                        bw.write(0x0a);
                        if (pluNumberAsPluId) {
                            bw.write(StringUtils.join(Arrays.asList(item.pluNumber, item.pluNumber).iterator(), "\t"));
                        } else {
                            bw.write(StringUtils.join(Arrays.asList(item.pluNumber, item.idBarcode).iterator(), "\t"));
                        }
                    }
                }
            }

            return AclasSDK.loadData(scales.port, file.getAbsolutePath(), hotKeyFile, sleep);
        } finally {
            safeDelete(file);
        }
    }


    private String escape(String value) {
        return value.replace("\t", "{$09}").replace("\n", "{$0A}").replace("\r", "{$0D}");
    }

    private String getErrorDescription(int error) {
        switch (error) {
            case 0:
                return null;
            case 1:
                return "Progress event";
            case 2:
                return "Manual stop";
            case 256:
                return "Initialized";
            case 257:
                return "Uninitialized";
            case 258:
                return "Device doesn't exist";
            case 259:
                return "Unsupported protocol type";
            case 260:
                return "This data type doesn't support this operation";
            case 261:
                return "Not support this data type";
            case 264:
                return "Unable to open input file";
            case 265:
                return "The number of fields doesn't match the number of content";
            case 266:
                return "Communication data exception";
            case 267:
                return "Parsing data exception";
            case 268:
                return "Code page error";
            case 269:
                return "Unable to create output file";
            case 270:
                return "Invalid protocol type";
            default:
                return "error " + error;
        }
    }

    @Override
    protected void beforeStartTransactionExecutor() {
        aclasls2Logger.info(getLogPrefix() + "Connecting to library...");
        AclasLS2Settings aclasLS2Settings = springContext.containsBean("aclasLS2Settings") ? (AclasLS2Settings) springContext.getBean("aclasLS2Settings") : new AclasLS2Settings();
        String libraryDir = aclasLS2Settings.getLibraryDir();
        List<String> libraryNames = aclasLS2Settings.getLibraryNames();
        init(libraryDir, libraryNames);
    }

    @Override
    protected void afterFinishTransactionExecutor() {
        aclasls2Logger.info(getLogPrefix() + "Disconnecting from library...");
        release();
    }

    @Override
    protected SendTransactionTask getTransactionTask(TransactionScalesInfo transaction, ScalesInfo scales) {
        AclasLS2Settings aclasLS2Settings = springContext.containsBean("aclasLS2Settings") ? (AclasLS2Settings) springContext.getBean("aclasLS2Settings") : new AclasLS2Settings();
        String libraryDir = aclasLS2Settings.getLibraryDir();
        long sleep = aclasLS2Settings.getSleepBetweenLibraryCalls();
        AclasLS2Handler.enableParallel = aclasLS2Settings.isEnableParallel();
        return new AClasLS2SendTransactionTask(transaction, scales, libraryDir, sleep);
    }

    class AClasLS2SendTransactionTask extends SendTransactionTask {
        String libraryDir;
        long sleep;

        public AClasLS2SendTransactionTask(TransactionScalesInfo transaction, ScalesInfo scales, String libraryDir, long sleep) {
            super(transaction, scales);
            this.libraryDir = libraryDir;
            this.sleep = sleep;
        }

        @Override
        protected SendTransactionResult run() {
            AclasLS2Settings aclasLS2Settings = springContext.containsBean("aclasLS2Settings") ? (AclasLS2Settings) springContext.getBean("aclasLS2Settings") : new AclasLS2Settings();
            boolean commaDecimalSeparator = aclasLS2Settings.isCommaDecimalSeparator();
            boolean pluNumberAsPluId = aclasLS2Settings.isPluNumberAsPluId();
            boolean skipLoadHotKey = aclasLS2Settings.isSkipLoadHotKey();
            long sleep = aclasLS2Settings.getSleepBetweenLibraryCalls();
            String overBarcodeTypeForPieceItems = aclasLS2Settings.getOverBarcodeTypeForPieceItems();

            String error;
            boolean cleared = false;
            try {
                int result = 0;
                boolean needToClear = !transaction.itemsList.isEmpty() && transaction.snapshot && !scales.cleared;
                if (needToClear) {
                    result = clearData(scales, sleep);
                    cleared = result == 0;
                }

                if (result == 0) {
                    aclasls2Logger.info(getLogPrefix() + String.format("transaction %s, ip %s, sending %s items...", transaction.id, scales.port, transaction.itemsList.size()));
                    result = loadData(scales, transaction, pluNumberAsPluId, commaDecimalSeparator, skipLoadHotKey, overBarcodeTypeForPieceItems, sleep);
                }
                error = getErrorDescription(result);
                if(error != null) {
                    aclasls2Logger.error(getLogPrefix() + error);
                }
            } catch (Throwable t) {
                interrupted = t instanceof InterruptedException;
                error = String.format(getLogPrefix() + "IP %s error, transaction %s: %s", scales.port, transaction.id, ExceptionUtils.getStackTraceString(t));
                aclasls2Logger.error(error, t);
            }
            aclasls2Logger.info(getLogPrefix() + "Completed ip: " + scales.port);
            return new SendTransactionResult(scales, error != null ? Collections.singletonList(error) : new ArrayList<>(), interrupted, cleared);
        }
    }

    private static boolean interrupted = false; //прерываем загрузку в рамках одной транзакции. Устанавливается при interrupt exception и сбрасывается при release

    public static class AclasSDK {

        public interface AclasSDKLibrary extends Library {

            Map<String, AclasSDKLibrary> aclasSDKs = new HashMap<>();

            boolean AclasSDK_Initialize(Integer pointer);

            void AclasSDK_Finalize();

            Integer AclasSDK_Sync_ExecTaskA_PB(byte[] addr, Integer port, Integer protocolType, Integer procType, Integer dataType, byte[] fileName);

        }

        public static Map<String, Object> getOptions() {
            Map<String, Object> options = new HashMap<>();
            options.put(Library.OPTION_ALLOW_OBJECTS, Boolean.TRUE);
            return options;
        }

        private static final Map<String, Pair<ReentrantLock, Integer>> libraryAccessCounter = new HashMap<>();

        public static synchronized void init(List<String> libraryNames) {
            for (String libraryName : libraryNames) {
                Pair<ReentrantLock, Integer> counterEntry = libraryAccessCounter.getOrDefault(libraryName, Pair.create(new ReentrantLock(), 0));
                if (counterEntry.second == 0) {
                    AclasSDKLibrary library = Native.load(libraryName, AclasSDKLibrary.class, getOptions());
                    log("init " + libraryName);
                    library.AclasSDK_Initialize(null);
                    AclasSDKLibrary.aclasSDKs.put(libraryName, library);
                }
                log("library access counter inc " + libraryName + " = " + (counterEntry.second + 1));
                libraryAccessCounter.put(libraryName, Pair.create(counterEntry.first, counterEntry.second + 1));
            }
        }

        public static synchronized void release() {
            if (!interrupted) {
                AclasSDKLibrary.aclasSDKs.entrySet().removeIf(entry -> {
                    String libraryName = entry.getKey();
                    Pair<ReentrantLock, Integer> counterEntry = libraryAccessCounter.get(libraryName);
                    if (counterEntry.second == 1) {
                        log("release " + libraryName);
                        entry.getValue().AclasSDK_Finalize();
                        log("library access counter removed " + counterEntry.first);
                        libraryAccessCounter.remove(libraryName);
                        return true;
                    } else {
                        log("library access counter dec " + libraryName + " = " + (counterEntry.second - 1));
                        libraryAccessCounter.put(libraryName, Pair.create(counterEntry.first, counterEntry.second -1));
                        return false;
                    }
                });
            }
            interrupted = false;
        }

        public static int clearData(String ip, String filePath, Integer dataType, long sleep) throws InterruptedException {
            if (!interrupted) {
                int result = libraryCall(ip, filePath, dataType, 3);
                if (sleep > 0) {
                    Thread.sleep(sleep);
                }
                return result;
            } else {
                return -1;
            }
        }

        public static int loadData(String ip, String filePath, Integer dataType, long sleep) throws InterruptedException {
            if (!interrupted) {
                int result = libraryCall(ip, filePath, dataType, 0);
                if (sleep > 0) {
                    Thread.sleep(sleep);
                }
                return result;

            } else {
                return -1;
            }
        }

        private static int libraryCall(String ip, String filePath, Integer dataType, Integer procType) throws InterruptedException {
            ReentrantLock lock = null;
            AclasSDKLibrary library = null;
            String libName = null;
            try {
                if (enableParallel) {
                    while (lock == null) {
                        for (Map.Entry<String, Pair<ReentrantLock, Integer>> entry : libraryAccessCounter.entrySet()) {
                            String libraryName = entry.getKey();
                            Pair<ReentrantLock, Integer> counterEntry = entry.getValue();

                            if (counterEntry.first.tryLock()) {
                                lock = counterEntry.first;
                                library = AclasSDKLibrary.aclasSDKs.get(libraryName);
                                libName = libraryName;
                                log("locked " + libName);
                                break;
                            }
                        }
                        if (lock == null) {
                            log("no free library found, try again in 1 second");
                            Thread.sleep(1000);
                        }
                    }
                } else {
                    library = AclasSDKLibrary.aclasSDKs.get("aclassdk");
                }
                return library.AclasSDK_Sync_ExecTaskA_PB(getBytes(ip), 0, 0, procType, dataType, getBytes(filePath));
            } finally {
                if (enableParallel) {
                    assert lock != null;
                    log("unlocked " + libName);
                    lock.unlock();
                }
            }
        }

        private static byte[] getBytes(String value) {
            return (value + "\0").getBytes(StandardCharsets.UTF_8);
        }

        private static void log(String text) {
            aclasls2Logger.info("LibraryLocker: " + text);
        }
    }
}