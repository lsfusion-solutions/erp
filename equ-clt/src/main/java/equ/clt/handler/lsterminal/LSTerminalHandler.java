package equ.clt.handler.lsterminal;

import com.google.common.base.Throwables;
import equ.api.*;
import equ.clt.EquipmentServer;
import org.apache.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class LSTerminalHandler extends TerminalHandler {

    protected final static Logger logger = Logger.getLogger(EquipmentServer.class);
    
    public LSTerminalHandler() {
    }

    @Override
    public void sendTerminalDocumentTypes(List list, List list2) throws IOException {
        
    }

    @Override
    public List<TerminalDocumentInfo> readTerminalDocumentInfo(List list) throws IOException {
        return new ArrayList<TerminalDocumentInfo>();
    }

    @Override
    public void finishSendingTerminalDocumentInfo(List list, List list2) throws IOException {

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
    public TransactionInfo loadTransactionInfo(String dbPath) throws IOException {

        try {
            Class.forName("org.sqlite.JDBC");
            Connection connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);

            TransactionInfo transactionInfo = readTransactionInfo(connection);

            connection.close();

            return transactionInfo;

        } catch (Exception e) {
            logger.error(e);
            throw Throwables.propagate(e);
        }

    }
    
    private void createItemTableIfNotExists(Connection connection) throws SQLException {
        Statement statement = connection.createStatement();
        String sql = "CREATE TABLE IF NOT EXISTS item " +
                "(barcode CHAR(20) PRIMARY KEY     NOT NULL," +
                "name     CHAR(50)                 NOT NULL," +
                "price    REAL                     NOT NULL," +
                "quantity REAL                     NOT NULL," +
                "image    CHAR(20)                 )";
        statement.executeUpdate(sql);
        statement.close();
    }
    
    private void updateItemTable(Connection connection, TransactionTerminalInfo transactionInfo) throws SQLException {
        Statement statement = connection.createStatement();
        String sql = "BEGIN TRANSACTION;";
        for (TerminalItemInfo item : transactionInfo.itemsList) {
            if(item.idBarcode != null && item.name != null && item.price != null && item.quantity != null)
                sql += String.format("INSERT OR REPLACE INTO item VALUES(%s, %s, %s, %s, %s);", 
                        item.idBarcode, item.name, item.price, item.quantity, item.image);
        }
        sql += "COMMIT;";               
        statement.executeUpdate(sql);
        statement.close();
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
        Statement statement = connection.createStatement();
        String sql = "BEGIN TRANSACTION;";
        for (TerminalItemInfo item : transactionInfo.itemsList) {
            if(/*item.idSupplier != null && */item.idBarcode != null)
                sql += String.format("INSERT OR REPLACE INTO assortment VALUES(%s, %s);",
                        null/*item.idSupplier*/, item.idBarcode);
        }
        sql += "COMMIT;";
        statement.executeUpdate(sql);
        statement.close();
    }
    
    
    
    
    private TransactionTerminalInfo readTransactionInfo(Connection connection) throws SQLException {
        
        List<TerminalItemInfo> itemsList = new ArrayList<TerminalItemInfo>();
        
        Statement statement = connection.createStatement();
        String sql = "SELECT barcode, price FROM item;";
        ResultSet resultSet = statement.executeQuery(sql);
        while (resultSet.next()) {
            String barcode = resultSet.getString("barcode");
            String name = resultSet.getString("name");
            BigDecimal price = new BigDecimal(resultSet.getDouble("price"));
            BigDecimal quantity = new BigDecimal(resultSet.getDouble("quantity"));
            String image = resultSet.getString("image");
            itemsList.add(new TerminalItemInfo(barcode, name, price, null, false, null, quantity, image));
            
        }
        resultSet.close();
        statement.close();
        return new TransactionTerminalInfo(null, null, itemsList, null, null);
    }
}