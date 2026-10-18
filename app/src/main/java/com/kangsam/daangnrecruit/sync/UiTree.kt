package com.kangsam.daangnrecruit.sync

/*
 * 당근 가져오기(프로세스 C)가 화면을 다루는 기본 도구.
 * 안드로이드 기능을 쓰지 않는 순수 코드라, uiautomator 덤프로 폰 없이 시험할 수 있음.
 */

/** 화면 한 장: 읽은 칸들과, 누르기·스크롤에 쓰는 실제 칸 묶음(handle — 폰에서는 접근성 칸 목록) */
class Snapshot(val nodes: List<UiNode>, val handle: Any? = null) {
    val page: DaangnPage by lazy { DaangnNav.page(nodes) }
    val screenNodes: List<ScreenNode> by lazy { UiTree.toScreenNodes(nodes) }
}

/**
 * 화면의 한 칸. 목록 번호는 화면 위에서부터의 차례(부모 먼저), parent는 부모 칸 번호(맨 위는 -1), depth는 깊이.
 * desc = 설명 글자(content-desc), viewId = 화면 안 이름(웹뷰는 HTML id). 위치는 화면 좌표(px)
 */
data class UiNode(
    val parent: Int,
    val depth: Int,
    val cls: String,
    val text: String,
    val desc: String = "",
    val viewId: String = "",
    val clickable: Boolean = false,
    val longClickable: Boolean = false,
    val scrollable: Boolean = false,
    val selected: Boolean = false,
    val left: Int = 0,
    val top: Int = 0,
    val right: Int = 0,
    val bottom: Int = 0,
) {
    val shortCls: String get() = cls.substringAfterLast('.')
    val isImage: Boolean get() = shortCls == "Image" || shortCls == "ImageView"
    val centerX: Int get() = (left + right) / 2
}

object UiTree {
    /** i 칸과 그 아래 칸들 (차례가 이어져 있음) */
    fun subtree(nodes: List<UiNode>, i: Int): IntRange {
        var end = i + 1
        while (end < nodes.size && nodes[end].depth > nodes[i].depth) end++
        return i until end
    }

    /** 자기 또는 가장 가까운 '누를 수 있는' 윗칸. stopAt 칸에 닿으면 멈춤 (그 칸은 안 봄) */
    fun clickableAncestor(nodes: List<UiNode>, i: Int, stopAt: Int = -1): Int? {
        var p = i
        while (p >= 0 && p != stopAt) {
            if (nodes[p].clickable) return p
            p = nodes[p].parent
        }
        return null
    }

    /** i 칸 아래(자기 포함)에서 글자가 있는 칸의 글자들 (그림 빼고, 차례대로) */
    fun texts(nodes: List<UiNode>, i: Int): List<String> =
        subtree(nodes, i).map { nodes[it] }.filter { !it.isImage && it.text.isNotBlank() }.map { it.text.trim() }

    /** 공고·지원자 화면 읽기(MyPostParser·ApplicantListParser)에 넘기는 모양으로 */
    fun toScreenNodes(nodes: List<UiNode>): List<ScreenNode> = nodes.map { ScreenNode(it.cls, it.text, it.viewId) }

    /** 개발용: 화면 모양을 칸 수로만 (이름·글자는 안 넣음) — 실행 기록에 남겨 앱이 본 화면이 덤프와 같은지 봄 */
    fun shape(nodes: List<UiNode>): String =
        "칸 ${nodes.size} · 누를 칸 ${nodes.count { it.clickable }} · 길게 누를 칸 ${nodes.count { it.longClickable }} · " +
            "스크롤 칸 ${nodes.count { it.scrollable }} · 글자 칸 ${nodes.count { it.text.isNotBlank() }}"

    /**
     * 개발용: 앱이 본 화면을 uiautomator 덤프와 같은 모양의 XML로 (시험 도구가 덤프처럼 그대로 다시 읽음).
     * extra = 칸마다 더 적을 값 (폰에서는 접근성 동작 목록)
     */
    fun toXml(nodes: List<UiNode>, extra: (Int) -> Map<String, String> = { emptyMap() }): String {
        val sb = StringBuilder("<?xml version='1.0' encoding='UTF-8' standalone='yes' ?>\n<hierarchy rotation=\"0\">\n")
        val openDepths = mutableListOf<Int>() // 아직 안 닫은 칸들의 깊이
        val childCounts = mutableListOf<Int>() // 그 칸들의 지금까지 아이 수 (index 값)
        var roots = 0
        fun attr(name: String, value: String) {
            sb.append(' ').append(name).append("=\"").append(escape(value)).append('"')
        }
        for ((i, n) in nodes.withIndex()) {
            while (openDepths.isNotEmpty() && openDepths[openDepths.size - 1] >= n.depth) {
                sb.append("</node>")
                openDepths.removeAt(openDepths.size - 1)
                childCounts.removeAt(childCounts.size - 1)
            }
            val index = if (childCounts.isEmpty()) {
                roots++
            } else {
                val last = childCounts.size - 1
                childCounts[last].also { childCounts[last] = it + 1 }
            }
            sb.append("<node index=\"").append(index).append('"')
            attr("text", n.text)
            attr("resource-id", n.viewId)
            attr("class", n.cls)
            attr("content-desc", n.desc)
            attr("clickable", n.clickable.toString())
            attr("long-clickable", n.longClickable.toString())
            attr("scrollable", n.scrollable.toString())
            attr("selected", n.selected.toString())
            attr("bounds", "[${n.left},${n.top}][${n.right},${n.bottom}]")
            for ((k, v) in extra(i)) attr(k, v)
            sb.append('>')
            openDepths.add(n.depth)
            childCounts.add(0)
        }
        repeat(openDepths.size) { sb.append("</node>") }
        sb.append("\n</hierarchy>\n")
        return sb.toString()
    }

    /** XML 글자값으로 (줄바꿈은 그대로 남게, XML에 못 쓰는 글자는 ?로 — uiautomator와 같음) */
    private fun escape(s: String): String {
        val sb = StringBuilder(s.length)
        for (c in s) {
            when {
                c == '&' -> sb.append("&amp;")
                c == '<' -> sb.append("&lt;")
                c == '>' -> sb.append("&gt;")
                c == '"' -> sb.append("&quot;")
                c == '\n' -> sb.append("&#10;")
                c == '\r' -> sb.append("&#13;")
                c == '\t' -> sb.append("&#9;")
                c < ' ' || c == '￾' || c == '￿' -> sb.append('?')
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }
}
