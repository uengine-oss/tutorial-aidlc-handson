package kr.voiceparking.search.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import kr.voiceparking.search.service.ErrorCode;
import kr.voiceparking.search.service.PassInfo;
import kr.voiceparking.search.service.PassLookupService;
import kr.voiceparking.search.service.SearchException;
import kr.voiceparking.search.web.dto.PassResponse;

/** C4 주차권 조회: GET /internal/passes/{passId}?durationHours=. 예약 서비스만 직접 호출한다. */
@RestController
public class InternalPassController {

    private final PassLookupService passLookupService;

    public InternalPassController(PassLookupService passLookupService) {
        this.passLookupService = passLookupService;
    }

    @GetMapping("/internal/passes/{passId}")
    public PassResponse lookup(@PathVariable String passId,
            @RequestParam(name = "durationHours", required = false) String durationHours) {
        PassInfo info = passLookupService.lookup(passId, parseDuration(durationHours));
        return new PassResponse(info.passId(), info.lotName(), info.durationHours(), info.price());
    }

    /** 정수가 아니거나 없으면 INVALID_DURATION. 범위(1~24)는 서비스가 확인한다. */
    private static int parseDuration(String raw) {
        if (raw == null) {
            throw new SearchException(ErrorCode.INVALID_DURATION);
        }
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            throw new SearchException(ErrorCode.INVALID_DURATION);
        }
    }
}
