package com.example.utils;

public class InfoUrl {
    private String nomClasse;
    private String nomMethode;

    public InfoUrl(String nomClasse, String nomMethode) {
        this.nomClasse = nomClasse;
        this.nomMethode = nomMethode;
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
