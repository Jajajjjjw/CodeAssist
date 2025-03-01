package com.tyron.builder.internal.buildTree;

import com.tyron.builder.internal.invocation.BuildAction;

public interface BuildTreeActionExecutor {
    /**
     * Runs the given action and returns the result. Failures should be packaged in the result.
     * When this method returns, all user code will have completed, including 'build finished' hooks.
     */
    BuildActionRunner.Result execute(BuildAction action, BuildTreeContext buildTreeContext);
}