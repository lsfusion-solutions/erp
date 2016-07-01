package equ.api;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public abstract class MachineryHandler<T extends TransactionInfo, M extends MachineryInfo, S extends SalesBatch>/* extends Serializable*/ {

    public EquipmentServerInterface remote;
    
    public abstract String getGroupId(T transactionInfo) throws IOException;

    public abstract Map<Integer, SendTransactionBatch> sendTransaction(List<T> transactionInfoList) throws IOException;

    public abstract void sendSoftCheck(SoftCheckInfo softCheckInfo) throws IOException;

    public void setRemoteObject(EquipmentServerInterface remote) {
        this.remote = remote;
    }

    //цена передаётся как int, кол-во знаков в дробной части задаётся на кассе
    public int denominateMultiplyType1(BigDecimal value, String denominationStage) {
        if (value == null)
            return 0;
        else if (denominationStage == null || denominationStage.trim().endsWith("before")) {
            return value.intValue();
        } else if (denominationStage.trim().endsWith("fusion")) {
            return value.multiply(BigDecimal.valueOf(10000)).intValue();
        } else
            return value.multiply(BigDecimal.valueOf(100)).intValue();
    }

    //цена передаётся как BigDecimal
    public BigDecimal denominateMultiplyType2(BigDecimal value, String denominationStage) {
        return denominationStage != null && denominationStage.endsWith("fusion") ? value.multiply(new BigDecimal(10000)) : value;
    }

    //цена передаётся как BigDecimal
    public BigDecimal denominateDivideType2(BigDecimal value, String denominationStage) {
        return value == null ? null : (denominationStage != null && denominationStage.endsWith("fusion") ? value.divide(new BigDecimal(10000), 2, BigDecimal.ROUND_HALF_UP) : value);
    }

    public double denominateDivideType2(double value, String denominationStage) {
        return denominationStage != null && denominationStage.endsWith("fusion") ? (value / 10000) : value;
    }
}
