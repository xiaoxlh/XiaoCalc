package com.example.xiaocalc.calc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * 高精度内核 [Dec] 的验证。
 *
 * 验证策略刻意分三层，避免"用同一套公式自证"：
 *
 *  1. **已知常数**：π / e 的前 30 位与公认数值逐字符比对；
 *  2. **数学恒等式**：`sin²+cos²=1`、`e^ln x = x`、`√x² = x` 等在工作精度
 *     （40 位以上）上必须成立——这些等式对**任何**输入都成立，比比对具体数值更强；
 *  3. **独立实现交叉验证**：与 JDK 的 [Math] / [StrictMath] 在 15 位内比对。
 *     JDK 用的是另一套算法（fdlibm），两条独立实现的吻合才说明我们的级数是对的。
 */
class DecTest {

    private fun d(s: String) = BigDecimal(s)

    /** 舍到 [n] 位有效数字的紧凑字符串，便于与已知常数逐字符比对。 */
    private fun sig(v: BigDecimal, n: Int): String =
        v.round(MathContext(n, RoundingMode.HALF_UP)).stripTrailingZeros().toPlainString()

    /** 相对误差比对，用于与 JDK 实现交叉验证。 */
    private fun assertClose(expected: Double, actual: BigDecimal, tol: Double = 1e-14) {
        val a = actual.toDouble()
        val err = Math.abs(a - expected) / Math.max(1.0, Math.abs(expected))
        assertTrue("期望 $expected，实际 $a（相对误差 $err）", err < tol)
    }

    // ------------------------------------------------------------ 1. 已知常数

    @Test
    fun `π 的前 30 位有效数字与公认值一致`() {
        // 3.14159265358979323846264338327950288...
        assertEquals("3.14159265358979323846264338328", sig(Dec.PI, 30))
    }

    @Test
    fun `e 的前 30 位有效数字与公认值一致`() {
        // 2.71828182845904523536028747135266249...
        assertEquals("2.71828182845904523536028747135", sig(Dec.E, 30))
    }

    @Test
    fun `π 与 e 和 JDK 常量在 15 位内一致`() {
        assertClose(Math.PI, Dec.PI)
        assertClose(Math.E, Dec.E)
    }

    // ------------------------------------------------------- 2. 数学恒等式

    @Test
    fun `sin 平方加 cos 平方恒等于 1`() {
        val points = listOf("0", "0.5", "1", "2", "3", "-1.5", "10", "-7.25", "100")
        for (p in points) {
            val x = d(p)
            val s = Dec.sin(x)
            val c = Dec.cos(x)
            val sum = Dec.add(Dec.mul(s, s), Dec.mul(c, c))
            // 恒等式必须成立到接近工作精度；留 35 位余量已极其宽松
            assertEquals("x=$p 时 sin²+cos² 偏离 1", 0, sig(sum, 35).compareTo("1"))
        }
    }

    @Test
    fun `exp 与 ln 互为逆运算`() {
        for (p in listOf("0.1", "1", "2", "10", "0.001", "123.456", "1E-6", "1E6")) {
            val x = d(p)
            val back = Dec.exp(Dec.ln(x))
            assertEquals("x=$p", 0, sig(back, 30).compareTo(sig(x, 30)))
        }
    }

    @Test
    fun `开方后再平方回到原值`() {
        for (p in listOf("2", "3", "0.5", "1E10", "1E-10", "123456.789", "7")) {
            val x = d(p)
            val back = Dec.mul(Dec.sqrt(x), Dec.sqrt(x))
            assertEquals("x=$p", 0, sig(back, 30).compareTo(sig(x, 30)))
        }
    }

    @Test
    fun `ln 的底数为自身时结果为一`() {
        assertEquals("1", sig(Dec.ln(Dec.E), 30))
    }

    @Test
    fun `π 的余弦为负一 正弦为零`() {
        assertEquals("-1", sig(Dec.cos(Dec.PI), 30))
        assertEquals("0", sig(Dec.sin(Dec.PI), 30))
    }

