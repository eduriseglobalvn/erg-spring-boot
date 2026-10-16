package com.erg.ergspringboot.learningstandards;

/**
 * Provider-neutral outbound xAPI port. Implementations may forward accepted
 * statements to an LRS, but the module never exposes provider SDK types or
 * raw statement bodies to the application core.
 */
public interface XapiStatementForwarder {
    void forward(XapiStatementAccepted event);
}
