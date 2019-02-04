package equ.clt.handler.artix;

import java.io.Serializable;

public class ArtixSettings implements Serializable{

    private String globalExchangeDirectory;
    private boolean appendBarcode;
    private String giftCardRegexp;
    private boolean disableCopyToSuccess;
    private boolean readCashDocuments;
    private boolean exportClients;
    private boolean exportSoftCheckItem;
    private Integer maxFilesCount;
    private Integer maxFilesDirectoryCount;
    private boolean bonusesInDiscountPositions; //появляется оплата "бонусами", надо считать сумму и % скидки вручную (введено для Fancy)
    private boolean giftCardPriceInCertificatePositions; //берём цену сертификата из certificatePositions (введено для Ostrov)
    private String priorityDirectories; //dir1,dir2,dir3

    public ArtixSettings() {
    }

    public String getGlobalExchangeDirectory() {
        return globalExchangeDirectory;
    }

    public void setGlobalExchangeDirectory(String globalExchangeDirectory) {
        this.globalExchangeDirectory = globalExchangeDirectory;
    }

    public boolean isAppendBarcode() {
        return appendBarcode;
    }

    public void setAppendBarcode(boolean appendBarcode) {
        this.appendBarcode = appendBarcode;
    }

    public String getGiftCardRegexp() {
        return giftCardRegexp;
    }

    public void setGiftCardRegexp(String giftCardRegexp) {
        this.giftCardRegexp = giftCardRegexp;
    }

    public boolean isDisableCopyToSuccess() {
        return disableCopyToSuccess;
    }

    public void setDisableCopyToSuccess(boolean disableCopyToSuccess) {
        this.disableCopyToSuccess = disableCopyToSuccess;
    }

    public boolean isReadCashDocuments() {
        return readCashDocuments;
    }

    public void setReadCashDocuments(boolean readCashDocuments) {
        this.readCashDocuments = readCashDocuments;
    }

    public boolean isExportClients() {
        return exportClients;
    }

    public void setExportClients(boolean exportClients) {
        this.exportClients = exportClients;
    }

    public boolean isExportSoftCheckItem() {
        return exportSoftCheckItem;
    }

    public void setExportSoftCheckItem(boolean exportSoftCheckItem) {
        this.exportSoftCheckItem = exportSoftCheckItem;
    }

    public Integer getMaxFilesCount() {
        return maxFilesCount;
    }

    public void setMaxFilesCount(Integer maxFilesCount) {
        this.maxFilesCount = maxFilesCount;
    }

    public Integer getMaxFilesDirectoryCount() {
        return maxFilesDirectoryCount;
    }

    public void setMaxFilesDirectoryCount(Integer maxFilesDirectoryCount) {
        this.maxFilesDirectoryCount = maxFilesDirectoryCount;
    }

    public boolean isBonusesInDiscountPositions() {
        return bonusesInDiscountPositions;
    }

    public void setBonusesInDiscountPositions(boolean bonusesInDiscountPositions) {
        this.bonusesInDiscountPositions = bonusesInDiscountPositions;
    }

    public boolean isGiftCardPriceInCertificatePositions() {
        return giftCardPriceInCertificatePositions;
    }

    public void setGiftCardPriceInCertificatePositions(boolean giftCardPriceInCertificatePositions) {
        this.giftCardPriceInCertificatePositions = giftCardPriceInCertificatePositions;
    }

    public String getPriorityDirectories() {
        return priorityDirectories;
    }

    public void setPriorityDirectories(String priorityDirectories) {
        this.priorityDirectories = priorityDirectories;
    }
}