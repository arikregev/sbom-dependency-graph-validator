package org.validator.sbom.model;

public class Violation {

    public enum Severity { ERROR, WARNING }

    private String ruleId;
    private Severity severity;
    private String message;
    private String componentRef; // nullable

    public Violation() {}

    public Violation(String ruleId, Severity severity, String message, String componentRef) {
        this.ruleId = ruleId;
        this.severity = severity;
        this.message = message;
        this.componentRef = componentRef;
    }

    public String getRuleId() { return ruleId; }
    public void setRuleId(String ruleId) { this.ruleId = ruleId; }

    public Severity getSeverity() { return severity; }
    public void setSeverity(Severity severity) { this.severity = severity; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getComponentRef() { return componentRef; }
    public void setComponentRef(String componentRef) { this.componentRef = componentRef; }
}
