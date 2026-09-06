package com.qiuminal.zhhhelper

import android.content.Context
import android.util.AttributeSet
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat

/**
 * 拆分栏视图：字根编码与拆分部件逐组上下对齐展示。
 *
 * 数据来自 chai.txt 的两个字段：字根码（若干组「大写+小写」两字母编码，空白
 * 分隔）与拆分部件（等量单码点字符，空格分隔或直接连写，如「⺊一」）。
 * 两者数量一致时，每组编码与其部件组成一个配对单元：编码居中悬于部件正上方，
 * 单元横向排列，空间不足时以单元为单位整体换行——换行后编码仍跟随其部件，
 * 对应关系不断裂。数量不一致（如 𠔻）或字段缺失时，退回旧版
 * 「编码一行 + 部件一行」的两行展示。
 *
 * 字号：部件默认 16sp、编码默认 12sp（与错字速查弹窗一致）；查询结果卡片与
 * 分享图片通过 [setTextSizes] 按用户字号同步缩放。子视图均为真实 TextView，
 * AppFonts.applyToHierarchy 的字符级 fallback 字体可正常生效。
 */
class RootPairsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ViewGroup(context, attrs, defStyleAttr) {

    companion object {
        /** 配对单元之间的横向间距（dp） */
        private const val GAP_H_DP = 8
        /** 换行后行与行的纵向间距（dp） */
        private const val GAP_V_DP = 6
        /** 单元内编码与部件的纵向间距（dp） */
        private const val GAP_INNER_DP = 2
    }

    private var componentSp = 16f
    private var codeSp = 12f

    /** true：配对模式，child 顺序为 [码1,部件1,码2,部件2,...]；false：两行降级模式 */
    private var pairedMode = false

    /** 记录每个 child 是否为编码视图（两行模式下同样适用） */
    private val childIsCode = ArrayList<Boolean>()

    private val gapH = context.dp(GAP_H_DP)
    private val gapV = context.dp(GAP_V_DP)
    private val gapInner = context.dp(GAP_INNER_DP)
    private val colorPrimary = ContextCompat.getColor(context, R.color.text_primary)
    private val colorHint = ContextCompat.getColor(context, R.color.text_hint)

    // onMeasure 计算好每个 child 的落点，onLayout 直接消费
    private val childLeft = ArrayList<Int>()
    private val childTop = ArrayList<Int>()

    /**
     * 设置内容并重建子视图。配对成功时逐组对齐展示；
     * 任一字段为空或数量不配时自动降级为两行展示。
     */
    fun setContent(rootCodes: String?, components: String?) {
        val pairs = parseRootPairs(rootCodes, components)
        removeAllViews()
        childIsCode.clear()
        pairedMode = pairs != null
        if (pairs != null) {
            for (pair in pairs) {
                addText(pair.first, codeSp, colorHint, isCode = true)
                addText(pair.second, componentSp, colorPrimary, isCode = false)
            }
        } else {
            val codes = rootCodes?.trim().orEmpty()
            val comps = components?.trim().orEmpty()
            if (codes.isNotEmpty()) {
                addText(codes, codeSp, colorHint, isCode = true)
            }
            if (comps.isNotEmpty()) {
                addText(comps, componentSp, colorPrimary, isCode = false)
            }
        }
        requestLayout()
        invalidate()
    }

    /** 同步字号（查询卡片与分享图片随用户字号缩放时调用）。 */
    fun setTextSizes(componentSp: Float, codeSp: Float) {
        this.componentSp = componentSp
        this.codeSp = codeSp
        for (i in childIsCode.indices) {
            val tv = getChildAt(i) as? TextView ?: continue
            tv.setTextSize(if (childIsCode[i]) codeSp else componentSp)
        }
        requestLayout()
        invalidate()
    }

