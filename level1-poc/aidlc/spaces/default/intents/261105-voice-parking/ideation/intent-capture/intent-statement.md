# 의도 정리: VoiceParking 프로토타입

## Problem Statement

- 사용자가 "강남역 근처 2시간 주차권 찾아줘" 같은 음성 텍스트를 보내면, 조건에 맞는 주차권 중 최적의 것을 추천하고 결제와 예약까지 한 흐름으로 처리해 주는 서비스가 필요하다. [desc]
- 결제가 끝나면 사용자에게 예약 완료 상태를 바로 보여 주어야 한다. [desc]

## Target Customer

- 주 사용자는 운전 중이거나 손이 자유롭지 않아 음성으로 빠르게 주차장을 찾고 싶은 일반 운전자다. [Q2]

## Success Metrics

- 데모 시나리오에서 추천된 주차권이 여러 후보 중 왜 "최적"인지 설명이 화면에 함께 표시된다. [Q1]
- 데모 시나리오에서 결제가 끝나는 즉시 예약 완료 화면이 사용자에게 표시된다. [desc]

## Initiative Trigger

- 이번 요청은 VoiceParking 프로토타입을 만들어 달라는 것이며, 그 밖의 시작 계기는 답변에서 확인되지 않았다. [desc]

## Initial Scope Signal

- 워크플로 선택 범위: `poc` (workflow-selected). [scope]
- 사용자가 확인한 첫 버전의 범위 밖: 실제 결제대행사(PG) 연동, 실제 주차장 데이터 연동, 진짜 음성 인식, 계정 관리. [Q3]

## Assumptions & Open Questions

None.
