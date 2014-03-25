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
        
        Integer nppGroupMachinery = ((TerminalInfo)transactionInfo.machineryInfoList.get(0)).number;
        File directory = new File("\\db");
        if (directory.exists() || directory.mkdir()) {
            String dbPath = directory.getAbsolutePath() + "\\" + nppGroupMachinery + ".db";
            try {
                Class.forName("org.sqlite.JDBC");
                Connection connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
                createTableIfNotExists(connection);

                updateTable(connection, transactionInfo);

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
    
    private void createTableIfNotExists(Connection connection) throws SQLException {
        Statement statement = connection.createStatement();
        String sql = "CREATE TABLE IF NOT EXISTS item " +
                "(barcode CHAR(15) PRIMARY KEY     NOT NULL," +
                " price            REAL            NOT NULL)";
        statement.executeUpdate(sql);
        statement.close();
    }
    
    private void updateTable(Connection connection, TransactionInfo transactionInfo) throws SQLException {
        Statement statement = connection.createStatement();
        String sql = "BEGIN TRANSACTION;";
        for (Object itemObject : transactionInfo.itemsList) {
            ItemInfo item = (ItemInfo) itemObject;
            if(item.idBarcode != null && item.price != null)
                sql += String.format("INSERT OR REPLACE INTO item VALUES(%s, %s);", item.idBarcode, item.price);
        }
        sql += "COMMIT;";               
        statement.executeUpdate(sql);
        statement.close();
    }

    private TransactionInfo readTransactionInfo(Connection connection) throws SQLException {
        
        List<ItemInfo> itemsList = new ArrayList<ItemInfo>();
        
        Statement statement = connection.createStatement();
        String sql = "SELECT barcode, price FROM item;";
        ResultSet resultSet = statement.executeQuery(sql);
        while (resultSet.next()) {
            String barcode = resultSet.getString("barcode");
            BigDecimal price = new BigDecimal(resultSet.getDouble("price"));
            itemsList.add(new ItemInfo(barcode, null, price, null, null, null, null, null, null, false, null, null, null));
            
        }
        resultSet.close();
        statement.close();
        return new TransactionTerminalInfo(null, null, itemsList, null, null);
    }
}