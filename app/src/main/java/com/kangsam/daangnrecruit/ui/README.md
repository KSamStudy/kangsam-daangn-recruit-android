# ui — 화면

앱 화면이에요 (Jetpack Compose, Material3). 프로젝트 문서 '화면 설계 v0.7'과 화면 시안 v0.7에 맞췄어요.

## 화면 구성

아래 탭 5개와, 탭 안에서 들어가는 화면이 있어요. 들어가는 화면이 열리면 아래 탭은 숨겨요 (`AppRoot.kt`).

| 탭 | 화면 | 파일 |
|---|---|---|
| 홈 | 앱 상태 · 기능 3개 · 오늘 건수 · 최근 24시간 활동 | `HomeScreen.kt` (한 줄씩 만드는 규칙은 같은 파일의 `HomeSummary`) |
| 지원자 | 스위치 2개 → 공고 칸 → 처리할 것 · 확인 필요 · 넘김 | `ApplicantsScreen.kt`, 개발용 '실제 발송 시험' 칸 `LiveTestPanel.kt` |
| └ 지원자 상세 | 정보 · 날짜별 대화 · 지금 상태와 다음에 앱이 할 일 · 직접 연락하기 · 개발용 드라이런 | `ApplicantDetailScreen.kt` |
| 공고 | 자동 게시 스위치 · 공고 카드 · '마감했어요' · 직접 올린 공고 등록 | `PostsScreen.kt` |
| └ 공고 편집 | 문구 · 게시 규칙 · 켜기 전 확인 · 미리보기 | `PostEditScreen.kt` |
| 확인 | 합치기 확인 · 공고 못 찾음 · 담당자 확인 · 닉네임이 달라 멈춘 가져오기 · 공고 쪽 카드 (탭의 숫자는 `CheckCount.kt` 한 곳) | `CheckScreen.kt` |
| 설정 | 전체 멈춤 · 당근 가져오기 · 계정 · 1차 값 · DB | `SettingsScreen.kt` |
| └ 알림 기록 | 개발용 — 권한·스위치, 오류 기록, 예시 데이터 넣기·지우기, 최근 알림 | `NotificationLogScreen.kt` |
| (탭 대신) | DB를 못 열었을 때 보이는 안내 한 장 | `DbErrorScreen.kt` |

## 고칠 때

- 색·글꼴·아이콘은 `theme/` 한 곳에서만 정해요 — `Palette`(색) · `AppText`(글자 크기·굵기) · `AppIcons`(선 아이콘). 화면 파일에 색 값을 직접 쓰지 않아요.
- 카드·버튼·스위치·필터·입력 칸 같은 공통 부품은 `Components.kt`에 있어요. 지원자 상태 색은 `statusTone` 한 곳에서 정해요.
- 시각·날짜 글자(10/19 14:05, 10월 19일 (월))와 조사 '(으)로·와/과'는 `Format.kt`에 있어요.
- 화면은 DB를 Flow로 읽어 보여 주기만 해요. 기록을 바꾸는 규칙은 화면 밖에 있어요 — 지원자 줄은 `ApplicantStore`, 확인 탭 버튼은 `CheckActions`, 공고 버튼은 `PostActions`.
- 어떤 지원자를 보일지는 `ApplicantView`, 앱이 맡은 지원인지는 `reply/ReplyScope`가 정해요. 화면마다 따로 정하지 않아요.
- 개발 중에만 있는 칸(드라이런 · 실제 발송 시험 · 알림 기록 · 예시 데이터)은 점선 카드 `DevCard`로 표시해요. 운영 전에 숨길 칸이에요.
- 글꼴 파일은 `app/src/main/res/font/`에 있어요 (IBM Plex Sans KR 4굵기, OFL 라이선스 — 앱에 넣어 배포해도 돼요).

## 확인하는 법

화면은 Android Studio로 폰에 깔아서 봐요. 설정 → 알림 기록 (개발용) → '예시 데이터 넣기'로 화면 설계의 예시 상황을 넣어 볼 수 있어요. 예시는 모두 예시 표시가 붙어 있어서 한 번에 지워져요.
