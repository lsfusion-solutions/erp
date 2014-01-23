package lsfusion.erp.integration.universal;

import lsfusion.base.IOUtils;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImOrderMap;
import lsfusion.base.col.interfaces.immutable.ImRevMap;
import lsfusion.erp.integration.DefaultIntegrationActionProperty;
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
import lsfusion.server.logics.scripted.ScriptingErrorLog;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;

import java.io.File;
import java.sql.SQLException;

public class ImportUserPriceListsActionProperty extends DefaultIntegrationActionProperty {

    public ImportUserPriceListsActionProperty(ScriptingLogicsModule LM) throws ScriptingErrorLog.SemanticErrorException {
        super(LM);
    }

    @Override
    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {

        try {

            boolean disableVolatileStats = Settings.get().isDisableExplicitVolatileStats();
            
            LCP<?> isImportUserPriceListType = LM.is(getClass("ImportUserPriceListType"));
            ImRevMap<Object, KeyExpr> importUserPriceListTypeKeys = (ImRevMap<Object, KeyExpr>) isImportUserPriceListType.getMapKeys();
            KeyExpr importUserPriceListTypeKey = importUserPriceListTypeKeys.singleValue();
            QueryBuilder<Object, Object> importUserPriceListTypeQuery = new QueryBuilder<Object, Object>(importUserPriceListTypeKeys);
            importUserPriceListTypeQuery.addProperty("autoImportDirectoryImportUserPriceListType", getLCP("autoImportDirectoryImportUserPriceListType").getExpr(context.getModifier(), importUserPriceListTypeKey));
            importUserPriceListTypeQuery.addProperty("captionImportUserPriceListTypeFileExtensionImportUserPriceListType", getLCP("captionImportUserPriceListTypeFileExtensionImportUserPriceListType").getExpr(context.getModifier(), importUserPriceListTypeKey));
            importUserPriceListTypeQuery.addProperty("startRowImportUserPriceListType", getLCP("startRowImportUserPriceListType").getExpr(context.getModifier(), importUserPriceListTypeKey));
            importUserPriceListTypeQuery.addProperty("isPostedImportUserPriceListType", getLCP("isPostedImportUserPriceListType").getExpr(context.getModifier(), importUserPriceListTypeKey));
            importUserPriceListTypeQuery.addProperty("separatorImportUserPriceListType", getLCP("separatorImportUserPriceListType").getExpr(context.getModifier(), importUserPriceListTypeKey));
            importUserPriceListTypeQuery.addProperty("nameImportUserPriceListKeyTypeImportUserPriceListType", getLCP("nameImportUserPriceListKeyTypeImportUserPriceListType").getExpr(context.getModifier(), importUserPriceListTypeKey));
           
            importUserPriceListTypeQuery.and(isImportUserPriceListType.getExpr(importUserPriceListTypeKey).getWhere());
            importUserPriceListTypeQuery.and(getLCP("autoImportImportUserPriceListType").getExpr(importUserPriceListTypeKey).getWhere());
            importUserPriceListTypeQuery.and(getLCP("autoImportDirectoryImportUserPriceListType").getExpr(importUserPriceListTypeKey).getWhere());
            ImOrderMap<ImMap<Object, DataObject>, ImMap<Object, ObjectValue>> importUserPriceListTypeResult = importUserPriceListTypeQuery.executeClasses(context);

            for (int i = 0, size = importUserPriceListTypeResult.size(); i < size; i++) {
                ImMap<Object, ObjectValue> entryValue = importUserPriceListTypeResult.getValue(i);

                DataObject importUserPriceListTypeObject = importUserPriceListTypeResult.getKey(i).valueIt().iterator().next();

                String directory = (String) entryValue.get("autoImportDirectoryImportUserPriceListType").getValue();
                String fileExtension = (String) entryValue.get("captionImportUserPriceListTypeFileExtensionImportUserPriceListType").getValue();
                Integer startRow = (Integer) entryValue.get("startRowImportUserPriceListType").getValue();
                startRow = startRow == null ? 1 : startRow;
                Boolean isPosted = (Boolean) entryValue.get("isPostedImportUserPriceListType").getValue();
                String csvSeparator = trim((String) entryValue.get("separatorImportUserPriceListType").getValue());
                String itemKeyType = (String) entryValue.get("nameImportUserPriceListKeyTypeImportUserPriceListType").getValue();
                String[] parts = itemKeyType == null ? null : itemKeyType.split("\\.");
                itemKeyType = parts == null ? null : trim(parts[parts.length - 1]);
                
                ImportColumns importColumns = ImportUserPriceListActionProperty.readImportColumns(context, LM, importUserPriceListTypeObject);

                if (directory != null && fileExtension != null) {
                    File dir = new File(trim(directory));

                    if (dir.exists()) {

                        for (File f : dir.listFiles()) {
                            if (f.getName().toLowerCase().endsWith(trim(fileExtension).toLowerCase())) {
                                DataObject userPriceListObject = context.addObject((ConcreteCustomClass) getClass("UserPriceList"));

                                try {

                                    boolean importResult = new ImportUserPriceListActionProperty(LM).importData(context,
                                            userPriceListObject, importColumns, IOUtils.getFileBytes(f), trim(fileExtension),
                                            startRow, isPosted, csvSeparator, itemKeyType, true, disableVolatileStats);

                                    if (importResult)
                                        renameImportedFile(context, f.getAbsolutePath(), "." + trim(fileExtension));

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