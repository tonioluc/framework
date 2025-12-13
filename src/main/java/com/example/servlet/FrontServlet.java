package com.example.servlet;

import java.io.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

import com.example.annotation.ParametreRequete;
import com.example.annotation.VariableChemin;
import com.example.utils.AnnotationScanner;
import com.example.utils.InfoUrl;
import com.example.utils.ModelView;
import com.example.utils.ViewHelper;

import jakarta.servlet.*;
import jakarta.servlet.annotation.*;
import jakarta.servlet.http.*;

@WebServlet("/")
public class FrontServlet extends HttpServlet {

    RequestDispatcher defaultDispatcher;

    @Override
    public void init() {
        defaultDispatcher = getServletContext().getNamedDispatcher("default");
        String classesPath = getServletContext().getRealPath("/WEB-INF/classes");
        String packageName = "com.example";

        // Nouveau : Map<String, List<InfoUrl>>
        Map<String, List<InfoUrl>> mappings = AnnotationScanner.scan(classesPath, packageName);

        getServletContext().setAttribute("mappings", mappings);
    }

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        String path = req.getRequestURI().substring(req.getContextPath().length());
        if (path.isEmpty())
            path = "/";

        boolean resourceExists = getServletContext().getResource(path) != null;

        @SuppressWarnings("unchecked")
        Map<String, List<InfoUrl>> mappings = (Map<String, List<InfoUrl>>) getServletContext().getAttribute("mappings");

        if (resourceExists) {
            defaultServe(req, res);
            return;
        }

        if (mappings == null || mappings.isEmpty()) {
            ViewHelper.show404(req, res);
            return;
        }

        String httpMethod = req.getMethod(); // GET, POST, etc.

        // 1. Recherche exacte
        List<InfoUrl> candidates = mappings.get(path);
        InfoUrl selected = selectByHttpMethod(candidates, httpMethod);

        // 2. Si pas trouvé → recherche par regex
        if (selected == null) {
            selected = findByRegex(path, mappings, httpMethod, req);
        }

        // 3. Si toujours pas trouvé
        if (selected == null) {
            // On cherche toutes les méthodes autorisées pour cette URL (exacte ou regex)
            Set<String> allowed = new HashSet<>();

            // Exacte
            if (candidates != null) {
                for (InfoUrl info : candidates) {
                    allowed.addAll(info.getHttpMethods());
                }
            }

            // Regex
            for (Map.Entry<String, List<InfoUrl>> entry : mappings.entrySet()) {
                for (InfoUrl info : entry.getValue()) {
                    if (info.getUrlRegex() != null && path.matches(info.getUrlRegex())) {
                        allowed.addAll(info.getHttpMethods());
                    }
                }
            }

            if (allowed.isEmpty()) {
                ViewHelper.show404(req, res);
            } else {
                ViewHelper.show405(req, res, allowed);
            }
            return;
        }

