package lsfusion.erp.integration.universal.purchaseinvoice;

import com.github.junrar.Archive;
import com.github.junrar.exception.RarException;
import com.github.junrar.impl.FileVolumeManager;
import com.github.junrar.rarfile.FileHeader;
import com.google.common.base.Throwables;
import lsfusion.base.*;
import lsfusion.base.col.MapFact;
import lsfusion.base.col.SetFact;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImOrderMap;
import lsfusion.base.col.interfaces.immutable.ImRevMap;
import lsfusion.base.file.FileData;
import lsfusion.base.file.RawFileData;
import lsfusion.erp.integration.universal.ImportDocumentActionProperty;
import lsfusion.erp.integration.universal.ImportDocumentSettings;
import lsfusion.interop.form.property.Compare;
import lsfusion.server.ServerLoggers;
import lsfusion.server.classes.ConcreteCustomClass;
import lsfusion.server.classes.DynamicFormatFileClass;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.data.expr.KeyExpr;
import lsfusion.server.data.query.QueryBuilder;
import lsfusion.server.logics.DataObject;
import lsfusion.server.logics.ObjectValue;
import lsfusion.server.logics.linear.LCP;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.logics.property.PropertyInterface;
import lsfusion.server.logics.property.SessionDataProperty;
import lsfusion.server.logics.scripted.ScriptingErrorLog;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;
import lsfusion.server.session.DataSession;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class ImportPurchaseInvoicesEmailActionProperty extends ImportDocumentActionProperty {

    public ImportPurchaseInvoicesEmailActionProperty(ScriptingLogicsModule LM) {
        super(LM);
    }

    @Override
    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {
        super.executeCustom(context);
        try {

            List<String> errors = new ArrayList<>();

            DataSession session = context.getSession();

            LCP<PropertyInterface> isImportType = (LCP<PropertyInterface>) is(findClass("ImportType"));
            ImRevMap<PropertyInterface, KeyExpr> importTypeKeys = isImportType.getMapKeys();
            KeyExpr importTypeKey = importTypeKeys.singleValue();
            QueryBuilder<PropertyInterface, Object> importTypeQuery = new QueryBuilder<>(importTypeKeys);
            importTypeQuery.addProperty("autoImportEmailImportType", findProperty("autoImportEmail[ImportType]").getExpr(session.getModifier(), importTypeKey));
            importTypeQuery.addProperty("autoImportAccountImportType", findProperty("autoImportAccount[ImportType]").getExpr(session.getModifier(), importTypeKey));
            importTypeQuery.addProperty("autoImportCheckInvoiceExistenceImportType", findProperty("autoImportCheckInvoiceExistence[ImportType]").getExpr(session.getModifier(), importTypeKey));
            importTypeQuery.addProperty("completeIdItemAsEANImportType", findProperty("completeIdItemAsEAN[ImportType]").getExpr(session.getModifier(), importTypeKey));

            importTypeQuery.and(isImportType.getExpr(importTypeKey).getWhere());
            importTypeQuery.and(findProperty("autoImport[ImportType]").getExpr(importTypeKey).getWhere());
            importTypeQuery.and(findProperty("autoImportEmail[ImportType]").getExpr(importTypeKey).getWhere());
            ImOrderMap<ImMap<PropertyInterface, DataObject>, ImMap<Object, ObjectValue>> importTypeResult = importTypeQuery.executeClasses(session);

            for (int i = 0, size = importTypeResult.size(); i < size; i++) {
                ImMap<Object, ObjectValue> entryValue = importTypeResult.getValue(i);

                DataObject importTypeObject = importTypeResult.getKey(i).valueIt().iterator().next();

                ObjectValue accountObject = entryValue.get("autoImportAccountImportType");
                String emails = (String) entryValue.get("autoImportEmailImportType").getValue();
                boolean completeIdItemAsEAN = entryValue.get("completeIdItemAsEANImportType") instanceof DataObject;
                boolean checkInvoiceExistence = entryValue.get("autoImportCheckInvoiceExistenceImportType") instanceof DataObject;
                String staticNameImportType = (String) findProperty("staticNameImportTypeDetail[ImportType]").read(session, importTypeObject);
                String staticCaptionImportType = (String) findProperty("staticCaptionImportTypeDetail[ImportType]").read(session, importTypeObject);
                
                ImportDocumentSettings settings = readImportDocumentSettings(session, importTypeObject);
                String fileExtension = settings.getFileExtension();
                boolean multipleDocuments = settings.isMultipleDocuments();

                if (fileExtension != null && emails != null && accountObject instanceof DataObject) {

                    String[] emailPatterns = emails.replace("*", ".*").toLowerCase().split(";");

                    KeyExpr emailExpr = new KeyExpr("email");
                    KeyExpr attachmentEmailExpr = new KeyExpr("attachmentEmail");
                    ImRevMap<Object, KeyExpr> emailKeys = MapFact.toRevMap((Object) "email", emailExpr, "attachmentEmail", attachmentEmailExpr);

                    QueryBuilder<Object, Object> emailQuery = new QueryBuilder<>(emailKeys);
                    emailQuery.addProperty("fromAddressEmail", findProperty("fromAddress[Email]").getExpr(session.getModifier(), emailExpr));
                    emailQuery.addProperty("dateTimeReceivedEmail", findProperty("dateTimeReceived[Email]").getExpr(session.getModifier(), emailExpr));
                    emailQuery.addProperty("fileAttachmentEmail", findProperty("file[AttachmentEmail]").getExpr(session.getModifier(), attachmentEmailExpr));
                    emailQuery.addProperty("nameAttachmentEmail", findProperty("name[AttachmentEmail]").getExpr(session.getModifier(), attachmentEmailExpr));

                    emailQuery.and(findProperty("email[AttachmentEmail]").getExpr(session.getModifier(), attachmentEmailExpr).compare(emailExpr, Compare.EQUALS));
                    emailQuery.and(findProperty("account[Email]").getExpr(session.getModifier(), emailExpr).compare(accountObject.getExpr(), Compare.EQUALS));
                    emailQuery.and(findProperty("notImported[AttachmentEmail]").getExpr(session.getModifier(), attachmentEmailExpr).getWhere());
                    emailQuery.and(findProperty("file[AttachmentEmail]").getExpr(session.getModifier(), attachmentEmailExpr).getWhere());
                    emailQuery.and(findProperty("skip[AttachmentEmail]").getExpr(session.getModifier(), attachmentEmailExpr).getWhere().not());

                    ImOrderMap<ImMap<Object, DataObject>, ImMap<Object, ObjectValue>> emailResult = emailQuery.executeClasses(session);

                    for (int j = 0, sizej = emailResult.size(); j < sizej; j++) {
                        ImMap<Object, ObjectValue> emailEntryValue = emailResult.getValue(j);
                        ObjectValue attachmentEmailObject = emailResult.getKey(j).get("attachmentEmail");
                        Timestamp dateTimeReceivedEmail = (Timestamp) emailEntryValue.get("dateTimeReceivedEmail").getValue();
                        boolean isOld = (Calendar.getInstance().getTime().getTime() - dateTimeReceivedEmail.getTime()) > (24*60*60*1000); //старше 24 часов
                        String nameAttachmentEmail = trim((String) emailEntryValue.get("nameAttachmentEmail").getValue());
                        String fromAddressEmail = (String) emailEntryValue.get("fromAddressEmail").getValue();

                        boolean matches = false;
                        if (fromAddressEmail != null) {
                            for (String emailPattern : emailPatterns) {
                                if (fromAddressEmail.toLowerCase().matches(emailPattern)) {
                                    matches = true;
                                    break;
                                }
                            }
                        }

                        if (matches) {
                            RawFileData fileAttachment = null;
                            try {
                                fileAttachment = ((FileData) emailEntryValue.get("fileAttachmentEmail").getValue()).getRawFile();
                            } catch (Exception e) {
                                errors.add(e.getLocalizedMessage());
                                logImportError(context, attachmentEmailObject, e.getLocalizedMessage(), isOld);
                                ServerLoggers.importLogger.error("ImportPurchaseInvoices Error for attachment: " + nameAttachmentEmail, e);
                            }
                            if(fileAttachment != null) {

                                List<Pair<String, RawFileData>> files = new ArrayList<>();
                                if (nameAttachmentEmail != null) {
                                    if (nameAttachmentEmail.toLowerCase().endsWith(".rar")) {
                                        files = unpackRARFile(fileAttachment, fileExtension);
                                        if (files.isEmpty()) {
                                            errors.add("Архив пуст или повреждён");
                                            logImportError(context, attachmentEmailObject, "Архив пуст или повреждён", isOld);
                                        }
                                    } else if (nameAttachmentEmail.toLowerCase().endsWith(".zip")) {
                                        files = unpackZIPFile(fileAttachment, fileExtension);
                                        if (files.isEmpty()) {
                                            errors.add("Архив пуст или повреждён");
                                            logImportError(context, attachmentEmailObject, "Архив пуст или повреждён", isOld);
                                        }
                                    } else
                                        files.add(Pair.create(nameAttachmentEmail, fileAttachment));
                                }

                                boolean imported = true;
                                for (Pair<String, RawFileData> file : files) {
                                    try (ExecutionContext.NewSession<ClassPropertyInterface> newContext = context.newSession()) {
                                        DataObject invoiceObject = multipleDocuments ? null : newContext.addObject((ConcreteCustomClass) findClass("Purchase.UserInvoice"));

                                        try {

                                            boolean ignoreInvoicesAfterDocumentsClosedDate = findProperty("ignoreInvoicesAfterDocumentsClosedDate[]").read(session) != null;
                                            int importResult = new ImportPurchaseInvoiceActionProperty(LM).makeImport(
                                                    newContext, invoiceObject, importTypeObject, file.second, fileExtension,
                                                    settings, staticNameImportType, staticCaptionImportType, completeIdItemAsEAN,
                                                    checkInvoiceExistence, ignoreInvoicesAfterDocumentsClosedDate);

                                            if(invoiceObject != null) {
                                                findProperty("original[Purchase.Invoice]").change(new DataObject(new FileData(file.second, fileExtension), DynamicFormatFileClass.get()), newContext, invoiceObject);
                                                findProperty("currentInvoice[]").change(invoiceObject, newContext);
                                            }

                                            boolean cancelSession = false;
                                            String script = (String) findProperty("script[ImportType]").read(newContext, importTypeObject);
                                            if(script != null && !script.isEmpty()) {
                                                findAction("executeScript[ImportType]").execute(newContext, importTypeObject);
                                                cancelSession = findProperty("cancelSession[]").read(newContext) != null;
                                            }

                                            findAction("executeLocalEvents[TEXT]").execute(newContext, new DataObject("Purchase.UserInvoice"));

                                            if (importResult >= IMPORT_RESULT_OK) {
                                                String result;
                                                if(cancelSession) {
                                                    newContext.cancel(SetFact.<SessionDataProperty>EMPTY());
                                                    result = "Session canceled";
                                                } else 
                                                    result = newContext.applyMessage();

                                                if (result != null) {
                                                    importResult = IMPORT_RESULT_ERROR;
                                                    errors.add(file.first + ": " + result);
                                                    logImportError(context, attachmentEmailObject, file.first + ": " + result, isOld);
                                                }
                                            }
                                            if (importResult < IMPORT_RESULT_EMPTY) {
                                                imported = false;
                                                if(importResult == IMPORT_RESULT_DOCUMENTS_CLOSED_DATE) {
                                                    errors.add(file.first + ": " + "Запрещено принимать инвоисы по закрытым документам");
                                                    logImportError(context, attachmentEmailObject, file.first + ": " + "Запрещено принимать инвоисы по закрытым документам", isOld);
                                                }
                                            }

                                        } catch (Exception e) {
                                            imported = false;
                                            String error = file.first + ": " + e.toString() + "\n" + ExceptionUtils.getStackTrace(e);
                                            errors.add(error);
                                            logImportError(context, attachmentEmailObject, error, isOld);
                                            ServerLoggers.importLogger.error("ImportPurchaseInvoices Error: ", e);
                                        }
                                    }
                                }

                                if (imported) {
                                    try (ExecutionContext.NewSession newContext = context.newSession()) {
                                        findProperty("imported[AttachmentEmail]").change(true, newContext, (DataObject) attachmentEmailObject);
                                        newContext.apply();
                                    }
                                } else if (isOld) {
                                    try (ExecutionContext.NewSession newContext = context.newSession()) {
                                        findProperty("importError[AttachmentEmail]").change(true, newContext, (DataObject) attachmentEmailObject);
                                        newContext.apply();
                                    }
                                }
                            }

                        }
                    }
                }
            }
            if(!errors.isEmpty()) {
                String error = "";
                for(String e : errors) {
                    error += error.isEmpty() ? e : ("\n" + e);
                }
                throw new RuntimeException(error);
            }
        } catch (ScriptingErrorLog.SemanticErrorException e) {
            throw new RuntimeException(e);
        }
    }

    private void logImportError(ExecutionContext context, ObjectValue attachmentEmailObject, String error, boolean isOld) throws SQLException, ScriptingErrorLog.SemanticErrorException, SQLHandledException {
        try (ExecutionContext.NewSession newContext = context.newSession()) {
            findProperty("lastError[AttachmentEmail]").change(error, newContext, (DataObject) attachmentEmailObject);
            if (isOld)
                findProperty("importError[AttachmentEmail]").change(true, newContext, (DataObject) attachmentEmailObject);
            newContext.apply();
        }
    }
    
    private List<Pair<String, RawFileData>> unpackRARFile(RawFileData fileBytes, String extensionFilter) {

        List<Pair<String, RawFileData>> result = new ArrayList<>();
        File inputFile = null;
        File outputFile = null;
        try {
            inputFile = File.createTempFile("email", ".rar");
            fileBytes.write(inputFile);

            List<File> dirList = new ArrayList<>();
            File outputDirectory = new File(inputFile.getParent() + "/" + getFileName(inputFile));
            if(inputFile.exists() && (outputDirectory.exists() || outputDirectory.mkdir())) {
                dirList.add(outputDirectory);
                Archive a = new Archive(new FileVolumeManager(inputFile));

                FileHeader fh = a.nextFileHeader();

                while (fh != null) {
                    String fileName = (fh.isUnicode() ? fh.getFileNameW() : fh.getFileNameString());
                    outputFile = new File(outputDirectory.getPath() + "/" + fileName);
                    File dir = outputFile.getParentFile();
                    dir.mkdirs();
                    if(!dirList.contains(dir))
                        dirList.add(dir);
                    if(!outputFile.isDirectory()) {
                        try (FileOutputStream os = new FileOutputStream(outputFile)) {
                            a.extractFile(fh, os);
                        }
                        String outExtension = BaseUtils.getFileExtension(outputFile);
                        if (outExtension != null && extensionFilter.toLowerCase().equals(outExtension.toLowerCase()))
                            result.add(Pair.create(fileName, new RawFileData(outputFile)));
                        if(!outputFile.delete())
                            outputFile.deleteOnExit();
                    }
                    fh = a.nextFileHeader();
                }
                a.close();
            }

            for(File dir : dirList)
                if(dir != null && dir.exists() && !dir.delete())
                    dir.deleteOnExit();
            
        } catch (RarException | IOException e) {
            throw Throwables.propagate(e);
        } finally {
            if(inputFile != null && !inputFile.delete())
                inputFile.deleteOnExit();
            if(outputFile != null && !outputFile.delete())
                outputFile.deleteOnExit();
        }
        return result;
    }
    
    private List<Pair<String, RawFileData>> unpackZIPFile(RawFileData fileBytes, String extensionFilter) {

        List<Pair<String, RawFileData>> result = new ArrayList<>();
        File inputFile = null;
        File outputFile = null;
        try {
            inputFile = File.createTempFile("email", ".zip");
            fileBytes.write(inputFile);

            byte[] buffer = new byte[1024];
            Set<File> dirList = new HashSet<>();
            File outputDirectory = new File(inputFile.getParent() + "/" + getFileName(inputFile));
            if(inputFile.exists() && (outputDirectory.exists() || outputDirectory.mkdir())) {
                dirList.add(outputDirectory);
                ZipInputStream inputStream = new ZipInputStream(new FileInputStream(inputFile), Charset.forName("cp866"));

                ZipEntry ze = inputStream.getNextEntry();
                while (ze != null) {
                    if(ze.isDirectory()) {
                        File dir = new File(outputDirectory.getPath() + "/" + ze.getName());
                        dir.mkdirs();
                        dirList.add(dir);
                    }
                    else {
                        String fileName = ze.getName();
                        outputFile = new File(outputDirectory.getPath() + "/" + fileName);
                        FileOutputStream outputStream = new FileOutputStream(outputFile);
                        int len;
                        while ((len = inputStream.read(buffer)) > 0) {
                            outputStream.write(buffer, 0, len);
                        }
                        outputStream.close();
                        String outExtension = BaseUtils.getFileExtension(outputFile);
                        if (outExtension != null && extensionFilter.toLowerCase().equals(outExtension.toLowerCase()))
                            result.add(Pair.create(fileName, new RawFileData(outputFile)));
                        if(!outputFile.delete())
                            outputFile.deleteOnExit();
                    }
                    ze = inputStream.getNextEntry();
                }
                inputStream.closeEntry();
                inputStream.close();
            }
            
            for(File dir : dirList)
                if(dir != null && dir.exists() && !dir.delete())
                    dir.deleteOnExit();
                    
        } catch (IOException e) {
            throw Throwables.propagate(e);
        } finally {
            if(inputFile != null && !inputFile.delete())
                inputFile.deleteOnExit();
            if(outputFile != null && !outputFile.delete())
                outputFile.deleteOnExit();
        }
        return result;
    }

    public static String getFileName(File file) {
        String name = file.getName();
        int index = name.lastIndexOf(".");
        return (index == -1) ? "" : name.substring(0, index);
    }
}