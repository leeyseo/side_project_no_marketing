package com.spamblocker.election

import com.spamblocker.election.filter.DefaultRules
import com.spamblocker.election.filter.SpamFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpamFilterTest {

    private fun newFilter(whitelist: Set<String> = emptySet()) = SpamFilter(
        keywords = DefaultRules.keywords,
        whitelistNumbers = whitelist,
    )

    @Test fun classifies_election_keyword_as_spam() {
        val d = newFilter().classifyMessage(
            sender = "010-1234-5678",
            body = "[기호 3번] 소중한 한표 부탁드립니다.",
        )
        assertTrue(d.isSpam)
        assertEquals("한표", d.matchedKeyword)
    }

    @Test fun classifies_polling_request_as_spam() {
        val d = newFilter().classifyMessage(sender = "1644-1234", body = "여론조사에 응답해주세요")
        assertTrue(d.isSpam)
    }

    @Test fun normal_message_passes_through() {
        val d = newFilter().classifyMessage(sender = "엄마", body = "저녁 먹었어?")
        assertFalse(d.isSpam)
    }

    @Test fun whitelist_overrides_keyword() {
        val d = newFilter(whitelist = setOf("010-0000-0000")).classifyMessage(
            sender = "010-0000-0000",
            body = "사전투표 같이 가자",
        )
        assertFalse(d.isSpam)
    }

    @Test fun null_sender_with_spam_body_still_detected() {
        val d = newFilter().classifyMessage(sender = null, body = "지방선거 사전투표 안내")
        assertTrue(d.isSpam)
    }

    @Test fun party_name_in_body_detected() {
        val d = newFilter().classifyMessage(sender = "선거사무소", body = "더불어민주당 OOO 후보")
        assertTrue(d.isSpam)
    }
}
