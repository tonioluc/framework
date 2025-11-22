package com.example.servlet;

import java.io.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

import com.example.utils.AnnotationScanner;
import com.example.utils.InfoUrl;
import com.example.utils.ModelView;

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
                        ressourceNonTrouve(req, res);
                    }
                }

            } else {
                ressourceNonTrouve(req, res);
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
            // 1️⃣ Charger la classe du controller
            Class<?> controllerClass = Class.forName(info.getNomClasse());
            Object controller = controllerClass.getDeclaredConstructor().newInstance();

            // 2️⃣ Extraire les paramètres trouvés dans le regex
            Map<String, String> pathParams = (Map<String, String>) req.getAttribute("pathParams");

            // 3️⃣ Trouver la méthode du controller correspondant à info.getNomMethode()
            Method m = null;
            for (Method method : controllerClass.getMethods()) {
                if (method.getName().equals(info.getNomMethode())) {
                    m = method;
                    break;
                }
            }

            if (m == null) {
                throw new NoSuchMethodException("Méthode introuvable: " + info.getNomMethode());
            }

            // Construire les arguments réels pour l'invocation
            Parameter[] params = m.getParameters();
            Object[] realArgs = new Object[params.length];

            for (int i = 0; i < params.length; i++) {
                // On prend les paramètres dans l'ordre
                String rawValue = pathParams.get(info.getParamNames().get(i));
                realArgs[i] = convertValue(rawValue, params[i].getType());
            }

            // Invocation
            Object result = m.invoke(controller, realArgs);

            // Si méthode renvoie ModelView → forward JSP avec données
            if (result instanceof ModelView) {
                ModelView mv = (ModelView) result;

                // Ajouter les données dans request
                if (mv.getData() != null) {
                    for (Map.Entry<String, Object> e : mv.getData().entrySet()) {
                        req.setAttribute(e.getKey(), e.getValue());
                    }
                }

                String view = mv.getViewName();
                if (view != null && !view.isEmpty()) {
                    // S'assure que la vue commence par /
                    String viewPath = view.startsWith("/") ? view : "/" + view;
                    RequestDispatcher dispatcher = getServletContext().getRequestDispatcher(viewPath);
                    dispatcher.forward(req, res);
                    return;
                }
            }

            // Si renvoie une String → l’afficher
            if (result instanceof String) {
                res.setContentType("text/html;charset=UTF-8");
                try (PrintWriter out = res.getWriter()) {
                    out.println("<html>");
                    out.println("<head><title>Résultat du mapping</title></head>");
                    out.println("<body style='font-family: Arial, sans-serif; margin: 20px;'>");
                    out.println("<h2 style='color: green;'> Chemin trouvé : " + req.getRequestURI() + "</h2>");
                    out.println("<pre>" + escapeHtml(result.toString()) + "</pre>");
                    out.println("</body></html>");
                }
                return;
            }

            // Sinon afficher simple retour
            res.setContentType("text/html;charset=UTF-8");
            try (PrintWriter out = res.getWriter()) {
                out.println("<html>");
                out.println("<head><title>Résultat du mapping</title></head>");
                out.println("<body style='font-family: Arial, sans-serif; margin: 20px;'>");
                out.println("<h2 style='color: green;'> Chemin trouvé : " + req.getRequestURI() + "</h2>");
                out.println("<p><strong>Classe :</strong> " + info.getNomClasse() + "</p>");
                out.println("<p><strong>Méthode :</strong> " + info.getNomMethode() + "</p>");
                out.println("</body></html>");
            }

        } catch (ClassNotFoundException e) {
            res.setContentType("text/html;charset=UTF-8");
            try (PrintWriter out = res.getWriter()) {
                out.println("<html><body style='font-family: Arial, sans-serif; margin: 20px;'>");
                out.println("<p style='color:red;'>Classe introuvable: " + info.getNomClasse() + "</p>");
                out.println("</body></html>");
            }

        } catch (NoSuchMethodException e) {
            res.setContentType("text/html;charset=UTF-8");
            try (PrintWriter out = res.getWriter()) {
                out.println("<html><body style='font-family: Arial, sans-serif; margin: 20px;'>");
                out.println("<p style='color:red;'>Méthode introuvable: " + info.getNomMethode() + "</p>");
                out.println("</body></html>");
            }

        } catch (Throwable t) {
            res.setContentType("text/html;charset=UTF-8");
            try (PrintWriter out = res.getWriter()) {
                out.println("<html><body style='font-family: Arial, sans-serif; margin: 20px;'>");
                out.println("<p style='color:red;'>Erreur lors de l'invocation: " + escapeHtml(t.toString()) + "</p>");

                // stack trace visible
                StringWriter sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw);
                t.printStackTrace(pw);
                out.println("<pre>" + escapeHtml(sw.toString()) + "</pre>");

                out.println("</body></html>");
            }
        }
    }

    /**
     * Petit helper pour échapper du HTML basique afin d'éviter l'injection lors de
     * l'affichage.
     */
    private String escapeHtml(String s) {
        if (s == null)
            return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#x27;");
    }

    private void ressourceNonTrouve(HttpServletRequest req, HttpServletResponse res) throws IOException {
        try (PrintWriter out = res.getWriter()) {
            String url = req.getRequestURI();
            res.setContentType("text/html;charset=UTF-8");
            out.println("<html><head><title>FrontServlet</title></head><body>");
            out.println("<h1>URL demandée : " + url + "</h1>");
            out.println("<p>Ceci est le FrontServlet. Aucune ressource trouvée pour cette URL.</p>");
            out.println("</body></html>");
        }
    }

    private void defaultServe(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        defaultDispatcher.forward(req, res);
    }
}