package org.rts.micro;

import io.ballerina.projects.plugins.CompilerPlugin;
import io.ballerina.projects.plugins.CompilerPluginContext;

public class MicroRTSCompilerPlugin extends CompilerPlugin {

    @Override
    public void init(CompilerPluginContext pluginContext) {
        pluginContext.addCodeAnalyzer(new MicroRTSCodeAnalyzer());
    }
}