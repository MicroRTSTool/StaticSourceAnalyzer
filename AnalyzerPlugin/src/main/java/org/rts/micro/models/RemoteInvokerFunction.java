package org.rts.micro.models;

import java.util.ArrayList;
import java.util.List;

public class RemoteInvokerFunction {
    String name;
    boolean isTest;

    public RemoteInvokerFunction(String name, boolean isTest) {
        this.name = name;
        this.isTest = isTest;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isTest() {
        return isTest;
    }

    public void setTest(boolean test) {
        isTest = test;
    }

    public List<String> getCalledClients() {
        return calledClients;
    }

    public void addCalledClients(String clientName) {
        if (this.calledClients == null) {
            this.calledClients = new ArrayList<>();
        }
        if (!this.calledClients.contains(clientName))
            this.calledClients.add(clientName);
    }

    public void setCalledClients(List<String> calledClients) {
        this.calledClients = calledClients;
    }

    List<String> calledClients;

    @Override
    public String toString() {
        return "RemoteInvokerFunction{" +
                "name='" + name + '\'' +
                ", isTest=" + isTest +
                ", calledClients=" + calledClients +
                '}';
    }


}
