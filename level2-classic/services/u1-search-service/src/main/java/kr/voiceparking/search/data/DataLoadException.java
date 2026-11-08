package kr.voiceparking.search.data;

/** 목업 데이터를 읽거나 검증하지 못했을 때. 기동을 중단시킨다 (NFR2.1). */
public class DataLoadException extends RuntimeException {

    public DataLoadException(String message) {
        super(message);
    }

    public DataLoadException(String message, Throwable cause) {
        super(message, cause);
    }
}
