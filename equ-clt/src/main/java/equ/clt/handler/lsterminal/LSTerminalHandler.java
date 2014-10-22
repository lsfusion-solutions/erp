package equ.clt.handler.lsterminal;

import com.google.common.base.Throwables;
import equ.api.MachineryInfo;
import equ.api.SoftCheckInfo;
import equ.api.TransactionInfo;
import equ.api.terminal.*;
import equ.clt.EquipmentServer;
import org.apache.log4j.Logger;
import java.io.*;
import java.math.BigDecimal;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.sql.*;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class LSTerminalHandler extends TerminalHandler {

    protected final static Logger logger = Logger.getLogger(EquipmentServer.class);

    String dbPath = "/db";

    public LSTerminalHandler() {
    }

    @Override
    public List<MachineryInfo> sendTransaction(TransactionInfo transactionInfo, List machineryInfoList) throws IOException {
        try {
            Integer nppGroupTerminal = ((TransactionTerminalInfo) transactionInfo).nppGroupTerminal;
            String directory = ((TransactionTerminalInfo) transactionInfo).directoryGroupTerminal;
            if (directory != null) {
                String exchangeDirectory = directory + "/exchange";
                if ((new File(exchangeDirectory).exists() || new File(exchangeDirectory).mkdir())) {
                    //copy base to exchange directory                   
                    FileInputStream fis = new FileInputStream(new File(makeDBPath(directory + dbPath, nppGroupTerminal)));
                    FileOutputStream fos = new FileOutputStream(new File(exchangeDirectory + "/base.zip"));
                    ZipOutputStream zos = new ZipOutputStream(fos);
                    zos.putNextEntry(new ZipEntry("tsd.db"));
                    byte[] buf = new byte[1024];
                    int len;
                    while ((len = fis.read(buf)) > 0) {
                        zos.write(buf, 0, len);
                    }
                    fis.close();
                    zos.close();
                }
            }          
        } catch (Exception e) {
            logger.error(e);
            throw Throwables.propagate(e);
        }
        return null;
    }

    @Override
    public void sendSoftCheck(SoftCheckInfo softCheckInfo) throws IOException {
    }

    @Override
    public void sendTerminalOrderList(List terminalOrderList, Integer nppGroupTerminal, String directoryGroupTerminal) throws IOException {
        try {

            File directory = new File(directoryGroupTerminal + dbPath);
            if (directory.exists() || directory.mkdir()) {
                Class.forName("org.sqlite.JDBC");
                Connection connection = DriverManager.getConnection("jdbc:sqlite:" + makeDBPath(directoryGroupTerminal + dbPath, nppGroupTerminal));

                createGoodsTableIfNotExists(connection);
                updateTerminalGoodsTable(connection, terminalOrderList);

                createOrderTable(connection);
                updateOrderTable(connection, terminalOrderList);

                connection.close();

            } else {
                logger.error("Directory " + directory.getAbsolutePath() + " doesn't exist");
                throw Throwables.propagate(new RuntimeException("Directory " + directory.getAbsolutePath() + " doesn't exist"));
            }

            if (directoryGroupTerminal != null) {
                String exchangeDirectory = directoryGroupTerminal + "/exchange";
                if ((new File(exchangeDirectory).exists() || new File(exchangeDirectory).mkdir())) {
                    //copy base to exchange directory                   
                    FileInputStream fis = new FileInputStream(new File(makeDBPath(directoryGroupTerminal + dbPath, nppGroupTerminal)));
                    FileOutputStream fos = new FileOutputStream(new File(exchangeDirectory + "/base.zip"));
                    ZipOutputStream zos = new ZipOutputStream(fos);
                    zos.putNextEntry(new ZipEntry("tsd.db"));
                    byte[] buf = new byte[1024];
                    int len;
                    while ((len = fis.read(buf)) > 0) {
                        zos.write(buf, 0, len);
                    }
                    fis.close();
                    zos.close();
                }
            }
        } catch (Exception e) {
            logger.error(e);
            throw Throwables.propagate(e);
        }
    }

    @Override
    public void saveTransactionTerminalInfo(TransactionTerminalInfo transactionInfo) throws IOException {

        logger.info("LSTerminal: save Transaction #" + transactionInfo.id);

        Integer nppGroupTerminal = transactionInfo.nppGroupTerminal;
        File directory = new File(transactionInfo.directoryGroupTerminal + dbPath);
        if (directory.exists() || directory.mkdir()) {
            try {
                Class.forName("org.sqlite.JDBC");
                Connection connection = DriverManager.getConnection("jdbc:sqlite:" +
                        makeDBPath(transactionInfo.directoryGroupTerminal + dbPath, nppGroupTerminal));

                createGoodsTableIfNotExists(connection);
                updateGoodsTable(connection, transactionInfo);

                createAssortTableIfNotExists(connection);
                updateAssortTable(connection, transactionInfo);

                createVANTableIfNotExists(connection);
                updateVANTable(connection, transactionInfo);

                createANATableIfNotExists(connection);
                updateANATable(connection, transactionInfo);

                createVOPTableIfNotExists(connection);
                updateVOPTable(connection, transactionInfo);

                connection.close();

            } catch (Exception e) {
                logger.error(e);
                throw Throwables.propagate(e);
            }
        } else {
            logger.error("Directory " + directory.getAbsolutePath() + " doesn't exist");
            throw Throwables.propagate(new RuntimeException("Directory " + directory.getAbsolutePath() + " doesn't exist"));
        }
    }

    public void finishReadingTerminalDocumentInfo(TerminalDocumentBatch terminalDocumentBatch) {
        logger.info("LSTerminal: Finish Reading started");
        for (String readFile : terminalDocumentBatch.readFiles) {
            File f = new File(readFile);
            if (f.delete()) {
                logger.info("LSTerminal: file " + readFile + " has been deleted");
            } else {
                throw new RuntimeException("The file " + f.getAbsolutePath() + " can not be deleted");
            }
        }
    }

    @Override
    public TerminalDocumentBatch readTerminalDocumentInfo(List machineryInfoList) throws IOException {

        try {

            Class.forName("org.sqlite.JDBC");

            Set<String> directorySet = new HashSet<String>();
            for (Object m : machineryInfoList) {
                TerminalInfo t = (TerminalInfo) m;
                if (t.directory != null)
                    directorySet.add(t.directory);
            }

            List<String> filePathList = new ArrayList<String>();

            List<TerminalDocumentDetail> terminalDocumentDetailList = new ArrayList<TerminalDocumentDetail>();

            for (String directory : directorySet) {

                String exchangeDirectory = directory + "/exchange";

                File[] filesList = new File(exchangeDirectory).listFiles(new FileFilter() {
                    @Override
                    public boolean accept(File pathname) {
                        return pathname.getName().toUpperCase().startsWith("DOK_") && pathname.getPath().toUpperCase().endsWith(".DB");
                    }
                });

                if (filesList == null || filesList.length == 0)
                    logger.info("LSTerminal: No terminal documents found in " + exchangeDirectory);
                else {
                    logger.info("LSTerminal: found " + filesList.length + " file(s) in " + exchangeDirectory);

                    for (File file : filesList) {
                        try {
                            String fileName = file.getName();
                            logger.info("LSTerminal: reading " + fileName);
                            if (isFileLocked(file)) {
                                logger.info("LSTerminal: " + fileName + " is locked");
                            } else {

                                Connection connection = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
                                List<List<Object>> dokData = readDokFile(connection);

                                for (List<Object> entry : dokData) {

                                    String dateTime = (String) entry.get(0); //DV
                                    String numberTerminalDocument = (String) entry.get(1); //NUM
                                    String idTerminalDocument = dateTime + "/" + numberTerminalDocument;
                                    String idTerminalDocumentType = (String) entry.get(2); //VOP
                                    String idTerminalHandbookType1 = (String) entry.get(3); //ANA1
                                    String idTerminalHandbookType2 = (String) entry.get(4); //ANA2
                                    String barcode = (String) entry.get(5); //BARCODE
                                    BigDecimal quantity = (BigDecimal) entry.get(6); //QUANT
                                    BigDecimal price = (BigDecimal) entry.get(7); //PRICE
                                    String numberTerminalDocumentDetail = (String) entry.get(8); //npp
                                    BigDecimal sum = safeMultiply(quantity, price);
                                    String idTerminalDocumentDetail = idTerminalDocument + numberTerminalDocumentDetail;
                                    
                                    if (quantity != null && !quantity.equals(BigDecimal.ZERO))
                                        terminalDocumentDetailList.add(new TerminalDocumentDetail(idTerminalDocument, numberTerminalDocument, 
                                                directory, idTerminalHandbookType1, idTerminalHandbookType2, idTerminalDocumentType, 
                                                idTerminalDocumentDetail, numberTerminalDocumentDetail, barcode, price, quantity, sum));
                                }

                                connection.close();
                                filePathList.add(file.getAbsolutePath());
                            }
                        } catch (Throwable e) {
                            logger.error("File: " + file.getAbsolutePath(), e);
                        }
                    }
                }
            }

            return new TerminalDocumentBatch(terminalDocumentDetailList, filePathList);
        } catch (Exception e) {
            logger.error(e);
            throw Throwables.propagate(e);
        }
    }

    private List<List<Object>> readDokFile(Connection connection) throws SQLException {

        List<List<Object>> itemsList = new ArrayList<List<Object>>();

        String dv = null;
        String num = null;
        String vop = null;
        String ana1 = null;
        String ana2 = null;

        Statement statement = connection.createStatement();
        String sql = "SELECT dv, num, vop, ana1, ana2 FROM dok LIMIT 1;";
        ResultSet resultSet = statement.executeQuery(sql);
        if (resultSet.next()) {
            dv = resultSet.getString("dv");
            num = resultSet.getString("num");
            vop = resultSet.getString("vop");
            ana1 = resultSet.getString("ana1");
            ana2 = resultSet.getString("ana2");
        }
        resultSet.close();
        statement.close();

        statement = connection.createStatement();
        sql = "SELECT barcode, quant, price, npp FROM pos;";
        resultSet = statement.executeQuery(sql);
        int count = 1;
        while (resultSet.next()) {
            String barcode = resultSet.getString("barcode");
            BigDecimal quantity = new BigDecimal(resultSet.getDouble("quant"));
            BigDecimal price = new BigDecimal(resultSet.getDouble("price"));
            Integer npp = resultSet.getInt("npp");
            npp = npp == 0 ? count : npp;
            count++;
            itemsList.add(Arrays.asList((Object) dv, num, vop, ana1, ana2, barcode, quantity, price, String.valueOf(npp)));
        }
        resultSet.close();
        statement.close();

        return itemsList;
    }

    private void createANATableIfNotExists(Connection connection) throws SQLException {
        Statement statement = connection.createStatement();
        String sql = "CREATE TABLE IF NOT EXISTS ana " +
                "(ana  TEXT PRIMARY KEY," +
                " naim TEXT," +
                " fld1 TEXT," +
                " fld2 TEXT," +
                " fld3 TEXT)";
        statement.executeUpdate(sql);
        statement.close();
    }

    private void updateANATable(Connection connection, TransactionTerminalInfo transactionInfo) throws SQLException {
        if (listNotEmpty(transactionInfo.terminalLegalEntityList)) {
            Statement statement = connection.createStatement();
            String sql = "BEGIN TRANSACTION;";
            for (TerminalLegalEntity legalEntity : transactionInfo.terminalLegalEntityList) {
                if (legalEntity.idLegalEntity != null) {
                    sql += String.format("INSERT OR REPLACE INTO ana VALUES('%s', '%s', '%s', '%s', '%s');",
                            "ПС" + formatValue(legalEntity.idLegalEntity), formatValue(legalEntity.nameLegalEntity), "", "", "");
                }
            }
            sql += "COMMIT;";
            statement.executeUpdate(sql);
            statement.close();
        }
    }

    private void createVOPTableIfNotExists(Connection connection) throws SQLException {
        Statement statement = connection.createStatement();
        String sql = "CREATE TABLE IF NOT EXISTS vop " +
                "(vop  TEXT PRIMARY KEY," +
                " rvop TEXT," +
                " naim TEXT," +
                " van1 TEXT," +
                " van2 TEXT," +
                " van3 TEXT," +
                " FLAGS INTEGER )";
        statement.executeUpdate(sql);
        statement.close();
    }

    private void updateVOPTable(Connection connection, TransactionTerminalInfo transactionInfo) throws SQLException {
        if (listNotEmpty(transactionInfo.terminalDocumentTypeList)) {
            Statement statement = connection.createStatement();
            String sql = "BEGIN TRANSACTION;";
            for (TerminalDocumentType tdt : transactionInfo.terminalDocumentTypeList) {
                if (tdt.id != null)
                    sql += String.format("INSERT OR REPLACE INTO vop VALUES('%s', '%s', '%s', '%s', '%s', '%s', '%s');",
                            formatValue(tdt.id), "", formatValue(tdt.name), formatValue(tdt.analytics1), formatValue(tdt.analytics2), "", formatValue(tdt.flag == null ? "31" : tdt.flag));
            }
            sql += "COMMIT;";
            statement.executeUpdate(sql);
            statement.close();
        }
    }


    private void createAssortTableIfNotExists(Connection connection) throws SQLException {
        Statement statement = connection.createStatement();
        String sql = "CREATE TABLE IF NOT EXISTS assort " +
                "(post    TEXT," +
                " barcode TEXT," +
                "PRIMARY KEY ( post, barcode))";
        statement.executeUpdate(sql);
        statement.close();
    }

    private void updateAssortTable(Connection connection, TransactionTerminalInfo transactionInfo) throws SQLException {
        if(transactionInfo.snapshot) {
            Statement statement = connection.createStatement();
            statement.executeUpdate("DELETE FROM assort");
            statement.close();
        }
        if (listNotEmpty(transactionInfo.terminalAssortmentList)) {
            Statement statement = connection.createStatement();
            String sql = "BEGIN TRANSACTION;";
            for (TerminalAssortment assortment : transactionInfo.terminalAssortmentList) {
                if (assortment.idBarcode != null && assortment.idSupplier != null)
                    sql += String.format("INSERT OR REPLACE INTO assort VALUES('%s', '%s');",
                            ("ПС" + assortment.idSupplier), assortment.idBarcode);
            }
            sql += "COMMIT;";
            statement.executeUpdate(sql);
            statement.close();
        }
    }

    private void createGoodsTableIfNotExists(Connection connection) throws SQLException {
        Statement statement = connection.createStatement();
        String sql = "CREATE TABLE IF NOT EXISTS goods " +
                "(barcode TEXT PRIMARY KEY," +
                " naim    TEXT," +
                " price   REAL," +
                " quant   REAL," +
                " fld1    TEXT," +
                " fld2    TEXT," +
                " fld3    TEXT," +
                " fld4    TEXT," +
                " fld5    TEXT," +
                " image   TEXT)";
        statement.executeUpdate(sql);
        statement.close();
    }

    private void updateTerminalGoodsTable(Connection connection, List<TerminalOrder> terminalOrderList) throws SQLException {
        if (listNotEmpty(terminalOrderList)) {
            Statement statement = connection.createStatement();
            String sql = "BEGIN TRANSACTION;";
            for (TerminalOrder order : terminalOrderList) {
                if (order.barcode != null)
                    sql += String.format("INSERT OR IGNORE INTO goods VALUES('%s', '%s', '%s', '%s', '%s', '%s', '%s', '%s', '%s', '%s');",
                            formatValue(order.barcode), formatValue(order.name), formatValue(order.price), "", "", "", "", "", "", "");
            }
            sql += "COMMIT;";
            statement.executeUpdate(sql);
            statement.close();
        }
    }
    
    private void updateGoodsTable(Connection connection, TransactionTerminalInfo transactionInfo) throws SQLException {
        if(transactionInfo.snapshot) {
            Statement statement = connection.createStatement();      
            statement.executeUpdate("BEGIN TRANSACTION; DELETE FROM zayavki; DELETE FROM goods; COMMIT;");
            statement.close();
        }
        if (listNotEmpty(transactionInfo.itemsList)) {
            Statement statement = connection.createStatement();
            String sql = "BEGIN TRANSACTION;";
            for (TerminalItemInfo item : transactionInfo.itemsList) {
                if (item.idBarcode != null)
                    sql += String.format("INSERT OR REPLACE INTO goods VALUES('%s', '%s', '%s', '%s', '%s', '%s', '%s', '%s', '%s', '%s');",
                            formatValue(item.idBarcode), formatValue(item.name), formatValue(item.price),
                            formatValue(item.quantity), "", "", "", "", "", formatValue(item.image));
            }
            sql += "COMMIT;";
            statement.executeUpdate(sql);
            statement.close();
        }
    }

    private void createVANTableIfNotExists(Connection connection) throws SQLException {
        Statement statement = connection.createStatement();
        String sql = "CREATE TABLE IF NOT EXISTS van " +
                "(van    TEXT PRIMARY KEY," +
                " naim   TEXT)";
        statement.executeUpdate(sql);
        statement.close();
    }

    private void updateVANTable(Connection connection, TransactionTerminalInfo transactionInfo) throws SQLException {
        if (listNotEmpty(transactionInfo.terminalHandbookTypeList)) {
            Statement statement = connection.createStatement();
            String sql = "BEGIN TRANSACTION;";
            for (TerminalHandbookType terminalHandbookType : transactionInfo.terminalHandbookTypeList) {
                if (terminalHandbookType.id != null && terminalHandbookType.name != null)
                    sql += String.format("INSERT OR REPLACE INTO van VALUES('%s', '%s');",
                            terminalHandbookType.id, terminalHandbookType.name);
            }
            sql += "COMMIT;";
            statement.executeUpdate(sql);
            statement.close();
        }
    }

    private void createOrderTable(Connection connection) throws SQLException {
        Statement statement = connection.createStatement();
        String sql = "DROP TABLE IF EXISTS zayavki;" +
                "CREATE TABLE zayavki " +
                "(dv     TEXT," +
                " num   TEXT," +
                " post  TEXT," +
                " barcode   TEXT," +
                " quant   REAL," +
                " price    REAL," +
                " minquant    REAL," +
                " maxquant    REAL," +
                " minprice    REAL," +
                " maxprice    REAL," +
                "PRIMARY KEY (num, barcode))";
        statement.executeUpdate(sql);
        statement.close();
    }

    private void updateOrderTable(Connection connection, List<TerminalOrder> terminalOrderList) throws SQLException {
        if (listNotEmpty(terminalOrderList)) {
            Statement statement = connection.createStatement();
            String sql = "BEGIN TRANSACTION;";
            for (TerminalOrder order : terminalOrderList) {
                if (order.number != null) {
                    String supplier = order.supplier == null ? "" : ("ПС" + formatValue(order.supplier));
                    sql += String.format("INSERT OR REPLACE INTO zayavki VALUES('%s', '%s', '%s', '%s', '%s', '%s', '%s', '%s', '%s', '%s');",
                            formatValue(order.date), formatValue(order.number), supplier, formatValue(order.barcode),
                            formatValue(order.quantity), formatValue(order.price), formatValue(order.minQuantity),
                            formatValue(order.maxQuantity), formatValue(order.minPrice), formatValue(order.maxPrice));
                }
            }
            sql += "COMMIT;";
            statement.executeUpdate(sql);
            statement.close();
        }
    }

    private Object formatValue(Object value) {
        return value == null ? "" : value;
    }

    private String makeDBPath(String directory, Integer nppGroupTerminal) {
        return directory + "/" + (nppGroupTerminal == null ? "tsd" : nppGroupTerminal) + ".db";
    }

    public static boolean isFileLocked(File file) {
        boolean isLocked = false;
        FileChannel channel = null;
        FileLock lock = null;
        try {
            channel = new RandomAccessFile(file, "rw").getChannel();
            lock = channel.tryLock();
            if (lock == null)
                isLocked = true;
        } catch (Exception e) {
            logger.info(e);
            isLocked = true;
        } finally {
            if (lock != null) {
                try {
                    lock.release();
                } catch (Exception e) {
                    logger.info(e);
                    isLocked = true;
                }
            }
            if (channel != null)
                try {
                    channel.close();
                } catch (IOException e) {
                    logger.info(e);
                    isLocked = true;
                }
        }
        return isLocked;
    }

    protected BigDecimal safeMultiply(BigDecimal operand1, BigDecimal operand2) {
        if (operand1 == null || operand1.doubleValue() == 0 || operand2 == null || operand2.doubleValue() == 0)
            return null;
        else return operand1.multiply(operand2);
    }

    protected boolean listNotEmpty(List list) {
        return list != null && !list.isEmpty();
    }
}