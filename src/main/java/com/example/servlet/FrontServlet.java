package com.example.servlet;

import java.io.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

        // Scan des annotations
        Map<String, InfoUrl> mappings = AnnotationScanner.scan(classesPath, packageName);

        // Stockage dans le ServletContext
        getServletContext().setAttribute("mappings", mappings);
    }

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        String path = req.getRequestURI().substring(req.getContextPath().length());

        boolean resourceExists = getServletContext().getResource(path) != null;

        Map<String, InfoUrl> mappings = (Map<String, InfoUrl>) getServletContext().getAttribute("mappings");
        if (resourceExists) {
            defaultServe(req, res);
        } else {
            if (mappings != null) {
                InfoUrl info = mappings.get(path);
                if (info != null) {
                    servirUrlTrouvee(req, res, info);
                } else {
                    // pas trouvé exactement → essayer regex
                    InfoUrl matched = null;

                    for (Map.Entry<String, InfoUrl> entry : mappings.entrySet()) {
                        String regex = entry.getValue().getUrlRegex();
                        if (regex != null && path.matches(regex)) {
                            matched = entry.getValue();
                            break;
                        }
                    }

                    if (matched != null) {
                        Matcher matcher = Pattern.compile(matched.getUrlRegex()).matcher(path);
                        if (matcher.matches()) {
                            List<String> names = matched.getParamNames();
                            Map<String, String> values = new HashMap<>();

                            for (int i = 0; i < names.size(); i++) {
                                values.put(names.get(i), matcher.group(i + 1));
                            }

                            // stocker dans la requête
                            req.setAttribute("pathParams", values);
                        }
                        servirUrlTrouvee(req, res, matched);
                    } else {
                        ViewHelper.show404(req, res);
                    }
                }

            } else {
                ViewHelper.show404(req, res);
            }
        }
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
                String defaultParamName = param.getName(); // Nom de l'argument Java par défaut
                String value = null;
                boolean required = true; // Par défaut obligatoire

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