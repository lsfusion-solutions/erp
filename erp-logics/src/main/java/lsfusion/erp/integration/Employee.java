package lsfusion.erp.integration;


public class Employee {
    public String idEmployee;
    public String firstNameEmployee;
    public String lastNameEmployee;
    public String idPosition;
    public String loginEmployee;
    public Boolean limitAccessEmployee;

    public Employee(String idEmployee, String firstNameEmployee, String lastNameEmployee, String idPosition, 
                    String loginEmployee, Boolean limitAccessEmployee) {
        this.idEmployee = idEmployee;
        this.firstNameEmployee = firstNameEmployee;
        this.lastNameEmployee = lastNameEmployee;
        this.idPosition = idPosition;
        this.loginEmployee = loginEmployee;
        this.limitAccessEmployee = limitAccessEmployee;
    }
}
