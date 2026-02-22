package upo.pissir;

import com.zaxxer.hikari.HikariDataSource;
import upo.pissir.db.SchemaInitializer;
import upo.pissir.http.HttpServer;
import upo.pissir.mqtt.MqttConfig;
import upo.pissir.mqtt.MqttListenerService;
import upo.pissir.repo.FareRepository;
import upo.pissir.repo.TelepassDebtRepository;
import upo.pissir.repo.TollboothRepository;
import upo.pissir.repo.TripRepository;
import upo.pissir.service.InfrastructureService;
import upo.pissir.service.PaymentService;
import upo.pissir.service.TollQueryService;
import upo.pissir.service.TollProcessingService;
import upo.pissir.db.Db;

public class Main {

    public static void main(String[] args) {
        System.out.println("Pissir backend starting...");

        // 1) DB
        HikariDataSource ds = (HikariDataSource) Db.createDataSource();
        SchemaInitializer.init(ds);

        // 2) Repos + Service
        FareRepository fareRepo = new FareRepository(ds);
        TripRepository tripRepo = new TripRepository(ds);
        TelepassDebtRepository debtRepo = new TelepassDebtRepository(ds);
        TollboothRepository tollboothRepo = new TollboothRepository(ds);

        InfrastructureService infrastructureService = new InfrastructureService(tollboothRepo, fareRepo);
        TollQueryService tollQueryService = new TollQueryService(fareRepo);
        PaymentService paymentService = new PaymentService(debtRepo, tripRepo);
        TollProcessingService processingService = new TollProcessingService(fareRepo, tripRepo, debtRepo);

        // 3) HTTP
        int httpPort = upo.pissir.config.AppConfig.httpPort();
        HttpServer.start(httpPort, infrastructureService, tollQueryService, paymentService);

        // 4) MQTT Listener
        MqttConfig mqttConfig = MqttConfig.fromEnv();
        MqttListenerService mqtt = new MqttListenerService(mqttConfig, processingService);
        mqtt.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                mqtt.stop();
            } catch (Exception ignored) {}
            ds.close();
        }));

        System.out.println("Backend listening on port " + httpPort);
    }
}
