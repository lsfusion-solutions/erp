package equ.clt.handler.lsterminal;

import com.google.common.base.Throwables;
import com.hexiong.jdbf.DBFWriter;
import com.hexiong.jdbf.JDBFException;
import equ.api.SoftCheckInfo;
import equ.api.TransactionInfo;
import equ.api.terminal.*;
import equ.clt.EquipmentServer;
import org.apache.log4j.Logger;
import org.xBaseJ.DBF;
import org.xBaseJ.xBaseJException;

import java.io.*;
import java.math.BigDecimal;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.sql.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

public class LSTerminalHandler extends TerminalHandler {

    protected final static Logger logger = Logger.getLogger(EquipmentServer.class);
    
    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd");
    String charset = "cp1251";
    String dbPath = "\\db";
    
    public LSTerminalHandler() {
    }

    @Override
    public void sendTransaction(TransactionInfo transactionInfo, List machineryInfoList) throws IOException {

        Integer nppGroupTerminal = ((TransactionTerminalInfo) transactionInfo).nppGroupTerminal;
        String directory = ((TransactionTerminalInfo) transactionInfo).directoryGroupTerminal;
        if (directory != null) {
            String exchangeDirectory = directory + "\\import";
            if ((new File(exchangeDirectory).exists() || new File(exchangeDirectory).mkdir())) {

                Connection connection = null;
                try {
                    Class.forName("org.sqlite.JDBC");
                    connection = DriverManager.getConnection("jdbc:sqlite:" + makeDBPath(dbPath, nppGroupTerminal));

                    createVOPFile(connection, exchangeDirectory);
                    createGoodsFile(connection, exchangeDirectory);
                    createAssortmentFile(connection, exchangeDirectory);
                    createVANFile(connection, exchangeDirectory);
                    createANAFile(connection, exchangeDirectory);
                    createOrderFile(connection, exchangeDirectory);
                    connection.close();
                } catch (Exception e) {
                    if (connection != null)
                        try {
                            connection.close();
                        } catch (SQLException e1) {
                            logger.error(e1);
                            throw Throwables.propagate(e1);
                        }
                    logger.error(e);
                    throw Throwables.propagate(e);
                }
            }
        }
    }

    @Override
    public void sendSoftCheck(SoftCheckInfo softCheckInfo) throws IOException {
    }

