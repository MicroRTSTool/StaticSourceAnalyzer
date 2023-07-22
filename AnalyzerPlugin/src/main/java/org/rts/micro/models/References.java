package org.rts.micro.models;

import io.ballerina.compiler.api.symbols.Symbol;
import io.ballerina.compiler.syntax.tree.FunctionDefinitionNode;
import io.ballerina.tools.diagnostics.Location;

import java.util.List;

public class References {
    FunctionDefinitionNode node;
    List<Location> locationList;

    public References(FunctionDefinitionNode node, List<Location> locationList) {
        this.node = node;
        this.locationList = locationList;
    }

    public FunctionDefinitionNode getNode() {
        return node;
    }

    public void setNode(FunctionDefinitionNode node) {
        this.node = node;
    }

    public List<Location> getLocationList() {
        return locationList;
    }

    public void setLocationList(List<Location> locationList) {
        this.locationList = locationList;
    }


    public void print() {

        System.out.println("References{" +
                "node=" + node.toString() +
                ", locationList=" ) ;
        locationList.forEach(
                location -> {
                    System.out.println(location.toString());
                }
        );
        System.out.println('}');
    }
}
