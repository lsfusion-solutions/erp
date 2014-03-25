package equ.api;

import java.io.Serializable;

public class OrderInfo implements Serializable{
    public String numberOrder;
    public OrderInfo(String numberOrder) {
        this.numberOrder = numberOrder;        
    }
}
