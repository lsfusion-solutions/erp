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
            return (int) (value.doubleValue() * 10000);
        } else
            return (int) (value.doubleValue() * 100);
    }

    //цена передаётся как BigDecimal
    public BigDecimal denominateMultiplyType2(BigDecimal value, String denominationStage) {
        return denominationStage == null || denominationStage.endsWith("before") ? value : value.multiply(new BigDecimal(10000));
    }

//    public double denominateMultiply(double value, String denominationStage) {
//        return denominationStage == null || denominationStage.endsWith("before") ? value : (value * 10000);
//    }
//
//    public double denominateDivide(double value, String denominationStage) {
//        return denominationStage == null || denominationStage.endsWith("before") ? value : (value / 10000);
//    }
//
//    public BigDecimal denominateDivide(BigDecimal value, String denominationStage) {
//        return denominationStage == null || denominationStage.endsWith("before") ? value : value.divide(new BigDecimal(10000), 2, RoundingMode.HALF_UP);
//    }
}
