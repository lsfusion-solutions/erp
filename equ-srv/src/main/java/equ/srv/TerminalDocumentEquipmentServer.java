package equ.srv;

import com.google.common.base.Throwables;
import equ.api.terminal.TerminalDocumentDetail;
import equ.api.terminal.TerminalInfo;
import lsfusion.base.col.MapFact;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImOrderMap;
import lsfusion.base.col.interfaces.immutable.ImRevMap;
import lsfusion.interop.form.property.Compare;
import lsfusion.server.language.property.LP;
import lsfusion.server.logics.classes.user.ConcreteCustomClass;
import lsfusion.server.logics.action.controller.stack.ExecutionStack;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.data.expr.key.KeyExpr;
import lsfusion.server.data.query.builder.QueryBuilder;
import lsfusion.server.logics.BusinessLogics;
import lsfusion.server.physics.exec.db.controller.manager.DBManager;
import lsfusion.server.data.value.DataObject;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;
import lsfusion.server.physics.dev.integration.service.*;
import lsfusion.server.logics.action.session.DataSession;
import org.apache.log4j.Logger;

import java.rmi.RemoteException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static lsfusion.base.BaseUtils.trim;

public class TerminalDocumentEquipmentServer {
    private static final Logger logger = Logger.getLogger(TerminalDocumentEquipmentServer.class);

    static ScriptingLogicsModule terminalLM;

    public static void init(BusinessLogics BL) {
        terminalLM = BL.getModule("EquipmentTerminal");
    }

    public static List<TerminalInfo> readTerminalInfo(DBManager dbManager, EquipmentServer server, String sidEquipmentServer) throws RemoteException, SQLException {
        List<TerminalInfo> terminalInfoList = new ArrayList<>();
        if (terminalLM != null) {
            try (DataSession session = server.createSession()) {

                KeyExpr groupTerminalExpr = new KeyExpr("groupTerminal");
                KeyExpr terminalExpr = new KeyExpr("terminal");

                ImRevMap<Object, KeyExpr> keys = MapFact.toRevMap((Object) "GroupTerminal", groupTerminalExpr, "terminal", terminalExpr);
                QueryBuilder<Object, Object> query = new QueryBuilder<>(keys);

                String[] terminalNames = new String[]{"nppMachinery", "portMachinery"};
                LP[] terminalProperties = terminalLM.findProperties("npp[Machinery]", "port[Machinery]");
                for (int i = 0; i < terminalProperties.length; i++) {
                    query.addProperty(terminalNames[i], terminalProperties[i].getExpr(terminalExpr));
                }

                String[] groupTerminalNames = new String[]{"handlerModelGroupMachinery",
                        "directoryGroupTerminal", "idPriceListTypeGroupMachinery", "nppGroupMachinery"};
                LP[] groupTerminalProperties = terminalLM.findProperties("handlerModel[GroupMachinery]",
                        "directory[GroupTerminal]", "idPriceListType[GroupMachinery]", "npp[GroupMachinery]");
                for (int i = 0; i < groupTerminalProperties.length; i++) {
                    query.addProperty(groupTerminalNames[i], groupTerminalProperties[i].getExpr(groupTerminalExpr));
                }

                query.and(terminalLM.findProperty("handlerModel[GroupMachinery]").getExpr(groupTerminalExpr).getWhere());
                query.and(terminalLM.findProperty("directory[GroupTerminal]").getExpr(groupTerminalExpr).getWhere());
                query.and(terminalLM.findProperty("groupTerminal[Terminal]").getExpr(terminalExpr).compare(groupTerminalExpr, Compare.EQUALS));
                query.and(terminalLM.findProperty("active[GroupTerminal]").getExpr(groupTerminalExpr).getWhere());
                query.and(terminalLM.findProperty("sidEquipmentServer[GroupMachinery]").getExpr(groupTerminalExpr).compare(new DataObject(sidEquipmentServer), Compare.EQUALS));

                ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> result = query.execute(session);

                for (ImMap<Object, Object> row : result.values()) {
                    terminalInfoList.add(new TerminalInfo(true, false, false, (Integer) row.get("nppGroupMachinery"), (Integer) row.get("nppMachinery"),
                            null, (String) row.get("handlerModelGroupMachinery"), (String) row.get("portMachinery"),
                            trim((String) row.get("directoryGroupTerminal")), (String) row.get("idPriceListTypeGroupMachinery")));
                }
            } catch (ScriptingErrorLog.SemanticErrorException e) {
                throw new RuntimeException(e.toString());
            } catch (SQLHandledException e) {
                throw Throwables.propagate(e);
            }
        }
        return terminalInfoList;
    }

