package upo.pissir.service;

import upo.pissir.dto.CreateFareRequest;
import upo.pissir.repo.FareRepository;
import upo.pissir.repo.TollboothRepository;

import java.util.List;

public class InfrastructureService {
    private final TollboothRepository tollboothRepo;
    private final FareRepository fareRepo;

    public InfrastructureService(TollboothRepository tollboothRepo, FareRepository fareRepo) {
        this.tollboothRepo = tollboothRepo;
        this.fareRepo = fareRepo;
    }

    public List<String> listTollbooths() {
        return tollboothRepo.findAll();
    }

    public void createTollbooth(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("tollboothId is required");
        }
        if (tollboothRepo.exists(id)) {
            throw new IllegalStateException("tollbooth already exists");
        }
        tollboothRepo.create(id);
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
}
