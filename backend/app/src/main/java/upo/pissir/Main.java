package upo.pissir;

import com.zaxxer.hikari.HikariDataSource;
import upo.pissir.db.SchemaInitializer;
import upo.pissir.http.HttpServer;
import upo.pissir.mqtt.MqttConfig;
import upo.pissir.mqtt.MqttListenerService;
import upo.pissir.mqtt.MqttPublisher;
import upo.pissir.repo.FareRepository;
import upo.pissir.repo.TelepassDebtRepository;
import upo.pissir.repo.DeviceRepository;
import upo.pissir.repo.FaultRepository;
import upo.pissir.repo.TollboothRepository;
import upo.pissir.repo.TripRepository;
import upo.pissir.service.InfrastructureService;
import upo.pissir.service.FaultService;
import upo.pissir.service.PaymentService;
import upo.pissir.service.ReportService;
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
        DeviceRepository deviceRepo = new DeviceRepository(ds);
        FaultRepository faultRepo = new FaultRepository(ds);

        InfrastructureService infrastructureService = new InfrastructureService(tollboothRepo, fareRepo, deviceRepo);
        TollQueryService tollQueryService = new TollQueryService(fareRepo);
        PaymentService paymentService = new PaymentService(debtRepo, tripRepo);
        ReportService reportService = new ReportService(tripRepo);
        TollProcessingService processingService = new TollProcessingService(fareRepo, tripRepo, debtRepo, tollboothRepo);
        MqttConfig mqttConfig = MqttConfig.fromEnv();
        MqttPublisher mqttPublisher = new MqttPublisher(mqttConfig);
        FaultService faultService = new FaultService(faultRepo, mqttPublisher);

        // 3) HTTP
        int httpPort = upo.pissir.config.AppConfig.httpPort();
        HttpServer.start(httpPort, infrastructureService, tollQueryService, paymentService, reportService, faultService);

        // 4) MQTT Listener
        mqttPublisher.start();
        MqttListenerService mqtt = new MqttListenerService(mqttConfig, processingService, faultService, mqttPublisher);
        mqtt.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                mqtt.stop();
            } catch (Exception ignored) {}
            try {
                mqttPublisher.stop();
            } catch (Exception ignored) {}
            ds.close();
        }));

        System.out.println("Backend listening on port " + httpPort);
    }
}
