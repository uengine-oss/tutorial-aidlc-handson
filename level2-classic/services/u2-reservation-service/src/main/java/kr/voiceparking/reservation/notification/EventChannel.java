package kr.voiceparking.reservation.notification;

import java.io.IOException;

/**
 * 구독 연결 하나로 쓰는 출구. 실제 구현은 SseEmitter를 감싸고 시험은 가짜를 쓴다.
 * 쓰기는 연결별 배출 작업 하나만 부르므로 같은 연결에서 두 쓰기가 겹치지 않는다.
 */
public interface EventChannel {

    /** 이벤트 이름 snapshot, id = version, 데이터 = 스냅샷 JSON으로 쓴다. */
    void sendSnapshot(Snapshot snapshot) throws IOException;

    /** SSE 주석 줄(": keep-alive")을 쓴다. 이벤트가 아니므로 화면이 처리하지 않는다 (BR4.5). */
    void sendKeepAlive() throws IOException;

    /** 서버가 연결을 닫는다. 쓰기가 막혀 있어도 호출 스레드를 붙잡지 않도록 알림이 별도 스레드에서 부른다. */
    void close(String cause);
}
