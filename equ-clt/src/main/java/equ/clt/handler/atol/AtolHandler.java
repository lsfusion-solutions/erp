package equ.clt.handler.atol;

import equ.api.SalesBatch;
import equ.api.SalesInfo;
import equ.api.SoftCheckInfo;
import equ.api.cashregister.*;
import equ.clt.EquipmentServer;
import org.apache.log4j.Logger;

import java.io.*;
import java.math.BigDecimal;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.sql.Date;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.*;

public class AtolHandler extends CashRegisterHandler<AtolSalesBatch> {

    protected final static Logger logger = Logger.getLogger(EquipmentServer.class);
    
    public AtolHandler() {
    }

    @Override
    public void sendTransaction(TransactionCashRegisterInfo transactionInfo, List<CashRegisterInfo> machineryInfoList) throws IOException {

        logger.info("Atol: Send Transaction # " + transactionInfo.id);
        
        List<String> directoriesList = new ArrayList<String>();
        for (CashRegisterInfo cashRegisterInfo : machineryInfoList) {
            if ((cashRegisterInfo.port != null) && (!directoriesList.contains(cashRegisterInfo.port.trim())))
                directoriesList.add(cashRegisterInfo.port.trim());
            if ((cashRegisterInfo.directory != null) && (!directoriesList.contains(cashRegisterInfo.directory.trim())))
                directoriesList.add(cashRegisterInfo.directory.trim());
        }

        for (String directory : directoriesList) {

            File exchangeFile = new File(directory + "/import/file.txt");
            PrintWriter writer = new PrintWriter(new OutputStreamWriter(new FileOutputStream(exchangeFile), "windows-1251"));

            writer.println("##@@&&");
            writer.println("#");

            if(!transactionInfo.itemsList.isEmpty()) {
                writer.println("$$$ADDQUANTITY");
                for (CashRegisterItemInfo item : transactionInfo.itemsList) {
                    String idItemGroup = item.hierarchyItemGroup == null || item.hierarchyItemGroup.isEmpty() ? "" : item.hierarchyItemGroup.get(0).idItemGroup;
                    String record = format(item.idBarcode, ";") + format(item.idBarcode, ";") + format(item.name, 100, ";") + //3
                            format(item.composition, 100, ";") + format(item.price, ";") + ";;" + format(item.isWeightItem, ";") + //8
                            ";;;;;;;" + format(idItemGroup, ";") + "1;" + ";;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;";
                    writer.println(record);
                }
            }
            writer.close();            
        }
    }

    @Override
    public void sendSoftCheck(SoftCheckInfo softCheckInfo) throws IOException {

        for (String directory : softCheckInfo.directorySet) {

            //мы пока не знаем формат выгрузки мягких чеков
            
            File exchangeFile = new File(directory + "/import/file.txt");
            
            logger.info("Atol: creating " + exchangeFile.getName() + " file");
            PrintWriter writer = new PrintWriter(new OutputStreamWriter(new FileOutputStream(exchangeFile), "windows-1251"));
            writer.println("$$$ADDSOFTCHEQUE");
            for (String userInvoice : softCheckInfo.invoiceSet) {
                
                writer.println(userInvoice);
            }
            writer.close();
            
        }
    }

    @Override
    public String requestSalesInfo(Map<java.util.Date, Set<String>> requestSalesInfo) throws IOException, ParseException {

        for (Map.Entry<java.util.Date, Set<String>> entry : requestSalesInfo.entrySet()) {
            
            Set<String> directoriesList = entry.getValue();
            logger.info("Atol: creating request files");
            for (String directory : directoriesList) {
                
                //creating request files for sales info
                
            }
        }
        return null;
    }

    @Override
    public void finishReadingSalesInfo(AtolSalesBatch salesBatch) {
        logger.info("Atol: Finish Reading started");
        for (String readFile : salesBatch.readFiles) {
            File f = new File(readFile);
            if (f.delete()) {
                logger.info("Atol: file " + readFile + " has been deleted");
            } else {
                throw new RuntimeException("The file " + f.getAbsolutePath() + " can not be deleted");
            }
        }
    }

    @Override
    public Set<String> requestSucceededSoftCheckInfo(DBSettings dbSettings) throws ClassNotFoundException, SQLException {
        
        logger.info("Atol: requesting succeeded SoftCheckInfo");

        //requesting Succeeded Soft Check Info

        return null;
    }

    @Override
    public CashDocumentBatch readCashDocumentInfo(List<CashRegisterInfo> cashRegisterInfoList, Set<String> cashDocumentSet, DBSettings dbSettings) throws ClassNotFoundException {
        
        //читаем инкассации. Теоретически, они будут вместе с чеками
        
        List<CashDocument> result = new ArrayList<CashDocument>();
        
        if(result.size()==0)
            logger.info("Atol: no CashDocuments found");
        else
            logger.info(String.format("Atol: found %s CashDocument(s)", result.size()));
        return new CashDocumentBatch(result, null);
    }

    @Override
    public void finishReadingCashDocumentInfo(CashDocumentBatch cashDocumentBatch) {   
        
        // удаляем файл инкассации
    }

    @Override
    public SalesBatch readSalesInfo(List<CashRegisterInfo> cashRegisterInfoList, DBSettings dbSettings) throws IOException, ParseException, ClassNotFoundException {
        
        Set<String> directorySet = new HashSet<String>();
        Map<String, Integer> directoryGroupCashRegisterMap = new HashMap<String, Integer>();
        Map<String, Date> directoryStartDateMap = new HashMap<String, Date>();
        for (CashRegisterInfo c : cashRegisterInfoList) {
            if (c.directory != null)
                directorySet.add(c.directory);
            if(c.directory != null && c.number != null && c.numberGroup != null)
                directoryGroupCashRegisterMap.put(c.directory + "_" + c.number, c.numberGroup);
            if(c.directory != null && c.number != null && c.startDate != null)
                directoryStartDateMap.put(c.directory + "_" + c.number, c.startDate);
        }

        List<SalesInfo> salesInfoList = new ArrayList<SalesInfo>();
        List<String> filePathList = new ArrayList<String>();
        for (String directory : directorySet) {

            //читаем чеки            
            
        }
        return (salesInfoList.isEmpty() && filePathList.isEmpty()) ? null : 
                new AtolSalesBatch(salesInfoList, filePathList);
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

    protected String format(Object input, String postfix) {
        return format(input, null, postfix);
    }
    
    protected String format(Object input, Integer length, String postfix) {
        String result = "";
        if(input != null) {
            if (input instanceof BigDecimal)
                result = String.valueOf(input);
            else if(input instanceof Boolean)
                result = ((Boolean)input) ? "1": "0";
            else {
                String str = ((String) input).trim();
                result = length == null || length >= str.length() ? str : str.substring(0, length);
            }
        }
        return result + (postfix == null ? "" : postfix);
    }
}
