package org.rts.micro;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.ballerina.compiler.api.SemanticModel;
import io.ballerina.compiler.syntax.tree.FunctionDefinitionNode;
import io.ballerina.projects.Package;
import io.ballerina.projects.plugins.AnalysisTask;
import io.ballerina.projects.plugins.CompilationAnalysisContext;
import io.ballerina.tools.diagnostics.Location;
import org.rts.micro.models.References;
import org.rts.micro.models.RemoteInvokerFunction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CompilationAnalysisTask implements AnalysisTask<CompilationAnalysisContext> {

    private static final String TEST_MODULE_NAME = "test";
    List<RemoteInvokerFunction> remoteInvokerFunctions;
    Map<String, String> clientDetails;

    List<String> svcMappings;

    CompilationAnalysisTask(List<RemoteInvokerFunction> remoteInvokerFunctions, Map<String, String> clientDetails,
                            List<String> svcMappings) {
        this.clientDetails = clientDetails;
        this.remoteInvokerFunctions = remoteInvokerFunctions;
        this.svcMappings = svcMappings;
    }


    @Override
    public void perform(CompilationAnalysisContext compilationAnalysisContext) {
        // get non test functions from the remote invoker functions
        List<RemoteInvokerFunction> nonTestRemoteInvokerFunctions = new ArrayList<>();
        // get non test function references
        List<References> referencesList = new ArrayList<>();
        // Get all test and non test function list in modules
        remoteInvokerFunctions.forEach(remoteInvokerFunction -> {
            if (!remoteInvokerFunction.isTest()) {
                nonTestRemoteInvokerFunctions.add(remoteInvokerFunction);
            }
        });
        Package pack = compilationAnalysisContext.currentPackage();
        List<org.rts.micro.models.Module> modules = Utils.getModules(pack);

        // Get references for test only functions which have client calls
        // TODO: Get references for non test functions which have client calls
        modules.forEach(module -> {
            module.getTestOnlyScopedFunctions().forEach(testScopedFunction -> {
                nonTestRemoteInvokerFunctions.forEach(nonTestRemoteInvokerFunction -> {
                    if (testScopedFunction.functionName().text().equals(nonTestRemoteInvokerFunction.getName())) {
                        SemanticModel semanticModel = pack.module(module.getModuleName()).getCompilation().getSemanticModel();
                        semanticModel.symbol(testScopedFunction).ifPresent(
                                symbol -> {
                                    List<Location> referenceLocations = new ArrayList<>();
                                    semanticModel.references(symbol).forEach(
                                            location -> {
                                                if (!symbol.getLocation().get().equals(location)) {
                                                    referenceLocations.add(location);
                                                }
                                            });
                                    referencesList.add(
                                            new References(testScopedFunction, referenceLocations));
                                }
                        );
                    }
                });
            });
        });

        // Get functions which include the references.
        // Supports only one level. Test function calls the reference function
        referencesList.forEach(references -> {
            references.getLocationList().forEach(location -> {
                String testFunctionName = getTestFunction(location, modules);
                if (testFunctionName != null) {
                    updateRemoteInvokerFunction(remoteInvokerFunctions, references.getNode().functionName().text(), testFunctionName);
                }
            });
        });
        ObjectMapper objectMapper = new ObjectMapper();
        // Write test to client data
        List<RemoteInvokerFunction> updatedInvokerFunctions = new ArrayList<>();
        remoteInvokerFunctions.forEach(remoteInvokerFunction -> {
            List<String> updatedCalledClients = new ArrayList<>();
            List<String> calledClients = remoteInvokerFunction.getCalledClients();
            for (String calledClient : calledClients) {
                for (String key : clientDetails.keySet()) {
                    if (key.trim().equals(calledClient.trim())) {
                        try {
                            updatedCalledClients.add(objectMapper.readValue(clientDetails.get(key), String.class));
                        } catch (JsonProcessingException e) {
                            throw new RuntimeException(e);
                        }
                        break;
                    }
                }
            }
            remoteInvokerFunction.setCalledClients(updatedCalledClients);
            updatedInvokerFunctions.add(remoteInvokerFunction);
        });
        this.remoteInvokerFunctions = updatedInvokerFunctions;
        Map<String, List<String>> testToSvcData = new HashMap<>();
        updatedInvokerFunctions.forEach(
                remoteInvokerFunction -> {
                    if (remoteInvokerFunction.isTest()) {
                        testToSvcData.put(remoteInvokerFunction.getName(), remoteInvokerFunction.getCalledClients());
                    }
                }
        );

        if (!testToSvcData.isEmpty()) {
            Utils.writeListToFile(testToSvcData, "test_svc_mappings.json");
        }

        // Write svc path mappings
        String projectPath = compilationAnalysisContext.currentPackage().project().sourceRoot().toString();
        Map<String, String> svcPathMappings = new HashMap<>();
        for (String svc : this.svcMappings) {
            try {
                svcPathMappings.put(objectMapper.readValue(svc, String.class), projectPath);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }

        if (!svcPathMappings.isEmpty()) {
            Utils.writeToFile(svcPathMappings, "svc_path_mappings.json");
        }
    }

    private void updateRemoteInvokerFunction(List<RemoteInvokerFunction> remoteInvokerFunctions,
                                             String text, String testFunction) {
        remoteInvokerFunctions.forEach(
                remoteInvokerFunction -> {
                    if (remoteInvokerFunction.getName().equals(text)) {
                        remoteInvokerFunction.setName(testFunction);
                        remoteInvokerFunction.setTest(true);
                    }
                }
        );
    }

    private String getTestFunction(Location location, List<org.rts.micro.models.Module> modules) {
        for (org.rts.micro.models.Module module : modules) {
            for (FunctionDefinitionNode testFunction : module.getTestFunctions()) { // Assuming the type of elements inside getTestFunctions() is TestFunction
                if (testFunction.location().lineRange().fileName().equals(location.lineRange().fileName())) {
                    if (testFunction.location().lineRange().startLine().line() <= location.lineRange().startLine().line()
                            && testFunction.location().lineRange().endLine().line() >= location.lineRange().endLine().line()) {
                        return testFunction.functionName().text();
                    }
                }
            }
        }
        return null;
    }

}
