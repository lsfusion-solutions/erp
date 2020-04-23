package equ.clt.handler.kristal10;

import equ.api.ItemGroup;
import equ.api.ItemInfo;
import equ.api.cashregister.CashRegisterInfo;
import equ.api.cashregister.CashRegisterItemInfo;
import equ.api.cashregister.ExtraCheckZReportBatch;
import equ.api.cashregister.TransactionCashRegisterInfo;
import equ.clt.handler.DefaultCashRegisterHandler;
import org.jdom.Attribute;
import org.jdom.Element;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public abstract class Kristal10DefaultHandler extends DefaultCashRegisterHandler<Kristal10SalesBatch> {

    protected static Map<String, Map<String, String>> deleteBarcodeDirectoryMap = new HashMap<>();
    protected FileSystemXmlApplicationContext springContext;
    String encoding = "utf-8";

    public Kristal10DefaultHandler(FileSystemXmlApplicationContext springContext) {
        this.springContext = springContext;
    }

    protected static String removeZeroes(String value) {
        if(value != null) {
            while(value.startsWith("0")) {
                value = value.substring(1);
            }
        }
        return value;
    }

    protected static void addProductType(Element good, ItemInfo item, List<String> tobaccoGroups) {
        String productType;
        if(item.idItemGroup != null && tobaccoGroups != null && tobaccoGroups.contains(item.idItemGroup))
            productType = "ProductCiggyEntity";
        else if (item.passScalesItem)
            productType = item.splitItem ? "ProductWeightEntity" : "ProductPieceWeightEntity";
        else
            productType = (item.flags == null || ((item.flags & 256) == 0)) ? "ProductPieceEntity" : "ProductSpiritsEntity";
        addStringElement(good, "product-type", productType);
    }

    protected static Integer getDepartNumber(TransactionCashRegisterInfo transaction, CashRegisterItemInfo item, boolean useSectionAsDepartNumber) {
        Integer departNumber;
        if(useSectionAsDepartNumber && item.section != null) {
            departNumber = Integer.parseInt(item.section.split(",")[0].split("\\|")[0]);
        } else {
            departNumber = transaction.departmentNumberGroupCashRegister;
        }
        return departNumber;
    }

    protected static void addIntegerElement(Element parent, String id, Integer value) {
        if (value != null)
            parent.addContent(new Element(id).setText(String.valueOf(value)));
    }

    protected static void addStringElement(Element parent, String id, String value) {
        if (value != null)
            parent.addContent(new Element(id).setText(value));
    }

    protected static void setAttribute(Element element, String id, Object value) {
        if (value != null)
            element.setAttribute(new Attribute(id, String.valueOf(value)));
    }

    protected static void addHierarchyItemGroup(Element parent, List<ItemGroup> hierarchyItemGroup) {
        if (!hierarchyItemGroup.isEmpty()) {
            Element element = new Element("parent-group");
            setAttribute(element, "id", hierarchyItemGroup.get(0).idItemGroup);
            addStringElement(element, "name", hierarchyItemGroup.get(0).nameItemGroup);
            parent.addContent(element);
            addHierarchyItemGroup(element, hierarchyItemGroup.subList(1, hierarchyItemGroup.size()));
        }
    }

    protected static void addPriceEntryElement(Element parent, Object price, boolean deleted, String beginDate, String endDate, String number, Object departmentNumber) {
        Element priceEntry = new Element("price-entry");
        setAttribute(priceEntry, "price", price);
        setAttribute(priceEntry, "deleted", deleted);
        addStringElement(priceEntry, "begin-date", beginDate);
        addStringElement(priceEntry, "end-date", endDate);
        addStringElement(priceEntry, "number", number);

        if(departmentNumber != null) {
            //parent: priceEntry
            Element department = new Element("department");
            setAttribute(department, "number", departmentNumber);
            priceEntry.addContent(department);
        }

        parent.addContent(priceEntry);
    }

    protected static String formatDate(LocalDate date, String format) {
        return date == null ? null : date.format(DateTimeFormatter.ofPattern(format));
    }

    protected static String formatDateTime(LocalDateTime dateTime, String format, String defaultValue) {
        return dateTime == null ? defaultValue : dateTime.format(DateTimeFormatter.ofPattern(format));
    }

    protected static String currentDate() {
        return LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + "T00:00:00";
    }

    protected static String transformBarcode(String idBarcode, String weightCode, boolean passScalesItem, boolean skipWeightPrefix) {
        //временное решение для весовых товаров
        return passScalesItem && idBarcode.length() <= 6 && weightCode != null && !skipWeightPrefix ? (weightCode + idBarcode) : idBarcode;
    }

    @Override
    public ExtraCheckZReportBatch compareExtraCheckZReport(Map<String, List<Object>> handlerZReportSumMap, Map<String, BigDecimal> baseZReportSumMap) {

        StringBuilder message = new StringBuilder();
        List<String> idZReportList = new ArrayList<>();

        for (Map.Entry<String, List<Object>> kristalEntry : handlerZReportSumMap.entrySet()) {

            String idZReportHandler = kristalEntry.getKey();
            List<Object> valuesHandler = kristalEntry.getValue();
            BigDecimal sumHandler = (BigDecimal) valuesHandler.get(0);
            Integer numberCashRegister = (Integer) valuesHandler.get(1);
            String numberZReport = (String) valuesHandler.get(2);
            String idZReport = (String) valuesHandler.get(3);

            BigDecimal sumBase = baseZReportSumMap.get(idZReportHandler);

            if (sumHandler == null || sumBase == null || sumHandler.doubleValue() != sumBase.doubleValue())
                message.append(String.format("CashRegister %s. \nZReport %s checksum failed: %s(fusion) != %s(kristal);\n",
                        numberCashRegister, numberZReport, sumBase, sumHandler));
            else
                idZReportList.add(idZReport);
        }
        return idZReportList.isEmpty() && (message.length() == 0) ? null : new ExtraCheckZReportBatch(idZReportList, message.toString());
    }

    protected List<String> getTobaccoGroups (String tobaccoGroup) {
        List<String> tobaccoGroups = new ArrayList<>();
        if (tobaccoGroup != null)
            Collections.addAll(tobaccoGroups, tobaccoGroup.split(","));
        return tobaccoGroups;
    }

    protected String transformUPCBarcode(String idBarcode, String transformUPCBarcode) {
        if(idBarcode != null && transformUPCBarcode != null) {
            if(transformUPCBarcode.equals("13to12") && idBarcode.length() == 13 && idBarcode.startsWith("0"))
                idBarcode = idBarcode.substring(1);
            else if(transformUPCBarcode.equals("12to13") && idBarcode.length() == 12)
                idBarcode += "0";

        }
        return idBarcode;
    }

    protected String readStringXMLValue(Object element, String field) {
        if (!(element instanceof Element))
            return null;
        String value = ((Element) element).getChildText(field);
        if (value == null || value.isEmpty()) {
            sendSalesLogger.error("Attribute " + field + " is empty");
            return null;
        }
        return value;
    }

    protected String readStringXMLAttribute(Object element, String field) {
        if (!(element instanceof Element))
            return null;
        String value = ((Element) element).getAttributeValue(field);
        if (value == null || value.isEmpty()) {
            sendSalesLogger.error("Attribute " + field + " is empty");
            return null;
        }
        return value;
    }

    protected BigDecimal readBigDecimalXMLValue(Object element, String field) {
        if (!(element instanceof Element))
            return null;
        String value = ((Element) element).getChildText(field);
        if (value == null || value.isEmpty()) {
            sendSalesLogger.error("Attribute " + field + " is empty");
            return null;
        }
        try {
            return new BigDecimal(value);
        } catch (Exception e) {
            sendSalesLogger.error("Kristal10 Error: ", e);
            return null;
        }
    }

    protected BigDecimal readBigDecimalXMLAttribute(Object element, String field) {
        if (!(element instanceof Element))
            return null;
        String value = ((Element) element).getAttributeValue(field);
        if (value == null || value.isEmpty()) {
            sendSalesLogger.error("Attribute " + field + " is empty");
            return null;
        }
        try {
            return new BigDecimal(value);
        } catch (Exception e) {
            sendSalesLogger.error("Kristal10 Error: ", e);
            return null;
        }
    }

    protected Integer readIntegerXMLValue(Object element, String field) {
        if (!(element instanceof Element))
            return null;
        String value = ((Element) element).getChildText(field);
        if (value == null || value.isEmpty()) {
            sendSalesLogger.error("Attribute " + field + " is empty");
            return null;
        }
        try {
            return Integer.parseInt(value);
        } catch (Exception e) {
            sendSalesLogger.error("Kristal10 Error: ", e);
            return null;
        }
    }

    protected Integer readIntegerXMLAttribute(Object element, String field) {
        if (!(element instanceof Element))
            return null;
        String value = ((Element) element).getAttributeValue(field);
        if (value == null || value.isEmpty()) {
            sendSalesLogger.error("Attribute " + field + " is empty");
            return null;
        }
        try {
            return Integer.parseInt(value);
        } catch (Exception e) {
            sendSalesLogger.error("Kristal10 Error: ", e);
            return null;
        }
    }

    protected double parseWeight(String value) {
        try {
            return (double) Integer.parseInt(value) / 1000;
        } catch (Exception e) {
            return 0.0;
        }
    }

    protected boolean notNullNorEmpty(String value) {
        return value != null && !value.isEmpty();
    }

    protected CashRegisterInfo getCashRegister(Map<String, List<CashRegisterInfo>> cashRegisterMap, String key) {
        //ищем кассу без disableSales. Если все с disableSales, берём первую
        CashRegisterInfo cashRegister = null;
        List<CashRegisterInfo> cashRegisterList = cashRegisterMap.get(key);
        if(cashRegisterList != null) {
            for(CashRegisterInfo c : cashRegisterList) {
                if(!c.disableSales) {
                    cashRegister = c;
                    break;
                }
            }
            if(cashRegister == null) {
                cashRegister = cashRegisterList.get(0);
            }
        }
        return  cashRegister;
    }

    public class DeleteBarcode {
        Integer nppGroupMachinery;
        String directory;
        Set<String> barcodes;

        public DeleteBarcode(Integer nppGroupMachinery, String directory) {
            this.nppGroupMachinery = nppGroupMachinery;
            this.directory = directory;
            this.barcodes = new HashSet<>();
        }
    }
}
