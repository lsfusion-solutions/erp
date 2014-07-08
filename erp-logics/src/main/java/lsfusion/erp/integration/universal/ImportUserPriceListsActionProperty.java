package lsfusion.erp.integration.universal;

import lsfusion.base.IOUtils;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImOrderMap;
import lsfusion.base.col.interfaces.immutable.ImRevMap;
import lsfusion.erp.integration.DefaultImportActionProperty;
import lsfusion.interop.action.MessageClientAction;
import lsfusion.server.ServerLoggers;
import lsfusion.server.Settings;
import lsfusion.server.classes.ConcreteCustomClass;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.data.expr.KeyExpr;
import lsfusion.server.data.query.QueryBuilder;
import lsfusion.server.logics.DataObject;
import lsfusion.server.logics.ObjectValue;
import lsfusion.server.logics.linear.LCP;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.logics.property.PropertyInterface;
import lsfusion.server.logics.scripted.ScriptingErrorLog;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;

import java.io.File;
import java.sql.SQLException;

public class ImportUserPriceListsActionProperty extends DefaultImportActionProperty {

    public ImportUserPriceListsActionProperty(ScriptingLogicsModule LM) throws ScriptingErrorLog.SemanticErrorException {
        super(LM);
    }

    @Override
    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {

        try {

            LCP<PropertyInterface> isImportUserPriceListType = (LCP<PropertyInterface>) is(getClass("ImportUserPriceListType"));
            ImRevMap<PropertyInterface, KeyExpr> importUserPriceListTypeKeys = isImportUserPriceListType.getMapKeys();
            KeyExpr importUserPriceListTypeKey = importUserPriceListTypeKeys.singleValue();
            QueryBuilder<PropertyInterface, Object> importUserPriceListTypeQuery = new QueryBuilder<PropertyInterface, Object>(importUserPriceListTypeKeys);
            importUserPriceListTypeQuery.addProperty("autoImportDirectoryImportUserPriceListType", getLCP("autoImportDirectoryImportUserPriceListType").getExpr(context.getModifier(), importUserPriceListTypeKey));
            
            importUserPriceListTypeQuery.and(isImportUserPriceListType.getExpr(importUserPriceListTypeKey).getWhere());
            importUserPriceListTypeQuery.and(getLCP("autoImportImportUserPriceListType").getExpr(importUserPriceListTypeKey).getWhere());
            importUserPriceListTypeQuery.and(getLCP("autoImportDirectoryImportUserPriceListType").getExpr(importUserPriceListTypeKey).getWhere());
            ImOrderMap<ImMap<PropertyInterface, DataObject>, ImMap<Object, ObjectValue>> importUserPriceListTypeResult = importUserPriceListTypeQuery.executeClasses(context);

            for (int i = 0, size = importUserPriceListTypeResult.size(); i < size; i++) {
                ImMap<Object, ObjectValue> entryValue = importUserPriceListTypeResult.getValue(i);

                DataObject importUserPriceListTypeObject = importUserPriceListTypeResult.getKey(i).valueIt().iterator().next();

                String directory = (String) entryValue.get("autoImportDirectoryImportUserPriceListType").getValue();
                
                ImportColumns importColumns = new ImportUserPriceListActionProperty(LM).readImportColumns(context, importUserPriceListTypeObject);

                if (directory != null && importColumns.getFileExtension() != null) {
                    File dir = new File(trim(directory));

                    if (dir.exists()) {

                        for (File f : dir.listFiles()) {
                            if (f.getName().toLowerCase().endsWith(importColumns.getFileExtension().toLowerCase())) {
                                DataObject userPriceListObject = context.addObject((ConcreteCustomClass) getClass("UserPriceList"));

                                try {

                                    boolean importResult = new ImportUserPriceListActionProperty(LM).importData(context,
                                            userPriceListObject, importColumns, IOUtils.getFileBytes(f),
                                            true);

                                    if (importResult)
                                        renameImportedFile(context, f.getAbsolutePath(), "." + importColumns.getFileExtension());

                                } catch (Exception e) {
                                    ServerLoggers.systemLogger.error(e);
                                }
                            }
                        }
                    }
                }
            }
        } catch (ScriptingErrorLog.SemanticErrorException e) {
            throw new RuntimeException(e);
        }
    }

    protected void renameImportedFile(ExecutionContext context, String oldPath, String extension) {
        File importedFile = new File(oldPath);
        String newExtensionUpCase = extension.substring(0, extension.length() - 1) + "E";
        String newExtensionLowCase = extension.toLowerCase().substring(0, extension.length() - 1) + "e";
        if (importedFile.isFile()) {
            File renamedFile = oldPath.endsWith(extension) ? new File(oldPath.replace(extension, newExtensionUpCase)) :
                    (oldPath.endsWith(extension.toLowerCase()) ? new File(oldPath.replace(extension.toLowerCase(), newExtensionLowCase)) : null);
            if (renamedFile != null && !importedFile.renameTo(renamedFile))
                context.requestUserInteraction(new MessageClientAction("Ошибка при переименовании импортированного файла " + oldPath, "Ошибка"));
        }
    }
}