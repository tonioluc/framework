package com.itu;

import java.io.IOException;
import java.io.PrintWriter;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@WebServlet("/")
public class FrontServlet extends HttpServlet {
	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		handleLogic(request, response);
	}

	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		handleLogic(request, response);
	}

	private void handleLogic(HttpServletRequest request, HttpServletResponse response)
			throws IOException, ServletException {
		String resourcePath = request.getRequestURI().substring(request.getContextPath().length());

		try {
			java.net.URL resource = request.getServletContext().getResource(resourcePath);
			if (resource != null) {
				RequestDispatcher dispatcher = getServletContext().getNamedDispatcher("default");
				if (dispatcher != null) {
					dispatcher.forward(request, response);
					return;
				}
			} else {
				showPage(request, response, resourcePath);
			}
		} catch (Exception e) {
			throw new ServletException("Error processing request", e);
		}
	}

	private void showPage(HttpServletRequest request, HttpServletResponse response, String fullUrl)
			throws IOException {
		response.setContentType("text/html");
		PrintWriter out = response.getWriter();
		out.println("<html><head><title>FrontServlet</title></head><body>");
		out.println("<h1>URL demandée : " + fullUrl + "</h1>");
		out.println("<p>Ceci est le FrontServlet. Aucune ressource trouvée pour cette URL.</p>");
		out.println("</body></html>");	
	}

}
