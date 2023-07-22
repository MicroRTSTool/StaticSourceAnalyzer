package org.rts.micro;

import io.ballerina.compiler.syntax.tree.FunctionDefinitionNode;
import io.ballerina.compiler.syntax.tree.ModulePartNode;
import io.ballerina.compiler.syntax.tree.QualifiedNameReferenceNode;
import io.ballerina.compiler.syntax.tree.SyntaxKind;
import io.ballerina.projects.Document;
import io.ballerina.projects.Package;
import org.rts.micro.models.Module;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import com.fasterxml.jackson.databind.ObjectMapper;


public class Utils {
    private static final String TEST_MODULE_NAME = "test";

    public static boolean getIsTest(FunctionDefinitionNode funcDefNode) {
        AtomicBoolean isTest = new AtomicBoolean(false);

        funcDefNode.metadata().ifPresent(metadata -> {
            metadata.annotations().forEach(annotation -> {
                if (annotation.annotReference().kind() == SyntaxKind.QUALIFIED_NAME_REFERENCE) {
                    QualifiedNameReferenceNode qualifiedNameReferenceNode =
                            (QualifiedNameReferenceNode) annotation.annotReference();
                    String modulePrefix = qualifiedNameReferenceNode.modulePrefix().text();
                    if (TEST_MODULE_NAME.equals(modulePrefix)) {
                        isTest.set(true);
                    }
                }
            });
        });

        return isTest.get();
    }

    public static List<Module> getModules(Package pack) {
        List<org.rts.micro.models.Module> modules = new ArrayList<>();
        pack.modules().forEach(module -> {
            org.rts.micro.models.Module rtsModule = new org.rts.micro.models.Module(module.moduleName());
            List<FunctionDefinitionNode> testScopedFunctions = new ArrayList<>();
            List<FunctionDefinitionNode> testFunctions = new ArrayList<>();
            List<FunctionDefinitionNode> functions = new ArrayList<>();
            module.testDocumentIds().forEach(documentId -> {
                Document document = module.document(documentId);
                ModulePartNode modulePartNode = document.syntaxTree().rootNode();
                modulePartNode.members().forEach(member -> {
                    if (member instanceof FunctionDefinitionNode) {
                        functions.add((FunctionDefinitionNode) member);
                    }
                });
            });
            module.testDocumentIds().forEach(documentId -> {
                Document document = module.document(documentId);
                ModulePartNode modulePartNode = document.syntaxTree().rootNode();
                modulePartNode.members().forEach(member -> {
                    if (member instanceof FunctionDefinitionNode) {
                        if (getIsTest((FunctionDefinitionNode) member)) {
                            testFunctions.add((FunctionDefinitionNode) member);
                        } else {
                            testScopedFunctions.add((FunctionDefinitionNode) member);
                        }

                    }
                });
            });
            rtsModule.setFunctions(functions);
            rtsModule.setTestFunctions(testFunctions);
            rtsModule.setTestOnlyScopedFunctions(testScopedFunctions);
            modules.add(rtsModule);
        });
        return modules;
    }

    public static void writeToFile(Map<String, List<String>> data, String filePath) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            // Write the map to a file in JSON format
            File file = new File(filePath);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(file, data);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


}
