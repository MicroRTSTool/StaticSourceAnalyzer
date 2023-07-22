package org.rts.micro;

import io.ballerina.compiler.syntax.tree.BasicLiteralNode;
import io.ballerina.compiler.syntax.tree.CaptureBindingPatternNode;
import io.ballerina.compiler.syntax.tree.ClassDefinitionNode;
import io.ballerina.compiler.syntax.tree.ClientResourceAccessActionNode;
import io.ballerina.compiler.syntax.tree.FunctionDefinitionNode;
import io.ballerina.compiler.syntax.tree.ImplicitNewExpressionNode;
import io.ballerina.compiler.syntax.tree.ModuleVariableDeclarationNode;
import io.ballerina.compiler.syntax.tree.Node;
import io.ballerina.compiler.syntax.tree.ParenthesizedArgList;
import io.ballerina.compiler.syntax.tree.PositionalArgumentNode;
import io.ballerina.compiler.syntax.tree.QualifiedNameReferenceNode;
import io.ballerina.compiler.syntax.tree.RemoteMethodCallActionNode;
import io.ballerina.compiler.syntax.tree.ServiceDeclarationNode;
import io.ballerina.compiler.syntax.tree.SimpleNameReferenceNode;
import io.ballerina.compiler.syntax.tree.SyntaxKind;
import io.ballerina.compiler.syntax.tree.TypedBindingPatternNode;
import io.ballerina.projects.Package;
import io.ballerina.projects.plugins.AnalysisTask;
import io.ballerina.projects.plugins.CodeAnalysisContext;
import io.ballerina.projects.plugins.CodeAnalyzer;
import io.ballerina.projects.plugins.CompilationAnalysisContext;
import io.ballerina.projects.plugins.SyntaxNodeAnalysisContext;
import io.ballerina.tools.diagnostics.Diagnostic;
import io.ballerina.tools.diagnostics.DiagnosticFactory;
import io.ballerina.tools.diagnostics.DiagnosticInfo;
import io.ballerina.tools.diagnostics.DiagnosticSeverity;
import org.rts.micro.models.RemoteInvokerFunction;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class MicroRTSCodeAnalyzer extends CodeAnalyzer {


    @Override
    public void init(CodeAnalysisContext analysisContext) {
        List<RemoteInvokerFunction> remoteInvokerFunctions = new ArrayList<>();
        Map<String, String> clientDetails = new HashMap<>();

        analysisContext.addSyntaxNodeAnalysisTask(syntaxNodeAnalysisContext -> {
            if (syntaxNodeAnalysisContext.node() instanceof ImplicitNewExpressionNode) {
                ImplicitNewExpressionNode implicitNewExpressionNode = (ImplicitNewExpressionNode) syntaxNodeAnalysisContext.node();
                String clientName = findClientName(implicitNewExpressionNode);
                if (clientName != null) {
                    if(implicitNewExpressionNode.children().get(1) instanceof ParenthesizedArgList){
                        ParenthesizedArgList argList = (ParenthesizedArgList) implicitNewExpressionNode.children().get(1);
                        if( argList.children().get(1) instanceof PositionalArgumentNode){
                            PositionalArgumentNode positionalArgumentNode = (PositionalArgumentNode) argList.children().get(1);
                            // basic literal
                            clientDetails.put(clientName, positionalArgumentNode.children().get(0).toString());
                            System.out.println("Client Name: " + clientName + " URL: " + positionalArgumentNode.children().get(0).toString());
                        }
                    }
                }
            } else {
                processNode(remoteInvokerFunctions, syntaxNodeAnalysisContext.node());
            }

        }, Arrays.asList(SyntaxKind.IMPLICIT_NEW_EXPRESSION, SyntaxKind.REMOTE_METHOD_CALL_ACTION, SyntaxKind.CLIENT_RESOURCE_ACCESS_ACTION));

        analysisContext.addCompilationAnalysisTask(new CompilationAnalysisTask(remoteInvokerFunctions, clientDetails));
    }

    private String findClientName(ImplicitNewExpressionNode implicitNewExpressionNode) {
        Node node = implicitNewExpressionNode.parent();
        while (node != null) {
            if (node instanceof ModuleVariableDeclarationNode) {
                ModuleVariableDeclarationNode moduleVariableDeclarationNode = (ModuleVariableDeclarationNode) node;
                if (moduleVariableDeclarationNode.children().get(0) instanceof TypedBindingPatternNode) {
                    TypedBindingPatternNode typedBindingPatternNode =
                            (TypedBindingPatternNode) moduleVariableDeclarationNode.children().get(0);
                    if (typedBindingPatternNode.children().get(1) instanceof CaptureBindingPatternNode) {
                        CaptureBindingPatternNode captureBindingPatternNode =
                                (CaptureBindingPatternNode) typedBindingPatternNode.children().get(1);
                        // identifier token
                        return captureBindingPatternNode.children().get(0).toString();
                    }
                }
            }
            node = node.parent();
        }
        return null;
    }


    private void processNode(List<RemoteInvokerFunction> remoteInvokerFunctions, Node remoteActionNode) {
        Node node = remoteActionNode.parent();
        while (node != null) {
            if (node instanceof FunctionDefinitionNode) {
                FunctionDefinitionNode funcDefNode = (FunctionDefinitionNode) node;
                if (remoteActionNode instanceof RemoteMethodCallActionNode) {
                    RemoteMethodCallActionNode remoteMethodCallActionNode = (RemoteMethodCallActionNode) remoteActionNode;
                    if (remoteMethodCallActionNode.children().get(0) instanceof SimpleNameReferenceNode) {
                        SimpleNameReferenceNode clientNode =
                                (SimpleNameReferenceNode) remoteMethodCallActionNode.children().get(0);
                        addRemoteInvocation(remoteInvokerFunctions, funcDefNode, clientNode);
                    }
                } else if (remoteActionNode instanceof ClientResourceAccessActionNode) {
                    ClientResourceAccessActionNode clientResourceAccessActionNode =
                            (ClientResourceAccessActionNode) remoteActionNode;
                    if (clientResourceAccessActionNode.children().get(0) instanceof SimpleNameReferenceNode) {
                        SimpleNameReferenceNode clientNode =
                                (SimpleNameReferenceNode) clientResourceAccessActionNode.children().get(0);
                        addRemoteInvocation(remoteInvokerFunctions, funcDefNode, clientNode);
                    }
                }
            }
            node = node.parent();
        }
    }

    private void addRemoteInvocation(List<RemoteInvokerFunction> remoteInvokerFunctions, FunctionDefinitionNode funcDefNode, SimpleNameReferenceNode clientNode) {
        RemoteInvokerFunction remoteInvokerFunction =
                new RemoteInvokerFunction(funcDefNode.functionName().text(), Utils.getIsTest(funcDefNode));
        remoteInvokerFunction.addCalledClients(clientNode.name().text());
        remoteInvokerFunctions.add(remoteInvokerFunction);
    }

}


