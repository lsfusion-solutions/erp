package lsfusion.erp.integration.universal;

import jxl.read.biff.BiffException;
import lsfusion.interop.action.MessageClientAction;
import lsfusion.server.classes.CustomStaticFormatFileClass;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.logics.DataObject;
import lsfusion.server.logics.NullValue;
import lsfusion.server.logics.ObjectValue;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.logics.scripted.ScriptingErrorLog;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;
import lsfusion.server.session.DataSession;
import org.xBaseJ.xBaseJException;

import java.io.IOException;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.List;
import java.util.Map;

public class ImportPurchaseInvoicesActionProperty extends ImportDocumentActionProperty {

    public ImportPurchaseInvoicesActionProperty(ScriptingLogicsModule LM) throws ScriptingErrorLog.SemanticErrorException {
        super(LM);
    }

    @Override
    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {

        try {

            DataSession session = context.getSession();

            ImportPurchaseInvoiceActionProperty imp = new ImportPurchaseInvoiceActionProperty(LM);
            
            imp.initModules(context);

            ObjectValue importTypeObject = getLCP("importTypeUserInvoices").readClasses(session);

            if (!(importTypeObject instanceof NullValue)) {

                String fileExtension = trim((String) getLCP("captionFileExtensionImportType").read(session, importTypeObject));

                String primaryKeyType = parseKeyType((String) getLCP("namePrimaryKeyTypeImportType").read(session, importTypeObject));
                String secondaryKeyType = parseKeyType((String) getLCP("nameSecondaryKeyTypeImportType").read(session, importTypeObject));

                String csvSeparator = trim((String) getLCP("separatorImportType").read(session, importTypeObject));
                csvSeparator = csvSeparator == null ? ";" : csvSeparator.trim();
                Integer startRow = (Integer) getLCP("startRowImportType").read(session, importTypeObject);
                startRow = startRow == null || startRow.equals(0) ? 1 : startRow;
                Boolean isPosted = (Boolean) getLCP("isPostedImportType").read(session, importTypeObject);

                ObjectValue operationObject = getLCP("autoImportOperationImportType").readClasses(session, (DataObject) importTypeObject);
                ObjectValue supplierObject = getLCP("autoImportSupplierImportType").readClasses(session, (DataObject) importTypeObject);
                ObjectValue supplierStockObject = getLCP("autoImportSupplierStockImportType").readClasses(session, (DataObject) importTypeObject);
                ObjectValue customerObject = getLCP("autoImportCustomerImportType").readClasses(session, (DataObject) importTypeObject);
                ObjectValue customerStockObject = getLCP("autoImportCustomerStockImportType").readClasses(session, (DataObject) importTypeObject);

                Map<String, ImportColumnDetail> importColumns = readImportColumns(session, LM, importTypeObject);

                if (importColumns != null && fileExtension != null) {

                    CustomStaticFormatFileClass valueClass = CustomStaticFormatFileClass.get(false, false, fileExtension + " Files", fileExtension);
                    ObjectValue objectValue = context.requestUserData(valueClass, null);
                    if (objectValue != null) {
                        List<byte[]> fileList = valueClass.getFiles(objectValue.getValue());

                        for (byte[] file : fileList) {
                            
                            List<List<PurchaseInvoiceDetail>> userInvoiceDetailsList = imp.importUserInvoicesFromFile(session,
                                    null, importColumns, file, fileExtension, startRow,
                                    isPosted, csvSeparator, primaryKeyType, secondaryKeyType);

                            if (userInvoiceDetailsList != null && userInvoiceDetailsList.size() >= 1)
                                imp.importUserInvoices(userInvoiceDetailsList.get(0), session, importColumns, null,
                                        primaryKeyType, operationObject, supplierObject, supplierStockObject,
                                        customerObject, customerStockObject);

                            if (userInvoiceDetailsList != null && userInvoiceDetailsList.size() >= 2)
                                imp.importUserInvoices(userInvoiceDetailsList.get(1), session, importColumns, null,
                                        secondaryKeyType, operationObject, supplierObject, supplierStockObject,
                                        customerObject, customerStockObject);

                            session.apply(context);
                            session.close();

                            getLAP("formRefresh").execute(context);
                        }
                    }
                }
            }
        } catch (ScriptingErrorLog.SemanticErrorException e) {
            throw new RuntimeException(e);
        } catch (xBaseJException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (BiffException e) {
            throw new RuntimeException(e);
        } catch (ParseException e) {
            throw new RuntimeException(e);
        } catch (UniversalImportException e) {
            e.printStackTrace();
            context.requestUserInteraction(new MessageClientAction(e.getMessage(), e.getTitle()));
        }
    }
}