    @Override
    public void saveTransactionTerminalInfo(TransactionTerminalInfo transactionInfo) throws IOException {
        
        logger.info("LSTerminal: save Transaction #" + transactionInfo.id);
        
        Integer nppGroupTerminal = transactionInfo.nppGroupTerminal;
        File directory = new File(dbPath);
        if (directory.exists() || directory.mkdir()) {
            try {
                Class.forName("org.sqlite.JDBC");
                Connection connection = DriverManager.getConnection("jdbc:sqlite:" + makeDBPath(dbPath, nppGroupTerminal));

                createOperationTableIfNotExists(connection);
                updateOperationTable(connection, transactionInfo);
                
                createItemTableIfNotExists(connection);
                updateItemTable(connection, transactionInfo);
                
                createAssortmentTableIfNotExists(connection);
                updateAssortmentTable(connection, transactionInfo);

                createVANTableIfNotExists(connection);
                updateVANTable(connection, transactionInfo);

                createANATableIfNotExists(connection);
                updateANATable(connection, transactionInfo);

                createOrderTableIfNotExists(connection);
                updateOrderTable(connection, transactionInfo);               
                
                connection.close();               

            } catch (Exception e) {
                logger.error(e);
                throw Throwables.propagate(e);
            }
        } else {
            logger.error("Directory " + directory.getAbsolutePath() + "doesn't exist");
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
        Set<String> directorySet = new HashSet<String>();
        for (Object m : machineryInfoList) {
            TerminalInfo t = (TerminalInfo) m;
            if (t.directory != null)
                directorySet.add(t.directory);
        }

        List<String> filePathList = new ArrayList<String>();

        List<TerminalDocumentDetail> terminalDocumentDetailList = new ArrayList<TerminalDocumentDetail>();

        for (String directory : directorySet) {

            String exchangeDirectory = directory + "\\export";
            
            File[] filesList = new File(exchangeDirectory).listFiles(new FileFilter() {
                @Override
                public boolean accept(File pathname) {
                    return pathname.getName().toUpperCase().startsWith("DOK_") && pathname.getPath().toUpperCase().endsWith(".DBF");
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

                            Map<String, Integer> barcodeCountMap = new HashMap<String, Integer>();
                            
                            DBF importFile = new DBF(file.getPath());
                            int recordCount = importFile.getRecordCount();
                            
                            for (int i = 0; i < recordCount; i++) {

                                importFile.read();

                                String idTerminalDocumentType = trim(getDBFFieldValue(importFile, "VOP", charset, false, null));
                                //String dv = trim(getDBFFieldValue(importFile, "DV", charset, false, null));
                                String numberTerminalDocument = trim(getDBFFieldValue(importFile, "NUM", charset, false, null));
                                String idTerminalHandbookType1 = trim(getDBFFieldValue(importFile, "ANA1", charset, false, null));
                                String idTerminalHandbookType2 = trim(getDBFFieldValue(importFile, "ANA2", charset, false, null));
                                //String ana3 = getDBFFieldValue(importFile, "ANA3", charset, false, null);
                                String barcode = trim(getDBFFieldValue(importFile, "BARCODE", charset, false, null));
                                //String part = trim(getDBFFieldValue(importFile, "PART", charset, false, null));
                                BigDecimal quantity = getDBFBigDecimalFieldValue(importFile, "QUANT", charset, false, null);
                                BigDecimal price = getDBFBigDecimalFieldValue(importFile, "PRICE", charset, false, null);
                                BigDecimal sum = safeMultiply(quantity, price);
                                Integer count = barcodeCountMap.get(barcode);
                                String idTerminalDocumentDetail = numberTerminalDocument + "_" + barcode + (count == null ? "" : ("_" + count));
                                barcodeCountMap.put(barcode, count == null ? 1 : (count + 1));
                                
                                terminalDocumentDetailList.add(new TerminalDocumentDetail(numberTerminalDocument, idTerminalHandbookType1,
                                        idTerminalHandbookType2, idTerminalDocumentType, idTerminalDocumentDetail, barcode, price, quantity, sum));
                            }
                            
                            importFile.close();
                                                        
                            filePathList.add(file.getAbsolutePath());
                        }
                    } catch (Throwable e) {
                        logger.error("File: " + file.getAbsolutePath(), e);
                    }
                }
            }
        }
        return new TerminalDocumentBatch(terminalDocumentDetailList, filePathList); 
    }

    private void createOperationTableIfNotExists(Connection connection) throws SQLException {
        Statement statement = connection.createStatement();
        String sql = "CREATE TABLE IF NOT EXISTS operation " +
                "(id        CHAR(2) PRIMARY KEY  NOT NULL," +
                "name       CHAR(50)," +
                "analytics1 CHAR(2)," +
                "analytics2 CHAR(2))";
        statement.executeUpdate(sql);
        statement.close();
    }

    private void updateOperationTable(Connection connection, TransactionTerminalInfo transactionInfo) throws SQLException {
        if (transactionInfo.terminalDocumentTypeList != null && !transactionInfo.terminalDocumentTypeList.isEmpty()) {
            Statement statement = connection.createStatement();
            String sql = "BEGIN TRANSACTION;";
            for (TerminalDocumentType documentType : transactionInfo.terminalDocumentTypeList) {
                if (documentType.id != null)
                    sql += String.format("INSERT OR REPLACE INTO operation VALUES('%s', '%s', '%s', '%s');",
                            documentType.id, formatValue(documentType.name), formatValue(documentType.analytics1),
                            formatValue(documentType.analytics2));
            }
            sql += "COMMIT;";
            statement.executeUpdate(sql);
            statement.close();
        }
    }

