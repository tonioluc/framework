package com.example.servlet;

import java.io.*;
import java.util.Map;
import java.lang.reflect.Method;

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
                    customServe(req, res);
                }
            }
        }
    }

    private void servirUrlTrouvee(HttpServletRequest req, HttpServletResponse res, InfoUrl info) throws IOException, ServletException {
        try {
            // Chargement et instanciation de la classe contrôleur
            Class<?> controllerClass = Class.forName(info.getNomClasse());
            Object controller = controllerClass.getDeclaredConstructor().newInstance();

            // Récupération de la méthode (sans paramètres)
            Method m = controllerClass.getMethod(info.getNomMethode());

            // Invocation de la méthode
            Object result = m.invoke(controller);

            // Si le type de retour est ModelView, on forward vers la page JSP
            if (ModelView.class.isAssignableFrom(m.getReturnType()) && result instanceof ModelView) {
                ModelView modelView = (ModelView) result;
                String viewName = modelView.getViewName();
                if (viewName != null && !viewName.isEmpty()) {
                    // Forward vers la page JSP/HTML
                    String viewPath = "/" + viewName;
                    RequestDispatcher dispatcher = getServletContext().getRequestDispatcher(viewPath);
                    dispatcher.forward(req, res);
                    return;
                }
            }

            // Si le type de retour est String, l'afficher dans la page
            if (m.getReturnType().equals(String.class) && result != null) {
                res.setContentType("text/html;charset=UTF-8");
                try (PrintWriter out = res.getWriter()) {
                    out.println("<html>");
                    out.println("<head><title>Résultat du mapping</title></head>");
                    out.println("<body style='font-family: Arial, sans-serif; margin: 20px;'>");
                    out.println("<h2 style='color: green;'> Chemin trouvé : " + req.getRequestURI() + "</h2>");
                    out.println("<div><strong>Résultat de la méthode :</strong></div>");
                    out.println("<pre>" + escapeHtml(result.toString()) + "</pre>");
                    out.println("</body>");
                    out.println("</html>");
                }
            } else {
                // Sinon afficher les informations actuelles (et la méthode aura quand même été invoquée)
                res.setContentType("text/html;charset=UTF-8");
                try (PrintWriter out = res.getWriter()) {
                    out.println("<html>");
                    out.println("<head><title>Résultat du mapping</title></head>");
                    out.println("<body style='font-family: Arial, sans-serif; margin: 20px;'>");
                    out.println("<h2 style='color: green;'> Chemin trouvé : " + req.getRequestURI() + "</h2>");
                    out.println("<p><strong>Classe :</strong> " + info.getNomClasse() + "</p>");
                    out.println("<p><strong>Méthode :</strong> " + info.getNomMethode() + "</p>");
                    out.println("</body>");
                    out.println("</html>");
                }
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
                // Pour débogage, afficher la pile d'erreur
                StringWriter sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw);
                t.printStackTrace(pw);
                out.println("<pre>" + escapeHtml(sw.toString()) + "</pre>");
                out.println("</body></html>");
            }
        }
    }

    /**
     * Petit helper pour échapper du HTML basique afin d'éviter l'injection lors de l'affichage.
     */
    private String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#x27;");
    }

    private void customServe(HttpServletRequest req, HttpServletResponse res) throws IOException {
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