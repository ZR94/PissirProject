package upo.pissir.routes;

import io.javalin.Javalin;
import upo.pissir.auth.Auth;
import upo.pissir.auth.AuthMiddleware;
import upo.pissir.auth.Role;
import upo.pissir.dto.CreateFareRequest;
import upo.pissir.dto.CreateTollboothRequest;
import upo.pissir.service.InfrastructureService;

public final class InfrastructureRoutes {
    private InfrastructureRoutes() {}

    public static void register(Javalin app, InfrastructureService service) {
        app.get("/api/infrastructure/tollbooths", ctx -> {
            Auth.requireAnyRole(ctx, AuthMiddleware.requireUser(ctx), Role.EMPLOYEE, Role.ADMINISTRATOR);
            ctx.json(service.listTollbooths());
        });

        app.post("/api/infrastructure/tollbooths", ctx -> {
            Auth.requireAnyRole(ctx, AuthMiddleware.requireUser(ctx), Role.ADMINISTRATOR);
            CreateTollboothRequest req = ctx.bodyAsClass(CreateTollboothRequest.class);
            service.createTollbooth(req.id());
            ctx.status(201).json(req);
        });

        app.get("/api/infrastructure/fares", ctx -> {
            Auth.requireAnyRole(ctx, AuthMiddleware.requireUser(ctx), Role.EMPLOYEE, Role.ADMINISTRATOR);
            ctx.json(service.listFares());
        });

        app.post("/api/infrastructure/fares", ctx -> {
            Auth.requireAnyRole(ctx, AuthMiddleware.requireUser(ctx), Role.ADMINISTRATOR);
            CreateFareRequest req = ctx.bodyAsClass(CreateFareRequest.class);
            long id = service.createFare(req);
            ctx.status(201).json(java.util.Map.of("id", id));
        });
    }
}
