package upo.pissir.routes;

import io.javalin.Javalin;
import upo.pissir.auth.Auth;
import upo.pissir.auth.AuthMiddleware;
import upo.pissir.auth.Role;
import upo.pissir.dto.FaultReplyRequest;
import upo.pissir.service.FaultService;

public final class FaultRoutes {
    private FaultRoutes() {}

    public static void register(Javalin app, FaultService faultService) {
        app.get("/api/faults", ctx -> {
            Auth.requireAnyRole(ctx, AuthMiddleware.requireUser(ctx), Role.EMPLOYEE, Role.ADMINISTRATOR);
            ctx.json(faultService.listFaults());
        });

        app.post("/api/faults/{faultId}/respond", ctx -> {
            Auth.requireAnyRole(ctx, AuthMiddleware.requireUser(ctx), Role.ADMINISTRATOR);
            long faultId = ctx.pathParamAsClass("faultId", Long.class).get();
            FaultReplyRequest req = ctx.bodyAsClass(FaultReplyRequest.class);
            ctx.json(faultService.respondToFault(faultId, req));
        });
    }
}
