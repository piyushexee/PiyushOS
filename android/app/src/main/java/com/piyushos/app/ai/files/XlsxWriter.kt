package com.piyushos.app.ai.files

import java.io.File

/**
 * Minimal but valid XLSX generator (styled headers - openpyxl version jaisa look).
 * Pure JVM - Android ke bina bhi test ho sakta hai.
 */
object XlsxWriter {

    data class Sheet(
        val name: String,
        val headers: List<String> = emptyList(),
        val rows: List<List<Any?>> = emptyList()
    )

    fun colLetter(c: Int): String {
        var n = c
        var s = ""
        while (n > 0) {
            var rem = (n - 1) % 26
            s = "ABCDEFGHIJKLMNOPQRSTUVWXYZ".substring(rem, rem + 1) + s
            n = (n - rem - 1) / 26
        }
        return s
    }

    fun sanitizeSheetName(s: String, fallback: String = "Sheet"): String {
        val clean = s.filterNot { it in "[]:*?/\\" }.trim().take(31)
        return clean.ifEmpty { fallback }
    }

    private fun isNumber(v: Any?): Boolean {
        if (v is Number) return true
        if (v is String) return v.toDoubleOrNull() != null && v.isNotBlank()
        return false
    }

    private fun cellXml(ref: String, v: Any?, style: Int): String {
        if (v == null || v.toString().isEmpty()) {
            return if (style == 0) "" else "<c r=\"$ref\" s=\"$style\"/>"
        }
        if (isNumber(v)) {
            val num = if (v is Number) v.toString() else v.toString().toDouble().toString()
            return "<c r=\"$ref\" s=\"$style\"><v>$num</v></c>"
        }
        return "<c r=\"$ref\" s=\"$style\" t=\"inlineStr\"><is><t xml:space=\"preserve\">${Ooxml.esc(v.toString())}</t></is></c>"
    }

    fun build(title: String, sheets: List<Sheet>, out: File): File {
        val shs = sheets.ifEmpty { listOf(Sheet("Sheet")) }.take(8)

        val sheetXmls = shs.map { sh ->
            val headers = sh.headers
            val rows = sh.rows.take(500)
            val lastCol = maxOf(headers.size, 1)
            val lastRow = rows.size + (if (headers.isNotEmpty()) 1 else 0)

            // column widths (python jaisa: max text length + 3, 10..42)
            val colsXml = (1..lastCol).joinToString("") { c ->
                val vals = mutableListOf(0)
                if (c - 1 < headers.size) vals.add(headers[c - 1].length)
                for (r in rows.take(200)) if (c - 1 < r.size) vals.add(r[c - 1]?.toString()?.length ?: 0)
                val w = minOf(maxOf((vals.max() ?: 0) + 3, 10), 42)
                "<col min=\"$c\" max=\"$c\" width=\"$w\" customWidth=\"1\"/>"
            }

            val rowXmls = buildString {
                if (headers.isNotEmpty()) {
                    append("<row r=\"1\" spans=\"1:$lastCol\">")
                    headers.forEachIndexed { i, h -> append(cellXml("${colLetter(i + 1)}1", h, 1)) }
                    append("</row>")
                }
                rows.forEachIndexed { ri, r ->
                    val rowNum = ri + 1 + (if (headers.isNotEmpty()) 1 else 0)
                    append("<row r=\"$rowNum\" spans=\"1:$lastCol\">")
                    r.forEachIndexed { ci, v -> append(cellXml("${colLetter(ci + 1)}$rowNum", v, 0)) }
                    append("</row>")
                }
            }

            val pane = if (headers.isNotEmpty())
                "<pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/><selection pane=\"bottomLeft\" activeCell=\"A2\" sqref=\"A2\"/>"
            else ""
            val autoFilter = if (headers.isNotEmpty() && lastRow >= 2)
                "<autoFilter ref=\"A1:${colLetter(lastCol)}$lastRow\"/>" else ""

            """
                <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"
                           xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                  <dimension ref="A1:${colLetter(lastCol)}${maxOf(lastRow, 1)}"/>
                  <sheetViews><sheetView workbookViewId="0">$pane</sheetView></sheetViews>
                  <sheetFormatPr defaultRowHeight="15"/>
                  <cols>$colsXml</cols>
                  <sheetData>$rowXmls</sheetData>
                  $autoFilter
                </worksheet>
            """.trimIndent()
        }

        val contentTypes = buildString {
            append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            append("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">")
            append("<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>")
            append("<Default Extension=\"xml\" ContentType=\"application/xml\"/>")
            append("<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>")
            append("<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>")
            shs.forEachIndexed { i, _ ->
                append("<Override PartName=\"/xl/worksheets/sheet${i + 1}.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>")
            }
            append("</Types>")
        }

        val rootRels = """
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
              <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
            </Relationships>
        """.trimIndent()

        val sheetsXml = shs.mapIndexed { i, sh ->
            "<sheet name=\"${Ooxml.esc(sanitizeSheetName(sh.name))}\" sheetId=\"${i + 1}\" r:id=\"rId${i + 1}\"/>"
        }.joinToString("")

        val workbook = """
            <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"
                      xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
              <sheets>$sheetsXml</sheets>
            </workbook>
        """.trimIndent()

        val wbRels = buildString {
            append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            append("<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">")
            shs.forEachIndexed { i, _ ->
                append("<Relationship Id=\"rId${i + 1}\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet${i + 1}.xml\"/>")
            }
            append("<Relationship Id=\"rId${shs.size + 1}\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>")
            append("</Relationships>")
        }

        val styles = """
            <styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
              <fonts count="2">
                <font><sz val="11"/><name val="Calibri"/></font>
                <font><b/><sz val="12"/><color rgb="FFFFFFFF"/><name val="Calibri"/></font>
              </fonts>
              <fills count="3">
                <fill><patternFill patternType="none"/></fill>
                <fill><patternFill patternType="gray125"/></fill>
                <fill><patternFill patternType="solid"><fgColor rgb="FF141E3C"/><bgColor indexed="64"/></patternFill></fill>
              </fills>
              <borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>
              <cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
              <cellXfs count="2">
                <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
                <xf numFmtId="0" fontId="1" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1" applyAlignment="1"><alignment horizontal="center" vertical="center"/></xf>
              </cellXfs>
              <cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles>
            </styleSheet>
        """.trimIndent()

        val entries = LinkedHashMap<String, ByteArray>()
        entries["[Content_Types].xml"] = Ooxml.xml(contentTypes)
        entries["_rels/.rels"] = Ooxml.xml(rootRels)
        entries["xl/workbook.xml"] = Ooxml.xml(workbook)
        entries["xl/_rels/workbook.xml.rels"] = Ooxml.xml(wbRels)
        entries["xl/styles.xml"] = Ooxml.xml(styles)
        sheetXmls.forEachIndexed { i, sxml -> entries["xl/worksheets/sheet${i + 1}.xml"] = Ooxml.xml(sxml) }
        Ooxml.zip(entries, out)
        return out
    }
}
