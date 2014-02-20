package equ.api;

import java.io.IOException;
import java.util.List;

public abstract class MachineryHandler<T extends TransactionInfo, M extends MachineryInfo, S extends SalesBatch>/* extends Serializable*/ {

    public EquipmentServerInterface remote;

    public abstract void sendTransaction(T transactionInfo, List<M> machineryInfoList) throws IOException;

    public abstract void sendSoftCheck(SoftCheckInfo softCheckInfo) throws IOException;

    public void setRemoteObject(EquipmentServerInterface remote) {
        this.remote = remote;
    }
}