    public static String sendTerminalInfo(BusinessLogics BL, DBManager dbManager, EquipmentServer server, ExecutionStack stack, List<TerminalDocumentDetail> terminalDocumentDetailList) throws RemoteException, SQLException {
        try {

            List<ImportProperty<?>> props = new ArrayList<>();
            List<ImportField> fields = new ArrayList<>();
            List<ImportKey<?>> keys = new ArrayList<>();

            List<List<Object>> data = initData(terminalDocumentDetailList.size());

            if (terminalLM != null && EquipmentServer.notNullNorEmpty(terminalDocumentDetailList)) {

                ImportField idTerminalDocumentField = new ImportField(terminalLM.findProperty("id[TerminalDocument]"));
                ImportKey<?> terminalDocumentKey = new ImportKey((ConcreteCustomClass) terminalLM.findClass("TerminalDocument"),
                        terminalLM.findProperty("terminalDocument[VARSTRING[1000]]").getMapping(idTerminalDocumentField));
                keys.add(terminalDocumentKey);
                props.add(new ImportProperty(idTerminalDocumentField, terminalLM.findProperty("id[TerminalDocument]").getMapping(terminalDocumentKey)));
                fields.add(idTerminalDocumentField);
                for (int i = 0; i < terminalDocumentDetailList.size(); i++)
                    data.get(i).add(terminalDocumentDetailList.get(i).idDocument);

                ImportField titleTerminalDocumentField = new ImportField(terminalLM.findProperty("title[TerminalDocument]"));
                props.add(new ImportProperty(titleTerminalDocumentField, terminalLM.findProperty("title[TerminalDocument]").getMapping(terminalDocumentKey)));
                fields.add(titleTerminalDocumentField);
                for (int i = 0; i < terminalDocumentDetailList.size(); i++)
                    data.get(i).add(terminalDocumentDetailList.get(i).numberDocument);

                ImportField dateTerminalDocumentField = new ImportField(terminalLM.findProperty("date[TerminalDocument]"));
                props.add(new ImportProperty(dateTerminalDocumentField, terminalLM.findProperty("date[TerminalDocument]").getMapping(terminalDocumentKey)));
                fields.add(dateTerminalDocumentField);
                for (int i = 0; i < terminalDocumentDetailList.size(); i++)
                    data.get(i).add(terminalDocumentDetailList.get(i).dateDocument);

                ImportField timeTerminalDocumentField = new ImportField(terminalLM.findProperty("time[TerminalDocument]"));
                props.add(new ImportProperty(timeTerminalDocumentField, terminalLM.findProperty("time[TerminalDocument]").getMapping(terminalDocumentKey)));
                fields.add(timeTerminalDocumentField);
                for (int i = 0; i < terminalDocumentDetailList.size(); i++)
                    data.get(i).add(terminalDocumentDetailList.get(i).timeDocument);

                ImportField commentTerminalDocumentField = new ImportField(terminalLM.findProperty("comment[TerminalDocument]"));
                props.add(new ImportProperty(commentTerminalDocumentField, terminalLM.findProperty("comment[TerminalDocument]").getMapping(terminalDocumentKey)));
                fields.add(commentTerminalDocumentField);
                for (int i = 0; i < terminalDocumentDetailList.size(); i++)
                    data.get(i).add(terminalDocumentDetailList.get(i).commentDocument);

                ImportField directoryGroupTerminalField = new ImportField(terminalLM.findProperty("directory[GroupTerminal]"));
                ImportKey<?> groupTerminalKey = new ImportKey((ConcreteCustomClass) terminalLM.findClass("GroupTerminal"),
                        terminalLM.findProperty("groupTerminalDirectory[VARSTRING[100]]").getMapping(directoryGroupTerminalField));
                keys.add(groupTerminalKey);
                props.add(new ImportProperty(directoryGroupTerminalField, terminalLM.findProperty("groupTerminal[TerminalDocument]").getMapping(terminalDocumentKey),
                        terminalLM.object(terminalLM.findClass("GroupTerminal")).getMapping(groupTerminalKey)));
                fields.add(directoryGroupTerminalField);
                for (int i = 0; i < terminalDocumentDetailList.size(); i++)
                    data.get(i).add(terminalDocumentDetailList.get(i).directoryGroupTerminal);

                ImportField idTerminalHandbookType1TerminalDocumentField = new ImportField(terminalLM.findProperty("idTerminalHandbookType1[TerminalDocument]"));
                props.add(new ImportProperty(idTerminalHandbookType1TerminalDocumentField, terminalLM.findProperty("idTerminalHandbookType1[TerminalDocument]").getMapping(terminalDocumentKey)));
                fields.add(idTerminalHandbookType1TerminalDocumentField);
                for (int i = 0; i < terminalDocumentDetailList.size(); i++)
                    data.get(i).add(terminalDocumentDetailList.get(i).idTerminalHandbookType1);

                ImportField idTerminalHandbookType2TerminalDocumentField = new ImportField(terminalLM.findProperty("idTerminalHandbookType2[TerminalDocument]"));
                props.add(new ImportProperty(idTerminalHandbookType2TerminalDocumentField, terminalLM.findProperty("idTerminalHandbookType2[TerminalDocument]").getMapping(terminalDocumentKey)));
                fields.add(idTerminalHandbookType2TerminalDocumentField);
                for (int i = 0; i < terminalDocumentDetailList.size(); i++)
                    data.get(i).add(terminalDocumentDetailList.get(i).idTerminalHandbookType2);

                ImportField idTerminalDocumentTypeField = new ImportField(terminalLM.findProperty("id[TerminalDocumentType]"));
                ImportKey<?> terminalDocumentTypeKey = new ImportKey((ConcreteCustomClass) terminalLM.findClass("TerminalDocumentType"),
                        terminalLM.findProperty("terminalDocumentType[VARSTRING[100]]").getMapping(idTerminalDocumentTypeField));
                terminalDocumentTypeKey.skipKey = true;
                keys.add(terminalDocumentTypeKey);
                props.add(new ImportProperty(idTerminalDocumentTypeField, terminalLM.findProperty("id[TerminalDocumentType]").getMapping(terminalDocumentTypeKey)));
                props.add(new ImportProperty(idTerminalDocumentTypeField, terminalLM.findProperty("terminalDocumentType[TerminalDocument]").getMapping(terminalDocumentKey),
                        terminalLM.object(terminalLM.findClass("TerminalDocumentType")).getMapping(terminalDocumentTypeKey)));
                fields.add(idTerminalDocumentTypeField);
                for (int i = 0; i < terminalDocumentDetailList.size(); i++)
                    data.get(i).add(terminalDocumentDetailList.get(i).idTerminalDocumentType);

                ImportField quantityTerminalDocumentField = new ImportField(terminalLM.findProperty("quantity[TerminalDocument]"));
                props.add(new ImportProperty(quantityTerminalDocumentField, terminalLM.findProperty("quantity[TerminalDocument]").getMapping(terminalDocumentKey)));
                fields.add(quantityTerminalDocumentField);
                for (int i = 0; i < terminalDocumentDetailList.size(); i++)
                    data.get(i).add(terminalDocumentDetailList.get(i).quantityDocument);

                ImportField idTerminalDocumentDetailField = new ImportField(terminalLM.findProperty("id[TerminalDocumentDetail]"));
                ImportKey<?> terminalDocumentDetailKey = new ImportKey((ConcreteCustomClass) terminalLM.findClass("TerminalDocumentDetail"),
                        terminalLM.findProperty("terminalIdTerminalId[VARSTRING[1000],VARSTRING[1000]]").getMapping(idTerminalDocumentField, idTerminalDocumentDetailField));
                keys.add(terminalDocumentDetailKey);
                props.add(new ImportProperty(idTerminalDocumentDetailField, terminalLM.findProperty("id[TerminalDocumentDetail]").getMapping(terminalDocumentDetailKey)));
                props.add(new ImportProperty(idTerminalDocumentField, terminalLM.findProperty("terminalDocument[TerminalDocumentDetail]").getMapping(terminalDocumentDetailKey),
                        terminalLM.object(terminalLM.findClass("TerminalDocument")).getMapping(terminalDocumentKey)));
                fields.add(idTerminalDocumentDetailField);
                for (int i = 0; i < terminalDocumentDetailList.size(); i++)
                    data.get(i).add(terminalDocumentDetailList.get(i).idDocumentDetail);

                ImportField numberTerminalDocumentDetailField = new ImportField(terminalLM.findProperty("number[TerminalDocumentDetail]"));
                props.add(new ImportProperty(numberTerminalDocumentDetailField, terminalLM.findProperty("number[TerminalDocumentDetail]").getMapping(terminalDocumentDetailKey)));
                fields.add(numberTerminalDocumentDetailField);
                for (int i = 0; i < terminalDocumentDetailList.size(); i++)
                    data.get(i).add(parseInteger(terminalDocumentDetailList.get(i).numberDocumentDetail));

                ImportField barcodeTerminalDocumentDetailField = new ImportField(terminalLM.findProperty("barcode[TerminalDocumentDetail]"));
                props.add(new ImportProperty(barcodeTerminalDocumentDetailField, terminalLM.findProperty("barcode[TerminalDocumentDetail]").getMapping(terminalDocumentDetailKey)));
                fields.add(barcodeTerminalDocumentDetailField);
                for (int i = 0; i < terminalDocumentDetailList.size(); i++)
                    data.get(i).add(terminalDocumentDetailList.get(i).barcodeDocumentDetail);

                ImportField nameTerminalDocumentDetailField = new ImportField(terminalLM.findProperty("dataName[TerminalDocumentDetail]"));
                props.add(new ImportProperty(nameTerminalDocumentDetailField, terminalLM.findProperty("dataName[TerminalDocumentDetail]").getMapping(terminalDocumentDetailKey)));
                fields.add(nameTerminalDocumentDetailField);
                for (int i = 0; i < terminalDocumentDetailList.size(); i++)
                    data.get(i).add(terminalDocumentDetailList.get(i).nameDocumentDetail);

                ImportField priceTerminalDocumentDetailField = new ImportField(terminalLM.findProperty("price[TerminalDocumentDetail]"));
                props.add(new ImportProperty(priceTerminalDocumentDetailField, terminalLM.findProperty("price[TerminalDocumentDetail]").getMapping(terminalDocumentDetailKey)));
                fields.add(priceTerminalDocumentDetailField);
                for (int i = 0; i < terminalDocumentDetailList.size(); i++)
                    data.get(i).add(terminalDocumentDetailList.get(i).priceDocumentDetail);

                ImportField quantityTerminalDocumentDetailField = new ImportField(terminalLM.findProperty("quantity[TerminalDocumentDetail]"));
                props.add(new ImportProperty(quantityTerminalDocumentDetailField, terminalLM.findProperty("quantity[TerminalDocumentDetail]").getMapping(terminalDocumentDetailKey)));
                fields.add(quantityTerminalDocumentDetailField);
                for (int i = 0; i < terminalDocumentDetailList.size(); i++)
                    data.get(i).add(terminalDocumentDetailList.get(i).quantityDocumentDetail);

                ImportField sumTerminalDocumentDetailField = new ImportField(terminalLM.findProperty("sum[TerminalDocumentDetail]"));
                props.add(new ImportProperty(sumTerminalDocumentDetailField, terminalLM.findProperty("sum[TerminalDocumentDetail]").getMapping(terminalDocumentDetailKey)));
                fields.add(sumTerminalDocumentDetailField);
                for (int i = 0; i < terminalDocumentDetailList.size(); i++)
                    data.get(i).add(terminalDocumentDetailList.get(i).sumDocumentDetail);

                ImportTable table = new ImportTable(fields, data);

                try (DataSession session = server.createSession()) {
                    session.pushVolatileStats("ES_TI");
                    IntegrationService service = new IntegrationService(session, table, keys, props);
                    service.synchronize(true, false);
                    String result = session.applyMessage(BL, stack);
                    session.popVolatileStats();
                    return result;
                }
            } else return null;

        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    private static List<List<Object>> initData(int size) {
        List<List<Object>> data = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            data.add(new ArrayList<>());
        }
        return data;
    }

    private static Integer parseInteger(String value) {
        try {
            return value == null || value.isEmpty() ? null : Integer.parseInt(value);
        } catch (Exception e) {
            logger.error("Error occurred while parsing integer value: ", e);
            return null;
        }
    }

}