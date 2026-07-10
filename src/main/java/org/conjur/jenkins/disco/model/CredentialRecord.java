package org.conjur.jenkins.disco.model;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

public class CredentialRecord {
    private String credentialId;
    private String name;
    private String typeDisplayName;
    private String originId;
    private String type;
    private String location;
    private String description;
    private String error;
    private Map<String, String> additionalData;
    private Map<String, String> conjurization;
    private Map<String, String> fields;
    private String values;
    private List<String> valuesWithError;
    private List<String> whereUsed;
    private String inheritancePath;
    private String levelUpdatedAt;
    private String createdAt;
    private String updatedAt;

    public String getCredentialId() { return credentialId; }
    public void setCredentialId(String credentialId) { this.credentialId = credentialId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getTypeDisplayName() { return typeDisplayName; }
    public void setTypeDisplayName(String typeDisplayName) { this.typeDisplayName = typeDisplayName; }

    public String getOriginId() { return originId; }
    public void setOriginId(String originId) { this.originId = originId; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getError() { return error; }
    public void setError(String error) { this.error = error; }

    public Map<String, String> getAdditionalData() { return additionalData; }
    public void setAdditionalData(Map<String, String> additionalData) { this.additionalData = additionalData; }

    public Map<String, String> getConjurization() { return conjurization; }
    public void setConjurization(Map<String, String> conjurization) {
        this.conjurization = (conjurization == null || conjurization.isEmpty()) ? null : conjurization;
    }

    public Map<String, String> getFields() { return fields; }
    public void setFields(Map<String, String> fields) { this.fields = fields; }

    public String getValues() { return values; }
    public void setValues(String values) { this.values = values; }

    public List<String> getValuesWithError() { return valuesWithError; }
    public void setValuesWithError(List<String> valuesWithError) { this.valuesWithError = valuesWithError; }

    public List<String> getWhereUsed() { return whereUsed; }
    public void setWhereUsed(List<String> whereUsed) {
        this.whereUsed = whereUsed == null ? null : new ArrayList<>(new LinkedHashSet<>(whereUsed));
    }

    public String getInheritancePath() { return inheritancePath; }
    public void setInheritancePath(String inheritancePath) { this.inheritancePath = inheritancePath; }

    public String getLevelUpdatedAt() { return levelUpdatedAt; }
    public void setLevelUpdatedAt(String levelUpdatedAt) { this.levelUpdatedAt = levelUpdatedAt; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
}
