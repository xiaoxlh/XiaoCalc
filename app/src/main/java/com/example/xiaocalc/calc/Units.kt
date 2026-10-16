package com.example.xiaocalc.calc

import java.math.BigDecimal

/**
 * 单位类别。
 *
 * 每个类别内部所有单位都换算到同一个**基准单位**（见 [Units.catalog] 里各表第一项）。
 */
enum class UnitCategory(val label: String) {
    LENGTH("长度"),
    AREA("面积"),
    VOLUME("体积"),
    MASS("质量"),
    TEMPERATURE("温度"),
    SPEED("速度"),
    TIME("时间"),
    DATA("数据"),
    PRESSURE("压强"),
    ENERGY("能量"),
    POWER("功率"),
    ANGLE("角度"),
}

/**
 * 一个单位。
 *
 * 换算模型统一为**仿射变换**：
 * ```
 * 基准值 = (本值 + preOffset) × factor
 * 本值   = 基准值 ÷ factor − preOffset
 * ```
 *
 * 绝大多数单位 `preOffset = 0`，此时退化成单纯的倍率换算。
 * 只有温度需要偏移——`华氏 → 摄氏` 是 `(F − 32) × 5/9`，
 * 拆成"先加 −32 再乘 5/9"，就能和其它单位共用同一条公式，
 * 不必为温度单开一条代码路径。
 *
 * @param symbol 紧凑符号，显示在数字旁边（"m"、"ft"、"°C"）
 * @param label  完整中文名，显示在选择器里（"米"、"英尺"、"摄氏度"）
 */
data class UnitDef(
    val label: String,
    val symbol: String,
    val factor: BigDecimal,
    val preOffset: BigDecimal = BigDecimal.ZERO,
)

/**
 * 常用单位换算表。
 *
 * ## 基准单位的选取不是随意的
 *
 * 每个类别的基准都挑**能让其余因子写成有限小数**的那个：
 * 长度用米（英寸 0.0254 精确）、速度用米每秒、数据用字节。
 * 这样绝大多数因子是精确的十进制数，换算不引入任何舍入。
 *
 * 少数天生产生循环小数的（km/h = 5/18、节 = 1852/3600、弧度 = 180/π）
 * 用 [frac] 以商的形式**在运行时**算出来，而不是手写一长串近似小数——
 * 与 [Dec] 里 π/e 现算是同一个理由：手抄长数字出错时看不出来。
 */
object Units {

    /** 以精确商的形式构造因子，避免手写循环小数的近似值。 */
    private fun frac(numerator: String, denominator: String): BigDecimal =
        Dec.div(BigDecimal(numerator), BigDecimal(denominator))

    private fun of(s: String): BigDecimal = BigDecimal(s)

    // ------------------------------------------------------------ 各分类表

