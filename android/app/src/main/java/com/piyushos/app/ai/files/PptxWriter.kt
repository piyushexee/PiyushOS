package com.piyushos.app.ai.files

import java.io.File

/**
 * Minimal but valid PPTX generator (16:9, dark navy theme - python-pptx version jaisa look).
 * Pure JVM - Android ke bina bhi test ho sakta hai.
 */
object PptxWriter {

    data class Slide(val title: String, val subtitle: String? = null, val bullets: List<String> = emptyList())

    const val EMU = 914400L // per inch
    const val SLIDE_W = 12192000L // 13.333in
    const val SLIDE_H = 6858000L // 7.5in

    private const val NAVY = "141E3C"
    private const val ACCENT = "4FC3F7"
    private const val WHITE = "FFFFFF"
    private const val DARK = "202A44"
    private const val GREY = "8A97B5"

    fun emu(v: Double): Long = (v * EMU).toLong()

    // ---------------- shape helpers ----------------

    private fun rectShape(id: Int, name: String, x: Long, y: Long, cx: Long, cy: Long, fill: String): String = """
        <p:sp>
          <p:nvSpPr><p:cNvPr id="$id" name="$name"/><p:cNvSpPr/><p:nvPr/></p:nvSpPr>
          <p:spPr>
            <a:xfrm><a:off x="$x" y="$y"/><a:ext cx="$cx" cy="$cy"/></a:xfrm>
            <a:prstGeom prst="rect"><a:avLst/></a:prstGeom>
            <a:solidFill><a:srgbClr val="$fill"/></a:solidFill>
            <a:ln><a:noFill/></a:ln>
          </p:spPr>
          <p:txBody><a:bodyPr/><a:lstStyle/><a:p/></p:txBody>
        </p:sp>
    """.trimIndent()

    private fun run(text: String, sizePt: Int, color: String, bold: Boolean = false, italic: Boolean = false): String {
        val b = if (bold) " b=\"1\"" else ""
        val i = if (italic) " i=\"1\"" else ""
        return """
            <a:r>
              <a:rPr lang="en-US" altLang="hi-IN" sz="${sizePt * 100}"$b$i dirty="0">
                <a:solidFill><a:srgbClr val="$color"/></a:solidFill>
                <a:latin typeface="Calibri"/><a:ea typeface=""/>
              </a:rPr>
              <a:t>${Ooxml.esc(text)}</a:t>
            </a:r>
        """.trimIndent()
    }

    private fun para(runs: String, align: String? = null, spcAftPt: Int? = null): String {
        val pPr = buildString {
            if (align != null) append("<a:pPr algn=\"$align\">") else append("<a:pPr>")
            if (spcAftPt != null) append("<a:spcAft><a:spcPts val=\"${spcAftPt * 100}\"/></a:spcAft>")
            append("</a:pPr>")
        }
        return "<a:p>$pPr$runs</a:p>"
    }

    private fun textBox(
        id: Int, name: String, x: Long, y: Long, cx: Long, cy: Long,
        paras: String, anchor: String? = null, wrap: Boolean = true
    ): String {
        val wrapAttr = if (wrap) "square" else "none"
        val anchorAttr = anchor?.let { " anchor=\"$it\"" } ?: ""
        return """
            <p:sp>
              <p:nvSpPr><p:cNvPr id="$id" name="$name"/><p:cNvSpPr txBox="1"/><p:nvPr/></p:nvSpPr>
              <p:spPr>
                <a:xfrm><a:off x="$x" y="$y"/><a:ext cx="$cx" cy="$cy"/></a:xfrm>
                <a:prstGeom prst="rect"><a:avLst/></a:prstGeom>
                <a:noFill/>
              </p:spPr>
              <p:txBody>
                <a:bodyPr wrap="$wrapAttr"$anchorAttr lIns="0" tIns="0" rIns="0" bIns="0"/>
                <a:lstStyle/>
                $paras
              </p:txBody>
            </p:sp>
        """.trimIndent()
    }

