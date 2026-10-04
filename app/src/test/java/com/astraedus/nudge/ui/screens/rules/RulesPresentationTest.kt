package com.astraedus.nudge.ui.screens.rules

import android.content.Context
import com.astraedus.nudge.R
import com.astraedus.nudge.data.db.entity.BlockRule
import io.mockk.every
import io.mockk.mockk
import java.io.File
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Test

/** Uses the shipped resource text, while keeping these formatting checks on the plain JVM. */
class RulesPresentationTest {
    private fun contextFor(directory: String): Context {
        val relative = "src/main/res/$directory/strings_rules.xml"
        val file = File(relative).takeIf { it.exists() } ?: File("app/$relative")
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = document.getElementsByTagName("string")
        val textByName = (0 until nodes.length).associate { index ->
            val node = nodes.item(index)
            val name = node.attributes.getNamedItem("name").nodeValue
            name to node.textContent.removeSurrounding("\"").replace("\\'", "'").replace("\\n", "\n")
        }
        val nameById = R.string::class.java.fields.associate { it.getInt(null) to it.name }
        fun resource(id: Int) = textByName.getValue(nameById.getValue(id))
        return mockk<Context> {
            every { getString(any()) } answers { resource(firstArg()) }
            every { getString(any(), *anyVararg()) } answers {
                String.format(Locale.ROOT, resource(firstArg()), *secondArg<Array<out Any>>())
            }
        }
    }

    private fun rule(
        mode: String = "DELAY",
        features: String? = null,
        limit: Int? = null,
        start: Int? = null
    ) = BlockRule(
        packageName = "com.test",
        mode = mode,
        delaySeconds = 15,
        inAppFeatures = features,
        dailyLimitMinutes = limit,
        scheduleStartMinute = start
    )

    @Test
    fun `English summary retains mode limit schedule and feature formatting`() {
        val context = contextFor("values")
        val rules = listOf(rule(limit = 30, start = 540), rule("HARD_BLOCK", "REELS,EXPLORE"))
        assertEquals(
            "Delay 15s · 30min limit · Scheduled · Reels/Explore: Hard Block",
            localizedActiveRulesSummary(context, rules)
        )
    }

    @Test
    fun `Chinese summary translates stored feature IDs and formats durations`() {
        val context = contextFor("values-zh-rCN")
        val rules = listOf(rule(limit = 30, start = 540), rule("HARD_BLOCK", "REELS,EXPLORE"))
        assertEquals(
            "等待 15 秒 · 限额 30 分钟 · 定时计划 · Reels/探索：完全拦截",
            localizedActiveRulesSummary(context, rules)
        )
    }

    @Test
    fun `Chinese none hold breathing and TikTok labels remain presentation only`() {
        val context = contextFor("values-zh-rCN")
        assertEquals("不拦截", localizedRuleMode(context, "NONE", 15))
        assertEquals("长按 15 秒", localizedRuleMode(context, "HOLD", 15))
        assertEquals("呼吸练习 15 秒", localizedRuleMode(context, "BREATHING", 15))
        val storedRule = rule("HOLD", "TIKTOK_FEED")
        assertEquals("TikTok 信息流：长按 15 秒", localizedActiveRulesSummary(context, listOf(storedRule)))
        assertEquals("TIKTOK_FEED", storedRule.inAppFeatures)
        assertEquals("HOLD", storedRule.mode)
    }

    @Test
    fun `Chinese editor summary includes daily limit and both auto-close triggers`() {
        val context = contextFor("values-zh-rCN")
        val storedRule = rule(limit = 30).copy(
            grayscale = true,
            showCounter = true,
            autoKickAfter = 20,
            autoKickAfterMinutes = 5,
            showTimeRemaining = true
        )
        assertEquals(
            "整个应用：等待 15 秒 + 每日限额 30 分钟、灰度、计数器、20 次后自动退出、5 分钟后自动退出、剩余时间",
            localizedEditorRuleSummary(context, storedRule)
        )
    }
}
