package upo.pissir.routes;

import io.javalin.Javalin;
import upo.pissir.auth.Auth;
import upo.pissir.auth.AuthMiddleware;
import upo.pissir.auth.Role;
import upo.pissir.dto.CreateDeviceRequest;
import upo.pissir.dto.CreateFareRequest;
import upo.pissir.dto.CreateTollboothRequest;
import upo.pissir.dto.UpdateDeviceEnabledRequest;
import upo.pissir.dto.UpdateFareRequest;
import upo.pissir.dto.UpdateTollboothRequest;
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
            service.createTollbooth(req);
            ctx.status(201).json(req);
        });

        app.put("/api/infrastructure/tollbooths/{tollboothId}", ctx -> {
            Auth.requireAnyRole(ctx, AuthMiddleware.requireUser(ctx), Role.ADMINISTRATOR);
            String tollboothId = ctx.pathParam("tollboothId");
            UpdateTollboothRequest req = ctx.bodyAsClass(UpdateTollboothRequest.class);
            service.updateTollbooth(tollboothId, req);
            ctx.json(java.util.Map.of("id", tollboothId, "updated", true));
        });

        app.delete("/api/infrastructure/tollbooths/{tollboothId}", ctx -> {
            Auth.requireAnyRole(ctx, AuthMiddleware.requireUser(ctx), Role.ADMINISTRATOR);
            String tollboothId = ctx.pathParam("tollboothId");
            int removedFares = service.deleteTollbooth(tollboothId);
            ctx.json(java.util.Map.of("id", tollboothId, "deleted", true, "removedFares", removedFares));
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

        app.put("/api/infrastructure/fares/{fareId}", ctx -> {
            Auth.requireAnyRole(ctx, AuthMiddleware.requireUser(ctx), Role.ADMINISTRATOR);
            long fareId = ctx.pathParamAsClass("fareId", Long.class).get();
            UpdateFareRequest req = ctx.bodyAsClass(UpdateFareRequest.class);
            service.updateFare(fareId, req.amountCents());
            ctx.json(java.util.Map.of("id", fareId, "amountCents", req.amountCents(), "updated", true));
        });

        app.delete("/api/infrastructure/fares/{fareId}", ctx -> {
            Auth.requireAnyRole(ctx, AuthMiddleware.requireUser(ctx), Role.ADMINISTRATOR);
            long fareId = ctx.pathParamAsClass("fareId", Long.class).get();
            service.deleteFare(fareId);
            ctx.status(204);
        });

        app.get("/api/infrastructure/devices", ctx -> {
            Auth.requireAnyRole(ctx, AuthMiddleware.requireUser(ctx), Role.EMPLOYEE, Role.ADMINISTRATOR);
            ctx.json(service.listDevices(ctx.queryParam("tollboothId")));
        });

        app.post("/api/infrastructure/devices", ctx -> {
            Auth.requireAnyRole(ctx, AuthMiddleware.requireUser(ctx), Role.ADMINISTRATOR);
            CreateDeviceRequest req = ctx.bodyAsClass(CreateDeviceRequest.class);
            long id = service.createDevice(req);
            ctx.status(201).json(java.util.Map.of("id", id));
        });

        app.put("/api/infrastructure/devices/{deviceId}/enabled", ctx -> {
            Auth.requireAnyRole(ctx, AuthMiddleware.requireUser(ctx), Role.ADMINISTRATOR);
            long deviceId = ctx.pathParamAsClass("deviceId", Long.class).get();
            UpdateDeviceEnabledRequest req = ctx.bodyAsClass(UpdateDeviceEnabledRequest.class);
            service.setDeviceEnabled(deviceId, req.enabled());
            ctx.json(java.util.Map.of("id", deviceId, "enabled", req.enabled(), "updated", true));
        });

        app.delete("/api/infrastructure/devices/{deviceId}", ctx -> {
            Auth.requireAnyRole(ctx, AuthMiddleware.requireUser(ctx), Role.ADMINISTRATOR);
            long deviceId = ctx.pathParamAsClass("deviceId", Long.class).get();
            service.deleteDevice(deviceId);
            ctx.status(204);
        });
    }
}
