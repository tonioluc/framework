package com.example.utils;

import com.example.annotation.*;

import java.io.File;
import java.lang.reflect.Method;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Scanner mis à jour pour supporter @GetMethod, @PostMethod et @UrlMethod
 * avec gestion multi-méthode HTTP par URL.
 */
// public className="com.example.utils.AnnotationScanner" updated="2025">
// */
public class AnnotationScanner {

    public static Map<String, List<InfoUrl>> scan(String classesPath, String packageName) {
        Map<String, List<InfoUrl>> mappings = new HashMap<>();
        System.out.println("=== Démarrage du scanner d'annotations (version HTTP-aware) ===");

        File rootDir = new File(classesPath);
        if (!rootDir.exists() || !rootDir.isDirectory()) {
            System.err.println("Dossier introuvable : " + classesPath);
            return mappings;
        }

        String basePath = packageName.replace('.', '/');
        File baseDir = new File(rootDir, basePath);
        if (!baseDir.exists()) {
            System.err.println("Package non trouvé : " + basePath);
            return mappings;
        }

        scanDirectory(baseDir, packageName, mappings, classesPath);
        System.out.println("Scan terminé. " + mappings.size() + " URLs mappées.");
        return mappings;
    }

    private static void scanDirectory(File directory, String packageName,
            Map<String, List<InfoUrl>> mappings, String classesRootPath) {
        File[] files = directory.listFiles();
        if (files == null)
            return;

        for (File file : files) {
            if (file.isDirectory()) {
                scanDirectory(file, packageName + "." + file.getName(), mappings, classesRootPath);
            } else if (file.getName().endsWith(".class")) {
                String className = file.getName().substring(0, file.getName().length() - 6);
                String fullClassName = packageName + "." + className;
                processClass(fullClassName, mappings);
            }
        }
    }

    private static void processClass(String fullClassName, Map<String, List<InfoUrl>> mappings) {
        try {
            Class<?> clazz = Class.forName(fullClassName);

            if (!clazz.isAnnotationPresent(AnnotationController.class)) {
                return;
            }

            AnnotationController controllerAnno = clazz.getAnnotation(AnnotationController.class);
            String baseUrl = controllerAnno.url();

            for (Method method : clazz.getDeclaredMethods()) {
                // 1. @GetMethod
                if (method.isAnnotationPresent(GetMethod.class)) {
                    registerMapping(method, method.getAnnotation(GetMethod.class).path(),
                            baseUrl, fullClassName, method.getName(), Set.of("GET", "HEAD"), mappings);
                }
                // 2. @PostMethod
                else if (method.isAnnotationPresent(PostMethod.class)) {
                    registerMapping(method, method.getAnnotation(PostMethod.class).path(),
                            baseUrl, fullClassName, method.getName(), Set.of("POST"), mappings);
                }
                // 3. Ancien @UrlMethod → accepte GET + POST (rétrocompatibilité)
                else if (method.isAnnotationPresent(UrlMethod.class)) {
                    UrlMethod ann = method.getAnnotation(UrlMethod.class);
                    registerMapping(method, ann.path(), baseUrl, fullClassName, method.getName(),
                            Set.of("GET", "POST", "HEAD"), mappings);
                }
            }
        } catch (Exception e) {
            System.err.println("Erreur lors du scan de la classe : " + fullClassName);
            e.printStackTrace();
        }
    }

    private static void registerMapping(Method method, String methodPath, String baseUrl,
            String className, String methodName,
            Set<String> httpMethods,
            Map<String, List<InfoUrl>> mappings) {

        String fullUrl = (baseUrl + methodPath).replaceAll("//+", "/");
        if (fullUrl.isEmpty()) {
            fullUrl = "/";
        }
        // Extraction des noms de paramètres {id} → ["id"]
        Pattern p = Pattern.compile("\\{([^/]+)\\}");
        Matcher matcher = p.matcher(fullUrl);
        List<String> paramNames = new ArrayList<>();
        while (matcher.find()) {
            paramNames.add(matcher.group(1));
        }

        String regex = "^" + fullUrl.replaceAll("\\{[^/]+\\}", "([^/]+)") + "$";

        InfoUrl infoUrl = new InfoUrl(className, methodName, regex, paramNames, httpMethods);

        mappings.computeIfAbsent(fullUrl, k -> new ArrayList<>()).add(infoUrl);

        System.out.printf("Mapped: %8s %-30s → %s.%s()%n",
                httpMethods, fullUrl, className.substring(className.lastIndexOf('.') + 1), methodName);
    }
}