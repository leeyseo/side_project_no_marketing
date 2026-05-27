# 선거 스팸 차단 (SpamBlocker)

선거철에 쏟아지는 후보자 홍보 메시지와 여론조사 ARS 전화를 자동으로 차단하는 안드로이드 앱.

- **알림 차단**: 메시지 앱의 스팸 알림을 즉시 숨김 (저장은 그대로, 알림만 안 뜸)
- **통화 차단**: 정치권 대표번호 패턴의 전화를 무음 거절
- **온디바이스**: 키워드/번호 패턴 매칭이 모두 폰 안에서 처리, 외부 서버 없음
- **사용자 편집 가능**: 차단 키워드와 화이트리스트는 앱에서 직접 수정

---

## 스크린샷

| 권한 부여 전 | 권한 부여 + 차단 1회 후 | 차단 로그 |
|---|---|---|
| ![초기 UI](docs/screenshots/01_initial.png) | ![차단 후](docs/screenshots/02_after_block.png) | ![로그](docs/screenshots/03_block_log.png) |

---

## 작동 원리

### 1. SMS 알림 차단 — `NotificationListenerService`
시스템에서 발급한 알림 접근 권한을 받아, 메시지 앱(`com.google.android.apps.messaging`, `com.samsung.android.messaging` 등)이 띄운 알림의 제목·본문을 읽고 키워드/발신번호 패턴에 매칭되면 `cancelNotification(key)` 으로 즉시 숨깁니다.

- 메시지 자체는 삭제하지 않음 (정책상 기본 SMS 앱이 아니면 불가능)
- 알림이 잠깐 떴다가 사라지는 게 아니라, 시스템이 알림을 등록하는 동시에 가로채서 사용자 인지 전에 제거

### 2. 통화 차단 — `CallScreeningService`
Android 10 (API 29) 이상의 `RoleManager.ROLE_CALL_SCREENING` 역할을 부여받아, 수신 전화가 울리기 전에 발신번호를 검사합니다.

```kotlin
CallResponse.Builder()
    .setDisallowCall(true)
    .setRejectCall(true)
    .setSilenceCall(true)
    .setSkipNotification(true)
    .build()
```

차단 시 벨소리·진동·놓친전화 알림 모두 발생하지 않음. 통화 기록(call log)에는 남겨서 사용자가 나중에 확인 가능.

### 3. 필터 엔진
순수 Kotlin 라이브러리, Android 프레임워크 의존성 없음. 유닛 테스트로 검증.

- **키워드 매칭**: 발신자명 + 본문을 lowercase 후 부분 문자열 검사
- **번호 패턴 매칭**: 정규식 (한국 정치권 대표번호 대역 `15XX-XXXX`, `16XX-XXXX`, `18XX-XXXX`, `050X-XXXX-XXXX`)
- **화이트리스트**: 등록된 번호는 키워드와 무관하게 통과

---

## 기술 스택

| 항목 | 선택 |
|---|---|
| 언어 | Kotlin 2.2 |
| UI | Jetpack Compose (Material 3, BOM 2026.02) |
| 빌드 | Android Gradle Plugin 9.2.1 / Gradle 9.4 |
| compileSdk / minSdk | 36 / 29 |
| 저장소 | `SharedPreferences` + JSON 직렬화 |
| 상태 관리 | `StateFlow` + Compose `collectAsState` |
| 테스트 | JUnit 4 (유닛), Compose UI Test (예정) |

서버 없음. 외부 라이브러리는 AndroidX/Compose뿐.

---

## 프로젝트 구조

```
SpamBlocker/
└── app/src/main/java/com/spamblocker/election/
    ├── MainActivity.kt                          # Compose UI
    ├── filter/
    │   ├── SpamFilter.kt                        # 분류 로직
    │   └── DefaultRules.kt                      # 기본 키워드/패턴
    ├── data/
    │   ├── SettingsStore.kt                     # 키워드, 화이트리스트, 카운터
    │   └── BlockLog.kt                          # 차단 내역 (StateFlow)
    ├── service/
    │   ├── SpamNotificationListenerService.kt   # 알림 가로채기
    │   └── SpamCallScreeningService.kt          # 통화 차단
    └── ui/theme/                                # Material3 테마
```

총 10개 파일, 약 600 줄.

---

## 빌드 / 실행

### 요구사항
- Android Studio Koala 이상
- JDK 21 (Android Studio 번들 JBR 사용 가능)
- Android SDK API 36

