package com.example.servlet;

import java.io.*;
import java.util.Map;

import com.example.utils.AnnotationScanner;
import com.example.utils.InfoUrl;

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
        Map<String, InfoUrl> mappings = AnnotationScanner.scan(classesPath , packageName);

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
                    res.setContentType("text/html;charset=UTF-8");
                    PrintWriter out = res.getWriter();

                    out.println("<html>");
                    out.println("<head><title>Résultat du mapping</title></head>");
                    out.println("<body style='font-family: Arial, sans-serif; margin: 20px;'>");
                    out.println("<h2 style='color: green;'> Chemin trouvé : " + path + "</h2>");
                    out.println("<p><strong>Classe :</strong> " + info.getNomClasse() + "</p>");
                    out.println("<p><strong>Méthode :</strong> " + info.getNomMethode() + "</p>");
                    out.println("</body>");
                    out.println("</html>");

                    out.close();
                } else {
                    customServe(req, res);
                }
            }
        }
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