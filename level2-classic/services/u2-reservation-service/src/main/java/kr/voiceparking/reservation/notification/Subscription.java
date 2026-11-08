package kr.voiceparking.reservation.notification;

/** 구독 하나의 손잡이. 클라이언트가 끊었거나 연결이 끝났을 때 구독을 제거한다. */
public interface Subscription {

    /** 구독을 제거한다. 여러 번 불러도 한 번만 제거된다. 채널을 다시 닫지는 않는다. */
    void detach(String cause);
}