    // --------------------------------------------------- 3. 与 JDK 交叉验证

    @Test
    fun `三角函数与 JDK 实现在 15 位内一致`() {
        for (p in listOf("0", "0.3", "1", "2", "3.5", "-4.2", "100", "-250", "1000")) {
            val x = d(p).toDouble()
            assertClose(Math.sin(x), Dec.sin(d(p)))
            assertClose(Math.cos(x), Dec.cos(d(p)))
            // tan 在 cos 接近 0 的位置会放大误差，跳过那些点
            if (Math.abs(Math.cos(x)) > 1e-3) assertClose(Math.tan(x), Dec.tan(d(p)))
        }
    }

    @Test
    fun `对数与 JDK 实现在 15 位内一致`() {
        for (p in listOf("0.001", "0.5", "1", "2", "10", "1E5", "1E-5", "12345.6789")) {
            assertClose(Math.log(d(p).toDouble()), Dec.ln(d(p)))
        }
    }

    @Test
    fun `指数与开方与 JDK 实现在 15 位内一致`() {
        for (p in listOf("0", "0.5", "1", "-1", "3.25", "-12.5", "20")) {
            assertClose(Math.exp(d(p).toDouble()), Dec.exp(d(p)))
        }
        for (p in listOf("2", "3", "0.5", "1E10", "1E-10", "123456.789")) {
            assertClose(Math.sqrt(d(p).toDouble()), Dec.sqrt(d(p)))
        }
    }

    // ---------------------------------------------------------------- 幂与阶乘

    @Test
    fun `整数幂精确`() {
        assertEquals("1024", Dec.pow(d("2"), d("10")).toPlainString())
        assertEquals("1", Dec.pow(d("7"), d("0")).toPlainString())
        // 20^20 用 BigInteger 独立算一遍再比对，避免手写长数字数错零
        assertEquals(
            java.math.BigInteger.valueOf(20).pow(20).toString(),
            Dec.pow(d("20"), d("20")).toPlainString(),
        )    }

    @Test
    fun `分数次幂走开方路径`() {
        assertEquals("3", sig(Dec.pow(d("9"), d("0.5")), 30))
        assertEquals("2", sig(Dec.pow(d("8"), d("0.3333333333333333333333333333333333")), 8))
    }

    @Test
    fun `阶乘是精确整数而非被工作精度截断`() {
        // 20! 只有 19 位，本来就在工作精度内
        assertEquals("2432902008176640000", Dec.factorial(d("20")).toPlainString())
        // 170! 有 307 位，若被 50 位 MathContext 截断就不可能精确，也数不出 307 位
        val f170 = Dec.factorial(d("170"))
        assertEquals(307, f170.precision())
        // 170! 末尾有 41 个零（质因数 5 的个数 = ⌊170/5⌋+⌊170/25⌋+⌊170/125⌋ = 34+6+1）
        val text = f170.toPlainString()
        assertEquals(41, text.length - text.trimEnd('0').length)
    }

    @Test
    fun `阶乘拒绝非整数与负数`() {
        for (bad in listOf("-1", "2.5")) {
            var threw = false
            try {
                Dec.factorial(d(bad))
            } catch (_: ArithmeticException) {
                threw = true
            }
            assertTrue("$bad 应当被拒", threw)
        }
    }

    // ------------------------------------------------------------ 定义域保护

    @Test
    fun `定义域外抛算术异常而非返回脏值`() {
        val cases: List<() -> BigDecimal> = listOf(
            { Dec.sqrt(d("-1")) },
            { Dec.ln(d("0")) },
            { Dec.ln(d("-5")) },
            { Dec.pow(d("-2"), d("0.5")) },
            { Dec.tan(Dec.HALF_PI) },
        )
        for ((i, case) in cases.withIndex()) {
            var threw = false
            try {
                case()
            } catch (_: ArithmeticException) {
                threw = true
            }
            assertTrue("第 $i 个用例应当抛 ArithmeticException", threw)
        }
    }
}
