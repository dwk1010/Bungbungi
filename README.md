# 붕붕이 — 내 운전 습관을 먹고 자라는 차량 아바타

현대자동차그룹 차량용 앱 개발 공모전 (Pleos Connect) 출품작.

운전 습관이 차 모양 아바타의 외모로 드러나는 IVI 앱입니다. 과속, 급가속·급감속, 깜빡이 없는 차선변경, 안전벨트 미착용을 차량 신호로 감지하고, 100km당 위반 비율로 주행 점수를 낸 뒤 최근 2주 점수(거리 가중 평균)로 아바타 단계를 정합니다. 주행 중에는 화면 대신 아바타 목소리(TTS)와 실내 앰비언트 라이트 색으로 피드백합니다.

- **앱 이름**: 붕붕이
- **팀**: 일반 부문 4인 팀
- **APK 위치**: `apk/` 폴더 (`팀명_붕붕이_release.apk`)
- **시연 영상**: `docs/붕붕이_시연영상.mp4` (에뮬레이터 화면 녹화, 약 1분 45초, 소리 없음)

## 테스트 환경

| 항목 | 값 |
|---|---|
| 실행 대상 | Pleos Connect Emulator v2.1.0 (Android 14, API 34) |
| minSdk / targetSdk | 28 / 35 |
| 빌드 도구 | Android Studio, Gradle 8.10.2, AGP 8.8.2, Kotlin 2.0.0, JDK 17~21 |
| Pleos SDK | Vehicle 2.3.2, NaviHelper 3.0.5, FusedLocation 1.1.3, TextToSpeech 3.3.1, SpeechToText 3.3.1 |

## 실행 방법

### APK 설치
1. Pleos Connect 에뮬레이터를 켭니다.
2. `adb install -r -g apk/팀명_붕붕이_release.apk`
3. 앱 목록에서 **붕붕이**를 실행합니다. 처음 실행하면 위치·마이크 권한을 허용해 주세요.

### 소스에서 빌드
1. Android Studio에서 이 폴더를 엽니다 (Gradle JDK는 17 또는 21).
2. 기기 목록에서 Pleos Connect 에뮬레이터를 고르고 ▶ Run.
3. 명령줄: `./gradlew assembleRelease` → `app/build/outputs/apk/release/app-release.apk`
4. 단위 테스트: `./gradlew testDebugUnitTest`

## 시연 방법 (2차 PT)

왼쪽 메뉴의 **시연** 탭에서:

1. **안전 7일**을 두 번 누르면 2주가 지나고 알에서 아바타가 태어납니다 (탄생 연출).
2. **난폭 주행** 시나리오(약 45초)를 재생하면 붕붕이가 위반마다 목소리로 반응하고 실내 조명이 주황색으로 바뀝니다.
3. **거친 7일** 또는 **+1주**를 누르면 주간 평가가 돌아 아바타가 한 단계 퇴화합니다. 안전 주행으로 다시 진화시킬 수 있습니다.
4. **도감** 탭에서 지금까지 만난 모습을, **신호** 탭에서 실제 차량 신호(속도, 브레이크, 깜빡이, 핸들 각도 등)를 확인할 수 있습니다.

실제 신호로 시험할 때는 에뮬레이터의 차량 신호를 바꾸면 됩니다. 속도가 5km/h 이상으로 2초 넘게 유지되면 주행이 시작되고, P단이나 20초 정차로 끝납니다.

## 구조

```
app/src/main/java/com/bungbungi/app/
├── signal/                 차량 신호 수집
│   ├── PleosVehicleSource  Vehicle SDK: 브레이크, 방향지시등, 핸들 각도, 주행거리, 안전벨트, 크루즈, 기어, 앰비언트 라이트
│   ├── SpeedProbes         차량 속도 (PERF_VEHICLE_SPEED: Pleos 차량 서비스 + AAOS CarPropertyManager, GPS 보조)
│   └── NaviLocationSource  NaviHelper(제한속도·보호구역·단속카메라), FusedLocation
├── logic/                  순수 Kotlin (단위 테스트 대상)
│   ├── ViolationDetector   위반·보너스 감지
│   ├── Scoring             주행 점수, 거리 가중 평균
│   └── Avatar              알 → 5단계 아바타, 주 1회 한 칸씩 변화, 주된 위반별 부위
├── voice/                  Pleos TTS/STT (실패 시 Android TTS)
├── demo/Scenarios          시연용 안전/난폭 주행 시나리오
├── data/Store              주행 기록·아바타 상태 저장 (차량 내부 저장소)
├── ui/                     Compose 화면, 아바타 그리기
└── DriveEngine             신호 → 감지 → 점수 → 아바타 → 음성·조명
```

## 차량 속도를 읽는 방법

Vehicle SDK 2.3.2 공개 API에는 차량 속도가 없습니다. SDK 내부 속성 목록에 있는 `PERF_VEHICLE_SPEED`(단위 m/s)를 Pleos 차량 서비스(`IVehicleService`)에 직접 요청하고, 같은 속성을 AAOS `CarPropertyManager`로도 구독합니다. 둘 다 Pleos Connect 에뮬레이터 v2.1.0에서 값이 들어오는 것을 확인했습니다. 앱의 **신호** 탭에서 경로별 수신 상태를 볼 수 있습니다.

## 감지 기준 (초기값)

| 항목 | 기준 | 감점(100km당) |
|---|---|---|
| 과속 | 제한속도 +10km/h를 3초 이상 | 3 |
| 보호구역 과속 | 어린이보호구역에서 제한속도 초과 2초 이상 | 6 |
| 급가속 | 1초에 10km/h 이상 증가 | 2 |
| 급감속 | 1초에 10km/h 이상 감소 + 브레이크 | 2 |
| 깜빡이 없는 차선변경 | 40km/h 이상, 핸들 20° 이상, 직전 3초 방향지시등 꺼짐 | 3 |
| 안전벨트 미착용 | 주행 중 미착용 경고 | 5 |
| 보너스 | 크루즈 사용, 위반 없이 30km | +2 |
