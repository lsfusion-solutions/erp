package equ.srv.terminal;

import com.google.common.base.Throwables;
import lsfusion.server.classes.ConcreteCustomClass;
import lsfusion.server.integration.*;
import lsfusion.server.logics.DataObject;
import lsfusion.server.logics.LogicsInstance;
import lsfusion.server.logics.NullValue;
import lsfusion.server.logics.ObjectValue;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;
import lsfusion.server.session.DataSession;

import java.math.BigDecimal;
import java.rmi.RemoteException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class DefaultTerminalHandler implements TerminalHandlerInterface {

    private LogicsInstance logicsInstance;

    public void setLogicsInstance(LogicsInstance logicsInstance) {
        this.logicsInstance = logicsInstance;
    }

    public LogicsInstance getLogicsInstance() {
        return logicsInstance;
    }

    public DefaultTerminalHandler() {
        super();
    }

    @Override
    public List<String> readItem(DataSession session, DataObject user, String barcode) throws RemoteException, SQLException {
        try {
            ScriptingLogicsModule terminalHandlerLM = getLogicsInstance().getBusinessLogics().getModule("TerminalHandler");
            if(terminalHandlerLM != null) {
                String nameSkuBarcode = (String) terminalHandlerLM.findProperty("nameSkuBarcode").read(session, terminalHandlerLM.findProperty("barcodeId").readClasses(session, new DataObject(barcode)));
                if(nameSkuBarcode == null)
                    return null;
                ObjectValue skuObject = terminalHandlerLM.findProperty("skuBarcodeId").readClasses(session, new DataObject(barcode));
                ObjectValue stockObject = user == null ? NullValue.instance : terminalHandlerLM.findProperty("stockEmployee").readClasses(session, user);
                BigDecimal price = null;
                if(skuObject instanceof DataObject && stockObject instanceof DataObject) {
                    price = (BigDecimal) terminalHandlerLM.findProperty("currentRetailPricingPriceSkuStock").read(session, skuObject, stockObject);
                }
                return Arrays.asList(barcode, nameSkuBarcode, price == null ? "0" : String.valueOf(price.longValue()));
            } else return null;

        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    @Override
    public String readItemHtml(DataSession session, String barcode, String idStock) throws RemoteException, SQLException {
        try {
            ScriptingLogicsModule terminalHandlerLM = getLogicsInstance().getBusinessLogics().getModule("TerminalHandler");
            if(terminalHandlerLM != null) {
                String nameSkuBarcode = (String) terminalHandlerLM.findProperty("nameSkuBarcode").read(session, terminalHandlerLM.findProperty("barcodeId").readClasses(session, new DataObject(barcode)));
                if(nameSkuBarcode == null)
                    return null;

                ObjectValue skuObject = terminalHandlerLM.findProperty("skuBarcodeId").readClasses(session, new DataObject(barcode));
                ObjectValue stockObject = terminalHandlerLM.findProperty("stockId").readClasses(session, new DataObject(idStock));
                BigDecimal price = null;
                BigDecimal oldPrice = null;
                if(skuObject instanceof DataObject && stockObject instanceof DataObject) {
                    price = (BigDecimal) terminalHandlerLM.findProperty("transactionPriceSkuStock").read(session, skuObject, stockObject);
                    oldPrice = (BigDecimal) terminalHandlerLM.findProperty("transactionPriceSkuStock").read(session, skuObject, stockObject);
                }
                boolean action = price != null && oldPrice != null && price.compareTo(oldPrice) == 0;

                /*boolean action = barcode.equals("1");
                String nameSkuBarcode = barcode.equals("1") ? "Товар 1 со скидкой" : "Товар 2 без скидки";
                BigDecimal price = barcode.equals("1") ? BigDecimal.valueOf(5000) : BigDecimal.valueOf(10000);
                BigDecimal oldPrice = BigDecimal.valueOf(12000);*/

                return action ?
                        String.format("<html><body bgcolor=\"#FFFF00\">Наименование: <b>%s</b><br/><b><font color=\"#FF0000\">Акция</font></b> Цена: <b>%s</b>, Скидка: <b>%s</b></body></html>",
                        nameSkuBarcode, String.valueOf(price.longValue()), String.valueOf(oldPrice.longValue() - price.longValue()))
                        : String.format("<html><body>Наименование: <b>%s</b><br/>Цена: <b>%s</b></body></html>",
                        nameSkuBarcode, price == null ? "0" : String.valueOf(price.longValue()));
            } else return null;

        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    @Override
    public String importTerminalDocument(DataSession session, DataObject userObject, String idTerminalDocument, List<List<Object>> terminalDocumentDetailList, boolean emptyDocument) throws RemoteException, SQLException {
        try {

            ScriptingLogicsModule terminalHandlerLM = getLogicsInstance().getBusinessLogics().getModule("TerminalHandler");
            if(terminalHandlerLM != null) {

                List<ImportProperty<?>> props = new ArrayList<>();
                List<ImportField> fields = new ArrayList<>();
                List<ImportKey<?>> keys = new ArrayList<>();

                ImportField idTerminalDocumentField = new ImportField(terminalHandlerLM.findProperty("idTerminalDocument"));
                ImportKey<?> terminalDocumentKey = new ImportKey((ConcreteCustomClass) terminalHandlerLM.findClass("TerminalDocument"),
                        terminalHandlerLM.findProperty("terminalDocumentId").getMapping(idTerminalDocumentField));
                keys.add(terminalDocumentKey);
                props.add(new ImportProperty(idTerminalDocumentField, terminalHandlerLM.findProperty("idTerminalDocument").getMapping(terminalDocumentKey)));
                fields.add(idTerminalDocumentField);

                ImportField numberTerminalDocumentField = new ImportField(terminalHandlerLM.findProperty("titleTerminalDocument"));
                props.add(new ImportProperty(numberTerminalDocumentField, terminalHandlerLM.findProperty("titleTerminalDocument").getMapping(terminalDocumentKey)));
                fields.add(numberTerminalDocumentField);

                ImportField idTerminalDocumentTypeField = new ImportField(terminalHandlerLM.findProperty("idTerminalDocumentType"));
                ImportKey<?> terminalDocumentTypeKey = new ImportKey((ConcreteCustomClass) terminalHandlerLM.findClass("TerminalDocumentType"),
                        terminalHandlerLM.findProperty("terminalDocumentTypeId").getMapping(idTerminalDocumentTypeField));
                terminalDocumentTypeKey.skipKey = true;
                keys.add(terminalDocumentTypeKey);
                props.add(new ImportProperty(idTerminalDocumentTypeField, terminalHandlerLM.findProperty("terminalDocumentTypeTerminalDocument").getMapping(terminalDocumentKey),
                        terminalHandlerLM.object(terminalHandlerLM.findClass("TerminalDocumentType")).getMapping(terminalDocumentTypeKey)));
                fields.add(idTerminalDocumentTypeField);

                if (!emptyDocument) {

                    ImportField idTerminalDocumentDetailField = new ImportField(terminalHandlerLM.findProperty("idTerminalDocumentDetail"));
                    ImportKey<?> terminalDocumentDetailKey = new ImportKey((ConcreteCustomClass) terminalHandlerLM.findClass("TerminalDocumentDetail"),
                            terminalHandlerLM.findProperty("terminalDocumentDetailIdTerminalDocumentId").getMapping(idTerminalDocumentField, idTerminalDocumentDetailField));
                    keys.add(terminalDocumentDetailKey);
                    props.add(new ImportProperty(idTerminalDocumentDetailField, terminalHandlerLM.findProperty("idTerminalDocumentDetail").getMapping(terminalDocumentDetailKey)));
                    props.add(new ImportProperty(idTerminalDocumentField, terminalHandlerLM.findProperty("terminalDocumentTerminalDocumentDetail").getMapping(terminalDocumentDetailKey),
                            terminalHandlerLM.object(terminalHandlerLM.findClass("TerminalDocument")).getMapping(terminalDocumentKey)));
                    fields.add(idTerminalDocumentDetailField);

                    ImportField numberTerminalDocumentDetailField = new ImportField(terminalHandlerLM.findProperty("numberTerminalDocumentDetail"));
                    props.add(new ImportProperty(numberTerminalDocumentDetailField, terminalHandlerLM.findProperty("numberTerminalDocumentDetail").getMapping(terminalDocumentDetailKey)));
                    fields.add(numberTerminalDocumentDetailField);

                    ImportField barcodeTerminalDocumentDetailField = new ImportField(terminalHandlerLM.findProperty("barcodeTerminalDocumentDetail"));
                    props.add(new ImportProperty(barcodeTerminalDocumentDetailField, terminalHandlerLM.findProperty("barcodeTerminalDocumentDetail").getMapping(terminalDocumentDetailKey)));
                    fields.add(barcodeTerminalDocumentDetailField);

                    ImportField quantityTerminalDocumentDetailField = new ImportField(terminalHandlerLM.findProperty("quantityTerminalDocumentDetail"));
                    props.add(new ImportProperty(quantityTerminalDocumentDetailField, terminalHandlerLM.findProperty("quantityTerminalDocumentDetail").getMapping(terminalDocumentDetailKey)));
                    fields.add(quantityTerminalDocumentDetailField);

                    ImportField priceTerminalDocumentDetailField = new ImportField(terminalHandlerLM.findProperty("priceTerminalDocumentDetail"));
                    props.add(new ImportProperty(priceTerminalDocumentDetailField, terminalHandlerLM.findProperty("priceTerminalDocumentDetail").getMapping(terminalDocumentDetailKey)));
                    fields.add(priceTerminalDocumentDetailField);
                }

                ImportTable table = new ImportTable(fields, terminalDocumentDetailList);

                session.pushVolatileStats("TH_TD");
                IntegrationService service = new IntegrationService(session, table, keys, props);
                service.synchronize(true, false);

                ObjectValue terminalDocumentObject = terminalHandlerLM.findProperty("terminalDocumentId").readClasses(session, session.getModifier(), session.getQueryEnv(), new DataObject(idTerminalDocument));
                terminalHandlerLM.findProperty("createdUserTerminalDocument").change(userObject.object, session, (DataObject) terminalDocumentObject);
                terminalHandlerLM.findAction("processTerminalDocument").execute(session, terminalDocumentObject);

                String result = session.applyMessage(getLogicsInstance().getBusinessLogics());
                session.popVolatileStats();
                session.close();
                return result;

            } else return "-1";

        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    @Override
    public boolean isActiveTerminal(DataSession session, String idTerminal) throws RemoteException, SQLException {
        try {
            ScriptingLogicsModule terminalHandlerLM = getLogicsInstance().getBusinessLogics().getModule("TerminalHandler");
            if (terminalHandlerLM != null) {
                ObjectValue terminalObject = terminalHandlerLM.findProperty("terminalId").readClasses(session, new DataObject(idTerminal));
                return terminalObject instanceof DataObject && terminalHandlerLM.findProperty("notActiveTerminal").read(session, terminalObject) == null;
            }
            return false;
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    @Override
    public DataObject getUserObject(DataSession session, String login, String password) throws RemoteException, SQLException {
        try {

            ScriptingLogicsModule terminalHandlerLM = getLogicsInstance().getBusinessLogics().getModule("TerminalHandler");
            if(terminalHandlerLM != null) {
                terminalHandlerLM.findAction("calculateBase64Hash").execute(session, new DataObject("SHA-256"), new DataObject(password));
                String calculatedHash = (String) terminalHandlerLM.findProperty("calculatedHash").read(session);
                ObjectValue customUser = terminalHandlerLM.findProperty("customUserUpcaseLogin").readClasses(session, new DataObject(login.toUpperCase()));
                String sha256PasswordCustomUser = (String) terminalHandlerLM.findProperty("sha256PasswordCustomUser").read(session, customUser);
                boolean check = customUser instanceof DataObject && sha256PasswordCustomUser != null && calculatedHash != null && sha256PasswordCustomUser.equals(calculatedHash);
                return check ? (DataObject) customUser : null;
            } else return null;

        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }


}