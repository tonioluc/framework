package com.example.servlet;

import java.io.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

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
        if (candidates == null || candidates.isEmpty()) return null;
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
    private InfoUrl findByRegex(String path, Map<String, List<InfoUrl>> mappings, String httpMethod, HttpServletRequest req) {
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
                String defaultParamName = param.getName(); // Nom de l'argument Java par défaut
                String value = null;
                boolean required = true; // Par défaut obligatoire

                if (Map.class.isAssignableFrom(paramType)) {
                    // Injection d'une Map pour tous les paramètres
                    Map<String, Object> allParams = new HashMap<>();
                    req.getParameterMap().forEach((key, values) -> {
                        if (values.length == 1) {
                            allParams.put(key, values[0]);
                        } else {
                            allParams.put(key, values);
                        }
                    });
                    args[i] = allParams;
                    continue;
                }

                // Check @VariableChemin
                if (param.isAnnotationPresent(VariableChemin.class)) {
                    VariableChemin pv = param.getAnnotation(VariableChemin.class);
                    String paramName = pv.value().isEmpty() ? defaultParamName : pv.value();
                    required = pv.required();
                    value = pathParams.get(paramName); // Cherche seulement dans pathParams
                }
                // Check @ParametreRequete
                else if (param.isAnnotationPresent(ParametreRequete.class)) {
                    ParametreRequete rp = param.getAnnotation(ParametreRequete.class);
                    String paramName = rp.value().isEmpty() ? defaultParamName : rp.value();
                    required = rp.required();
                    value = req.getParameter(paramName); // Cherche seulement dans query/form params
                }
                // Fallback : comportement actuel (path > query)
                else {
                    String paramName = defaultParamName;
                    value = pathParams.get(paramName); // Priorité path
                    if (value == null) {
                        value = req.getParameter(paramName); // Puis query/form
                    }
                }

                if (value != null) {
                    args[i] = convertValue(value, param.getType());
                } else {
                    // Paramètre manquant
                    if (required) {
                        if (param.getType().isPrimitive()) {
                            throw new IllegalArgumentException(
                                    "Paramètre obligatoire manquant : " + defaultParamName +
                                            " (type primitif " + param.getType().getSimpleName() + ")");
                        } else {
                            throw new IllegalArgumentException("Paramètre obligatoire manquant : " + defaultParamName);
                        }
                    }
                    args[i] = null; // OK pour types non-primitifs si non required
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

    private void defaultServe(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        defaultDispatcher.forward(req, res);
    }
}