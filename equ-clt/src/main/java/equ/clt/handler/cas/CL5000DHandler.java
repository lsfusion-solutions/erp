package equ.clt.handler.cas;

import equ.api.MachineryInfo;
import equ.api.scales.TransactionScalesInfo;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import java.io.IOException;

public class CL5000DHandler extends CL5000JHandler {
    public CL5000DHandler(FileSystemXmlApplicationContext springContext) {
        super(springContext, 400);
    }

    @Override
    public String getGroupId(TransactionScalesInfo transactionInfo) {
        String groupId = "";
        for (MachineryInfo scales : transactionInfo.machineryInfoList) {
            groupId += scales.port + ";";
        }
        return "CL5000D" + groupId;
    }
}