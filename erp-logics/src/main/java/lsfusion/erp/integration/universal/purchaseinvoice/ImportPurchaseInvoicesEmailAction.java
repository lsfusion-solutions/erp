package lsfusion.erp.integration.universal.purchaseinvoice;

import com.github.junrar.Archive;
import com.github.junrar.exception.RarException;
import com.github.junrar.rarfile.FileHeader;
import com.google.common.base.Throwables;
import jxl.read.biff.BiffException;
import lsfusion.base.BaseUtils;
import lsfusion.base.ExceptionUtils;
import lsfusion.base.Pair;
import lsfusion.base.ReflectionUtils;
import lsfusion.base.col.MapFact;
import lsfusion.base.col.SetFact;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImOrderMap;
import lsfusion.base.col.interfaces.immutable.ImRevMap;
import lsfusion.base.file.FileData;
import lsfusion.base.file.RawFileData;
import lsfusion.erp.ERPLoggers;
import lsfusion.erp.integration.universal.ImportDocumentAction;
import lsfusion.erp.integration.universal.ImportDocumentSettings;
import lsfusion.erp.integration.universal.UniversalImportException;
import lsfusion.interop.form.property.Compare;
import lsfusion.server.data.expr.key.KeyExpr;
import lsfusion.server.data.query.build.QueryBuilder;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.data.value.DataObject;
import lsfusion.server.data.value.ObjectValue;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;
import lsfusion.server.language.property.LP;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.logics.classes.data.file.DynamicFormatFileClass;
import lsfusion.server.logics.classes.user.ConcreteCustomClass;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.logics.property.oraction.PropertyInterface;
import org.xBaseJ.xBaseJException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.sql.SQLException;
import java.text.ParseException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class ImportPurchaseInvoicesEmailAction extends ImportDocumentAction {

    public ImportPurchaseInvoicesEmailAction(ScriptingLogicsModule LM) {
        super(LM);
    }

    public int makeImport(ExecutionContext.NewSession<ClassPropertyInterface> newContext, DataObject invoiceObject, DataObject importTypeObject, Pair<String, RawFileData> file,
                          String fileExtension, ImportDocumentSettings settings, String staticNameImportType, String staticCaptionImportType, boolean completeIdItemAsEAN, boolean allowIncorrectBarcode,
                          boolean checkInvoiceExistence, boolean ignoreInvoicesAfterDocumentsClosedDate) throws ScriptingErrorLog.SemanticErrorException, ParseException, UniversalImportException, SQLHandledException, SQLException, BiffException, xBaseJException, IOException {
        return new ImportPurchaseInvoiceAction(LM).makeImport(
                newContext, invoiceObject, importTypeObject, file.second, fileExtension,
                settings, staticNameImportType, staticCaptionImportType, completeIdItemAsEAN, allowIncorrectBarcode,
                checkInvoiceExistence, ignoreInvoicesAfterDocumentsClosedDate);
    }

    @Override
    public void executeInternal(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {
        super.executeInternal(context);
        try {

            boolean allowIncorrectBarcode = readAllowIncorrectBarcode(context);

            List<String> errors = new ArrayList<>();

            //вложения, которые подошли типу импорта по отправителю, но не по типу файла: флаг ошибки им ставится в конце прогона,
            //если вложение не взял ни один тип импорта (один отправитель может стоять в нескольких типах с разными типами файла)
            Map<DataObject, SkippedAttachment> skippedAttachments = new LinkedHashMap<>();
            Set<DataObject> acceptedAttachments = new HashSet<>();
            Map<DataObject, String> attachmentExtensions = new HashMap<>();
            Map<DataObject, String> excelExtensions = new HashMap<>();
            //отправители типов без типа файла: их вложения разбирает своё действие клиента, флаг ошибки здесь им не ставится
            List<Pair<Object, String[]>> customEmailPatterns = new ArrayList<>();

            LP<PropertyInterface> isImportType = (LP<PropertyInterface>) is(findClass("ImportType"));
            ImRevMap<PropertyInterface, KeyExpr> importTypeKeys = isImportType.getMapKeys();
            KeyExpr importTypeKey = importTypeKeys.singleValue();
            QueryBuilder<PropertyInterface, Object> importTypeQuery = new QueryBuilder<>(importTypeKeys);
            importTypeQuery.addProperty("autoImportEmailImportType", findProperty("autoImportEmail[ImportType]").getExpr(context.getModifier(), importTypeKey));
            importTypeQuery.addProperty("autoImportAccountImportType", findProperty("autoImportAccount[ImportType]").getExpr(context.getModifier(), importTypeKey));
            importTypeQuery.addProperty("autoImportCheckInvoiceExistenceImportType", findProperty("autoImportCheckInvoiceExistence[ImportType]").getExpr(context.getModifier(), importTypeKey));
            importTypeQuery.addProperty("completeIdItemAsEANImportType", findProperty("completeIdItemAsEAN[ImportType]").getExpr(context.getModifier(), importTypeKey));

            importTypeQuery.and(isImportType.getExpr(importTypeKey).getWhere());
            importTypeQuery.and(findProperty("autoImport[ImportType]").getExpr(importTypeKey).getWhere());
            importTypeQuery.and(findProperty("autoImportEmail[ImportType]").getExpr(importTypeKey).getWhere());
            ImOrderMap<ImMap<PropertyInterface, DataObject>, ImMap<Object, ObjectValue>> importTypeResult = importTypeQuery.executeClasses(context);

            for (int i = 0, size = importTypeResult.size(); i < size; i++) {
                ImMap<Object, ObjectValue> entryValue = importTypeResult.getValue(i);

                DataObject importTypeObject = importTypeResult.getKey(i).valueIt().iterator().next();

                ObjectValue accountObject = entryValue.get("autoImportAccountImportType");
                String emails = (String) entryValue.get("autoImportEmailImportType").getValue();
                boolean completeIdItemAsEAN = entryValue.get("completeIdItemAsEANImportType") instanceof DataObject;
                boolean checkInvoiceExistence = entryValue.get("autoImportCheckInvoiceExistenceImportType") instanceof DataObject;
                String staticNameImportType = (String) findProperty("staticNameImportTypeDetail[ImportType]").read(context, importTypeObject);
                String staticCaptionImportType = (String) findProperty("staticCaptionImportTypeDetail[ImportType]").read(context, importTypeObject);
                
                ImportDocumentSettings settings = readImportDocumentSettings(context, importTypeObject);
                String fileExtension = settings.getFileExtension();
                boolean multipleDocuments = settings.isMultipleDocuments();

                if (fileExtension == null && emails != null && accountObject instanceof DataObject)
                    customEmailPatterns.add(Pair.create(accountObject.getValue(), getEmailPatterns(emails)));

                if (fileExtension != null && emails != null && accountObject instanceof DataObject) {

                    String[] emailPatterns = getEmailPatterns(emails);

                    KeyExpr emailExpr = new KeyExpr("email");
                    KeyExpr attachmentEmailExpr = new KeyExpr("attachmentEmail");
                    ImRevMap<Object, KeyExpr> emailKeys = MapFact.toRevMap("email", emailExpr, "attachmentEmail", attachmentEmailExpr);

                    QueryBuilder<Object, Object> emailQuery = new QueryBuilder<>(emailKeys);
                    String[] emailNames = new String[]{"fromAddressEmail", "dateTimeReceivedEmail", "subjectEmail"};
                    LP<?>[] emailProperties = findProperties("fromAddress[Email]", "dateTimeReceived[Email]", "subject[Email]");
                    for (int j = 0; j < emailProperties.length; j++) {
                        emailQuery.addProperty(emailNames[j], emailProperties[j].getExpr(context.getModifier(), emailExpr));
                    }

                    emailQuery.and(findProperty("email[AttachmentEmail]").getExpr(context.getModifier(), attachmentEmailExpr).compare(emailExpr, Compare.EQUALS));
                    emailQuery.and(findProperty("account[Email]").getExpr(context.getModifier(), emailExpr).compare(accountObject.getExpr(), Compare.EQUALS));
                    emailQuery.and(findProperty("notImported[AttachmentEmail]").getExpr(context.getModifier(), attachmentEmailExpr).getWhere());
                    emailQuery.and(findProperty("file[AttachmentEmail]").getExpr(context.getModifier(), attachmentEmailExpr).getWhere());
                    emailQuery.and(findProperty("skip[AttachmentEmail]").getExpr(context.getModifier(), attachmentEmailExpr).getWhere().not());

                    ImOrderMap<ImMap<Object, DataObject>, ImMap<Object, ObjectValue>> emailResult = emailQuery.executeClasses(context);

                    for (int j = 0, sizej = emailResult.size(); j < sizej; j++) {
                        ImMap<Object, ObjectValue> emailEntryValue = emailResult.getValue(j);
                        ObjectValue attachmentEmailObject = emailResult.getKey(j).get("attachmentEmail");
                        LocalDateTime dateTimeReceivedEmail = (LocalDateTime) emailEntryValue.get("dateTimeReceivedEmail").getValue();
                        boolean isOld = Duration.between(dateTimeReceivedEmail, LocalDateTime.now()).toHours() >= 24; //старше 24 часов
                        String fromAddressEmail = (String) emailEntryValue.get("fromAddressEmail").getValue();
                        String subjectEmail = (String) emailEntryValue.get("subjectEmail").getValue();

                        boolean matches = matchesEmail(fromAddressEmail, emailPatterns);

                        if (matches) {
                            String nameAttachmentEmail = null;
                            RawFileData fileAttachment = null;
                            try {
                                //тип файла вложения сверяем с типом файла импорта до чтения содержимого: вложение другого типа (не архив) не читаем и не разбираем, пару XLS и XLSX сверяем по содержимому
                                DataObject attachmentEmailDataObject = (DataObject) attachmentEmailObject;
                                if (!attachmentExtensions.containsKey(attachmentEmailDataObject))
                                    attachmentExtensions.put(attachmentEmailDataObject, readExtension(context, attachmentEmailDataObject));
                                String extensionAttachment = attachmentExtensions.get(attachmentEmailDataObject);
                                //XLS и XLSX различаем по содержимому: имя бывает от другого формата (.xls с XLSX внутри), а разбор XLS и XLSX читает только свой формат
                                RawFileData excelFile = null;
                                if (isExcelExtension(extensionAttachment) && isExcelExtension(fileExtension) && !excelExtensions.containsKey(attachmentEmailDataObject)) {
                                    excelFile = readFile(context, attachmentEmailDataObject);
                                    excelExtensions.put(attachmentEmailDataObject, getExcelExtension(excelFile, extensionAttachment));
                                }
                                if (isSuitableExtension(excelExtensions.getOrDefault(attachmentEmailDataObject, extensionAttachment), fileExtension)) {
                                    acceptedAttachments.add(attachmentEmailDataObject);
                                    //содержимое (и имя файла: расширение filename берётся из содержимого) читаем только для вложений подошедшего отправителя,
                                    //а не для всех вложений ящика на каждый тип импорта
                                    nameAttachmentEmail = trim((String) findProperty("filename[AttachmentEmail]").read(context, attachmentEmailDataObject));
                                    fileAttachment = excelFile != null ? excelFile : readFile(context, attachmentEmailDataObject);
                                } else
                                    skippedAttachments.computeIfAbsent(attachmentEmailDataObject, a -> new SkippedAttachment(extensionAttachment, fromAddressEmail, accountObject.getValue()))
                                            .fileExtensions.add(fileExtension);
                            } catch (Exception e) {
                                errors.add(e.getLocalizedMessage());
                                logImportError(context, attachmentEmailObject, e.getLocalizedMessage(), isOld);
                                ERPLoggers.importLogger.error("ImportPurchaseInvoices Error for attachment: " + nameAttachmentEmail, e);
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

                                            boolean ignoreInvoicesAfterDocumentsClosedDate = findProperty("ignoreInvoicesAfterDocumentsClosedDate[]").read(context) != null;
                                            int importResult = makeImport(newContext, invoiceObject, importTypeObject, file, fileExtension, settings, staticNameImportType, staticCaptionImportType,
                                                    completeIdItemAsEAN, allowIncorrectBarcode, checkInvoiceExistence, ignoreInvoicesAfterDocumentsClosedDate);

                                            if(invoiceObject != null) {
                                                findProperty("original[Purchase.Invoice]").change(new DataObject(new FileData(file.second, fileExtension), DynamicFormatFileClass.get()), newContext, invoiceObject);
                                                findProperty("currentInvoice[]").change(invoiceObject, newContext);
                                            }

                                            boolean cancelSession = false;
                                            if(findProperty("needExecuteScript[ImportType]").read(newContext, importTypeObject) != null) {
                                                findAction("executeScript[ImportType]").execute(newContext, importTypeObject);
                                                cancelSession = findProperty("cancelSession[]").read(newContext) != null;
                                            }

                                            findAction("executeLocalEvents[TEXT]").execute(newContext, new DataObject("Purchase.UserInvoice"));

                                            if (importResult >= IMPORT_RESULT_OK) {
                                                String result;
                                                if(cancelSession) {
                                                    newContext.cancel(SetFact.EMPTY());
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
                                            String error = String.format("email %s, file %s: %s", subjectEmail, file.first, e + "\n" + ExceptionUtils.getStackTrace(e));
                                            errors.add(error);
                                            logImportError(context, attachmentEmailObject, error, isOld);
                                            ERPLoggers.importLogger.error("ImportPurchaseInvoices Error: ", e);
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

            //вложение не того типа файла не разбирается и больше не берётся: флаг ошибки сразу, без ожидания 24 часов (тип файла не изменится)
            for (Map.Entry<DataObject, SkippedAttachment> entry : skippedAttachments.entrySet()) {
                SkippedAttachment skipped = entry.getValue();
                if (!acceptedAttachments.contains(entry.getKey()) && !skipped.matches(customEmailPatterns)) {
                    String excelExtension = excelExtensions.get(entry.getKey());
                    String extension = skipped.extension.toUpperCase() + (excelExtension != null && !excelExtension.equalsIgnoreCase(skipped.extension)
                            ? " (содержимое " + excelExtension.toUpperCase() + ")" : "");
                    String error = String.format("Тип файла %s не соответствует %s импорта %s", extension,
                            skipped.fileExtensions.size() > 1 ? "типам" : "типу", String.join(", ", skipped.fileExtensions));
                    logImportError(context, entry.getKey(), error, true);
                    ERPLoggers.importLogger.info(String.format("ImportPurchaseInvoices: attachment %s from %s skipped: %s", entry.getKey().getValue(), skipped.fromAddress, error));
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

    private void logImportError(ExecutionContext<ClassPropertyInterface> context, ObjectValue attachmentEmailObject, String error, boolean isOld) throws SQLException, ScriptingErrorLog.SemanticErrorException, SQLHandledException {
        try (ExecutionContext.NewSession newContext = context.newSession()) {
            findProperty("lastError[AttachmentEmail]").change(error, newContext, (DataObject) attachmentEmailObject);
            if (isOld)
                findProperty("importError[AttachmentEmail]").change(true, newContext, (DataObject) attachmentEmailObject);
            newContext.apply();
        }
    }
    
    private static String[] getEmailPatterns(String emails) {
        return emails.replace("*", ".*").toLowerCase().split(";");
    }

    private static boolean matchesEmail(String fromAddressEmail, String[] emailPatterns) {
        if (fromAddressEmail != null) {
            for (String emailPattern : emailPatterns) {
                if (fromAddressEmail.toLowerCase().matches(emailPattern))
                    return true;
            }
        }
        return false;
    }

    //тип файла вложения подходит типу импорта: тот же, архив (из него берутся файлы нужного типа) или не определён (разбирается, как раньше)
    private static boolean isSuitableExtension(String extension, String fileExtension) {
        return extension == null || extension.isEmpty() || extension.equalsIgnoreCase(fileExtension)
                || extension.equalsIgnoreCase("zip") || extension.equalsIgnoreCase("rar");
    }

    private static boolean isExcelExtension(String extension) {
        return "xls".equalsIgnoreCase(extension) || "xlsx".equalsIgnoreCase(extension);
    }

    //формат книги Excel по первым байтам: XLSX — zip-архив (50 4B 03 04), XLS — составной документ OLE2 (D0 CF 11 E0); иначе — по имени файла
    private static String getExcelExtension(RawFileData file, String extension) {
        byte[] bytes = file != null ? file.getBytes() : null;
        if (bytes != null && bytes.length >= 4) {
            if (bytes[0] == 0x50 && bytes[1] == 0x4B && bytes[2] == 0x03 && bytes[3] == 0x04)
                return "xlsx";
            if ((bytes[0] & 0xFF) == 0xD0 && (bytes[1] & 0xFF) == 0xCF && (bytes[2] & 0xFF) == 0x11 && (bytes[3] & 0xFF) == 0xE0)
                return "xls";
        }
        return extension;
    }

    private RawFileData readFile(ExecutionContext<ClassPropertyInterface> context, DataObject attachmentEmailObject) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        return ((FileData) findProperty("file[AttachmentEmail]").read(context, attachmentEmailObject)).getRawFile();
    }

    //расширение хранится в начале значения FILE и считается в базе: содержимое вложения в Java не читается
    private String readExtension(ExecutionContext<ClassPropertyInterface> context, DataObject attachmentEmailObject) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        KeyExpr attachmentEmailExpr = new KeyExpr("attachmentEmail");
        ImRevMap<Object, KeyExpr> keys = MapFact.singletonRev("attachmentEmail", attachmentEmailExpr);
        QueryBuilder<Object, Object> query = new QueryBuilder<>(keys);
        query.addProperty("extension", findProperty("extension[FILE]").getExpr(context.getModifier(),
                findProperty("file[AttachmentEmail]").getExpr(context.getModifier(), attachmentEmailExpr)));
        query.and(attachmentEmailExpr.compare(attachmentEmailObject, Compare.EQUALS));
        ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> result = query.execute(context);
        return result.isEmpty() ? null : trim((String) result.getValue(0).get("extension"));
    }

    private static class SkippedAttachment {
        private final String extension;
        private final String fromAddress;
        private final Object account;
        private final Set<String> fileExtensions = new TreeSet<>();

        private SkippedAttachment(String extension, String fromAddress, Object account) {
            this.extension = extension;
            this.fromAddress = fromAddress;
            this.account = account;
        }

        private boolean matches(List<Pair<Object, String[]>> emailPatterns) {
            for (Pair<Object, String[]> patterns : emailPatterns) {
                if (Objects.equals(patterns.first, account) && matchesEmail(fromAddress, patterns.second))
                    return true;
            }
            return false;
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
                Archive a = new Archive(inputFile);

                FileHeader fh = a.nextFileHeader();

                while (fh != null) {
                    String fileName = getRarFileName(fh);
                    outputFile = new File(outputDirectory.getPath() + "/" + fileName);
                    File dir = outputFile.getParentFile();
                    if(dir.mkdirs() && !dirList.contains(dir))
                        dirList.add(dir);
                    if(!outputFile.isDirectory()) {
                        try (FileOutputStream os = new FileOutputStream(outputFile)) {
                            a.extractFile(fh, os);
                        }
                        String outExtension = BaseUtils.getFileExtension(outputFile);
                        if (extensionFilter.equalsIgnoreCase(outExtension))
                            result.add(Pair.create(fileName, new RawFileData(outputFile)));
                        safeFileDelete(outputFile);
                    }
                    fh = a.nextFileHeader();
                }
                a.close();
            }

            for(File dir : dirList) {
                safeFileDelete(dir);
            };
            
        } catch (RarException | IOException e) {
            throw Throwables.propagate(e);
        } finally {
            safeFileDelete(inputFile);
            safeFileDelete(outputFile);
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
                        if(dir.mkdirs()) {
                            dirList.add(dir);
                        }
                    }
                    else {
                        String fileName = ze.getName();
                        outputFile = new File(outputDirectory.getPath() + "/" + fileName);
                        File parentDir = outputFile.getParentFile();
                        if(parentDir.exists() || parentDir.mkdirs()) {
                            dirList.add(parentDir);
                            FileOutputStream outputStream = new FileOutputStream(outputFile);
                            int len;
                            while ((len = inputStream.read(buffer)) > 0) {
                                outputStream.write(buffer, 0, len);
                            }
                            outputStream.close();
                            String outExtension = BaseUtils.getFileExtension(outputFile);
                            if (extensionFilter.equalsIgnoreCase(outExtension))
                                result.add(Pair.create(fileName, new RawFileData(outputFile)));
                            safeFileDelete(outputFile);
                        }
                    }
                    ze = inputStream.getNextEntry();
                }
                inputStream.closeEntry();
                inputStream.close();
            }
            
            for(File dir : dirList) {
                safeFileDelete(dir);
            }
                    
        } catch (IOException e) {
            throw Throwables.propagate(e);
        } finally {
            safeFileDelete(inputFile);
            safeFileDelete(outputFile);
        }
        return result;
    }

    public static String getFileName(File file) {
        String name = file.getName();
        int index = name.lastIndexOf(".");
        return (index == -1) ? "" : name.substring(0, index);
    }

    // junrar 0.7 (platform 6.x before #1908) has only getFileNameW() / getFileNameString(),
    // junrar 8.x (platform with RAR5 support) has only getFileName(),
    // so the methods are called through reflection to work with both platform versions
    // todo: remove after upgrading erp to 6.3 after #1908
    private static String getRarFileName(FileHeader fh) {
        try {
            return ReflectionUtils.getMethodValue(FileHeader.class, fh, "getFileName", new Class[0], new Object[0]);
        } catch (RuntimeException e) {
            if (!(e.getCause() instanceof NoSuchMethodException))
                throw e;
            return ReflectionUtils.getMethodValue(FileHeader.class, fh, fh.isUnicode() ? "getFileNameW" : "getFileNameString", new Class[0], new Object[0]);
        }
    }
}