package upo.pissir.routes;

import io.javalin.Javalin;
import upo.pissir.auth.Auth;
import upo.pissir.auth.AuthMiddleware;
import upo.pissir.auth.Role;
import upo.pissir.service.PaymentService;

public final class PaymentRoutes {
    private PaymentRoutes() {}

    public static void register(Javalin app, PaymentService paymentService) {
        app.get("/api/payments/telepass/{telepassId}/debts", ctx -> {
            Auth.requireAnyRole(ctx, AuthMiddleware.requireUser(ctx), Role.CUSTOMER, Role.EMPLOYEE, Role.ADMINISTRATOR);
            String telepassId = ctx.pathParam("telepassId");
            ctx.json(paymentService.listDebtsByTelepass(telepassId));
        });

        app.post("/api/payments/debts/{debtId}/pay", ctx -> {
            Auth.requireAnyRole(ctx, AuthMiddleware.requireUser(ctx), Role.CUSTOMER, Role.EMPLOYEE, Role.ADMINISTRATOR);
            long debtId = ctx.pathParamAsClass("debtId", Long.class).get();
            ctx.json(paymentService.payDebt(debtId));
        });

        app.get("/api/payments/summary", ctx -> {
            Auth.requireAnyRole(ctx, AuthMiddleware.requireUser(ctx), Role.EMPLOYEE, Role.ADMINISTRATOR);
            ctx.json(paymentService.summary());
        });
    }
}