        // On a trouvé la bonne méthode → on exécute
        servirUrlTrouvee(req, res, selected);
    }

    /**
     * Sélectionne la bonne InfoUrl parmi les candidates selon la méthode HTTP
     */
    private InfoUrl selectByHttpMethod(List<InfoUrl> candidates, String httpMethod) {
        if (candidates == null || candidates.isEmpty())
            return null;
        for (InfoUrl info : candidates) {
            if (info.supportsMethod(httpMethod)) {
                return info;
            }
        }
        return null;
    }

    /**
     * Recherche par expression régulière + méthode HTTP
     */
    private InfoUrl findByRegex(String path, Map<String, List<InfoUrl>> mappings, String httpMethod,
            HttpServletRequest req) {
        for (Map.Entry<String, List<InfoUrl>> entry : mappings.entrySet()) {
            for (InfoUrl info : entry.getValue()) {
                String regex = info.getUrlRegex();
                if (regex != null && path.matches(regex)) {
                    if (info.supportsMethod(httpMethod)) {
                        // Extraction des paramètres de chemin {id}, {name}, etc.
                        Matcher matcher = Pattern.compile(regex).matcher(path);
                        if (matcher.matches()) {
                            List<String> names = info.getParamNames();
                            Map<String, String> pathParams = new HashMap<>();
                            for (int i = 0; i < names.size(); i++) {
                                pathParams.put(names.get(i), matcher.group(i + 1));
                            }
                            req.setAttribute("pathParams", pathParams);
                        }
                        return info;
                    }
                }
            }
        }
        return null;
    }

    private Object convertValue(String value, Class<?> type) {
        if (type.equals(Integer.class) || type.equals(int.class)) {
            return Integer.parseInt(value);
        }
        if (type.equals(Double.class) || type.equals(double.class)) {
            return Double.parseDouble(value);
        }
        return value; // String par défaut
    }

    private void servirUrlTrouvee(HttpServletRequest req, HttpServletResponse res, InfoUrl info)
            throws IOException, ServletException {

        try {
            // 1. Charger la classe du controller
            Class<?> controllerClass = Class.forName(info.getNomClasse());
            Object controller = controllerClass.getDeclaredConstructor().newInstance();

            // 2. Récupérer les path parameters (peuvent être null si URL sans {id})
            @SuppressWarnings("unchecked")
            Map<String, String> pathParams = (Map<String, String>) req.getAttribute("pathParams");
            if (pathParams == null) {
                pathParams = new HashMap<>();
            }

            // 3. Trouver la méthode annotée
            Method method = null;
            for (Method m : controllerClass.getMethods()) {
                if (m.getName().equals(info.getNomMethode())) {
                    method = m;
                    break;
                }
            }
            if (method == null) {
                throw new NoSuchMethodException("Méthode introuvable : " + info.getNomMethode());
            }

            // 4. Résolution des paramètres avec support des annotations
            Parameter[] parameters = method.getParameters();
            Object[] args = new Object[parameters.length];
            for (int i = 0; i < parameters.length; i++) {
                Parameter param = parameters[i];
                Class<?> paramType = param.getType();
                String paramName = param.getName();

                // 1. Cas spécial : Map<String, Object>
                if (Map.class.isAssignableFrom(paramType)) {
                    Type genericType = param.getParameterizedType();
                    if (genericType instanceof ParameterizedType pType) {
                        Type[] argsTypes = pType.getActualTypeArguments();
                        if (argsTypes.length == 2 && argsTypes[0] == String.class && argsTypes[1] == Object.class) {
                            Map<String, Object> allParams = new HashMap<>();
                            req.getParameterMap().forEach((key, values) -> {
                                allParams.put(key, values.length == 1 ? values[0] : values);
                            });
                            args[i] = allParams;
                            continue; // ← Très important
                        }
                    }
                    args[i] = null;
                    continue;
                }

                // 2. Cas objet custom (binding automatique)
                // On vérifie que ce n'est PAS un type primitif, String, Map, Collection,
                // tableau
                boolean isSimpleType = paramType.isPrimitive() ||
                        paramType.equals(String.class) ||
                        Number.class.isAssignableFrom(paramType) ||
                        paramType.equals(Boolean.class) ||
                        Map.class.isAssignableFrom(paramType) ||
                        Collection.class.isAssignableFrom(paramType) ||
                        paramType.isArray();

                if (!isSimpleType && !paramType.getName().startsWith("java.")
                        && !paramType.getName().startsWith("jakarta.")) {
                    // C'est un objet custom du projet
                    try {
                        Object obj = paramType.getDeclaredConstructor().newInstance();
                        String prefix = paramType.getSimpleName() + ".";

                        req.getParameterMap().forEach((key, values) -> {
                            if (key.startsWith(prefix)) {
                                String propertyPath = key.substring(prefix.length());
                                setPropertyByPath(obj, propertyPath, values);
                            }
                        });

                        args[i] = obj;
                        continue; // ← CRUCIAL : on sort pour ne pas retomber dans le fallback
                    } catch (Exception e) {
                        // Si création échoue, on laisse null ou on log
                        args[i] = null;
                    }
                    continue;
                }

                // 3. Cas normaux : @VariableChemin, @ParametreRequete, fallback
                String value = null;
                boolean required = true;

                if (param.isAnnotationPresent(VariableChemin.class)) {
                    VariableChemin ann = param.getAnnotation(VariableChemin.class);
                    paramName = ann.value().isEmpty() ? paramName : ann.value();
                    required = ann.required();
                    value = pathParams.get(paramName);
                } else if (param.isAnnotationPresent(ParametreRequete.class)) {
                    ParametreRequete ann = param.getAnnotation(ParametreRequete.class);
                    paramName = ann.value().isEmpty() ? paramName : ann.value();
                    required = ann.required();
                    value = req.getParameter(paramName);
                } else {
                    value = pathParams.get(paramName);
                    if (value == null)
                        value = req.getParameter(paramName);
                }

                if (value != null && !value.isEmpty()) {
                    args[i] = convertValue(value, paramType);
                } else if (required) {
                    throw new IllegalArgumentException("Paramètre requis manquant : " + paramName);
                } else {
                    args[i] = null;
                }
            }

            // 5. Invocation de la méthode
            Object result = method.invoke(controller, args);

            // 6. Gestion du retour
            if (result instanceof ModelView mv) {
                // Ajout des données dans la request
                if (mv.getData() != null) {
                    mv.getData().forEach(req::setAttribute);
                }

                String view = mv.getViewName();
                if (view != null && !view.isEmpty()) {
                    String viewPath = view.startsWith("/") ? view : "/" + view;
                    getServletContext().getRequestDispatcher(viewPath).forward(req, res);
                    return;
                }
            }

            if (result instanceof String str) {
                ViewHelper.showStringResult(req, res, str);
                return;
            }

            // Retour par défaut (void ou autre objet)
            ViewHelper.showMappingFound(req, res, info);

        } catch (ClassNotFoundException e) {
            ViewHelper.showClassNotFound(res, info.getNomClasse());
        } catch (NoSuchMethodException e) {
            ViewHelper.showMethodNotFound(res, info.getNomMethode());
        } catch (IllegalArgumentException e) {
            ViewHelper.showError(res, "Paramètre invalide ou manquant", e);
        } catch (Exception e) {
            ViewHelper.showError(res, "Erreur lors de l'invocation de la méthode",
                    e.getCause() != null ? e.getCause() : e);
        }
    }

    // Nouvelle méthode helper pour set les properties par path (gère imbriqués et
    // listes)
    private void setPropertyByPath(Object obj, String path, String[] values) {
        String[] parts = path.split("\\.");
        Object current = obj;

        for (int j = 0; j < parts.length - 1; j++) {
            String part = parts[j];
            Field field = getField(current.getClass(), part);
            if (field == null)
                return; // Ignorer si non trouvé

            field.setAccessible(true);
            try {
                Object next = field.get(current);
                if (next == null) {
                    if (List.class.isAssignableFrom(field.getType())) {
                        next = new ArrayList<>();
                        field.set(current, next);
                    } else {
                        next = field.getType().getDeclaredConstructor().newInstance();
                        field.set(current, next);
                    }
                }
                current = next;
            } catch (Exception e) {
                e.printStackTrace();
                return;
            }
        }

        // Dernière partie : set la valeur
        String lastPart = parts[parts.length - 1];
        Field lastField = getField(current.getClass(), lastPart);
        if (lastField == null)
            return;

        lastField.setAccessible(true);
        try {
            Class<?> fieldType = lastField.getType();
            Object val;
            if (values.length == 1) {
                val = convertValue(values[0], fieldType);
            } else {
                if (List.class.isAssignableFrom(fieldType)) {
                    List<Object> list = (List<Object>) lastField.get(current);
                    if (list == null) {
                        list = new ArrayList<>();
                        lastField.set(current, list);
                    }
                    for (String v : values) {
                        list.add(convertValue(v, String.class)); // Assume List<String> pour hobbies
                    }
                    return;
                } else {
                    val = values; // String[]
                }
            }
            lastField.set(current, val);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private Field getField(Class<?> clazz, String name) {
        try {
            return clazz.getDeclaredField(name);
        } catch (NoSuchFieldException e) {
            return null;
        }
    }

    private void defaultServe(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        defaultDispatcher.forward(req, res);
    }
}