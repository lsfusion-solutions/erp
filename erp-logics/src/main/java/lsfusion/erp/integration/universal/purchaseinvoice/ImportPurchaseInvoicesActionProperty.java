package lsfusion.erp.integration.universal.purchaseinvoice;

import com.google.common.base.Throwables;
import jxl.read.biff.BiffException;
import lsfusion.base.col.SetFact;
import lsfusion.erp.integration.universal.ImportDocumentActionProperty;
import lsfusion.erp.integration.universal.ImportDocumentSettings;
import lsfusion.erp.integration.universal.UniversalImportException;
import lsfusion.server.classes.ConcreteCustomClass;
import lsfusion.server.classes.CustomStaticFormatFileClass;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.logics.DataObject;
import lsfusion.server.logics.ObjectValue;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.logics.property.SessionDataProperty;
import lsfusion.server.logics.scripted.ScriptingErrorLog;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;
import lsfusion.server.session.DataSession;
import org.xBaseJ.xBaseJException;

import java.io.IOException;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.List;

public class ImportPurchaseInvoicesActionProperty extends ImportDocumentActionProperty {

    public ImportPurchaseInvoicesActionProperty(ScriptingLogicsModule LM) {
        super(LM);
    }

    @Override
    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {
        super.executeCustom(context);
        try {

            ObjectValue importTypeObject = findProperty("importTypeUserInvoices[]").readClasses(context);
            String staticNameImportType = (String) findProperty("staticNameImportTypeDetail[ImportType]").read(context, importTypeObject);
            String staticCaptionImportType = (String) findProperty("staticCaptionImportTypeDetail[ImportType]").read(context, importTypeObject);
            boolean completeIdItemAsEAN = findProperty("completeIdItemAsEAN[ImportType]").read(context, importTypeObject) != null;

            ImportDocumentSettings settings = readImportDocumentSettings(context.getSession(), importTypeObject);
            String fileExtension = settings.getFileExtension();
            boolean multipleDocuments = settings.isMultipleDocuments();
            
            if (fileExtension != null) {

                CustomStaticFormatFileClass valueClass = CustomStaticFormatFileClass.get(false, false, fileExtension + " Files", fileExtension);
                ObjectValue objectValue = context.requestUserData(valueClass, null);
                if (objectValue != null) {
                    List<byte[]> listFiles = valueClass.getFiles(objectValue.getValue());
                    if (listFiles != null) {
                        for (byte[] file : listFiles) {
                            try(ExecutionContext.NewSession<ClassPropertyInterface> newContext = context.newSession()) {
                                DataObject invoiceObject = multipleDocuments ? null : newContext.addObject((ConcreteCustomClass) findClass("Purchase.UserInvoice"));

                                new ImportPurchaseInvoiceActionProperty(LM).makeImport(context, invoiceObject,
                                        (DataObject) importTypeObject, file, fileExtension, settings,
                                        staticNameImportType, staticCaptionImportType, completeIdItemAsEAN, false, false);

                                if(invoiceObject != null) {
                                    findProperty("currentInvoice[]").change(invoiceObject, newContext);
                                }
                                boolean cancelSession = false;
                                String script = (String) findProperty("script[ImportType]").read(newContext, importTypeObject);
                                if(script != null && !script.isEmpty()) {
                                    findAction("executeScript[ImportType]").execute(newContext, importTypeObject);
                                    cancelSession = findProperty("cancelSession[]").read(newContext) != null;
                                }

                                findAction("executeLocalEvents[TEXT]").execute(newContext, new DataObject("Purchase.UserInvoice"));

                                if(cancelSession) {
                                    newContext.cancel(SetFact.<SessionDataProperty>EMPTY());
                                } else {
                                    newContext.apply();
                                }
                            }
                        }
                    }
                }
            }
        } catch (ScriptingErrorLog.SemanticErrorException | BiffException | UniversalImportException | ParseException | xBaseJException | IOException e) {
            throw Throwables.propagate(e);
        }
    }
}