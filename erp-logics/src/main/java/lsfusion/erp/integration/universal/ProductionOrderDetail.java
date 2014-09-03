package lsfusion.erp.integration.universal;


import java.math.BigDecimal;
import java.sql.Date;

public class ProductionOrderDetail {
    public Boolean isPosted;
    public String idOrder;
    public String numberOrder;
    public Date dateOrder;
    public String idProductsStock;
    public String idProductDetail;
    public Integer dataIndex;
    public String idItem;
    public BigDecimal outputQuantity;
    public BigDecimal price;
    public BigDecimal componentsPrice;
    public BigDecimal valueVAT;
    public BigDecimal markup;
    public BigDecimal sum;

    public ProductionOrderDetail(Boolean isPosted, String idOrder, String numberOrder, Date dateOrder, String idProductsStock, 
                                 String idProductDetail, Integer dataIndex, String idItem, BigDecimal outputQuantity,
                                 BigDecimal price, BigDecimal componentsPrice, BigDecimal valueVAT, BigDecimal markup, 
                                 BigDecimal sum) {
        this.isPosted = isPosted;
        this.idOrder = idOrder;
        this.numberOrder = numberOrder;
        this.dateOrder = dateOrder;
        this.idProductsStock = idProductsStock;
        this.idProductDetail = idProductDetail;
        this.dataIndex = dataIndex;
        this.idItem = idItem;
        this.outputQuantity = outputQuantity;
        this.price = price;
        this.componentsPrice = componentsPrice;
        this.valueVAT = valueVAT;
        this.markup = markup;
        this.sum = sum;
    }
}
