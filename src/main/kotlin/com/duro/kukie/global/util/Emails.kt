package com.duro.kukie.global.util

/** 이메일 주소는 대소문자를 구분하지 않는다 — 초대가 주소 문자열로 사람을 가리키기 때문이다. */
// TODO: 이 유틸 대신 엔티티(User·TeamInvitation) 생성자에서 정규화해 저장 시점에 정합성을 보장한다.
fun String.normalizeEmail(): String = trim().lowercase()
