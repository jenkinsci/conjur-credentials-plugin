package org.conjur.jenkins.disco.model;

public class DiscoveryRunResult {

    public enum Status { IDLE, RUNNING, SUCCESS, ERROR, ABORTED }

    private long startTime;
    private Status status = Status.IDLE;
    private String message;
    private String kid;
    private String jwksUri;
    private String conjurUrl;

    public DiscoveryRunResult() {}

    public long getStartTime() { return startTime; }
    public void setStartTime(long startTime) { this.startTime = startTime; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getKid() { return kid; }
    public void setKid(String kid) { this.kid = kid; }

    public String getJwksUri() { return jwksUri; }
    public void setJwksUri(String jwksUri) { this.jwksUri = jwksUri; }

    public String getConjurUrl() { return conjurUrl; }
    public void setConjurUrl(String conjurUrl) { this.conjurUrl = conjurUrl; }
}
