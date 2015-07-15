package lsfusion.erp.region.by.integration.topby;

import java.sql.Date;
import java.sql.Time;
import java.util.List;

public class InputDocument {
    List<InputDocumentDetail> detailList;
    String uniqueNumber;
    String seriesNumber;
    Date date;
    Time time;
    String dateTime;
    String glnSupplier;
    String nameSupplier;
    String addressSupplier;
    String UNPSupplier;
    String glnCustomer;
    String nameCustomer;
    String addressCustomer;
    String UNPCustomer;
    String glnSupplierStock;
    String addressSupplierStock;
    String contactSupplierStock;
    String glnCustomerStock;
    String addressCustomerStock;
    String contactCustomerStock;

    public InputDocument(List<InputDocumentDetail> detailList, String uniqueNumber, String seriesNumber, Date date, Time time, String dateTime,
                         String glnSupplier, String nameSupplier, String addressSupplier, String UNPSupplier, String glnCustomer,
                         String nameCustomer, String addressCustomer, String UNPCustomer, String glnSupplierStock, String addressSupplierStock,
                         String contactSupplierStock, String glnCustomerStock, String addressCustomerStock, String contactCustomerStock) {
        this.detailList = detailList;
        this.uniqueNumber = uniqueNumber;
        this.seriesNumber = seriesNumber;
        this.date = date;
        this.time = time;
        this.dateTime = dateTime;
        this.glnSupplier = glnSupplier;
        this.nameSupplier = nameSupplier;
        this.addressSupplier = addressSupplier;
        this.UNPSupplier = UNPSupplier;
        this.glnCustomer = glnCustomer;
        this.nameCustomer = nameCustomer;
        this.addressCustomer = addressCustomer;
        this.UNPCustomer = UNPCustomer;
        this.glnSupplierStock = glnSupplierStock;
        this.addressSupplierStock = addressSupplierStock;
        this.contactSupplierStock = contactSupplierStock;
        this.glnCustomerStock = glnCustomerStock;
        this.addressCustomerStock = addressCustomerStock;
        this.contactCustomerStock = contactCustomerStock;
    }
}
