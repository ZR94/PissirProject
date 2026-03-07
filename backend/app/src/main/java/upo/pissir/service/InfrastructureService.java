package upo.pissir.service;

import upo.pissir.dto.CreateDeviceRequest;
import upo.pissir.dto.CreateFareRequest;
import upo.pissir.dto.CreateTollboothRequest;
import upo.pissir.dto.DeviceResponse;
import upo.pissir.dto.TollboothResponse;
import upo.pissir.dto.UpdateTollboothRequest;
import upo.pissir.repo.FareRepository;
import upo.pissir.repo.DeviceRepository;
import upo.pissir.repo.TollboothRepository;

import java.util.List;

public class InfrastructureService {
    private final TollboothRepository tollboothRepo;
    private final FareRepository fareRepo;
    private final DeviceRepository deviceRepo;

    public InfrastructureService(TollboothRepository tollboothRepo, FareRepository fareRepo) {
        this(tollboothRepo, fareRepo, null);
    }

    public InfrastructureService(TollboothRepository tollboothRepo, FareRepository fareRepo, DeviceRepository deviceRepo) {
        this.tollboothRepo = tollboothRepo;
        this.fareRepo = fareRepo;
        this.deviceRepo = deviceRepo;
    }

    public List<TollboothResponse> listTollbooths() {
        return tollboothRepo.findAll().stream()
                .map(this::toTollboothResponse)
                .toList();
    }

    public void createTollbooth(CreateTollboothRequest req) {
        if (req == null) {
            throw new IllegalArgumentException("request is required");
        }
        String id = normalize(req.id());
        String roadCode = normalize(req.roadCode());
        Double kmMarker = req.kmMarker();
        String region = normalize(req.region());
        String description = normalize(req.description());

        if (id == null || roadCode == null || kmMarker == null) {
            throw new IllegalArgumentException("id, roadCode and kmMarker are required");
        }
        if (kmMarker < 0) {
            throw new IllegalArgumentException("kmMarker must be >= 0");
        }
        if (tollboothRepo.exists(id)) {
            throw new IllegalStateException("tollbooth already exists");
        }
        tollboothRepo.create(id, roadCode, kmMarker, region, description);
        if (deviceRepo != null) {
            deviceRepo.createMandatoryForTollbooth(id);
        }
    }

    public void updateTollbooth(String id, UpdateTollboothRequest req) {
        String normalizedId = normalize(id);
        if (normalizedId == null) {
            throw new IllegalArgumentException("tollboothId is required");
        }
        if (req == null) {
            throw new IllegalArgumentException("request is required");
        }
        String roadCode = normalize(req.roadCode());
        Double kmMarker = req.kmMarker();
        String region = normalize(req.region());
        String description = normalize(req.description());
        if (roadCode == null || kmMarker == null) {
            throw new IllegalArgumentException("roadCode and kmMarker are required");
        }
        if (kmMarker < 0) {
            throw new IllegalArgumentException("kmMarker must be >= 0");
        }
        if (!tollboothRepo.update(normalizedId, roadCode, kmMarker, region, description)) {
            throw new IllegalStateException("tollbooth not found");
        }
    }

