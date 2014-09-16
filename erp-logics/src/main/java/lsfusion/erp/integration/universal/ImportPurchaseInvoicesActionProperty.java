package lsfusion.erp.integration.universal;

import com.google.common.base.Throwables;
import jxl.read.biff.BiffException;
import lsfusion.server.classes.ConcreteCustomClass;
import lsfusion.server.classes.CustomStaticFormatFileClass;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.logics.DataObject;
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

public class ImportPurchaseInvoicesActionProperty extends ImportDocumentActionProperty {

    public ImportPurchaseInvoicesActionProperty(ScriptingLogicsModule LM) throws ScriptingErrorLog.SemanticErrorException {
        super(LM);
    }

    @Override
    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {

        try {

            ObjectValue importTypeObject = findProperty("importTypeUserInvoices").readClasses(context);
            String staticNameImportType = (String) findProperty("staticNameImportTypeDetailImportType").read(context, importTypeObject);

            ImportDocumentSettings importDocumentSettings = readImportDocumentSettings(context.getSession(), importTypeObject);
            String fileExtension = importDocumentSettings.getFileExtension();
            
            if (fileExtension != null) {

                CustomStaticFormatFileClass valueClass = CustomStaticFormatFileClass.get(false, false, fileExtension + " Files", fileExtension);
                ObjectValue objectValue = context.requestUserData(valueClass, null);
                if (objectValue != null) {
                    List<byte[]> listFiles = valueClass.getFiles(objectValue.getValue());
                    if (listFiles != null) {
                        for (byte[] file : listFiles) {
                            DataSession currentSession = context.createSession();
                            DataObject invoiceObject = currentSession.addObject((ConcreteCustomClass) findClass("Purchase.UserInvoice"));

                            new ImportPurchaseInvoiceActionProperty(LM).makeImport(context, currentSession, invoiceObject,
                                    (DataObject) importTypeObject, file, fileExtension, importDocumentSettings, staticNameImportType, false);

                            currentSession.apply(context);
                        }
                    }
                }
            }
        } catch (ScriptingErrorLog.SemanticErrorException e) {
            throw Throwables.propagate(e);
        } catch (BiffException e) {
            throw Throwables.propagate(e);
        } catch (UniversalImportException e) {
            throw Throwables.propagate(e);
        } catch (xBaseJException e) {
            throw Throwables.propagate(e);
        } catch (ParseException e) {
            throw Throwables.propagate(e);
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }
}