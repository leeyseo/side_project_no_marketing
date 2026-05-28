package com.spamblocker.election

import com.spamblocker.election.filter.DefaultRules
import com.spamblocker.election.filter.SpamFilter
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 실제로 받는 형태의 선거 문자/전화 샘플을 필터에 던져보는 검증.
 * 결과는 콘솔에 표로 출력되고, 명확한 케이스는 단언한다.
 */
class RealWorldSpamTest {

    private val filter = SpamFilter(
        keywords = DefaultRules.keywords,
    )

    private data class Sms(val label: String, val sender: String?, val body: String)

    @Test fun election_sms_samples() {
        val spam = listOf(
            Sms("후보 홍보(대표번호)", "15881234",
                "[Web발신] [기호1번 홍길동] 안녕하십니까. 이번 지방선거 시장 후보 홍길동입니다. 소중한 한 표 부탁드립니다."),
            Sms("여론조사 안내", "16449999",
                "안녕하세요. OO리서치 여론조사입니다. OO시장 선거 관련 설문에 응답 부탁드립니다."),
            Sms("정당 공약 광고", "0257712345",
                "(광고)[국민의힘] 홍길동 후보 공약 안내드립니다. 깨끗한 정치를 약속합니다."),
            Sms("사전투표 독려", "18991234",
                "6월 3일 사전투표 꼭 하세요! 기호 2번 더불어민주당 홍길동."),
            Sms("발신번호 없는 출마 안내", null,
                "[Web발신] 우리 동네 구청장 후보 OOO 출마합니다. 많은 지지 부탁드립니다."),
        )
        val legit = listOf(
            Sms("가족 문자", "엄마", "저녁에 미역국 끓여놨어 데워 먹어"),
            Sms("병원 예약(일반번호)", "0212345678", "[OO병원] 내일 오전 10시 진료 예약 안내입니다."),
            Sms("택배 알림", "010-2222-3333", "[CJ대한통운] 고객님의 상품이 배송 출발했습니다."),
        )

        println("=== SMS 분류 결과 ===")
        (spam + legit).forEach { s ->
            val d = filter.classifyMessage(s.sender, s.body)
            val mark = if (d.isSpam) "차단" else "통과"
            println("[$mark] ${s.label} (발신:${s.sender ?: "없음"}) ${d.reason?.let { "→ $it" } ?: ""}")
        }

        spam.forEach { s ->
            assertTrue("선거 스팸이 통과됨: ${s.label}", filter.classifyMessage(s.sender, s.body).isSpam)
        }
        legit.forEach { s ->
            assertFalse("정상 문자가 차단됨: ${s.label}", filter.classifyMessage(s.sender, s.body).isSpam)
        }
    }
}
