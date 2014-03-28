package equ.clt.handler.lsterminal;

import com.google.common.base.Throwables;
import com.hexiong.jdbf.DBFWriter;
import com.hexiong.jdbf.JDBFException;
import equ.api.*;
import equ.api.terminal.*;
import equ.clt.EquipmentServer;
import org.apache.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

public class LSTerminalHandler extends TerminalHandler {

    protected final static Logger logger = Logger.getLogger(EquipmentServer.class);
    
    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd");
    
    public LSTerminalHandler() {
    }

    @Override
    public void sendTransaction(TransactionInfo transactionInfo, List machineryInfoList) throws IOException {

    }

    @Override
    public void sendSoftCheck(SoftCheckInfo softCheckInfo) throws IOException {

    }

    @Override
    public void saveTransactionInfo(TransactionInfo transactionInfo) throws IOException {
        
        Integer nppGroupMachinery = ((TransactionTerminalInfo)transactionInfo).machineryInfoList.get(0).number;
        File directory = new File("\\db");
        if (directory.exists() || directory.mkdir()) {
            String dbPath = directory.getAbsolutePath() + "\\" + nppGroupMachinery + ".db";
            try {
                Class.forName("org.sqlite.JDBC");
                Connection connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
                
                createItemTableIfNotExists(connection);
                updateItemTable(connection, (TransactionTerminalInfo) transactionInfo);
                
                createAssortmentTableIfNotExists(connection);
                updateAssortmentTable(connection, (TransactionTerminalInfo) transactionInfo);

                createVANTableIfNotExists(connection);
                updateVANTable(connection, (TransactionTerminalInfo) transactionInfo);

                createANATableIfNotExists(connection);
                updateANATable(connection, (TransactionTerminalInfo) transactionInfo);

                createOrderTableIfNotExists(connection);
                updateOrderTable(connection, (TransactionTerminalInfo) transactionInfo);
                
                sendTransactionInfo(directory.getAbsolutePath(), dbPath);
                
                connection.close();               

            } catch (Exception e) {
                logger.error(e);
                throw Throwables.propagate(e);
            }
        } else {
            logger.error("Directory " + directory.getAbsolutePath() + "doesn't exist");
        }
    }

    @Override
    public void sendTransactionInfo(String directory, String dbPath) throws IOException {

        Connection connection = null;
        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);

            createGoodsFile(connection, directory);
            createAssortmentFile(connection, directory);
            createVANFile(connection, directory);
            createANAFile(connection, directory);
            createOrderFile(connection, directory);
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
                            item.idBarcode, item.name == null ? "" : item.name, item.price == null ? "" : item.price,
                            item.quantity == null ? "" : item.quantity, item.image == null ? "" : item.price);
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
        DBFWriter dbfwriter = new DBFWriter(dbfFile.getAbsolutePath(), fields, "CP866");

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
            itemsList.add(new TerminalItemInfo(barcode, name, price, null, false, quantity, image));

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
        DBFWriter dbfwriter = new DBFWriter(dbfFile.getAbsolutePath(), fields, "CP866");

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
            handbookTypeList.add(new TerminalItemInfo(barcode, null, null, null,false, null, null));
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
        DBFWriter dbfwriter = new DBFWriter(dbfFile.getAbsolutePath(), fields, "CP866");

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
        DBFWriter dbfwriter = new DBFWriter(dbfFile.getAbsolutePath(), fields, "CP866");

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
            terminalDocumentTypeList.add(new TerminalDocumentType(id, name));
        }
        resultSet.close();
        statement.close();
        return terminalDocumentTypeList;
    }

    private void createOrderTableIfNotExists(Connection connection) throws SQLException {
        Statement statement = connection.createStatement();
        String sql = "CREATE TABLE IF NOT EXISTS orders " +
                "(date     CHAR(10)             NOT NULL," +
                " number   CHAR(20) PRIMARY KEY NOT NULL," +
                " supplier  CHAR(20)," +
                " barcode   CHAR(20)," +
                " price    REAL," +
                " quantity REAL)";
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
                            order.date == null ? "" : order.date, order.number,  order.supplier == null ? "" : order.supplier,
                            order.barcode == null ? "" : order.barcode, order.price == null ? "" : order.price,
                            order.quantity == null ? "" : order.quantity);
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
        DBFWriter dbfwriter = new DBFWriter(dbfFile.getAbsolutePath(), fields, "CP866");

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
}