package com.appbazaar;

import com.appbazaar.model.*;
import com.appbazaar.repo.*;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Makes sure the admin account exists. On a fresh database it also adds demo sellers and listings (SEED_DEMO_DATA=false turns that off). */
@Component
public class DataSeeder implements CommandLineRunner {
    private final UserRepository users;
    private final ProductRepository products;
    private final PasswordEncoder encoder;
    private final String seedPassword;
    private final boolean seedDemo;

    public DataSeeder(UserRepository users, ProductRepository products, PasswordEncoder encoder,
                      @Value("${app.admin-password}") String seedPassword,
                      @Value("${app.seed-demo-data}") boolean seedDemo) {
        this.users = users; this.products = products; this.encoder = encoder;
        this.seedPassword = seedPassword; this.seedDemo = seedDemo;
    }

    private User user(String name, String email, Role role) {
        User u = new User();
        u.setName(name); u.setEmail(email); u.setRole(role);
        u.setPasswordHash(encoder.encode(seedPassword));
        return users.save(u);
    }

    private void product(User s, String n, String c, int price, double r, String d) {
        Product p = new Product();
        p.setName(n); p.setCategory(c); p.setPrice(price); p.setRating(r); p.setDescription(d);
        p.setSeller(s); p.setStatus(Status.LIVE);
        products.save(p);
    }

    @Override
    public void run(String... args) {
        if (users.findByEmail("admin@appbazaar.com").isEmpty()) user("Admin", "admin@appbazaar.com", Role.ADMIN);
        if (!seedDemo) return;
        if (users.count() <= 1) seedBase(); // demo sellers and products only on a fresh database
        seedProjects();
    }

    private void seedBase() {
        User kavya = user("Kavya Labs", "kavya@appbazaar.com", Role.SELLER);
        User ravi = user("Ravi Dev", "ravi@appbazaar.com", Role.SELLER);
        User pixel = user("PixelNest", "pixel@appbazaar.com", Role.SELLER);
        User neural = user("Neural Nook", "neural@appbazaar.com", Role.SELLER);
        User meera = user("Studio Meera", "meera@appbazaar.com", Role.SELLER);
        product(kavya, "InvoiceFlow Pro", "Software", 19, 4.7, "Desktop invoicing and GST reports for freelancers. Lifetime licence, free updates for a year.");
        product(ravi, "FitTrack Flutter Kit", "Apps", 39, 4.5, "Complete Flutter fitness app source with onboarding, charts and Firebase sign-in.");
        product(pixel, "Shopify-style Storefront", "Website templates", 10, 4.8, "Responsive e-commerce template in React and Tailwind, with dark mode built in.");
        product(neural, "PromptVault AI", "AI tools", 25, 4.4, "Organise, version and share prompts across your team. Includes API access.");
        product(meera, "Landing Page Makeover", "Services", 45, 4.9, "A designer rebuilds your landing page in 5 days. Two revision rounds included.");
        product(kavya, "Spring Boot SaaS Starter", "Software", 32, 4.6, "Java 21 starter with JWT auth, roles, Flyway migrations and Docker files.");
        product(neural, "ChatSupport Bot", "AI tools", 35, 4.3, "Train a support bot on your docs and embed it with one script tag.");
        product(pixel, "Portfolio Pack (12 themes)", "Website templates", 12, 4.7, "Twelve clean portfolio layouts in plain HTML and CSS. No build step.");
    }

    /** Adds sample college and mini project listings once (also on databases created before these categories existed). */
    private void seedProjects() {
        if (products.countByCategory("College projects") == 0 && products.countByCategory("Mini projects") == 0) createSampleProjects();
        fillStacks();
    }

    private void createSampleProjects() {
        User s = users.findByEmail("projects@appbazaar.com").orElseGet(() -> user("CampusBuild", "projects@appbazaar.com", Role.SELLER));
        String c = "College projects", m = "Mini projects";
        product(s, "Online Examination System", c, 29, 4.6, "Timed MCQ tests with automatic evaluation and result analytics. Java, Spring Boot, React and MySQL.");
        product(s, "Hospital Management System", c, 35, 4.5, "Patients, doctors, appointments and billing in one place. Java, Spring Boot and PostgreSQL.");
        product(s, "Student Result and Attendance Portal", c, 25, 4.4, "Teachers upload marks and attendance, students view reports and shortage alerts. Spring Boot and React.");
        product(s, "E-commerce Store with Payments", c, 45, 4.8, "Cart, orders, admin panel and online payment. Spring Boot, React and PostgreSQL.");
        product(s, "Face Recognition Attendance", c, 39, 4.7, "Marks attendance by recognising faces from a camera feed. Python, OpenCV and Flask.");
        product(s, "Library Management System", c, 19, 4.3, "Desktop app to issue and return books and track fines. Java, JavaFX and SQLite.");
        product(s, "Expense Tracker", m, 5, 4.5, "Add income and expenses and see where the money goes with a simple chart. HTML, CSS and JavaScript.");
        product(s, "Quiz App", m, 6, 4.4, "Multiple-choice quiz with timer, score and result screen. React.");
        product(s, "URL Shortener", m, 9, 4.6, "Turns long links into short codes and counts clicks. Java, Spring Boot and H2.");
        product(s, "To-Do App with Login", m, 10, 4.5, "Each user logs in and manages their own tasks. Spring Boot, React and JWT.");
        product(s, "Chat Room", m, 12, 4.7, "Real-time group chat over WebSocket. Java and Spring Boot.");
        product(s, "Weather Dashboard", m, 5, 4.3, "Search a city and see current weather and a five-day forecast. JavaScript and a weather API.");
    }

    private static final Map<String, String> STACKS = Map.ofEntries(
        Map.entry("Online Examination System", "Java, Spring Boot, React, MySQL"),
        Map.entry("Hospital Management System", "Java, Spring Boot, PostgreSQL"),
        Map.entry("Student Result and Attendance Portal", "Spring Boot, React"),
        Map.entry("E-commerce Store with Payments", "Spring Boot, React, PostgreSQL"),
        Map.entry("Face Recognition Attendance", "Python, OpenCV, Flask"),
        Map.entry("Library Management System", "Java, JavaFX, SQLite"),
        Map.entry("Expense Tracker", "HTML, CSS, JavaScript"),
        Map.entry("Quiz App", "React"),
        Map.entry("URL Shortener", "Java, Spring Boot, H2"),
        Map.entry("To-Do App with Login", "Spring Boot, React, JWT"),
        Map.entry("Chat Room", "Java, Spring Boot, WebSocket"),
        Map.entry("Weather Dashboard", "JavaScript, Weather API"));

    /** Adds the tech stack to the sample project listings that were created before that field existed. */
    private void fillStacks() {
        for (Product p : products.findAll()) {
            String stack = STACKS.get(p.getName());
            if (stack != null && p.getTechStack() == null) {
                p.setTechStack(stack);
                products.save(p);
            }
        }
    }
}
