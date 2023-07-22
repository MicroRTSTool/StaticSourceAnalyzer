package org.rts.micro.models;

import io.ballerina.compiler.syntax.tree.FunctionDefinitionNode;
import io.ballerina.projects.ModuleName;

import java.util.List;

public class Module {
    public ModuleName getModuleName() {
        return moduleName;
    }

    ModuleName moduleName;
    public Module(ModuleName name) {
        this.moduleName = name;
    }
    List<FunctionDefinitionNode> testFunctions;

    public List<FunctionDefinitionNode> getTestOnlyScopedFunctions() {
        return testOnlyScopedFunctions;
    }

    public void setTestOnlyScopedFunctions(List<FunctionDefinitionNode> testOnlyScopedFunctions) {
        this.testOnlyScopedFunctions = testOnlyScopedFunctions;
    }

    List<FunctionDefinitionNode> testOnlyScopedFunctions;

    public List<FunctionDefinitionNode> getTestFunctions() {
        return testFunctions;
    }

    public void setTestFunctions(List<FunctionDefinitionNode> testFunctions) {
        this.testFunctions = testFunctions;
    }

    public List<FunctionDefinitionNode> getFunctions() {
        return functions;
    }

    public void setFunctions(List<FunctionDefinitionNode> functions) {
        this.functions = functions;
    }

    List<FunctionDefinitionNode> functions;

}