    private fun slideXml(bg: String, shapes: String): String = """
        <p:sld xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"
               xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"
               xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main">
          <p:cSld>
            <p:bg><p:bgPr><a:solidFill><a:srgbClr val="$bg"/></a:solidFill><a:effectLst/></p:bgPr></p:bg>
            <p:spTree>
              <p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr>
              <p:grpSpPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="0" cy="0"/><a:chOff x="0" y="0"/><a:chExt cx="0" cy="0"/></a:xfrm></p:grpSpPr>
              $shapes
            </p:spTree>
          </p:cSld>
        </p:sld>
    """.trimIndent()

    // ---------------- slides ----------------

    private fun titleSlide(s: Slide): String {
        val shapes = buildString {
            append(rectShape(2, "Accent", emu(0.9), emu(4.4), emu(2.2), emu(0.09), ACCENT))
            append(textBox(3, "Title", emu(0.9), emu(2.5), emu(11.5), emu(1.8),
                para(run(s.title.ifEmpty { "Presentation" }, 50, WHITE, bold = true))))
            if (!s.subtitle.isNullOrBlank()) {
                append(textBox(4, "Subtitle", emu(0.9), emu(4.7), emu(11.5), emu(0.8),
                    para(run(s.subtitle, 22, ACCENT))))
            }
            append(textBox(5, "Footer", emu(0.9), emu(6.75), emu(11.5), emu(0.5),
                para(run("PiyushOS se banaya hua", 12, GREY))))
        }
        return slideXml(NAVY, shapes)
    }

    private fun contentSlide(s: Slide): String {
        val shapes = buildString {
            // white background handled by slideXml; navy header bar
            append(rectShape(2, "Header", 0, 0, SLIDE_W, emu(1.35), NAVY))
            append(textBox(3, "Title", emu(0.9), emu(0.33), emu(11.5), emu(0.8),
                para(run(s.title, 30, WHITE, bold = true))))
            append(rectShape(4, "Accent", emu(0.9), emu(1.35), emu(1.6), emu(0.07), ACCENT))
            val paras = s.bullets.take(8).joinToString("") {
                para(run("▪  $it", 22, DARK), spcAftPt = 14)
            }
            append(textBox(5, "Body", emu(0.9), emu(1.9), emu(11.5), emu(5.1), paras))
        }
        return slideXml(WHITE, shapes)
    }

    private fun thanksSlide(): String {
        val shapes = textBox(2, "Thanks", emu(1.5), emu(3.0), emu(10.3), emu(1.5),
            para(run("Shukriya!", 48, WHITE, bold = true), align = "ctr"), anchor = "ctr")
        return slideXml(NAVY, shapes)
    }

    // ---------------- document parts ----------------

    private val SLIDE_RELS = """
        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
          <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideLayout" Target="../slideLayouts/slideLayout1.xml"/>
        </Relationships>
    """.trimIndent()

    private val MASTER = """
        <p:sldMaster xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"
                     xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"
                     xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main">
          <p:cSld>
            <p:bg><p:bgPr><a:solidFill><a:srgbClr val="FFFFFF"/></a:solidFill><a:effectLst/></p:bgPr></p:bg>
            <p:spTree>
              <p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr>
              <p:grpSpPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="0" cy="0"/><a:chOff x="0" y="0"/><a:chExt cx="0" cy="0"/></a:xfrm></p:grpSpPr>
            </p:spTree>
          </p:cSld>
          <p:clrMap bg1="lt1" tx1="dk1" bg2="lt2" tx2="dk2" accent1="accent1" accent2="accent2"
                    accent3="accent3" accent4="accent4" accent5="accent5" accent6="accent6"
                    hlink="hlink" folHlink="folHlink"/>
          <p:sldLayoutIdLst><p:sldLayoutId id="2147483649" r:id="rId1"/></p:sldLayoutIdLst>
        </p:sldMaster>
    """.trimIndent()

    private val MASTER_RELS = """
        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
          <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideLayout" Target="../slideLayouts/slideLayout1.xml"/>
          <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/theme" Target="../theme/theme1.xml"/>
        </Relationships>
    """.trimIndent()

