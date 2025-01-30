package equ.clt.handler.rbs;

import java.io.Serializable;

public class RBS4010ButtonSettings implements Serializable{
    
    private Integer test;
    
    public RBS4010ButtonSettings() {
    }
    
    public Integer getTest() {
        return test;
    }
    
    public void setTest(Integer test) {
        this.test = test;
    }
}