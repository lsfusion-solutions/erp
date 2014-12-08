package lsfusion.erp.integration.universal;

import com.google.common.base.Throwables;
import lsfusion.server.classes.StringClass;
import lsfusion.server.classes.ValueClass;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.logics.DataObject;
import lsfusion.server.logics.linear.LCP;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.logics.scripted.ScriptingActionProperty;
import lsfusion.server.logics.scripted.ScriptingErrorLog;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;

import java.sql.SQLException;

public class MigrateImportDocumentPropertyActionProperty extends ScriptingActionProperty {

    public MigrateImportDocumentPropertyActionProperty(ScriptingLogicsModule LM) throws ScriptingErrorLog.SemanticErrorException {
        super(LM, "Migrate", LM.findClass("ImportTypeDetail"));
    }

    protected void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {

        DataObject object = context.getSingleDataKeyValue();
        try {
            String propertyImportTypeDetail = (String) findProperty("propertyImportTypeDetail").read(context, object);
            String moduleName = ImportDocumentActionProperty.getSplittedPart(propertyImportTypeDetail, "\\.", 0);
            String sidProperty = ImportDocumentActionProperty.getSplittedPart(propertyImportTypeDetail, "\\.", 1);

            ScriptingLogicsModule customModuleLM = context.getBL().getModule(moduleName);
            LCP<?> lp = customModuleLM.findProperty(sidProperty);
            findProperty("propImportTypeDetail").change(findProperty("propertyCanonicalName").read(context, new DataObject(lp.property.getCanonicalName(), StringClass.get(255))), context, object);
        } catch (ScriptingErrorLog.SemanticErrorException e) {
            throw Throwables.propagate(e);
        }
    }
}