    private fun addText(text: String, sp: Float, color: Int, isCode: Boolean) {
        val tv = TextView(context)
        tv.text = AppFonts.style(text) ?: text
        tv.setTextColor(color)
        tv.setTextSize(sp)
        addView(tv, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        childIsCode.add(isCode)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (childCount == 0) {
            setMeasuredDimension(0, 0)
            return
        }
        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        // UNSPECIFIED（理论场景）下不换行；其余以父约束宽度为换行边界
        val maxW = if (widthMode == MeasureSpec.UNSPECIFIED) Int.MAX_VALUE / 4 else widthSize

        childLeft.clear()
        childTop.clear()
        if (pairedMode) {
            measurePaired(widthMeasureSpec, heightMeasureSpec, maxW)
        } else {
            measureStacked(widthMeasureSpec, heightMeasureSpec, maxW)
        }
    }

    /**
     * 配对模式：每个 child 按自然宽度度量，按单元流式排版，整体换行。
     *
     * 纵向对齐：部件可能来自不同字体（TumanPUA/霞鹜文楷/遍黑体，upem 与
     * ascent/descent 各不相同），若按各自 view 顶边堆叠，度量偏小的字体
     * （如 TumanPUA ascent 仅 0.86em）基线更靠近顶边，字形会整体偏上。
     * 与旧版「单 TextView 内所有部件共享一条基线」的观感保持一致，
     * 这里按 [TextView.getBaseline] 把同一行内所有部件对齐到统一基线
     * （原则同 CharLabels.glyphCenterOffset：以统一基准度量做纵向对齐）。
     */
    private fun measurePaired(widthMeasureSpec: Int, heightMeasureSpec: Int, maxW: Int) {
        val unspecifiedW = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        for (i in 0 until childCount) {
            measureChild(getChildAt(i), unspecifiedW, heightMeasureSpec)
        }
        val cellCount = childCount / 2
        val cellLeftArr = IntArray(cellCount)
        val cellWidthArr = IntArray(cellCount)
        val cellLine = IntArray(cellCount)
        var x = 0
        var line = 0
        var contentW = 0
        // 第一遍：按宽度断行
        for (c in 0 until cellCount) {
            val codeView = getChildAt(c * 2)
            val compView = getChildAt(c * 2 + 1)
            val cw = maxOf(codeView.measuredWidth, compView.measuredWidth)
            if (x > 0 && x + cw > maxW) {
                // 当前行放不下：单元整体换到下一行
                contentW = maxOf(contentW, x - gapH)
                x = 0
                line++
            }
            cellLeftArr[c] = x
            cellWidthArr[c] = cw
            cellLine[c] = line
            x += cw + gapH
        }
        contentW = maxOf(contentW, maxOf(0, x - gapH))
        // 第二遍：按行聚合基线——行内统一基线，不同字体的部件不再上下错位
        val lineCount = line + 1
        val lineCodeH = IntArray(lineCount)
        val lineAscent = IntArray(lineCount)
        val lineDescent = IntArray(lineCount)
        val compBaselineOff = IntArray(cellCount)
        for (c in 0 until cellCount) {
            val ln = cellLine[c]
            val codeView = getChildAt(c * 2)
            val compView = getChildAt(c * 2 + 1)
            lineCodeH[ln] = maxOf(lineCodeH[ln], codeView.measuredHeight)
            // 度量后 layout 已就绪，baseline 为部件基线距自身顶边的真实距离；
            // 取不到（理论兜底）时按贴底处理
            val bl = compView.baseline
            val baseOff = if (bl >= 0) bl else compView.measuredHeight
            compBaselineOff[c] = baseOff
            lineAscent[ln] = maxOf(lineAscent[ln], baseOff)
            lineDescent[ln] = maxOf(lineDescent[ln], compView.measuredHeight - baseOff)
        }
        // 第三遍：分配行顶与内容高
        val lineTopArr = IntArray(lineCount)
        var y = 0
        for (ln in 0 until lineCount) {
            lineTopArr[ln] = y
            y += lineCodeH[ln] + gapInner + lineAscent[ln] + lineDescent[ln] + gapV
        }
        val contentH = if (lineCount > 0) y - gapV else 0

        childLeft.clear()
        childTop.clear()
        for (c in 0 until cellCount) {
            val ln = cellLine[c]
            val codeView = getChildAt(c * 2)
            val compView = getChildAt(c * 2 + 1)
            // 编码居中悬于部件正上方，部件居中于编码下方
            childLeft.add(cellLeftArr[c] + (cellWidthArr[c] - codeView.measuredWidth) / 2)
            childTop.add(lineTopArr[ln])
            childLeft.add(cellLeftArr[c] + (cellWidthArr[c] - compView.measuredWidth) / 2)
            // 部件下移使自身基线落在该行统一基线上（多字体度量差异被此处吸收）
            childTop.add(lineTopArr[ln] + lineCodeH[ln] + gapInner + (lineAscent[ln] - compBaselineOff[c]))
        }
        setMeasuredDimension(resolveSize(contentW, widthMeasureSpec), resolveSize(contentH, heightMeasureSpec))
    }

    /** 两行降级模式：child 在可用宽度内自然换行，自上而下堆叠（与旧版样式一致）。 */
    private fun measureStacked(widthMeasureSpec: Int, heightMeasureSpec: Int, maxW: Int) {
        val lineSpec = if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.EXACTLY) {
            widthMeasureSpec
        } else {
            MeasureSpec.makeMeasureSpec(maxW, MeasureSpec.AT_MOST)
        }
        var y = 0
        var contentW = 0
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            measureChild(child, lineSpec, heightMeasureSpec)
            childLeft.add(0)
            childTop.add(y)
            y += child.measuredHeight
            contentW = maxOf(contentW, child.measuredWidth)
        }
        setMeasuredDimension(resolveSize(contentW, widthMeasureSpec), resolveSize(y, heightMeasureSpec))
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            val cl = if (i < childLeft.size) childLeft[i] else 0
            val ct = if (i < childTop.size) childTop[i] else 0
            child.layout(cl, ct, cl + child.measuredWidth, ct + child.measuredHeight)
        }
    }
}
