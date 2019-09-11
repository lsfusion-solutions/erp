package lsfusion.erp.integration.universal.productionorder;

import lsfusion.base.file.RawFileData;
import lsfusion.erp.integration.universal.ImportColumnDetail;
import lsfusion.erp.integration.universal.ImportDocumentAction;
import lsfusion.erp.integration.universal.ImportDocumentSettings;
import lsfusion.erp.integration.universal.UniversalImportException;
import lsfusion.interop.action.MessageClientAction;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.data.value.DataObject;
import lsfusion.server.data.value.NullValue;
import lsfusion.server.data.value.ObjectValue;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.logics.classes.data.file.CustomStaticFormatFileClass;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import org.xBaseJ.xBaseJException;

import java.io.IOException;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.Map;

public class ImportProductionOrdersFileAction extends ImportDocumentAction {
    
    public ImportProductionOrdersFileAction(ScriptingLogicsModule LM) {
        super(LM);
    }

    @Override
    public void executeInternal(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {
        super.executeInternal(context);
        try {

            ObjectValue importTypeObject = findProperty("importTypeOrders[]").readClasses(context);

            if (!(importTypeObject instanceof NullValue)) {

                ObjectValue operationObject = findProperty("autoImportOperation[ImportType]").readClasses(context, (DataObject) importTypeObject);

                Map<String, ImportColumnDetail> importColumns = readImportColumns(context, importTypeObject).get(0);
                ImportDocumentSettings settings = readImportDocumentSettings(context.getSession(), importTypeObject);
                String fileExtension = settings.getFileExtension();

                if (importColumns != null && fileExtension != null) {

                    CustomStaticFormatFileClass valueClass = CustomStaticFormatFileClass.get(fileExtension + " Files", fileExtension);
                    ObjectValue objectValue = context.requestUserData(valueClass, null);
                    if (objectValue != null) {
                        new ImportProductionOrderAction(LM).makeImport(context, null, importColumns, (RawFileData) objectValue.getValue(), settings, fileExtension, operationObject);

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