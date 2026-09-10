package com.duro.kukie.global.util

/**
 * 이메일 주소는 대소문자를 구분하지 않고 다룬다.
 *
 * 초대는 회원 id 가 아니라 **주소 문자열**로 사람을 가리키므로(제품기획서 02 §4), 초대한 주소와 가입한
 * 주소의 대소문자가 다르면 그 초대는 받은 초대함에도 안 뜨고 수락도 안 되고 재초대도 막혀 영영 남는다.
 * 그래서 초대 쪽에서 오가는 주소는 모두 이 함수를 거친다.
 */
fun String.normalizeEmail(): String = trim().lowercase()
