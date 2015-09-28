package equ.api.scales;

import equ.api.MachineryHandler;
import equ.api.MachineryInfo;
import equ.api.SalesBatch;
import equ.api.StopListInfo;

import java.io.IOException;
import java.util.List;

public abstract class ScalesHandler extends MachineryHandler<TransactionScalesInfo, ScalesInfo, SalesBatch> {

    public abstract void sendStopListInfo(StopListInfo stopListInfo, List<MachineryInfo> machineryInfoList) throws IOException;

}
