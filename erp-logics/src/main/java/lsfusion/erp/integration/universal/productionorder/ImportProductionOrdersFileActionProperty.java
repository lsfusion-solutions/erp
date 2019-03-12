package lsfusion.erp.integration.universal.productionorder;

import lsfusion.base.file.RawFileData;
import lsfusion.erp.integration.universal.ImportColumnDetail;
import lsfusion.erp.integration.universal.ImportDocumentActionProperty;
import lsfusion.erp.integration.universal.ImportDocumentSettings;
import lsfusion.erp.integration.universal.UniversalImportException;
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
import java.util.Map;

public class ImportProductionOrdersFileActionProperty extends ImportDocumentActionProperty {
    
    public ImportProductionOrdersFileActionProperty(ScriptingLogicsModule LM) {
        super(LM);
    }

    @Override
    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {
        super.executeCustom(context);
        try {

            DataSession session = context.getSession();

            ObjectValue importTypeObject = findProperty("importTypeOrders[]").readClasses(session);

            if (!(importTypeObject instanceof NullValue)) {

                ObjectValue operationObject = findProperty("autoImportOperation[ImportType]").readClasses(session, (DataObject) importTypeObject);

                Map<String, ImportColumnDetail> importColumns = readImportColumns(context, importTypeObject).get(0);
                ImportDocumentSettings settings = readImportDocumentSettings(session, importTypeObject);
                String fileExtension = settings.getFileExtension();

                if (importColumns != null && fileExtension != null) {

                    CustomStaticFormatFileClass valueClass = CustomStaticFormatFileClass.get(fileExtension + " Files", fileExtension);
                    ObjectValue objectValue = context.requestUserData(valueClass, null);
                    if (objectValue != null) {
                        new ImportProductionOrderActionProperty(LM).makeImport(context, null, importColumns, (RawFileData) objectValue.getValue(), settings, fileExtension, operationObject);

                        context.apply();
                        
                        findAction("formRefresh[]").execute(context);
                    }
                }
            }
        } catch (ScriptingErrorLog.SemanticErrorException | ParseException | IOException | xBaseJException e) {
            throw new RuntimeException(e);
        } catch (UniversalImportException e) {
            e.printStackTrace();
            context.requestUserInteraction(new MessageClientAction(e.getMessage(), e.getTitle()));
        }
    }
}