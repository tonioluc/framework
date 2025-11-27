package com.example.utils;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class InfoUrl {
private String nomClasse;
    private String nomMethode;
    private List<String> paramNames;
    private String urlRegex;

    // quelles méthodes HTTP sont autorisées ?
    private Set<String> httpMethods = new HashSet<>(); // "GET", "POST", etc.

    public InfoUrl(String nomClasse, String nomMethode, String urlRegex, List<String> paramNames) {
        this.nomClasse = nomClasse;
        this.nomMethode = nomMethode;
        this.urlRegex = urlRegex;
        this.paramNames = paramNames;
        // Par défaut, si on arrive ici via l'ancien @UrlMethod → on accepte GET et POST
        this.httpMethods.add("GET");
        this.httpMethods.add("POST");
    }

    // Constructeur dédié pour les nouvelles annotations
    public InfoUrl(String nomClasse, String nomMethode, String urlRegex, List<String> paramNames, Set<String> httpMethods) {
        this(nomClasse, nomMethode, urlRegex, paramNames);
        this.httpMethods.clear();
        this.httpMethods.addAll(httpMethods);
    }

    public Set<String> getHttpMethods() {
        return httpMethods;
    }

    public boolean supportsMethod(String method) {
        return httpMethods.contains(method.toUpperCase());
    }
    public List<String> getParamNames() {
        return paramNames;
    }

    public void setParamNames(List<String> paramNames) {
        this.paramNames = paramNames;
    }

    public String getUrlRegex() {
        return urlRegex;
    }

    public void setUrlRegex(String urlRegex) {
        this.urlRegex = urlRegex;
    }

    public String getNomClasse() {
        return nomClasse;
    }

    public void setNomClasse(String nomClasse) {
        this.nomClasse = nomClasse;
    }

    public String getNomMethode() {
        return nomMethode;
    }

    public void setNomMethode(String nomMethode) {
        this.nomMethode = nomMethode;
    }

    @Override
    public String toString() {
        return "Url info [nomClasse=" + nomClasse + ", nomMethode=" + nomMethode + "]";
    }
}