    public int deleteTollbooth(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("tollboothId is required");
        }
        if (!tollboothRepo.exists(id)) {
            throw new IllegalStateException("tollbooth not found");
        }
        int removedFares = fareRepo.deleteByTollboothId(id);
        if (!tollboothRepo.delete(id)) {
            throw new IllegalStateException("tollbooth not found");
        }
        return removedFares;
    }

    public List<FareRepository.FareRow> listFares() {
        return fareRepo.findAll();
    }

    public long createFare(CreateFareRequest req) {
        if (req == null || req.entryTollboothId() == null || req.entryTollboothId().isBlank()
                || req.exitTollboothId() == null || req.exitTollboothId().isBlank()
                || req.amountCents() == null) {
            throw new IllegalArgumentException("entryTollboothId, exitTollboothId, amountCents are required");
        }
        if (req.amountCents() < 0) {
            throw new IllegalArgumentException("amountCents must be >= 0");
        }
        return fareRepo.createFare(req.entryTollboothId(), req.exitTollboothId(), req.amountCents());
    }

    public void updateFare(long fareId, Integer amountCents) {
        if (fareId <= 0) {
            throw new IllegalArgumentException("fareId must be positive");
        }
        if (amountCents == null || amountCents < 0) {
            throw new IllegalArgumentException("amountCents must be >= 0");
        }
        if (!fareRepo.updateFare(fareId, amountCents)) {
            throw new IllegalStateException("fare not found");
        }
    }

    public void deleteFare(long fareId) {
        if (fareId <= 0) {
            throw new IllegalArgumentException("fareId must be positive");
        }
        if (!fareRepo.deleteFare(fareId)) {
            throw new IllegalStateException("fare not found");
        }
    }

    public List<DeviceResponse> listDevices(String tollboothId) {
        ensureDeviceRepoConfigured();
        String normalizedTollbooth = normalize(tollboothId);
        return deviceRepo.findAll(normalizedTollbooth).stream()
                .map(this::toDeviceResponse)
                .toList();
    }

    public long createDevice(CreateDeviceRequest req) {
        ensureDeviceRepoConfigured();
        if (req == null) {
            throw new IllegalArgumentException("request is required");
        }
        String tollboothId = normalize(req.tollboothId());
        String direction = normalize(req.direction());
        String channel = normalize(req.channel());
        boolean enabled = req.enabled() == null || req.enabled();

        if (tollboothId == null || direction == null || channel == null) {
            throw new IllegalArgumentException("tollboothId, direction, channel are required");
        }
        if (!tollboothRepo.exists(tollboothId)) {
            throw new IllegalStateException("tollbooth not found");
        }
        if (!"entry".equals(direction) && !"exit".equals(direction)) {
            throw new IllegalArgumentException("direction must be entry or exit");
        }
        if (!"manual".equals(channel) && !"telepass".equals(channel) && !"camera".equals(channel)) {
            throw new IllegalArgumentException("channel must be manual, telepass or camera");
        }
        return deviceRepo.create(tollboothId, direction, channel, enabled);
    }

    public void setDeviceEnabled(long deviceId, Boolean enabled) {
        ensureDeviceRepoConfigured();
        if (deviceId <= 0) {
            throw new IllegalArgumentException("deviceId must be positive");
        }
        if (enabled == null) {
            throw new IllegalArgumentException("enabled is required");
        }
        if (!deviceRepo.updateEnabled(deviceId, enabled)) {
            throw new IllegalStateException("device not found");
        }
    }

    public void deleteDevice(long deviceId) {
        ensureDeviceRepoConfigured();
        if (deviceId <= 0) {
            throw new IllegalArgumentException("deviceId must be positive");
        }
        if (!deviceRepo.delete(deviceId)) {
            throw new IllegalStateException("device not found");
        }
    }

    private void ensureDeviceRepoConfigured() {
        if (deviceRepo == null) {
            throw new IllegalStateException("device repository not configured");
        }
    }

    private static String normalize(String raw) {
        if (raw == null) return null;
        String out = raw.trim();
        return out.isBlank() ? null : out;
    }

    private DeviceResponse toDeviceResponse(DeviceRepository.DeviceRow row) {
        return new DeviceResponse(
                row.id(),
                row.tollboothId(),
                row.direction(),
                row.channel(),
                row.enabled(),
                row.createdAt() == null ? null : row.createdAt().toString()
        );
    }

    private TollboothResponse toTollboothResponse(TollboothRepository.TollboothRow row) {
        return new TollboothResponse(
                row.id(),
                row.roadCode(),
                row.kmMarker(),
                row.region(),
                row.description()
        );
    }
}
