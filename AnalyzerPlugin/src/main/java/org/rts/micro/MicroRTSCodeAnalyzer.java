package org.rts.micro;

import io.ballerina.compiler.syntax.tree.AnnotationNode;
import io.ballerina.compiler.syntax.tree.BasicLiteralNode;
import io.ballerina.compiler.syntax.tree.CaptureBindingPatternNode;
import io.ballerina.compiler.syntax.tree.ClassDefinitionNode;
import io.ballerina.compiler.syntax.tree.ClientResourceAccessActionNode;
import io.ballerina.compiler.syntax.tree.ExpressionNode;
import io.ballerina.compiler.syntax.tree.FunctionDefinitionNode;
import io.ballerina.compiler.syntax.tree.ImplicitNewExpressionNode;
import io.ballerina.compiler.syntax.tree.MappingConstructorExpressionNode;
import io.ballerina.compiler.syntax.tree.MetadataNode;
import io.ballerina.compiler.syntax.tree.ModuleVariableDeclarationNode;
import io.ballerina.compiler.syntax.tree.Node;
import io.ballerina.compiler.syntax.tree.NodeList;
import io.ballerina.compiler.syntax.tree.ParenthesizedArgList;
import io.ballerina.compiler.syntax.tree.PositionalArgumentNode;
import io.ballerina.compiler.syntax.tree.QualifiedNameReferenceNode;
import io.ballerina.compiler.syntax.tree.RemoteMethodCallActionNode;
import io.ballerina.compiler.syntax.tree.ServiceDeclarationNode;
import io.ballerina.compiler.syntax.tree.SimpleNameReferenceNode;
import io.ballerina.compiler.syntax.tree.SpecificFieldNode;
import io.ballerina.compiler.syntax.tree.SyntaxKind;
import io.ballerina.compiler.syntax.tree.Token;
import io.ballerina.compiler.syntax.tree.TypedBindingPatternNode;
import io.ballerina.compiler.syntax.tree.VariableDeclarationNode;
import io.ballerina.projects.plugins.CodeAnalysisContext;
import io.ballerina.projects.plugins.CodeAnalyzer;
import org.rts.micro.models.RemoteInvokerFunction;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class MicroRTSCodeAnalyzer extends CodeAnalyzer {


    public static final String DISPLAY = "display";
    public static final String LABEL = "label";

    @Override
    public void init(CodeAnalysisContext analysisContext) {
        List<RemoteInvokerFunction> remoteInvokerFunctions = new ArrayList<>();
        Map<String, String> clientDetails = new HashMap<>();
        List<String> svcPathMappings = new ArrayList<>();

        analysisContext.addSyntaxNodeAnalysisTask(syntaxNodeAnalysisContext -> {
            if (syntaxNodeAnalysisContext.node() instanceof ServiceDeclarationNode) {
                ServiceDeclarationNode serviceDeclarationNode = (ServiceDeclarationNode) syntaxNodeAnalysisContext.node();
                Optional<MetadataNode> metadata = serviceDeclarationNode.metadata();
                if (metadata.isPresent()) {
                    MetadataNode metadataNode = metadata.get();
                    NodeList<AnnotationNode> annotations = metadataNode.annotations();
                    annotations.forEach(annotationNode -> {
                        Node annotReference = annotationNode.annotReference();
                        if (annotReference.kind() == SyntaxKind.SIMPLE_NAME_REFERENCE && annotReference.toString().trim().equals(DISPLAY)) {
                            Optional<MappingConstructorExpressionNode> mappingNodes = annotationNode.annotValue();
                            if (!mappingNodes.isEmpty()) {
                                mappingNodes.get().fields().forEach(mappingFieldNode -> {
                                    if (mappingFieldNode.kind() == SyntaxKind.SPECIFIC_FIELD) {
                                        SpecificFieldNode specificField = (SpecificFieldNode) mappingFieldNode;
                                        if (LABEL.equals(getFieldName(specificField))) {
                                            ExpressionNode valueExpr = specificField.valueExpr().orElse(null);
                                            if (valueExpr != null) {
                                                if (SyntaxKind.STRING_LITERAL == valueExpr.kind()) {
                                                    String serviceName = ((BasicLiteralNode) valueExpr).literalToken().text();
                                                    svcPathMappings.add(serviceName);
                                                }
                                            }
                                        }
                                    }
                                });
                            }
                        }
                    });
                }

            } else if (syntaxNodeAnalysisContext.node() instanceof ImplicitNewExpressionNode) {

                ImplicitNewExpressionNode implicitNewExpressionNode = (ImplicitNewExpressionNode) syntaxNodeAnalysisContext.node();
                String clientName = findClientName(implicitNewExpressionNode);
                if (clientName != null) {
                    if (implicitNewExpressionNode.children().size() > 1 &&
                            implicitNewExpressionNode.children().get(1) instanceof ParenthesizedArgList) {
                        ParenthesizedArgList argList = (ParenthesizedArgList) implicitNewExpressionNode.children().get(1);
                        if (argList.children().size() > 1 && argList.children().get(1) instanceof PositionalArgumentNode) {
                            PositionalArgumentNode positionalArgumentNode = (PositionalArgumentNode) argList.children().get(1);
                            // basic literal
                            clientDetails.put(clientName, positionalArgumentNode.children().get(0).toString());
                        }
                    }
                }
            } else {
                processNode(remoteInvokerFunctions, syntaxNodeAnalysisContext.node());
            }

        }, Arrays.asList(SyntaxKind.SERVICE_DECLARATION, SyntaxKind.IMPLICIT_NEW_EXPRESSION,
                SyntaxKind.REMOTE_METHOD_CALL_ACTION, SyntaxKind.CLIENT_RESOURCE_ACCESS_ACTION));

        analysisContext.addCompilationAnalysisTask(new CompilationAnalysisTask(remoteInvokerFunctions, clientDetails, svcPathMappings));
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
            if (node instanceof VariableDeclarationNode) {
                VariableDeclarationNode variableDeclarationNode = (VariableDeclarationNode) node;
                if (variableDeclarationNode.children().size() > 0 &&
                        variableDeclarationNode.children().get(0) instanceof TypedBindingPatternNode) {
                    TypedBindingPatternNode typedBindingPatternNode =
                            (TypedBindingPatternNode) variableDeclarationNode.children().get(0);
                    if (typedBindingPatternNode.children().size() > 1 &&
                            typedBindingPatternNode.children().get(1) instanceof CaptureBindingPatternNode) {
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

    private String getFieldName(SpecificFieldNode specificField) {
        Node fieldNameNode = specificField.fieldName();

        if (fieldNameNode.kind() != SyntaxKind.STRING_LITERAL) {
            return ((Token) fieldNameNode).text();
        }

        String fieldName = ((BasicLiteralNode) fieldNameNode).literalToken().text();
        return fieldName.substring(1, fieldName.length() - 1);
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