    private val LAYOUT = """
        <p:sldLayout xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"
                     xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"
                     xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main" type="blank" preserve="1">
          <p:cSld name="Blank">
            <p:spTree>
              <p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr>
              <p:grpSpPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="0" cy="0"/><a:chOff x="0" y="0"/><a:chExt cx="0" cy="0"/></a:xfrm></p:grpSpPr>
            </p:spTree>
          </p:cSld>
          <p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr>
        </p:sldLayout>
    """.trimIndent()

    private val LAYOUT_RELS = """
        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
          <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideMaster" Target="../slideMasters/slideMaster1.xml"/>
        </Relationships>
    """.trimIndent()

    private val THEME = """
        <a:theme xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" name="PiyushOS">
          <a:themeElements>
            <a:clrScheme name="Piyush">
              <a:dk1><a:srgbClr val="202A44"/></a:dk1>
              <a:lt1><a:srgbClr val="FFFFFF"/></a:lt1>
              <a:dk2><a:srgbClr val="141E3C"/></a:dk2>
              <a:lt2><a:srgbClr val="F2F2F2"/></a:lt2>
              <a:accent1><a:srgbClr val="4FC3F7"/></a:accent1>
              <a:accent2><a:srgbClr val="EF5350"/></a:accent2>
              <a:accent3><a:srgbClr val="66BB6A"/></a:accent3>
              <a:accent4><a:srgbClr val="FFA726"/></a:accent4>
              <a:accent5><a:srgbClr val="B388FF"/></a:accent5>
              <a:accent6><a:srgbClr val="4DD0E1"/></a:accent6>
              <a:hlink><a:srgbClr val="4FC3F7"/></a:hlink>
              <a:folHlink><a:srgbClr val="8A97B5"/></a:folHlink>
            </a:clrScheme>
            <a:fontScheme name="Piyush">
              <a:majorFont><a:latin typeface="Calibri Light"/><a:ea typeface=""/><a:cs typeface=""/></a:majorFont>
              <a:minorFont><a:latin typeface="Calibri"/><a:ea typeface=""/><a:cs typeface=""/></a:minorFont>
            </a:fontScheme>
            <a:fmtScheme name="Office">
              <a:fillStyleLst>
                <a:solidFill><a:schemeClr val="phClr"/></a:solidFill>
                <a:solidFill><a:schemeClr val="phClr"/></a:solidFill>
                <a:solidFill><a:schemeClr val="phClr"/></a:solidFill>
              </a:fillStyleLst>
              <a:lnStyleLst>
                <a:ln w="6350"><a:solidFill><a:schemeClr val="phClr"/></a:solidFill></a:ln>
                <a:ln w="12700"><a:solidFill><a:schemeClr val="phClr"/></a:solidFill></a:ln>
                <a:ln w="19050"><a:solidFill><a:schemeClr val="phClr"/></a:solidFill></a:ln>
              </a:lnStyleLst>
              <a:effectStyleLst>
                <a:effectStyle><a:effectLst/></a:effectStyle>
                <a:effectStyle><a:effectLst/></a:effectStyle>
                <a:effectStyle><a:effectLst/></a:effectStyle>
              </a:effectStyleLst>
              <a:bgFillStyleLst>
                <a:solidFill><a:schemeClr val="phClr"/></a:solidFill>
                <a:solidFill><a:schemeClr val="phClr"/></a:solidFill>
                <a:solidFill><a:schemeClr val="phClr"/></a:solidFill>
              </a:bgFillStyleLst>
            </a:fmtScheme>
          </a:themeElements>
        </a:theme>
    """.trimIndent()

    private val ROOT_RELS = """
        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
          <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="ppt/presentation.xml"/>
          <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties" Target="docProps/core.xml"/>
          <Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/extended-properties" Target="docProps/app.xml"/>
        </Relationships>
    """.trimIndent()

    private val CORE = """
        <cp:coreProperties xmlns:cp="http://schemas.openxmlformats.org/package/2006/metadata/core-properties"
                           xmlns:dc="http://purl.org/dc/elements/1.1/"
                           xmlns:dcterms="http://purl.org/dc/terms/"
                           xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
          <dc:creator>PiyushOS</dc:creator>
          <cp:lastModifiedBy>PiyushOS</cp:lastModifiedBy>
        </cp:coreProperties>
    """.trimIndent()

