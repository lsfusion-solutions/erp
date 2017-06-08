package lsfusion.erp.integration.universal.purchaseinvoice;

import com.github.junrar.Archive;
import com.github.junrar.exception.RarException;
import com.github.junrar.impl.FileVolumeManager;
import com.github.junrar.rarfile.FileHeader;
import com.google.common.base.Throwables;
import lsfusion.base.BaseUtils;
import lsfusion.base.IOUtils;
import lsfusion.base.Pair;
import lsfusion.base.col.MapFact;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImOrderMap;
import lsfusion.base.col.interfaces.immutable.ImRevMap;
import lsfusion.erp.integration.universal.ImportDocumentActionProperty;
import lsfusion.erp.integration.universal.ImportDocumentSettings;
import lsfusion.interop.Compare;
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

    public ImportPurchaseInvoicesEmailActionProperty(ScriptingLogicsModule LM) throws ScriptingErrorLog.SemanticErrorException {
        super(LM);
    }

    @Override
    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {
        super.executeCustom(context);
        try {

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
                ObjectValue emailObject = entryValue.get("autoImportEmailImportType");
                boolean completeIdItemAsEAN = entryValue.get("completeIdItemAsEANImportType") instanceof DataObject;
                boolean checkInvoiceExistence = entryValue.get("autoImportCheckInvoiceExistenceImportType") instanceof DataObject;
                String emailPattern = emailObject instanceof DataObject ? ((String) ((DataObject) emailObject).object).replace("*", ".*").toLowerCase() : null;
                String staticNameImportType = (String) findProperty("staticNameImportTypeDetail[ImportType]").read(session, importTypeObject);
                String staticCaptionImportType = (String) findProperty("staticCaptionImportTypeDetail[ImportType]").read(session, importTypeObject);
                
                ImportDocumentSettings settings = readImportDocumentSettings(session, importTypeObject);
                String fileExtension = settings.getFileExtension();
                boolean multipleDocuments = settings.isMultipleDocuments();

                if (fileExtension != null && emailObject instanceof DataObject && accountObject instanceof DataObject) {

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

                    ImOrderMap<ImMap<Object, DataObject>, ImMap<Object, ObjectValue>> emailResult = emailQuery.executeClasses(session);

                    for (int j = 0, sizej = emailResult.size(); j < sizej; j++) {
                        ImMap<Object, ObjectValue> emailEntryValue = emailResult.getValue(j);
                        ObjectValue attachmentEmailObject = emailResult.getKey(j).get("attachmentEmail");
                        Timestamp dateTimeReceivedEmail = (Timestamp) emailEntryValue.get("dateTimeReceivedEmail").getValue();
                        boolean isOld = (Calendar.getInstance().getTime().getTime() - dateTimeReceivedEmail.getTime()) > (24*60*60*1000); //старше 24 часов
                        String nameAttachmentEmail = trim((String) emailEntryValue.get("nameAttachmentEmail").getValue());
                        String fromAddressEmail = (String) emailEntryValue.get("fromAddressEmail").getValue();
                        if (fromAddressEmail != null && fromAddressEmail.toLowerCase().matches(emailPattern)) {
                            byte[] fileAttachment;
                            try {
                                fileAttachment = BaseUtils.getFile((byte[]) emailEntryValue.get("fileAttachmentEmail").getValue());
                            } catch (Exception e) {
                                logImportError(context, attachmentEmailObject, e.getLocalizedMessage(), isOld);
                                ServerLoggers.importLogger.error("ImportPurchaseInvoices Error for attachment: " + nameAttachmentEmail, e);
                                throw Throwables.propagate(e);
                            }

                            List<Pair<String, byte[]>> files = new ArrayList<>();
                            if (nameAttachmentEmail != null) {
                                if (nameAttachmentEmail.toLowerCase().endsWith(".rar")) {
                                    files = unpackRARFile(fileAttachment, fileExtension);
                                    if(files.isEmpty())
                                        logImportError(context, attachmentEmailObject, "Архив пуст или повреждён", isOld);                                    
                                } else if (nameAttachmentEmail.toLowerCase().endsWith(".zip")) {
                                    files = unpackZIPFile(fileAttachment, fileExtension);
                                    if(files.isEmpty())
                                        logImportError(context, attachmentEmailObject, "Архив пуст или повреждён", isOld);
                                } else
                                    files.add(Pair.create(nameAttachmentEmail, fileAttachment));
                            } 

                            boolean imported = true;
                            for(Pair<String, byte[]> file : files) {
                                try (DataSession currentSession = context.createSession()) {
                                    DataObject invoiceObject = multipleDocuments ? null : currentSession.addObject((ConcreteCustomClass) findClass("Purchase.UserInvoice"));

                                    try {

                                        int importResult = new ImportPurchaseInvoiceActionProperty(LM).makeImport(context,
                                                currentSession, invoiceObject, importTypeObject, file.second, fileExtension,
                                                settings, staticNameImportType, staticCaptionImportType, completeIdItemAsEAN, checkInvoiceExistence);

                                        findProperty("original[Purchase.Invoice]").change(
                                                new DataObject(BaseUtils.mergeFileAndExtension(file.second, fileExtension.getBytes()), DynamicFormatFileClass.get(false, true)).object, currentSession, invoiceObject);

                                        findAction("executeLocalEvents[TEXT]").execute(currentSession, context.stack, new DataObject("Purchase.UserInvoice"));

                                        if (importResult >= IMPORT_RESULT_OK) {
                                            String result = currentSession.applyMessage(context);
                                            if(result != null) {
                                                importResult = IMPORT_RESULT_ERROR;
                                                logImportError(context, attachmentEmailObject, file.first + ": " + result, isOld);
                                            }
                                        }
                                        if (importResult < IMPORT_RESULT_EMPTY) {
                                            imported = false;
                                        }

                                    } catch (Exception e) {
                                        imported = false;
                                        logImportError(context, attachmentEmailObject, file.first + ": " + e.toString(), isOld);
                                        ServerLoggers.importLogger.error("ImportPurchaseInvoices Error: ", e);
                                    }
                                }
                            }

                            if (imported) {
                                try (DataSession postImportSession = context.createSession()) {
                                    findProperty("imported[AttachmentEmail]").change(true, postImportSession, (DataObject) attachmentEmailObject);
                                    postImportSession.apply(context);
                                }
                            } else if (isOld) {
                                try (DataSession postImportSession = context.createSession()) {
                                    findProperty("importError[AttachmentEmail]").change(true, postImportSession, (DataObject) attachmentEmailObject);
                                    postImportSession.apply(context);
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

    private void logImportError(ExecutionContext context, ObjectValue attachmentEmailObject, String error, boolean isOld) throws SQLException, ScriptingErrorLog.SemanticErrorException, SQLHandledException {
        try (DataSession postImportSession = context.createSession()) {
            findProperty("lastError[AttachmentEmail]").change(error, postImportSession, (DataObject) attachmentEmailObject);
            if (isOld)
                findProperty("importError[AttachmentEmail]").change(true, postImportSession, (DataObject) attachmentEmailObject);
            postImportSession.apply(context);
        }
    }
    
    private List<Pair<String, byte[]>> unpackRARFile(byte[] fileBytes, String extensionFilter) {

        List<Pair<String, byte[]>> result = new ArrayList<>();
        File inputFile = null;
        File outputFile = null;
        try {
            inputFile = File.createTempFile("email", ".rar");
            try (FileOutputStream stream = new FileOutputStream(inputFile)) {
                stream.write(fileBytes);
            }

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
                            result.add(Pair.create(fileName, IOUtils.getFileBytes(outputFile)));
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
    
    private List<Pair<String, byte[]>> unpackZIPFile(byte[] fileBytes, String extensionFilter) {

        List<Pair<String, byte[]>> result = new ArrayList<>();
        File inputFile = null;
        File outputFile = null;
        try {
            inputFile = File.createTempFile("email", ".zip");
            try (FileOutputStream stream = new FileOutputStream(inputFile)) {
                stream.write(fileBytes);
            }

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
                            result.add(Pair.create(fileName, IOUtils.getFileBytes(outputFile)));
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