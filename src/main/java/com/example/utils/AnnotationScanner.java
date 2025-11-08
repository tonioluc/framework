package com.example.utils;

import com.example.annotation.AnnotationController;
import com.example.annotation.UrlMethod;

import java.io.File;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * Scanner d'annotations pour détecter toutes les classes de l'application
 * (dans WEB-INF/classes) annotées avec @AnnotationController et leurs méthodes @UrlMethod.
 */
public class AnnotationScanner {

    // private static final String BASE_PACKAGE = "com.example";

    /**
     * Scanne le dossier WEB-INF/classes de l'application
     * pour trouver toutes les classes annotées.
     *
     * @param classesPath chemin absolu vers WEB-INF/classes
     * @return une map des URLs → ClasseUtilisataire
     */
    public static Map<String, InfoUrl> scan(String classesPath , String packageName) {
        Map<String, InfoUrl> mapping = new HashMap<>();

        System.out.println("=== Démarrage du scanner d'annotations ===");

        // Vérifie que le dossier existe
        File rootDir = new File(classesPath);
        if (!rootDir.exists() || !rootDir.isDirectory()) {
            System.err.println("❌ Dossier introuvable : " + classesPath);
            return mapping;
        }

        // Convertit le package de base en chemin (ex: com/example)
        String basePath = packageName.replace('.', '/');

        // Dossier de base à partir duquel commencer le scan
        File baseDir = new File(rootDir, basePath);

        if (!baseDir.exists()) {
            System.err.println("⚠️ Aucun dossier " + basePath + " trouvé dans " + classesPath);
            return mapping;
        }

        // Démarre le scan récursif
        scanDirectory(baseDir, packageName, mapping, classesPath);

        return mapping;
    }

    /**
     * Parcourt récursivement le dossier pour trouver les fichiers .class
     */
    private static void scanDirectory(File directory, String packageName, Map<String, InfoUrl> mapping, String classesRootPath) {
        File[] files = directory.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                // 🔁 Appel récursif
                scanDirectory(file, packageName + "." + file.getName(), mapping, classesRootPath);
            } else if (file.getName().endsWith(".class")) {
                // Supprime l'extension .class
                String className = file.getName().substring(0, file.getName().length() - 6);
                String fullClassName = packageName + "." + className;
                processClass(fullClassName, mapping);
            }
        }
    }

    /**
     * Vérifie si la classe a les annotations voulues et ajoute les mappings.
     */
    private static void processClass(String fullClassName, Map<String, InfoUrl> mapping) {
        try {
            Class<?> clazz = Class.forName(fullClassName);

            if (clazz.isAnnotationPresent(AnnotationController.class)) {
                AnnotationController controllerAnno = clazz.getAnnotation(AnnotationController.class);
                String baseUrl = controllerAnno.url();

                for (Method method : clazz.getDeclaredMethods()) {
                    if (method.isAnnotationPresent(UrlMethod.class)) {
                        UrlMethod urlMethodAnno = method.getAnnotation(UrlMethod.class);
                        String methodUrl = urlMethodAnno.path();

                        String finalUrl = (baseUrl + methodUrl).replaceAll("//+", "/");
                        mapping.put(finalUrl, new InfoUrl(fullClassName, method.getName()));

                        System.out.println("✅ Found mapping: " + finalUrl + " → " + fullClassName + "." + method.getName());
                    }
                }
            }
        } catch (ClassNotFoundException e) {
            System.err.println("❌ Classe introuvable : " + fullClassName);
        } catch (Throwable t) {
            System.err.println("⚠️ Erreur sur la classe : " + fullClassName);
            t.printStackTrace();
        }
    }
}
