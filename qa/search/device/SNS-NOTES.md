# SNS 자동 분류

- 지원: 스레드(threads.net / threads.com), 인스타그램(instagram.com), 유튜브(youtube.com / youtu.be / youtube-nocookie.com), 기타 웹사이트.
- 링크의 호스트 이름으로 분류하며 정상 하위 도메인도 포함한다.
- 일반 단축 URL(bit.ly 등)의 최종 목적지는 네트워크로 추적하지 않고 기타로 표시한다.
- 기존 링크와 복원한 백업도 즉시 분류. 폴더/메모/DB 스키마 변경 없음.
- 사이트 필터와 폴더 및 검색은 교집합으로 적용한다.
- 사이트 변경 시 다중 선택 종료. 전체 선택은 보이는 결과만 대상으로 한다.
- 카드 날짜 앞에 사이트 분류 표시.

검증: 단위 테스트 8개(분류 신규 3개 포함) 통과, assembleDebug/lintDebug 통과.
실기기: 별도 .searchtest 앱 APK 설치 완료. 휴대폰 잠금으로 UI 테스트 미완료.
잠금 해제 후 BookmarkSourceUiTest, BookmarkBulkTest, BookmarkSearchUiTest 실행 및 화면 캡처 확인 필요.
테스트 기기 애니메이션 설정은 원래 값으로 복구.
테스트 중단 시 남은 합성 테스트 항목은 다음 검증 전에 정리 필요(실사용 Play 앱은 영향 없음).
APK: DWBookMark-sns-test.apk.
스토어 배포/버전 번호 변경은 하지 않음.

도메인 참고: https://about.fb.com/news/2025/04/new-features-threads-web-experience/
