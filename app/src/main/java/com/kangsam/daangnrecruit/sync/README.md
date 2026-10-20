# sync — C. 당근 가져오기

당근 화면을 읽어서 앱 기록(계정·공고·지원자·대화)을 당근과 맞추는 기능이에요 (사용자 프로세스 C). 당근에 무언가를 쓰지는 않고, 화면을 옮기는 칸만 눌러요. 지원 감지(알림)·지원자 응대(`reply/`)와 상관없이 혼자 돌아요.

## 언제 도는지

- 설정의 '당근 가져오기' 스위치가 켜져 있을 때만 돌아요. 전체 멈춤이면 안 돌아요.
- '당근에서 가져오기' 버튼을 누르면 한 번 돌아요 (하루 10번까지 — `DaangnSync.MAX_RUNS_PER_DAY`).
- 사람이 당근에서 공고 상세·지원자 목록·채팅방을 열어도 그 화면을 읽어 맞춰요 (`ScreenImport` — 접근성 서비스가 화면이 바뀔 때 부름). 앱이 당근을 다루는 동안에는 쉬어요.
- 나의 당근 닉네임이 앱 계정과 다르면 가져오기와 사람이 연 화면 맞추기를 멈추고 폰 알림을 띄워요. 같은 계정인지는 사람이 설정에서 정해요.

## 한 번 돌 때 (`SyncRunner`)

1. 당근을 열고 아래 탭이 있는 첫 화면까지 뒤로 가요.
2. 나의 당근: 닉네임으로 계정을 맞추고 (`MyPageParser` → `AccountSync`), 진행중인 구인 공고 카드를 찾아요.
3. 공고마다: 공고 상세 (`MyPostParser` → `PostImport`·`PostRounds` — 회차·마감) → 지원자 보기 (`ApplicantListParser` → `ApplicantImport`) → 뒤로.
4. 채팅 탭: 목록을 맨 위로 올린 뒤 아래로 넘기며 앱에 있는 지원자의 방을 찾아요. 앱 지원자가 지원하기 전 방만 남으면 거기서 그만 봐요.
5. 읽은 방만 열어 대화를 읽어요 (`ChatRoomParser` → `ChatImport`). 안 읽은 방은 열지 않아요 — 열면 상대에게 읽음으로 보이거든요. 새 메시지는 알림으로 받아요 (`ChatNotify`).
6. 채팅 목록을 맨 위로 돌려놓고 우리 앱으로 돌아와요. 한 줄 결과는 설정의 '지난 실행 기록'에 남아요.

## 지원자 상태를 맞추는 규칙 (`ApplicantImport`)

- 안 읽은 카드('N') → '새 지원'. 읽은 카드 → '담당자 응대 중' (담당자가 이미 지원서를 본 사람이라 앱이 먼저 연락하지 않게).
- 단, 앱이 지원서를 열어서 읽은 카드가 된 사람(사유가 '앱이 지원서를 엶' — 드라이런·실제 발송 시험)은 '새 지원' 그대로 둬요.
- 그 글의 당근 목록에서 빠진 사람은 '종료'로 둬요 (안 읽은 지원서 만료·'다음 기회에'). 다시 보이면 되돌려요. 막 지원한 사람(30분 안)은 그대로 둬요.

## 파일 나눔

| 하는 일 | 파일 |
|---|---|
| 입구 (스위치·버튼·지난 실행 기록·닉네임 다름) | `DaangnSync.kt` |
| 한 번 도는 순서 | `SyncRunner.kt` |
| 길 찾기: 어느 화면인지 · 누를 칸 · **누르면 안 되는 칸** | `DaangnNav.kt` (순수 코드) |
| 손: 열기·누르기·뒤로·스크롤·기다리기·멈춤 | `DaangnWalker.kt` ← `ScreenDriver.kt` (폰에선 접근성 서비스, 시험에선 가짜) |
| 화면 읽기 | `MyPageParser` · `MyPostParser` · `ApplicantListParser` · `ChatRoomParser` · `UiTree` |
| 앱 기록에 맞추기 | `AccountSync` · `PostImport` · `PostRounds` · `ApplicantImport` · `ChatImport` · `ChatNotify` · `ScreenImport` |

`DaangnWalker`와 `DaangnNav`는 `reply/`의 드라이런·실제 발송 시험도 같이 써요.

## 당근 화면이 바뀌었을 때

1. 앱이 화면을 잘못 읽으면 그때 화면을 폰 안 `files/debug/<이름>.xml`로 남겨요 (개발용 — `DaangnWalker.keepScreen`). 실행 기록에는 칸 수만 적어요.
2. Android Studio의 Device Explorer로 `/data/data/com.kangsam.daangnrecruit/files/debug`를 꺼내 맥 `build/` 아래에 둬요. 지원자 이름·대화가 들어 있어서 git·프로젝트 문서에는 올리지 않아요.
3. 그 화면을 읽는 파서(`…Parser`)나 `DaangnNav`를 고치고, 덤프 모양을 줄인 가짜 화면으로 폰 없이 시험해요.

## 지키는 것

- 누르는 칸은 `DaangnNav.clickTargets` 안에 있고 `isSafeToClick`을 통과한 것만이에요. 글쓰기·전송·마감·수정·채팅하기·지원서·근무확정·당근페이 같은 칸은 절대 안 눌러요.
- 지원서와 '채팅하기'는 가져오기에서 누르지 않아요. 그 칸은 `reply/`의 드라이런·실제 발송 시험만 그 사람 것을 따로 찾아 눌러요 (`reply/RoomRoute.kt`).
- 누르기 전에 1.2~2.8초 쉬고, 한 번 돌 때 80번까지만 눌러요.
- 보안 인증·이용 제한 글자(`DaangnNav.STOP_WORDS`)가 보이면 바로 멈추고, 전체 멈춤을 켜고, 계정 상태를 기록해요 (`Safety.kt`).
- 사람이 홈 버튼을 누르거나 다른 앱이 뜨면 멈춰요.
