package equ.api.cashregister;

import java.io.Serializable;
import java.util.List;

public class PromotionInfo implements Serializable {
    public List<PromotionTime> promotionTimeList;
    public List<PromotionQuantity> promotionQuantityList;
    public List<PromotionSum> promotionSumList;


    public PromotionInfo(List<PromotionTime> promotionTimeList, List<PromotionQuantity> promotionQuantityList, List<PromotionSum> promotionSumList) {
        this.promotionTimeList = promotionTimeList;
        this.promotionQuantityList = promotionQuantityList;
        this.promotionSumList = promotionSumList;
    }
}
