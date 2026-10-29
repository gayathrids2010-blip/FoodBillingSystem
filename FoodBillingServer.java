
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class FoodBillingServer {

    static String readFile(String file) throws IOException {
        return Files.readString(Path.of(file));
    }

    static int getQuantity(String data, String name) {
        for (String part : data.split("&")) {
            String[] pair = part.split("=", 2);

            if (URLDecoder.decode(pair[0],
                    StandardCharsets.UTF_8).equals(name)) {
                int quantity = Integer.parseInt(
                    URLDecoder.decode(
                        pair.length > 1 ? pair[1] : "",
                        StandardCharsets.UTF_8));

                if (quantity < 0 || quantity > 100) {
                    throw new IllegalArgumentException();
                }

                return quantity;
            }
        }
        return 0;
    }

    static void send(HttpExchange exchange, String html)
            throws IOException {
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set(
            "Content-Type", "text/html; charset=UTF-8");
        exchange.sendResponseHeaders(200, bytes.length);

        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    public static void main(String[] args) throws Exception {

        HttpServer server = HttpServer.create(
            new InetSocketAddress(8080), 0);

        server.createContext("/", exchange -> {
            try {
                if (exchange.getRequestMethod().equals("GET")) {
                    send(exchange, readFile("index.html"));
                } else {
                    send(exchange, "<h2>Method not supported</h2>");
                }
            } catch (IOException e) {
                send(exchange, "<h2>Could not open index.html</h2>");
            } finally {
                exchange.close();
            }
        });

        server.createContext("/bill", exchange -> {
            try {
                if (!exchange.getRequestMethod().equals("POST")) {
                    send(exchange, "<h2>Please submit the food form.</h2>");
                    return;
                }

                String data;
                try (InputStream in = exchange.getRequestBody()) {
                    data = new String(in.readAllBytes(),
                            StandardCharsets.UTF_8);
                }

                int burger = getQuantity(data, "burger");
                int pizza = getQuantity(data, "pizza");
                int juice = getQuantity(data, "juice");

                double subtotal = burger * 100
                        + pizza * 180 + juice * 50;
                double gst = subtotal * 0.05;
                double total = subtotal + gst;

                String html = """
                    <!DOCTYPE html>
                    <html lang="en">
                    <head>
                      <meta charset="UTF-8">
                      <title>Customer Bill</title>
                      <style>
                        body { font-family: Arial; background: #f4f4f4;
                               padding: 30px; }
                        .bill { max-width: 500px; margin: auto;
                                background: white; padding: 25px;
                                border-radius: 10px; }
                        h1 { color: #d35400; }
                        button { padding: 10px; margin: 8px 5px 0 0; }
                      </style>
                    </head>
                    <body>
                    <div class="bill">
                      <h1>Food Billing System</h1>
                      <h2>Your Bill</h2>
                      <p>Burger: %d x Rs.100 = Rs. %.2f</p>
                      <p>Pizza: %d x Rs.180 = Rs. %.2f</p>
                      <p>Juice: %d x Rs.50 = Rs. %.2f</p>
                      <hr>
                      <p>Subtotal: Rs. %.2f</p>
                      <p>GST (5%% demo rate): Rs. %.2f</p>
                      <h2>Grand Total: Rs. %.2f</h2>
                      <button onclick="window.print()">Print Bill</button>
                      <button onclick="location.href='/'">New Bill</button>
                    </div>
                    </body>
                    </html>
                    """.formatted(
                        burger, burger * 100.0,
                        pizza, pizza * 180.0,
                        juice, juice * 50.0,
                        subtotal, gst, total);

                send(exchange, html);

            } catch (IllegalArgumentException e) {
                send(exchange,
                    "<h2>Invalid quantity. Enter a whole number from 0 to 100.</h2>"
                    + "<a href='/'>Go back</a>");
            } catch (Exception e) {
                send(exchange,
                    "<h2>Unable to generate bill.</h2>"
                    + "<a href='/'>Go back</a>");
            } finally {
                exchange.close();
            }
        });

        server.start();
        System.out.println("Food Billing Website is running!");
        System.out.println("Open http://localhost:8080");
    }
}