    private void createVOPFile(Connection connection, String directory) throws JDBFException, SQLException, IOException {
        List<TerminalDocumentType> terminalDocumentTypeList = readOperationTable(connection);
        OverJDBField[] fields = {
                new OverJDBField("VOP", 'C', 2, 0), new OverJDBField("RVOP", 'C', 2, 0),
                new OverJDBField("NAIM", 'C', 50, 0), new OverJDBField("VAN1", 'C', 2, 0),
                new OverJDBField("VAN2", 'C', 2, 0), new OverJDBField("VAN3", 'C', 2, 0),
                new OverJDBField("FLAGS", 'F', 10, 0)
        };
        File dbfFile = new File(directory + "\\vop.dbf");
        DBFWriter dbfwriter = new DBFWriter(dbfFile.getAbsolutePath(), fields, charset);

        for (TerminalDocumentType documentType : terminalDocumentTypeList) {
            Integer flag = (documentType.name == null || !documentType.name.equals("Приход")) ? 0 : 12;
            dbfwriter.addRecord(new Object[]{documentType.id, null, documentType.name, 
                    documentType.analytics1, documentType.analytics2, null, flag});
        }
        dbfwriter.close();
    }

    private List<TerminalDocumentType> readOperationTable(Connection connection) throws SQLException {

        List<TerminalDocumentType> terminalDocumentTypeList = new ArrayList<TerminalDocumentType>();

        Statement statement = connection.createStatement();
        String sql = "SELECT id, name, analytics1, analytics2 FROM operation;";
        ResultSet resultSet = statement.executeQuery(sql);
        while (resultSet.next()) {
            String id = resultSet.getString("id");
            String name = resultSet.getString("name");
            String analytics1 = resultSet.getString("analytics1");
            String analytics2 = resultSet.getString("analytics2");
            terminalDocumentTypeList.add(new TerminalDocumentType(id, name, analytics1, analytics2));
        }
        resultSet.close();
        statement.close();
        return terminalDocumentTypeList;
    }
    
    private void createItemTableIfNotExists(Connection connection) throws SQLException {
        Statement statement = connection.createStatement();
        String sql = "CREATE TABLE IF NOT EXISTS item " +
                "(barcode CHAR(20) PRIMARY KEY  NOT NULL," +
                "name     CHAR(50)," +
                "price    REAL," +
                "quantity REAL," +
                "image    CHAR(20))";
        statement.executeUpdate(sql);
        statement.close();
    }

    private void updateItemTable(Connection connection, TransactionTerminalInfo transactionInfo) throws SQLException {
        if (transactionInfo.itemsList != null && !transactionInfo.itemsList.isEmpty()) {
            Statement statement = connection.createStatement();
            String sql = "BEGIN TRANSACTION;";
            for (TerminalItemInfo item : transactionInfo.itemsList) {
                if (item.idBarcode != null)
                    sql += String.format("INSERT OR REPLACE INTO item VALUES('%s', '%s', '%s', '%s', '%s');",
                            formatValue(item.idBarcode), formatValue(item.name), formatValue(item.price),
                            formatValue(item.quantity), formatValue(item.image));
            }
            sql += "COMMIT;";
            statement.executeUpdate(sql);
            statement.close();
        }
    }

    private void createGoodsFile(Connection connection, String directory) throws JDBFException, SQLException, IOException {
        List<TerminalItemInfo> terminalItemInfoList = readItemTable(connection);
        OverJDBField[] fields = {
                new OverJDBField("BARCODE", 'C', 20, 0), new OverJDBField("NAIM", 'C', 50, 0),
                new OverJDBField("PRICE", 'F', 10, 2), new OverJDBField("QUANT", 'F', 10, 3),
                new OverJDBField("IMAGE", 'C', 20, 0)
        };
        File dbfFile = new File(directory + "\\goods.dbf");
        DBFWriter dbfwriter = new DBFWriter(dbfFile.getAbsolutePath(), fields, charset);

        for (TerminalItemInfo item : terminalItemInfoList) {
            dbfwriter.addRecord(new Object[]{item.idBarcode, item.name, item.price, item.quantity, item.image});
        }
        dbfwriter.close();
    }

