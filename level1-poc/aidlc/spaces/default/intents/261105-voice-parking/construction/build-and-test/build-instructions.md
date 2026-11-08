# 빌드 안내: VoiceParking 프로토타입

근거 문서: `code-generation-plan.md`(계획), `unit-test-instructions.md`(테스트 안내), `code-summary.md`(코드 요약).

## 준비물

- Java 17, Maven 3.9, Node 20 이상 (이 환경에서 확인한 버전: Java 17.0.17, Maven 3.9.12, Node 25.2.1, npm 11.6.2)
- 환경 변수나 외부 서비스는 필요 없습니다. 모든 데이터는 메모리에 있고 결제는 모의입니다.

## 의존성 설치

```bash
npm --prefix frontend install
```

백엔드 의존성은 Maven이 첫 빌드 때 받아 옵니다.

## 빌드 명령

```bash
mvn -q -f backend/pom.xml -DskipTests package
npm --prefix frontend run build
```

- 백엔드는 `backend/<서비스>/target/<서비스>-0.1.0.jar` 세 개를 만듭니다.
- 프런트는 `frontend/dist/`를 만듭니다. 이 폴더와 `target/`, `node_modules/`는 빌드 산출물이므로 버전 관리에서 빼는 것이 좋습니다. (기존 `.gitignore`는 건드리지 않았습니다.)

## 빌드 확인

- 두 명령이 종료 코드 0으로 끝나면 성공입니다.
- 실행 확인: `bash run-all.sh`로 서비스 3개(8081·8082·8083)와 프런트 개발 서버(5173)를 함께 띄웁니다. 시연 순서는 `RUN.md`에 있습니다.

## 자주 만나는 문제

| 증상 | 확인할 것 |
|---|---|
| `Address already in use` | 8081·8082·8083·5173 포트를 쓰는 다른 프로세스가 있는지 확인하고 종료합니다 |
| `release version 17 not supported` | `java -version`이 17 이상인지 확인합니다 |
| 프런트에서 `/api` 호출이 실패 | 백엔드 3개가 모두 떠 있는지 확인합니다. 개발 서버의 프록시가 포트별로 나눠 보냅니다 |
| 결제 오류 응답이 402로 나옴 | 결제 서비스의 정해진 규칙입니다(HTTP 402 + `{code, message}`). `RUN.md`의 코드 목록을 확인합니다 |
