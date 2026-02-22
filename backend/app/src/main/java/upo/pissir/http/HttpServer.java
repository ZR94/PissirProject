package upo.pissir.http;

import io.javalin.Javalin;
import upo.pissir.auth.Auth;
import upo.pissir.auth.AuthMiddleware;
import upo.pissir.auth.AuthUser;
import upo.pissir.auth.Role;
import upo.pissir.dto.ErrorResponse;
import upo.pissir.routes.Routes;
import upo.pissir.service.InfrastructureService;
import upo.pissir.service.PaymentService;
import upo.pissir.service.TollQueryService;


public final class HttpServer {
  private HttpServer() {
  }

  public static void start(
      int port,
      InfrastructureService infrastructureService,
      TollQueryService tollQueryService,
      PaymentService paymentService
  ) {
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

    AuthMiddleware.install(app);
    app.exception(Auth.Halt.class, (e, ctx) -> {});
    app.exception(IllegalArgumentException.class, (e, ctx) ->
        ctx.status(400).json(new ErrorResponse("bad_request", e.getMessage())));
    app.exception(IllegalStateException.class, (e, ctx) ->
        ctx.status(409).json(new ErrorResponse("conflict", e.getMessage())));

    app.get("/api/health", ctx -> ctx.json(new Health("ok")));

    app.get("/api/me", ctx -> {
      AuthUser user = AuthMiddleware.requireUser(ctx);
      ctx.json(java.util.Map.of(
          "ok", true,
          "sub", user.sub(),
          "username", user.username(),
          "roles", user.roles()));
    });

    app.get("/api/admin/ping", ctx -> {
      AuthUser user = AuthMiddleware.requireUser(ctx);
      Auth.requireAnyRole(ctx, user, Role.ADMINISTRATOR);
      ctx.json(java.util.Map.of("ok", true, "msg", "admin pong"));
    });

    Routes.register(app, infrastructureService, tollQueryService, paymentService);
    app.start(port);
  }

  private record Health(String status) {
  }
}