    private List<TerminalItemInfo> readItemTable(Connection connection) throws SQLException {

        List<TerminalItemInfo> itemsList = new ArrayList<TerminalItemInfo>();

        Statement statement = connection.createStatement();
        String sql = "SELECT barcode, name, price, quantity, image FROM item;";
        ResultSet resultSet = statement.executeQuery(sql);
        while (resultSet.next()) {
            String barcode = resultSet.getString("barcode");
            String name = resultSet.getString("name");
            BigDecimal price = new BigDecimal(resultSet.getDouble("price"));
            BigDecimal quantity = new BigDecimal(resultSet.getDouble("quantity"));
            String image = resultSet.getString("image");
            itemsList.add(new TerminalItemInfo(barcode, name, price, false, quantity, image));

        }
        resultSet.close();
        statement.close();
        return itemsList;
    }

    private void createAssortmentTableIfNotExists(Connection connection) throws SQLException {
        Statement statement = connection.createStatement();
        String sql = "CREATE TABLE IF NOT EXISTS assortment " +
                "(supplier CHAR(20)      NOT NULL," +
                " barcode   CHAR(20)      NOT NULL)";
        statement.executeUpdate(sql);
        statement.close();
    }

    private void updateAssortmentTable(Connection connection, TransactionTerminalInfo transactionInfo) throws SQLException {
        if (transactionInfo.itemsList != null && !transactionInfo.itemsList.isEmpty()) {
            Statement statement = connection.createStatement();
            String sql = "BEGIN TRANSACTION;";
            for (TerminalItemInfo item : transactionInfo.itemsList) {
                if (/*item.idSupplier != null && */item.idBarcode != null)
                    sql += String.format("INSERT OR REPLACE INTO assortment VALUES(%s, %s);",
                            "1"/*item.idSupplier*/, item.idBarcode);
            }
            sql += "COMMIT;";
            statement.executeUpdate(sql);
            statement.close();
        }
    }

    private void createAssortmentFile(Connection connection, String directory) throws JDBFException, SQLException, IOException {
        List<TerminalItemInfo> assortmentList = readAssortmentTable(connection);
        OverJDBField[] fields = {
                new OverJDBField("POST", 'C', 20, 0), new OverJDBField("BARCODE", 'C', 20, 0)
        };
        File dbfFile = new File(directory + "\\assort.dbf");
        DBFWriter dbfwriter = new DBFWriter(dbfFile.getAbsolutePath(), fields, charset);

        for (TerminalItemInfo assortment : assortmentList) {
            dbfwriter.addRecord(new Object[]{"1", assortment.idBarcode});
        }
        dbfwriter.close();
    }

    private List<TerminalItemInfo> readAssortmentTable(Connection connection) throws SQLException {

        List<TerminalItemInfo> handbookTypeList = new ArrayList<TerminalItemInfo>();

        Statement statement = connection.createStatement();
        String sql = "SELECT supplier, barcode FROM assortment;";
        ResultSet resultSet = statement.executeQuery(sql);
        while (resultSet.next()) {
            //String supplier = resultSet.getString("supplier");
             String barcode = resultSet.getString("barcode");
            handbookTypeList.add(new TerminalItemInfo(barcode, null, null,false, null, null));
        }
        resultSet.close();
        statement.close();
        return handbookTypeList;
    }

    private void createVANTableIfNotExists(Connection connection) throws SQLException {
        Statement statement = connection.createStatement();
        String sql = "CREATE TABLE IF NOT EXISTS van " +
                "(id     CHAR(2) PRIMARY KEY NOT NULL," +
                " name   CHAR(50)            NOT NULL)";
        statement.executeUpdate(sql);
        statement.close();
    }

