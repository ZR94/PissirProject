package upo.pissir.routes;

import io.javalin.Javalin;
import upo.pissir.auth.Auth;
import upo.pissir.auth.AuthMiddleware;
import upo.pissir.auth.Role;
import upo.pissir.service.TollQueryService;

public final class TollRoutes {
    private TollRoutes() {}

    public static void register(Javalin app, TollQueryService service) {
        app.get("/api/toll/calculate", ctx -> {
            Auth.requireAnyRole(ctx, AuthMiddleware.requireUser(ctx), Role.EMPLOYEE, Role.ADMINISTRATOR);
            String entry = ctx.queryParam("entry");
            String exit = ctx.queryParam("exit");
            ctx.json(service.calculate(entry, exit));
        });
    }
}
