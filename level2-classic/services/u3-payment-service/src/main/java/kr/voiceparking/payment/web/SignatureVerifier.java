package kr.voiceparking.payment.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import kr.voiceparking.payment.payment.ErrorCode;
import kr.voiceparking.payment.payment.PaymentException;
import kr.voiceparking.payment.signature.HmacSigner;

/**
 * 웹훅 서명 검증 (BR3.1, NFR6.1). 헤더가 정확히 {@code sha256=<64자 소문자 hex>}일 때만 원문 바이트의 HMAC-SHA256과 상수
 * 시간으로 비교한다. 거부하면 시각(로그 줄 자체), 본문 길이, 이유(MISSING, MALFORMED, MISMATCH)만 남기고 본문 내용과 서명
 * 값은 남기지 않는다 (NFR6.3).
 */
@Component
public class SignatureVerifier {

    private static final Logger log = LoggerFactory.getLogger(SignatureVerifier.class);

    private final HmacSigner signer;

    public SignatureVerifier(HmacSigner signer) {
        this.signer = signer;
    }

    /** 맞지 않으면 401 INVALID_SIGNATURE 예외를 던진다. */
    public void verify(byte[] body, String header) {
        String reason;
        if (header == null) {
            reason = "MISSING";
        } else if (!HmacSigner.isWellFormed(header)) {
            reason = "MALFORMED";
        } else if (!signer.matches(body, header)) {
            reason = "MISMATCH";
        } else {
            return;
        }
        log.warn("event=webhook_rejected cause=INVALID_SIGNATURE reason={} body_length={}", reason, body.length);
        throw new PaymentException(ErrorCode.INVALID_SIGNATURE);
    }
}
