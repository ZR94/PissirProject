package upo.pissir.service;

import upo.pissir.dto.TollCalculateResponse;
import upo.pissir.repo.FareRepository;

public class TollQueryService {
    private final FareRepository fareRepo;

    public TollQueryService(FareRepository fareRepo) {
        this.fareRepo = fareRepo;
    }

    public TollCalculateResponse calculate(String entry, String exit) {
        if (entry == null || entry.isBlank() || exit == null || exit.isBlank()) {
            throw new IllegalArgumentException("entry and exit are required");
        }

        Integer cents = fareRepo.findFareCents(entry, exit);
        if (cents == null) {
            throw new IllegalStateException("fare not found");
        }

        return new TollCalculateResponse(entry, exit, cents, "EUR");
    }
}