### 명령어 빌드
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
cd SpamBlocker
.\gradlew.bat assembleDebug          # APK 생성
.\gradlew.bat testDebugUnitTest      # 유닛 테스트
```

APK 위치: `SpamBlocker/app/build/outputs/apk/debug/app-debug.apk`

### 에뮬레이터 설치
```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb install -r SpamBlocker\app\build\outputs\apk\debug\app-debug.apk
& $adb shell am start -n com.spamblocker.election/.MainActivity
```

---

## 권한 부여 방법

앱 실행 후 두 가지 특수 권한을 사용자가 직접 시스템 설정에서 부여해야 합니다.

1. **알림 접근 권한** — 앱 내 "권한 허용" 버튼 → 시스템 설정 → "SpamBlocker" 토글 ON
2. **통화 차단 역할** — 앱 내 "권한 허용" 버튼 → 다이얼로그에서 "예" 선택

에뮬레이터에서 자동 부여 (테스트용):
```powershell
& $adb shell cmd notification allow_listener com.spamblocker.election/com.spamblocker.election.service.SpamNotificationListenerService
& $adb shell cmd role add-role-holder android.app.role.CALL_SCREENING com.spamblocker.election
```

---

## 검증 현황

### 자동 테스트
- 유닛 테스트 **9/9 통과** (`SpamFilterTest`)
  - 키워드/발신번호 매칭, 화이트리스트 우회, null safety, 정당명 매칭 등

### 수동 검증 (에뮬레이터 Pixel 7 API 36)
- 빌드: `assembleDebug` BUILD SUCCESSFUL
- 설치: 정상
- UI 렌더링: Compose 4개 카드 모두 정상, 한글 폰트 OK
- **실제 통화 차단**: 에뮬레이터 GSM 시뮬레이션으로 `1588-1234` 발신 → CallScreeningService 인터셉트 → 무음 거절 → 카운터 0→1 증가 → 차단 로그 표시까지 end-to-end 확인

```
05-27 14:53:12.063 SpamCS: Blocking call from 15881234 reason=발신패턴: ^15\d{6}$
```

### 아직 검증 안 한 항목
- 실기기 SMS 알림 차단 (에뮬레이터에서 임의 SMS 발신이 어려움)
- 한국 통신사 메시지 앱 (KT/SKT/LGU+) 의 알림 포맷 호환성

---

## 로드맵

### 완료
- [x] 필터 엔진 + 기본 키워드 30개
- [x] SettingsStore (SharedPreferences)
- [x] BlockLog (최근 200건)
- [x] NotificationListenerService
- [x] CallScreeningService
- [x] Compose UI (상태/권한/키워드/로그 4개 카드)
- [x] 유닛 테스트
- [x] 에뮬레이터 end-to-end 검증

### 출시 전 필수
- [ ] **앱 아이콘 디자인** — 현재 기본 안드로이드 아이콘
- [ ] **실기기 테스트** — 본인 폰에 sideload 설치, 실제 선거 스팸으로 검증
- [ ] **개인정보 처리방침** — 정적 페이지 (외부 서버 없으니 매우 간단)
- [ ] **릴리즈 서명키 생성** — `keystore.jks` + `signingConfigs.release`
- [ ] **AAB 빌드** — Play Store 배포 포맷
- [ ] **Play Console 등록** — 앱 설명, 스크린샷 8장, 콘텐츠 등급 설문, 카테고리 (Tools)

### 출시 후 개선 후보
- [ ] iOS 버전 (Mac 환경 확보 시)
- [ ] 실시간 차단 통계 시각화 (차트)
- [ ] 사용자 정의 정규식 패턴 지원
- [ ] 차단 키워드 클라우드 공유 (옵트인)
- [ ] 알림으로 차단 사실 알리기 (선택)
- [ ] 다국어 지원 (영문 i18n)

### 알려진 제약
- iOS는 OS 정책상 SMS 알림 차단 불가 (격리 폴더로 분류만 가능). 이 프로젝트는 Android 단독.
- 메시지 본문 자체에 접근 불가능 — 알림에 표시되는 텍스트만 사용 가능. 일부 메시지 앱은 알림 본문을 잘라서 보여줘서 필터가 놓칠 수 있음.
- `NotificationListenerService`는 시스템이 우선순위 낮게 다뤄서 부팅 후 활성화까지 수 초 걸릴 수 있음.

---

## Play Store 정책 체크리스트

기본 SMS 앱 권한을 안 쓰는 설계라 심사가 일반 앱 수준으로 끝납니다.

- [ ] [개인정보처리방침 URL](https://example.com/privacy) — 데이터 수집 없음을 명시
- [ ] 콘텐츠 등급 — IARC 설문 (전체 이용가 예상)
- [ ] 광고 포함 여부 — 없음
- [ ] 데이터 보안 양식 — "수집/공유 없음"
- [ ] 카테고리 — "도구" 또는 "통신"
- [ ] 대상 연령 — 만 13세 이상
- [ ] 카메라/위치/연락처 권한 — `READ_CONTACTS` 사용 시 사용 목적 명시 (현재 manifest에 선언만 되어있고 미사용, 제거 고려)

---

## 라이선스

미정. 출시 시점에 결정 (MIT 권장).
