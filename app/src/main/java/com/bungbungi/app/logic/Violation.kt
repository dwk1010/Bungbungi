package com.bungbungi.app.logic

/** 기획서 4장 표의 감지 항목. penalty = 100km당 1회 감점. */
enum class Violation(val label: String, val penalty: Int, val part: AvatarPart) {
    SPEEDING("과속", 3, AvatarPart.FLAME_EYES),
    ZONE_SPEEDING("보호구역 과속", 6, AvatarPart.FLAME_EYES),
    HARSH_ACCEL("급가속", 2, AvatarPart.EXHAUST_SMOKE),
    HARSH_BRAKE("급감속", 2, AvatarPart.DENTED_BUMPER),
    NO_SIGNAL_LANE_CHANGE("깜빡이 없는 차선변경", 3, AvatarPart.SIDE_SCRATCH),
    SEATBELT("안전벨트 미착용", 5, AvatarPart.LOOSE_BELT),
}

/** 주된 위반에 따라 따로 바뀌는 아바타 부위 */
enum class AvatarPart(val label: String) {
    NONE("없음"),
    FLAME_EYES("불꽃 눈"),
    EXHAUST_SMOKE("배기구 연기"),
    DENTED_BUMPER("찌그러진 범퍼"),
    SIDE_SCRATCH("옆면 흠집"),
    LOOSE_BELT("풀린 벨트"),
}

data class ViolationEvent(val type: Violation, val atMillis: Long, val detail: String)

enum class BonusType(val label: String) {
    CRUISE("크루즈 컨트롤 사용"),
    CLEAN_30KM("위반 없이 30km 연속 주행"),
}

data class BonusEvent(val type: BonusType, val atMillis: Long)