    private val APP = """
        <Properties xmlns="http://schemas.openxmlformats.org/officeDocument/2006/extended-properties"
                    xmlns:vt="http://schemas.openxmlformats.org/officeDocument/2006/docPropsVTypes">
          <Application>PiyushOS</Application>
        </Properties>
    """.trimIndent()

    // ---------------- main entry ----------------

    /**
     * @param topic presentation ka topic
     * @param slides outline (pehla slide = title slide, baaki content, auto thank-you last me)
     */
    fun build(topic: String, slides: List<Slide>, out: File): File {
        val all = slides.ifEmpty { listOf(Slide(topic)) }

        val slideXmls = mutableListOf(titleSlide(all[0]))
        slideXmls += all.drop(1).map { contentSlide(it) }
        slideXmls += thanksSlide()
        val n = slideXmls.size

        val contentTypes = buildString {
            append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            append("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">")
            append("<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>")
            append("<Default Extension=\"xml\" ContentType=\"application/xml\"/>")
            append("<Override PartName=\"/ppt/presentation.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml\"/>")
            append("<Override PartName=\"/ppt/slideMasters/slideMaster1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.slideMaster+xml\"/>")
            append("<Override PartName=\"/ppt/slideLayouts/slideLayout1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.slideLayout+xml\"/>")
            append("<Override PartName=\"/ppt/theme/theme1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.theme+xml\"/>")
            for (i in 1..n) {
                append("<Override PartName=\"/ppt/slides/slide$i.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.slide+xml\"/>")
            }
            append("<Override PartName=\"/docProps/core.xml\" ContentType=\"application/vnd.openxmlformats-package.core-properties+xml\"/>")
            append("<Override PartName=\"/docProps/app.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.extended-properties+xml\"/>")
            append("</Types>")
        }

        val sldIdLst = (1..n).joinToString("") { "<p:sldId id=\"${255 + it}\" r:id=\"rId${1 + it}\"/>" }
        val presentation = """
            <p:presentation xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"
                            xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"
                            xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main">
              <p:sldMasterIdLst><p:sldMasterId id="2147483648" r:id="rId1"/></p:sldMasterIdLst>
              <p:sldIdLst>$sldIdLst</p:sldIdLst>
              <p:sldSz cx="$SLIDE_W" cy="$SLIDE_H"/>
              <p:notesSz cx="6858000" cy="9144000"/>
            </p:presentation>
        """.trimIndent()

        val presRels = buildString {
            append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            append("<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">")
            append("<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideMaster\" Target=\"slideMasters/slideMaster1.xml\"/>")
            for (i in 1..n) {
                append("<Relationship Id=\"rId${1 + i}\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide\" Target=\"slides/slide$i.xml\"/>")
            }
            append("</Relationships>")
        }

        val entries = LinkedHashMap<String, ByteArray>()
        entries["[Content_Types].xml"] = Ooxml.xml(contentTypes)
        entries["_rels/.rels"] = Ooxml.xml(ROOT_RELS)
        entries["docProps/core.xml"] = Ooxml.xml(CORE)
        entries["docProps/app.xml"] = Ooxml.xml(APP)
        entries["ppt/presentation.xml"] = Ooxml.xml(presentation)
        entries["ppt/_rels/presentation.xml.rels"] = Ooxml.xml(presRels)
        entries["ppt/slideMasters/slideMaster1.xml"] = Ooxml.xml(MASTER)
        entries["ppt/slideMasters/_rels/slideMaster1.xml.rels"] = Ooxml.xml(MASTER_RELS)
        entries["ppt/slideLayouts/slideLayout1.xml"] = Ooxml.xml(LAYOUT)
        entries["ppt/slideLayouts/_rels/slideLayout1.xml.rels"] = Ooxml.xml(LAYOUT_RELS)
        entries["ppt/theme/theme1.xml"] = Ooxml.xml(THEME)
        for ((i, sxml) in slideXmls.withIndex()) {
            entries["ppt/slides/slide${i + 1}.xml"] = Ooxml.xml(sxml)
            entries["ppt/slides/_rels/slide${i + 1}.xml.rels"] = Ooxml.xml(SLIDE_RELS)
        }
        Ooxml.zip(entries, out)
        return out
    }
}
