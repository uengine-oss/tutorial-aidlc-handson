package com.voiceparking.recommendation.api;

import com.voiceparking.recommendation.domain.RecommendationResult;
import com.voiceparking.recommendation.service.RecommendationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RecommendationController {

    public record RecommendRequest(@NotBlank(message = "text는 비어 있을 수 없습니다")
                                   @Size(max = 200, message = "text는 200자 이하여야 합니다") String text) {
    }

    private final RecommendationService service;

    public RecommendationController(RecommendationService service) {
        this.service = service;
    }

    @PostMapping("/api/recommend")
    public RecommendationResult recommend(@Valid @RequestBody RecommendRequest request) {
        return service.recommend(request.text());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst().map(f -> f.getDefaultMessage()).orElse("잘못된 요청입니다");
        return Map.of("message", message);
    }
}
