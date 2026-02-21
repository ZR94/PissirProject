package upo.pissir.http;

import io.javalin.Javalin;
import upo.pissir.auth.Auth;
import upo.pissir.auth.AuthUser;


public final class HttpServer {
  private HttpServer() {
  }

  public static void start(int port) {
    Javalin app = Javalin.create(cfg -> {
      cfg.http.defaultContentType = "application/json";
    });

    app.before(ctx -> {
      // origin frontend (Vite di default: 5173)
      String frontendOrigin = System.getenv().getOrDefault("APP_CORS_ORIGIN", "http://localhost:5173");
      ctx.header("Access-Control-Allow-Origin", frontendOrigin);
      ctx.header("Vary", "Origin");
      ctx.header("Access-Control-Allow-Credentials", "true");
      ctx.header("Access-Control-Allow-Headers", "Authorization,Content-Type");
      ctx.header("Access-Control-Allow-Methods", "GET,POST,PUT,DELETE,OPTIONS");
    });

    // Risponde ai preflight OPTIONS
    app.options("/*", ctx -> {
      ctx.status(204);
    });

    app.get("/api/health", ctx -> ctx.json(new Health("ok")));

    app.get("/api/me", ctx -> {
      AuthUser user = Auth.requireAuth(ctx);
      ctx.json(java.util.Map.of(
          "ok", true,
          "sub", user.sub(),
          "username", user.username(),
          "roles", user.roles()));
    });

    app.get("/api/admin/ping", ctx -> {
      AuthUser user = Auth.requireAuth(ctx);
      Auth.requireRole(ctx, user, "admin");
      ctx.json(java.util.Map.of("ok", true, "msg", "admin pong"));
    });

    app.start(port);
  }

  private record Health(String status) {
  }
}