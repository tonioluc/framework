package com.example.utils;

import java.util.List;

public class InfoUrl {
    private String nomClasse;
    private String nomMethode;
    private List<String> paramNames;

    public List<String> getParamNames() {
        return paramNames;
    }

    public void setParamNames(List<String> paramNames) {
        this.paramNames = paramNames;
    }

    private String urlRegex;

    public String getUrlRegex() {
        return urlRegex;
    }

    public void setUrlRegex(String urlRegex) {
        this.urlRegex = urlRegex;
    }

    public InfoUrl(String nomClasse, String nomMethode, String urlRegex, List<String> paramNames) {
        this.nomClasse = nomClasse;
        this.nomMethode = nomMethode;
        this.urlRegex = urlRegex;
        this.paramNames = paramNames;
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