    private void updateVANTable(Connection connection, TransactionTerminalInfo transactionInfo) throws SQLException {
        if (transactionInfo.terminalHandbookTypeList != null && !transactionInfo.terminalHandbookTypeList.isEmpty()) {
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

    private void createVANFile(Connection connection, String directory) throws JDBFException, SQLException, IOException {
        List<TerminalHandbookType> terminalHandbookTypeList = readVANTable(connection);
        OverJDBField[] fields = {
                new OverJDBField("VAN", 'C', 2, 0), new OverJDBField("NAIM", 'C', 50, 0)
        };
        File dbfFile = new File(directory + "\\van.dbf");
        DBFWriter dbfwriter = new DBFWriter(dbfFile.getAbsolutePath(), fields, charset);

        for (TerminalHandbookType van : terminalHandbookTypeList) {
            dbfwriter.addRecord(new Object[]{van.id, van.name});
        }
        dbfwriter.close();
    }
    
    private List<TerminalHandbookType> readVANTable(Connection connection) throws SQLException {

        List<TerminalHandbookType> handbookTypeList = new ArrayList<TerminalHandbookType>();

        Statement statement = connection.createStatement();
        String sql = "SELECT id, name FROM van;";
        ResultSet resultSet = statement.executeQuery(sql);
        while (resultSet.next()) {
            String id = resultSet.getString("id");
            String name = resultSet.getString("name");
            handbookTypeList.add(new TerminalHandbookType(id, name));
        }
        resultSet.close();
        statement.close();
        return handbookTypeList;
    }

    private void createANATableIfNotExists(Connection connection) throws SQLException {
        Statement statement = connection.createStatement();
        String sql = "CREATE TABLE IF NOT EXISTS ana " +
                "(id     CHAR(20) PRIMARY KEY NOT NULL," +
                " name   CHAR(50)             NOT NULL)";
        statement.executeUpdate(sql);
        statement.close();
    }

    private void updateANATable(Connection connection, TransactionTerminalInfo transactionInfo) throws SQLException {
        if (transactionInfo.terminalDocumentTypeList != null && !transactionInfo.terminalDocumentTypeList.isEmpty()) {
            Statement statement = connection.createStatement();
            String sql = "BEGIN TRANSACTION;";
            for (TerminalDocumentType terminalDocumentType : transactionInfo.terminalDocumentTypeList) {
                if (terminalDocumentType.id != null && terminalDocumentType.name != null)
                    sql += String.format("INSERT OR REPLACE INTO ana VALUES('%s', '%s');",
                            terminalDocumentType.id, terminalDocumentType.name);
            }
            sql += "COMMIT;";
            statement.executeUpdate(sql);
            statement.close();
        }
    }

    private void createANAFile(Connection connection, String directory) throws JDBFException, SQLException, IOException {
        List<TerminalDocumentType> terminalDocumentTypeList = readANATable(connection);
        OverJDBField[] fields = {
                new OverJDBField("ANA", 'C', 20, 0), new OverJDBField("NAIM", 'C', 50, 0)
        };
        File dbfFile = new File(directory + "\\ana.dbf");
        DBFWriter dbfwriter = new DBFWriter(dbfFile.getAbsolutePath(), fields, charset);

        for (TerminalDocumentType ana : terminalDocumentTypeList) {
            dbfwriter.addRecord(new Object[]{ana.id, ana.name});
        }
        dbfwriter.close();
    }
    
    private List<TerminalDocumentType> readANATable(Connection connection) throws SQLException {

        List<TerminalDocumentType> terminalDocumentTypeList = new ArrayList<TerminalDocumentType>();

        Statement statement = connection.createStatement();
        String sql = "SELECT id, name FROM ana;";
        ResultSet resultSet = statement.executeQuery(sql);
        while (resultSet.next()) {
            String id = resultSet.getString("id");
            String name = resultSet.getString("name");
            terminalDocumentTypeList.add(new TerminalDocumentType(id, name, null, null));
        }
        resultSet.close();
        statement.close();
        return terminalDocumentTypeList;
    }

    private void createOrderTableIfNotExists(Connection connection) throws SQLException {
        Statement statement = connection.createStatement();
        String sql = "CREATE TABLE IF NOT EXISTS orders " +
                "(date     CHAR(10) NOT NULL," +
                " number   CHAR(20) NOT NULL," +
                " supplier  CHAR(20)," +
                " barcode   CHAR(20)," +
                " price    REAL," +
                " quantity REAL, " +
                "PRIMARY KEY ( number, barcode))";
        statement.executeUpdate(sql);
        statement.close();
    }

    private void updateOrderTable(Connection connection, TransactionTerminalInfo transactionInfo) throws SQLException {
        if (transactionInfo.terminalOrderList != null && !transactionInfo.terminalOrderList.isEmpty()) {
            Statement statement = connection.createStatement();
            String sql = "BEGIN TRANSACTION;";
            for (TerminalOrder order : transactionInfo.terminalOrderList) {
                if (order.number != null)
                    sql += String.format("INSERT OR REPLACE INTO orders VALUES('%s', '%s', '%s', '%s', '%s', '%s');",
                            formatValue(order.date), formatValue(order.number), formatValue(order.supplier),
                            formatValue(order.barcode), formatValue(order.price), formatValue(order.quantity));
            }
            sql += "COMMIT;";
            statement.executeUpdate(sql);
            statement.close();
        }
    }

    private void createOrderFile(Connection connection, String directory) throws JDBFException, SQLException, IOException, ParseException {
        List<TerminalOrder> orderList = readOrderTable(connection);
        OverJDBField[] fields = {
                new OverJDBField("DV", 'C', 8, 0), new OverJDBField("NUM", 'C', 20, 0),
                new OverJDBField("POST", 'C', 20, 0), new OverJDBField("BARCODE", 'C', 20, 0),
                new OverJDBField("QUANT", 'F', 10, 3), new OverJDBField("PRICE", 'F', 10, 2)
        };
        File dbfFile = new File(directory + "\\zayavki.dbf");
        DBFWriter dbfwriter = new DBFWriter(dbfFile.getAbsolutePath(), fields, charset);

        for (TerminalOrder order : orderList) {
            dbfwriter.addRecord(new Object[]{dateFormat.format(order.date), order.number, order.supplier, order.barcode, order.quantity, order.price});
        }
        dbfwriter.close();
    }
    
    private List<TerminalOrder> readOrderTable(Connection connection) throws SQLException, ParseException {

        List<TerminalOrder> terminalOrderList = new ArrayList<TerminalOrder>();

        Statement statement = connection.createStatement();
        String sql = "SELECT date, number, supplier, barcode, price, quantity FROM orders;";
        ResultSet resultSet = statement.executeQuery(sql);
        while (resultSet.next()) {
            String date = resultSet.getString("date");
            String number = resultSet.getString("number");
            String idSupplier = resultSet.getString("supplier");
            String barcode = resultSet.getString("barcode");
            BigDecimal price = new BigDecimal(resultSet.getDouble("price"));
            BigDecimal quantity = new BigDecimal(resultSet.getDouble("quantity"));
            terminalOrderList.add(new TerminalOrder(dateFormat.parse(date), number, idSupplier, barcode, price, quantity));
        }
        resultSet.close();
        statement.close();
        return terminalOrderList;
    }
    
    private Object formatValue(Object value) {
        return value == null ? "" : value;
    }
    
    private String makeDBPath(String directory, Integer nppGroupTerminal) {
        return directory + "\\" + nppGroupTerminal + ".db";
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
            if(lock != null) {
                try {
                    lock.release();
                } catch (Exception e) {
                    logger.info(e);
                    isLocked = true;
                }
            }
            if(channel != null)
                try {
                    channel.close();
                } catch (IOException e) {
                    logger.info(e);
                    isLocked = true;
                }
        }
        return isLocked;
    }

    protected String getDBFFieldValue(DBF importFile, String fieldName, String charset, Boolean zeroIsNull, String defaultValue) throws UnsupportedEncodingException {
        try {
            String result = new String(importFile.getField(fieldName).getBytes(), charset).trim();
            return result.isEmpty() || (zeroIsNull && result.equals("0")) ? defaultValue : result;
        } catch (xBaseJException e) {
            return defaultValue;
        }
    }

    protected BigDecimal getDBFBigDecimalFieldValue(DBF importFile, String fieldName, String charset, Boolean zeroIsNull, String defaultValue) throws UnsupportedEncodingException {
        String result = getDBFFieldValue(importFile, fieldName, charset, zeroIsNull, defaultValue);
        return (result == null || result.isEmpty() || (zeroIsNull && Double.valueOf(result).equals(new Double(0)))) ? null : new BigDecimal(result.replace(",", "."));
    }

    protected String trim(String input) {
        return input == null ? null : input.trim();
    }

    protected BigDecimal safeMultiply(BigDecimal operand1, BigDecimal operand2) {
        if (operand1 == null || operand1.doubleValue() == 0 || operand2 == null || operand2.doubleValue() == 0)
            return null;
        else return operand1.multiply(operand2);
    }
}