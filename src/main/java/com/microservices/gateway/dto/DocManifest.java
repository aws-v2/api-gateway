package com.microservices.gateway.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.ToString;


@ToString 
public class DocManifest {

    private String service;
    private String apiVersion;
    private String scope;

    @JsonProperty("internal")
    private List<DocCategory> internal;

    @JsonProperty("public")
    private List<DocCategory> publicDocs;

    // getters and setters
    public String getService() { return service; }
    public void setService(String service) { this.service = service; }

    public String getApiVersion() { return apiVersion; }
    public void setApiVersion(String apiVersion) { this.apiVersion = apiVersion; }

    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }

    public List<DocCategory> getInternal() { return internal; }
    public void setInternal(List<DocCategory> internal) { this.internal = internal; }

    public List<DocCategory> getPublicDocs() { return publicDocs; }
    public void setPublicDocs(List<DocCategory> publicDocs) { this.publicDocs = publicDocs; }
}



