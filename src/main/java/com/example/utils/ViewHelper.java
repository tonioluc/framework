package com.example.utils;

import jakarta.servlet.http.*;
import java.io.*;
import java.util.Map;

public class ViewHelper {

    private static final String CSS = """
        <style>
            body { font-family: Arial, sans-serif; margin: 40px; background: #f8f9fa; color: #333; }
            .container { max-width: 900px; margin: auto; padding: 30px; background: white; border-radius: 10px; box-shadow: 0 4px 12px rgba(0,0,0,0.1); }
            h1, h2 { color: #2c3e50; }
            .success { color: #27ae60; }
            .error { color: #e74b9ff; background: #ffeaa7; padding: 15px; border-radius: 8px; border-left: 5px solid #e74c3c; }
            .info { background: #ecf0f1; padding: 12px; border-radius: 6px; margin: 10px 0; }
            pre { background: #2c3e50; color: #1abc9c; padding: 15px; border-radius: 8px; overflow-x: auto; }
            a { color: #0984e3; text-decoration: none; }
            a:hover { text-decoration: underline; }
        </style>
        """;

    private ViewHelper() {} // classe utilitaire

    public static void renderHtml(HttpServletResponse res, String title, String bodyContent) throws IOException {
        res.setContentType("text/html;charset=UTF-8");
        res.setCharacterEncoding("UTF-8");
        try (PrintWriter out = res.getWriter()) {
            out.println("<!DOCTYPE html>");
            out.println("<html lang=\"fr\">");
            out.println("<head>");
            out.println("<meta charset=\"UTF-8\">");
            out.println("<title>" + escape(title) + "</title>");
            out.println(CSS);
            out.println("</head>");
            out.println("<body>");
            out.println("<div class=\"container\">");
            out.println("<h1>" + escape(title) + "</h1>");
            out.println(bodyContent);
            out.println("</div>");
            out.println("</body>");
            out.println("</html>");
        }
    }

    public static void showMappingFound(HttpServletRequest req, HttpServletResponse res, InfoUrl info) throws IOException {
        String body = """
            <p class="success"><strong>Chemin trouvé :</strong> %s</p>
            <div class="info">
                <p><strong>Classe :</strong> %s</p>
                <p><strong>Méthode :</strong> %s</p>
            </div>
            <p><a href="/test">Retour à l'accueil</a></p>
            """.formatted(
            escape(req.getRequestURI()),
            escape(info.getNomClasse()),
            escape(info.getNomMethode())
        );
        renderHtml(res, "Mapping trouvé", body);
    }

    public static void showStringResult(HttpServletRequest req, HttpServletResponse res, String result) throws IOException {
        String body = """
            <p class="success"><strong>Résultat de la méthode :</strong> %s</p>
            <pre>%s</pre>
            <p><a href="/test">Retour</a></p>
            """.formatted(
            escape(req.getRequestURI()),
            escape(result)
        );
        renderHtml(res, "Résultat String", body);
    }

    public static void showError(HttpServletResponse res, String message, Throwable t) throws IOException {
        String stack = "";
        if (t != null) {
            StringWriter sw = new StringWriter();
            t.printStackTrace(new PrintWriter(sw));
            stack = "<pre>" + escape(sw.toString()) + "</pre>";
        }

        String body = """
            <div class="error">
                <h2>Erreur d'exécution</h2>
                <p><strong>%s</strong></p>
                %s
            </div>
            <p><a href="/test">Retour</a></p>
            """.formatted(escape(message), stack);

        renderHtml(res, "Erreur", body);
    }

    public static void showClassNotFound(HttpServletResponse res, String className) throws IOException {
        showError(res, "Classe introuvable : <code>" + escape(className) + "</code>", null);
    }

    public static void showMethodNotFound(HttpServletResponse res, String methodName) throws IOException {
        showError(res, "Méthode introuvable : <code>" + escape(methodName) + "</code>", null);
    }

    public static void show404(HttpServletRequest req, HttpServletResponse res) throws IOException {
        String body = """
            <h2 class="error">ERREUR 404 - Page non trouvée</h2>
            <p>L'URL demandée n'existe pas :</p>
            <p><code>%s</code></p>
            <hr>
            <p><em>Ceci est géré par FrontServlet. Aucune correspondance dans les annotations @Url.</em></p>
            <p><a href="/test">Retour à l'accueil</a></p>
            """.formatted(escape(req.getRequestURI()));

        renderHtml(res, "404 - Non trouvé", body);
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#x27;");
    }
}