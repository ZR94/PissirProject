package upo.pissir.routes;

import io.javalin.Javalin;
import upo.pissir.service.InfrastructureService;
import upo.pissir.service.FaultService;
import upo.pissir.service.PaymentService;
import upo.pissir.service.ReportService;
import upo.pissir.service.TollQueryService;

public final class Routes {
    private Routes() {}

    public static void register(
            Javalin app,
            InfrastructureService infrastructureService,
            TollQueryService tollQueryService,
            PaymentService paymentService,
            ReportService reportService,
            FaultService faultService
    ) {
        InfrastructureRoutes.register(app, infrastructureService);
        TollRoutes.register(app, tollQueryService);
        PaymentRoutes.register(app, paymentService);
        ReportRoutes.register(app, reportService);
        FaultRoutes.register(app, faultService);
    }
}
