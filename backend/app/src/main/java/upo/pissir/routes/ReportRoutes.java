package upo.pissir.routes;

import io.javalin.Javalin;
import upo.pissir.auth.Auth;
import upo.pissir.auth.AuthMiddleware;
import upo.pissir.auth.Role;
import upo.pissir.service.ReportService;

public final class ReportRoutes {
    private ReportRoutes() {}

    public static void register(Javalin app, ReportService reportService) {
        app.get("/api/reports/trips", ctx -> {
            Auth.requireAnyRole(ctx, AuthMiddleware.requireUser(ctx), Role.EMPLOYEE, Role.ADMINISTRATOR);
            ctx.json(reportService.listTrips(
                    ctx.queryParam("from"),
                    ctx.queryParam("to"),
                    ctx.queryParam("entryTollboothId"),
                    ctx.queryParam("exitTollboothId"),
                    ctx.queryParam("channel"),
                    ctx.queryParam("paid"),
                    ctx.queryParam("limit")
            ));
        });

        app.get("/api/reports/routes", ctx -> {
            Auth.requireAnyRole(ctx, AuthMiddleware.requireUser(ctx), Role.EMPLOYEE, Role.ADMINISTRATOR);
            ctx.json(reportService.listRouteStats(
                    ctx.queryParam("from"),
                    ctx.queryParam("to"),
                    ctx.queryParam("entryTollboothId"),
                    ctx.queryParam("exitTollboothId"),
                    ctx.queryParam("channel"),
                    ctx.queryParam("paid")
            ));
        });

        app.get("/api/reports/active-trips", ctx -> {
            Auth.requireAnyRole(ctx, AuthMiddleware.requireUser(ctx), Role.EMPLOYEE, Role.ADMINISTRATOR);
            ctx.json(reportService.listActiveTrips(
                    ctx.queryParam("entryTollboothId"),
                    ctx.queryParam("channel"),
                    ctx.queryParam("limit")
            ));
        });
    }
}