    /**
     * 各分类表。
     *
     * **每类的前两项就是默认的起算/目标单位**（见 [UnitConverterState]），
     * 因此顺序不是随意的：把日常最常用的一对排在最前。
     * 基准单位（因子为 1 且无偏移）不一定排第一——[baseOf] 按特征查找，与顺序无关。
     */
    val catalog: Map<UnitCategory, List<UnitDef>> = mapOf(
        // 基准：米
        UnitCategory.LENGTH to listOf(
            UnitDef("米", "m", BigDecimal.ONE),
            UnitDef("英尺", "ft", of("0.3048")),
            UnitDef("厘米", "cm", of("0.01")),
            UnitDef("毫米", "mm", of("0.001")),
            UnitDef("千米", "km", of("1000")),
            UnitDef("英寸", "in", of("0.0254")),
            UnitDef("码", "yd", of("0.9144")),
            UnitDef("英里", "mi", of("1609.344")),
            UnitDef("海里", "nmi", of("1852")),
        ),

        // 基准：平方米。亩 = 2000/3 m²，是中国市制，必须用商而非近似值
        UnitCategory.AREA to listOf(
            UnitDef("平方米", "m²", BigDecimal.ONE),
            UnitDef("平方英尺", "ft²", of("0.09290304")),
            UnitDef("平方厘米", "cm²", of("0.0001")),
            UnitDef("平方千米", "km²", of("1000000")),
            UnitDef("公顷", "ha", of("10000")),
            UnitDef("亩", "亩", frac("2000", "3")),
            UnitDef("英亩", "acre", of("4046.8564224")),
        ),

        // 基准：升
        UnitCategory.VOLUME to listOf(
            UnitDef("升", "L", BigDecimal.ONE),
            UnitDef("加仑(美)", "gal", of("3.785411784")),
            UnitDef("毫升", "mL", of("0.001")),
            UnitDef("立方厘米", "cm³", of("0.001")),
            UnitDef("立方米", "m³", of("1000")),
            UnitDef("品脱(美)", "pt", of("0.473176473")),
        ),

        // 基准：千克。斤/两为中国市制（1 斤 = 500 g，1 两 = 50 g）
        UnitCategory.MASS to listOf(
            UnitDef("千克", "kg", BigDecimal.ONE),
            UnitDef("磅", "lb", of("0.45359237")),
            UnitDef("克", "g", of("0.001")),
            UnitDef("毫克", "mg", of("0.000001")),
            UnitDef("吨", "t", of("1000")),
            UnitDef("斤", "斤", of("0.5")),
            UnitDef("两", "两", of("0.05")),
            UnitDef("盎司", "oz", of("0.028349523125")),
        ),

        // 基准：摄氏度。唯一需要偏移的类别
        UnitCategory.TEMPERATURE to listOf(
            UnitDef("摄氏度", "°C", BigDecimal.ONE),
            // (F − 32) × 5/9
            UnitDef("华氏度", "°F", frac("5", "9"), of("-32")),
            // K − 273.15
            UnitDef("开尔文", "K", BigDecimal.ONE, of("-273.15")),
        ),

        // 基准：米每秒。日常用的是 km/h ↔ mph，因此排在最前
        UnitCategory.SPEED to listOf(
            // 1 km/h = 1000/3600 m/s = 5/18
            UnitDef("千米/时", "km/h", frac("5", "18")),
            UnitDef("英里/时", "mph", of("0.44704")),
            UnitDef("米/秒", "m/s", BigDecimal.ONE),
            // 1 节 = 1 海里/小时 = 1852/3600 m/s
            UnitDef("节", "kn", frac("1852", "3600")),
            UnitDef("英尺/秒", "ft/s", of("0.3048")),
        ),

        // 基准：秒
        UnitCategory.TIME to listOf(
            UnitDef("分", "min", of("60")),
            UnitDef("时", "h", of("3600")),
            UnitDef("秒", "s", BigDecimal.ONE),
            UnitDef("毫秒", "ms", of("0.001")),
            UnitDef("天", "d", of("86400")),
            UnitDef("周", "wk", of("604800")),
        ),

        // 基准：字节。
        // 1 KB = 1024 B（二进制前缀；与存储厂商的十进制标称不同，但计算器场景下用户要的是前者）
        UnitCategory.DATA to listOf(
            UnitDef("兆字节", "MB", of("1048576")),
            UnitDef("吉字节", "GB", of("1073741824")),
            UnitDef("千字节", "KB", of("1024")),
            UnitDef("字节", "B", BigDecimal.ONE),
            UnitDef("比特", "bit", of("0.125")),
            UnitDef("太字节", "TB", of("1099511627776")),
        ),

        // 基准：帕斯卡
        UnitCategory.PRESSURE to listOf(
            UnitDef("千帕", "kPa", of("1000")),
            UnitDef("兆帕", "MPa", of("1000000")),
            UnitDef("帕斯卡", "Pa", BigDecimal.ONE),
            UnitDef("巴", "bar", of("100000")),
            UnitDef("标准大气压", "atm", of("101325")),
            // 1 mmHg = 133.322387415 Pa（精确定义值）
            UnitDef("毫米汞柱", "mmHg", of("133.322387415")),
        ),

        // 基准：焦耳。千卡↔千焦是食品能量标签上的日常换算
        UnitCategory.ENERGY to listOf(
            UnitDef("千卡", "kcal", of("4184")),
            UnitDef("千焦", "kJ", of("1000")),
            UnitDef("卡", "cal", of("4.184")),
            UnitDef("焦耳", "J", BigDecimal.ONE),
            UnitDef("瓦时", "Wh", of("3600")),
            UnitDef("千瓦时", "kWh", of("3600000")),
        ),

        // 基准：瓦特。此处"马力"取中国通行的公制马力（1 PS = 735.49875 W），
        // 不是英制 hp(745.6998…W)
        UnitCategory.POWER to listOf(
            UnitDef("千瓦", "kW", of("1000")),
            UnitDef("公制马力", "PS", of("735.49875")),
            UnitDef("瓦", "W", BigDecimal.ONE),
            UnitDef("兆瓦", "MW", of("1000000")),
        ),

        // 基准：度
        UnitCategory.ANGLE to listOf(
            UnitDef("度", "°", BigDecimal.ONE),
            // 1 rad = 180/π 度
            UnitDef("弧度", "rad", Dec.div(of("180"), Dec.PI)),
            UnitDef("角分", "′", frac("1", "60")),
            UnitDef("角秒", "″", frac("1", "3600")),
            UnitDef("百分度", "grad", of("0.9")),
        ),
    )

    /** 某类别的基准单位：因子的定义基准，`factor == 1 && preOffset == 0`。 */
    fun baseOf(category: UnitCategory): UnitDef =
        catalog.getValue(category).first { it.preOffset.signum() == 0 && it.factor.compareTo(BigDecimal.ONE) == 0 }

    /**
     * 换算：先把 [value] 折到基准单位，再展开成 [to]。
     *
     * 全程走 [Dec] 的 50 位工作精度，因此"换算完再换回来"能精确还原原值
     * （除了显示层的 16 位舍入），不会出现每换一次就掉几位的情况。
     */
    fun convert(value: BigDecimal, from: UnitDef, to: UnitDef): BigDecimal {
        val base = Dec.mul(Dec.add(value, from.preOffset), from.factor)
        return Dec.sub(Dec.div(base, to.factor), to.preOffset)
    }
}
