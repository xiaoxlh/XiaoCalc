package com.example.xiaocalc.calc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 历史记录编码：必须对表达式里的任意可见字符做无损转义。 */
class HistoryCodecTest {

    @Test
    fun `往返编解码保持内容一致`() {
        val entries = listOf(
            HistoryEntry("1+1", "2", 1_700_000_000_000L),
            HistoryEntry("2×π", "2π", 1_700_000_001_000L),
            HistoryEntry("√8", "2√2", 1_700_000_002_000L),
        )
        assertEquals(entries, HistoryCodec.decode(HistoryCodec.encode(entries)))
    }

    @Test
    fun `分隔符与反斜杠被转义`() {
        // 这些字符理论上不会出现在表达式里，但编码层不能因此就假设它安全
        val nasty = "a\u001Eb\u001Fc\\d"
        val entries = listOf(HistoryEntry(nasty, nasty, 1L))
        val decoded = HistoryCodec.decode(HistoryCodec.encode(entries))
        assertEquals(nasty, decoded.single().expression)
        assertEquals(nasty, decoded.single().result)
    }

    @Test
    fun `空与损坏输入不会抛异常`() {
        assertTrue(HistoryCodec.decode(null).isEmpty())
        assertTrue(HistoryCodec.decode("").isEmpty())
        // 字段数不对 / 时间戳非数字 → 跳过该条，而不是整体崩掉
        assertTrue(HistoryCodec.decode("abc").isEmpty())
        assertTrue(HistoryCodec.decode("notanumber\u001Fx\u001Fy").isEmpty())
        assertEquals(1, HistoryCodec.decode("1\u001Fok\u001F1").size)
    }

    @Test
    fun `新条目插入到最前`() {
        val store = InMemoryHistoryStore()
        val state = CalculatorState(InMemorySettingsStore(), store) { 1L }
        repeat(3) { index ->
            state.onKey(CalcKey.Clear)
            state.onKey(CalcKey.Digit(('1' + index)))
            state.onKey(CalcKey.Plus)
            state.onKey(CalcKey.Digit('1'))
            state.onKey(CalcKey.Equals)
        }
        assertEquals("3+1", state.history.first().expression)
        assertEquals(3, store.loadEntries().size)
    }
}
