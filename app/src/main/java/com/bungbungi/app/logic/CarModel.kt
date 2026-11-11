package com.bungbungi.app.logic

/** 아바타의 바탕이 되는 차종. 운전자 차량에 맞는 모습으로 붕붕이를 그린다. */
enum class CarModel(val label: String, val kind: String) {
    AVANTE("아반떼", "세단"),
    SONATA("쏘나타", "세단"),
    TUCSON("투싼", "SUV"),
    SANTAFE("싼타페", "SUV"),
}
