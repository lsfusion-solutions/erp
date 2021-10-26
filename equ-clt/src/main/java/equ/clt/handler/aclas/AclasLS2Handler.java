package equ.clt.handler.aclas;

import com.sun.jna.Library;
import com.sun.jna.Native;
import equ.api.scales.ScalesInfo;
import equ.api.scales.ScalesItem;
import equ.api.scales.TransactionScalesInfo;
import equ.clt.handler.MultithreadScalesHandler;
import lsfusion.base.ExceptionUtils;
import lsfusion.base.Pair;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.springframework.context.support.FileSystemXmlApplicationContext;
import org.springframework.util.FileCopyUtils;

import java.io.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.Callable;

import static equ.clt.handler.HandlerUtils.safeMultiply;
import static lsfusion.base.BaseUtils.trimToEmpty;

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

    @Override
    public String getGroupId(TransactionScalesInfo transactionInfo) {
        return "aclasls2"; //параллелить нельзя, так как работаем с одной dll/so
    }

    protected String getLogPrefix() {
        return "Aclas LS-2: ";
    }

    @Override
    protected int getThreadPoolSize(Collection<Callable<SendTransactionResult>> taskList) {
        return 1; //отключаем распараллеливание
    }

    private boolean init(String libraryDir) {
        if(libraryDir != null) {
            setLibraryPath(libraryDir, "jna.library.path");
            setLibraryPath(libraryDir, "java.library.path");
            return AclasSDK.init();
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
            BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(clearFile), "cp1251"));
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

    private int loadData(ScalesInfo scales, TransactionScalesInfo transaction) throws IOException, InterruptedException {
        AclasLS2Settings aclasLS2Settings = springContext.containsBean("aclasLS2Settings") ? (AclasLS2Settings) springContext.getBean("aclasLS2Settings") : null;
        String logDir = aclasLS2Settings != null ? aclasLS2Settings.getLogDir() : null;
        boolean commaDecimalSeparator = aclasLS2Settings != null && aclasLS2Settings.isCommaDecimalSeparator();
        boolean pluNumberAsPluId = aclasLS2Settings != null && aclasLS2Settings.isPluNumberAsPluId();
        long sleep = aclasLS2Settings == null ? 0 : aclasLS2Settings.getSleepBetweenLibraryCalls();
        int result = loadPLU(scales, transaction, logDir, pluNumberAsPluId, commaDecimalSeparator, sleep);
        if(result == 0) {
            result = loadNotes(scales, transaction, logDir, pluNumberAsPluId, sleep);
        }
        if(result == 0) {
            result = loadHotKey(scales, transaction, logDir, sleep);
        }
        return result;
    }

    private int loadPLU(ScalesInfo scales, TransactionScalesInfo transaction, String logDir, boolean pluNumberAsPluId, boolean commaDecimalSeparator, long sleep) throws IOException, InterruptedException {
        File file = File.createTempFile("aclas", ".txt");
        try {
            try(BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), "cp1251"))) {
                bw.write(StringUtils.join(Arrays.asList("ID", "ItemCode", "DepartmentID", "Name1", "Name2", "Price", "UnitID", "BarcodeType1", "FreshnessDate", "ValidDate", "PackageType", "Flag1", "Flag2", "IceValue").iterator(), "\t"));

                String barcodePrefix = scales.weightCodeGroupScales != null ? scales.weightCodeGroupScales : "22";
                for (ScalesItem item : transaction.itemsList) {
                    bw.write(0x0d);
                    bw.write(0x0a);
                    boolean isWeight = isWeight(item, 1);
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
                    bw.write(StringUtils.join(Arrays.asList(id, item.idBarcode, barcodePrefix, name1, name2, price, unitID, "7", freshnessDate, freshnessDate, packageType, "60", "240", iceValue).iterator(), "\t"));
                }
            }

            logFile(logDir, file, transaction, "plu");

            return AclasSDK.loadData(scales.port, file.getAbsolutePath(), pluFile, sleep);
        } finally {
            safeDelete(file);
        }
    }

    private int loadNotes(ScalesInfo scales, TransactionScalesInfo transaction, String logDir, boolean pluNumberAsPluId, long sleep) throws IOException, InterruptedException {
        List<List<List<Object>>> data = getDataNotes(transaction, pluNumberAsPluId);

        int result = 0;
        if(!data.get(0).isEmpty()) {
            result = loadNote(scales, data.get(0), transaction, logDir, sleep, note1File);
        }

        if(result == 0 && !data.get(1).isEmpty()) {
            result = loadNote(scales, data.get(1), transaction, logDir, sleep, note2File);
        }

        if(result == 0 && !data.get(2).isEmpty()) {
            result = loadNote(scales, data.get(2), transaction, logDir, sleep, note3File);
        }

        if(result == 0 && !data.get(3).isEmpty()) {
            result = loadNote(scales, data.get(3), transaction, logDir, sleep, note4File);
        }

        return result;
    }

    private int loadNote(ScalesInfo scales, List<List<Object>> noteData, TransactionScalesInfo transaction, String logDir, long sleep, int noteFile) throws IOException, InterruptedException {
        File file = File.createTempFile("aclas", ".txt");
        try {
            try(BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), "cp1251"))) {
                bw.write(StringUtils.join(Arrays.asList("PLUID", "Value").iterator(), "\t"));

                for (List<Object> noteEntry : noteData) {
                    bw.write(0x0d);
                    bw.write(0x0a);
                    bw.write(StringUtils.join(Arrays.asList(noteEntry.get(0), noteEntry.get(1)).iterator(), "\t"));
                }
            }

            logFile(logDir, file, transaction, "note");

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

    private int loadHotKey(ScalesInfo scales, TransactionScalesInfo transaction, String logDir, long sleep) throws IOException, InterruptedException {
        File file = File.createTempFile("aclas", ".txt");
        try {
            try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), "cp1251"))) {
                bw.write(StringUtils.join(Arrays.asList("ButtonIndex", "ButtonValue").iterator(), "\t"));

                for (ScalesItem item : transaction.itemsList) {
                    if (item.pluNumber != null) {
                        bw.write(0x0d);
                        bw.write(0x0a);
                        bw.write(StringUtils.join(Arrays.asList(item.pluNumber, item.idBarcode).iterator(), "\t"));
                    }
                }
            }

            logFile(logDir, file, transaction, "hotkey");

            return AclasSDK.loadData(scales.port, file.getAbsolutePath(), hotKeyFile, sleep);
        } finally {
            safeDelete(file);
        }
    }

    private void logFile(String logDir, File file, TransactionScalesInfo transaction, String prefix) throws IOException {
        if (logDir != null) {
            if (new File(logDir).exists() || new File(logDir).mkdirs()) {
                FileCopyUtils.copy(file, new File(logDir + "/" + transaction.id + "-" + prefix + "-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss")) + ".txt"));
            }
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
            default:
                return "error " + error;
        }
    }

    @Override
    protected void beforeStartTransactionExecutor() {
        aclasls2Logger.info(getLogPrefix() + "Connecting to library...");
        AclasLS2Settings aclasLS2Settings = springContext.containsBean("aclasLS2Settings") ? (AclasLS2Settings) springContext.getBean("aclasLS2Settings") : null;
        String libraryDir = aclasLS2Settings == null ? null : aclasLS2Settings.getLibraryDir();
        init(libraryDir);
    }

    @Override
    protected void afterFinishTransactionExecutor() {
        aclasls2Logger.info(getLogPrefix() + "Disconnecting from library...");
        release();
    }

    @Override
    protected SendTransactionTask getTransactionTask(TransactionScalesInfo transaction, ScalesInfo scales) {
        AclasLS2Settings aclasLS2Settings = springContext.containsBean("aclasLS2Settings") ? (AclasLS2Settings) springContext.getBean("aclasLS2Settings") : null;
        String libraryDir = aclasLS2Settings == null ? null : aclasLS2Settings.getLibraryDir();
        long sleep = aclasLS2Settings == null ? 0 : aclasLS2Settings.getSleepBetweenLibraryCalls();
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
        protected Pair<List<String>, Boolean> run() {
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
                    result = loadData(scales, transaction);
                }
                error = getErrorDescription(result);
                if(error != null) {
                    aclasls2Logger.error(getLogPrefix() + error);
                }
            } catch (Throwable t) {
                error = String.format(getLogPrefix() + "IP %s error, transaction %s: %s", scales.port, transaction.id, ExceptionUtils.getStackTraceString(t));
                aclasls2Logger.error(error, t);
            }
            aclasls2Logger.info(getLogPrefix() + "Completed ip: " + scales.port);
            return Pair.create(error != null ? Collections.singletonList(error) : new ArrayList<>(), cleared);
        }
    }

    public static class AclasSDK {

        public interface AclasSDKLibrary extends Library {

            AclasSDKLibrary aclasSDK = Native.load("aclassdk", AclasSDKLibrary.class, getOptions());

            boolean AclasSDK_Initialize(Integer pointer);

            void AclasSDK_Finalize();

            Integer AclasSDK_Sync_ExecTaskA_PB(byte[] addr, Integer port, Integer protocolType, Integer procType, Integer dataType, byte[] fileName);

        }

        public static Map<String, Object> getOptions() {
            Map<String, Object> options = new HashMap<>();
            options.put(Library.OPTION_ALLOW_OBJECTS, Boolean.TRUE);
            return options;
        }

        public static boolean init() {
            return AclasSDKLibrary.aclasSDK.AclasSDK_Initialize(null);
        }

        public static void release() {
            AclasSDKLibrary.aclasSDK.AclasSDK_Finalize();
        }

        public static int clearData(String ip, String filePath, Integer dataType, long sleep) throws UnsupportedEncodingException, InterruptedException {
            int result = AclasSDKLibrary.aclasSDK.AclasSDK_Sync_ExecTaskA_PB(getBytes(ip), 0, 0, 3, dataType, getBytes(filePath));
            if(sleep > 0) {
                Thread.sleep(sleep);
            }
            return result;
        }

        public static int loadData(String ip, String filePath, Integer dataType, long sleep) throws UnsupportedEncodingException, InterruptedException {
            int result = AclasSDKLibrary.aclasSDK.AclasSDK_Sync_ExecTaskA_PB(getBytes(ip), 0, 0, 0, dataType, getBytes(filePath));
            if(sleep > 0) {
                Thread.sleep(sleep);
            }
            return result;
        }

        private static byte[] getBytes(String value) throws UnsupportedEncodingException {
            return (value + "\0").getBytes("cp1251");
        }
    }
}