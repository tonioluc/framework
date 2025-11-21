package com.example.utils;

public class InfoUrl {
    private String nomClasse;
    private String nomMethode;

    private String urlRegex;

    public String getUrlRegex() {
        return urlRegex;
    }

    public void setUrlRegex(String urlRegex) {
        this.urlRegex = urlRegex;
    }

    public InfoUrl(String nomClasse, String nomMethode, String urlRegex) {
        this.nomClasse = nomClasse;
        this.nomMethode = nomMethode;
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
