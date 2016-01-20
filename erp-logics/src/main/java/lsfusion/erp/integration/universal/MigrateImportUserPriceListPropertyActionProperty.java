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


public class MigrateImportUserPriceListPropertyActionProperty extends ScriptingActionProperty {

    public MigrateImportUserPriceListPropertyActionProperty(ScriptingLogicsModule LM, ValueClass... classes) throws ScriptingErrorLog.SemanticErrorException {
        super(LM, "Migrate", classes);
    }

    protected void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {

        DataObject object = context.getSingleDataKeyValue();
        try {
            String propertyImportTypeDetail = (String) findProperty("propertyImport[ImportUserPriceListTypeDetail]").read(context, object);
            String moduleName = ImportDocumentActionProperty.getSplittedPart(propertyImportTypeDetail, "\\.", 0);
            String sidProperty = ImportDocumentActionProperty.getSplittedPart(propertyImportTypeDetail, "\\.", 1);

            ScriptingLogicsModule customModuleLM = context.getBL().getModule(moduleName);
            LCP<?> lp = customModuleLM.findProperty(sidProperty);
            findProperty("propImport[ImportUserPriceListTypeDetail]").change(findProperty("propertyCanonicalName[VARSTRING[512]]").read(context, new DataObject(lp.property.getCanonicalName(), StringClass.get(255))), context, object);
        } catch (ScriptingErrorLog.SemanticErrorException e) {
            throw Throwables.propagate(e);
        }
    }
}
