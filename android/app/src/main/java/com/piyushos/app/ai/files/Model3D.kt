package com.piyushos.app.ai.files

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 3D model generator (STL binary + GLB) - trimesh recipes ka Kotlin port.
 * Pure JVM - Android ke bina bhi test ho sakta hai.
 */
object Model3D {

    // ---------- geometry ----------

    class Mesh(
        val verts: FloatArray,   // xyz triplets
        val colors: IntArray,    // ARGB per vertex-triangle (flat)
    ) {
        val triCount: Int get() = verts.size / 9
    }

    data class V(val x: Double, val y: Double, val z: Double) {
        fun rotY(deg: Double): V {
            val r = Math.toRadians(deg); val c = cos(r); val s = -sin(r)
            // rotation around Y (trimesh convention)
            return V(x * c + z * s, y, -x * s + z * c)
        }
        fun rotX(deg: Double): V {
            val r = Math.toRadians(deg); val c = cos(r); val s = sin(r)
            return V(x, y * c - z * s, y * s + z * c)
        }
        fun t(dx: Double, dy: Double, dz: Double) = V(x + dx, y + dy, z + dz)
    }

    private fun tris(parts: List<TriPart>): Mesh {
        val verts = ArrayList<Float>()
        val colors = ArrayList<Int>()
        for (p in parts) {
            for (t in p.tris) {
                verts.add(t[0].x.toFloat()); verts.add(t[0].y.toFloat()); verts.add(t[0].z.toFloat())
                verts.add(t[1].x.toFloat()); verts.add(t[1].y.toFloat()); verts.add(t[1].z.toFloat())
                verts.add(t[2].x.toFloat()); verts.add(t[2].y.toFloat()); verts.add(t[2].z.toFloat())
                repeat(3) { colors.add(p.color) }
            }
        }
        return Mesh(verts.toFloatArray(), colors.toIntArray())
    }

    data class TriPart(val tris: List<Array<V>>, val color: Int)

    private fun argb(hex: String): Int {
        val c = hex.removePrefix("#")
        return (0xFF shl 24) or (c.toInt(16) shl 0)
    }

    private fun box(w: Double, h: Double, d: Double, color: String, xform: ((V) -> V)? = null): TriPart {
        val v = listOf(
            V(-w / 2, 0.0, -d / 2), V(w / 2, 0.0, -d / 2), V(w / 2, 0.0, d / 2), V(-w / 2, 0.0, d / 2),
            V(-w / 2, h, -d / 2), V(w / 2, h, -d / 2), V(w / 2, h, d / 2), V(-w / 2, h, d / 2)
        ).map { xform?.invoke(it) ?: it }
        val t = listOf(
            arrayOf(v[0], v[2], v[1]), arrayOf(v[0], v[3], v[2]),
            arrayOf(v[0], v[1], v[5]), arrayOf(v[0], v[5], v[4]),
            arrayOf(v[2], v[3], v[6]), arrayOf(v[2], v[6], v[7]),
            arrayOf(v[1], v[2], v[5]), arrayOf(v[3], v[0], v[4]),
            arrayOf(v[4], v[5], v[6]), arrayOf(v[4], v[6], v[7]),
            arrayOf(v[7], v[6], v[2]), arrayOf(v[7], v[2], v[3]),
            arrayOf(v[1], v[7], v[3]), arrayOf(v[1], v[4], v[7])
        )
        return TriPart(t, argb(color))
    }

    private fun prism(w: Double, h: Double, d: Double, color: String, xform: ((V) -> V)? = null): TriPart {
        val v = listOf(
            V(-w / 2, 0.0, -d / 2), V(w / 2, 0.0, -d / 2), V(w / 2, 0.0, d / 2), V(-w / 2, 0.0, d / 2),
            V(-w / 2, h, 0.0), V(w / 2, h, 0.0)
        ).map { xform?.invoke(it) ?: it }
        val t = listOf(
            arrayOf(v[0], v[2], v[1]), arrayOf(v[0], v[3], v[2]),
            arrayOf(v[0], v[1], v[5]), arrayOf(v[0], v[5], v[4]),
            arrayOf(v[2], v[3], v[4]), arrayOf(v[2], v[4], v[5]),
            arrayOf(v[1], v[2], v[5]), arrayOf(v[3], v[0], v[4])
        )
        return TriPart(t, argb(color))
    }

    private fun cylinder(r: Double, h: Double, seg: Int = 40, color: String, xform: ((V) -> V)? = null): TriPart {
        // trimesh cylinder: base -h/2..h/2 along Y, centered at origin
        val y0 = -h / 2; val y1 = h / 2
        val tris = ArrayList<Array<V>>()
        for (i in 0 until seg) {
            val a0 = 2 * PI * i / seg; val a1 = 2 * PI * (i + 1) / seg
            val b0 = V(r * cos(a0), y0, r * sin(a0)); val b1 = V(r * cos(a1), y0, r * sin(a1))
            val t0 = V(r * cos(a0), y1, r * sin(a0)); val t1 = V(r * cos(a1), y1, r * sin(a1))
            val c = V(0.0, y0, 0.0); val ct = V(0.0, y1, 0.0)
            tris += arrayOf(b0, t0, t1); tris += arrayOf(b0, t1, b1)
            tris += arrayOf(b0, b1, c); tris += arrayOf(t0, ct, t1)
        }
        val mapped = tris.map { it.map { v -> xform?.invoke(v) ?: v }.toTypedArray() }
        return TriPart(mapped, argb(color))
    }

    private fun cone(r: Double, h: Double, seg: Int = 40, color: String, xform: ((V) -> V)? = null): TriPart {
        val y0 = -h / 2; val y1 = h / 2
        val tris = ArrayList<Array<V>>()
        val apex = V(0.0, y1, 0.0)
        val c = V(0.0, y0, 0.0)
        for (i in 0 until seg) {
            val a0 = 2 * PI * i / seg; val a1 = 2 * PI * (i + 1) / seg
            val b0 = V(r * cos(a0), y0, r * sin(a0)); val b1 = V(r * cos(a1), y0, r * sin(a1))
            tris += arrayOf(b0, b1, apex)
            tris += arrayOf(b0, c, b1)
        }
        val mapped = tris.map { it.map { v -> xform?.invoke(v) ?: v }.toTypedArray() }
        return TriPart(mapped, argb(color))
    }

    private fun icosphere(r: Double, color: String, xform: ((V) -> V)? = null): TriPart {
        val t = (1.0 + sqrt(5.0)) / 2.0
        var verts = listOf(
            V(-1.0, t, 0.0), V(1.0, t, 0.0), V(-1.0, -t, 0.0), V(1.0, -t, 0.0),
            V(0.0, -1.0, t), V(0.0, 1.0, t), V(0.0, -1.0, -t), V(0.0, 1.0, -t),
            V(t, 0.0, -1.0), V(t, 0.0, 1.0), V(-t, 0.0, -1.0), V(-t, 0.0, 1.0)
        ).map { norm(it) }.toMutableList()
        var faces = listOf(
            intArrayOf(0, 11, 5), intArrayOf(0, 5, 1), intArrayOf(0, 1, 7), intArrayOf(0, 7, 10), intArrayOf(0, 10, 11),
            intArrayOf(1, 5, 9), intArrayOf(5, 11, 4), intArrayOf(11, 10, 2), intArrayOf(10, 7, 6), intArrayOf(7, 1, 8),
            intArrayOf(3, 9, 4), intArrayOf(3, 4, 2), intArrayOf(3, 2, 6), intArrayOf(3, 6, 8), intArrayOf(3, 8, 9),
            intArrayOf(4, 9, 5), intArrayOf(2, 4, 11), intArrayOf(6, 2, 10), intArrayOf(8, 6, 7), intArrayOf(9, 8, 1)
        )
        repeat(2) {
            val cache = HashMap<Pair<Int, Int>, Int>()
            val newFaces = ArrayList<IntArray>()
            fun mid(a: Int, b: Int): Int {
                val key = if (a < b) Pair(a, b) else Pair(b, a)
                return cache.getOrPut(key) {
                    val va = verts[a]; val vb = verts[b]
                    verts.add(norm(V((va.x + vb.x) / 2, (va.y + vb.y) / 2, (va.z + vb.z) / 2)))
                    verts.size - 1
                }
            }
            for (f in faces) {
                val a = mid(f[0], f[1]); val b = mid(f[1], f[2]); val c = mid(f[2], f[0])
                newFaces += intArrayOf(f[0], a, c); newFaces += intArrayOf(f[1], b, a)
                newFaces += intArrayOf(f[2], c, b); newFaces += intArrayOf(a, b, c)
            }
            faces = newFaces
        }
        val scaled = verts.map { it.t(0.0, 0.0, 0.0) } // already unit sphere
        val tris = faces.map { f ->
            arrayOf(
                V(scaled[f[0]].x * r, scaled[f[0]].y * r, scaled[f[0]].z * r),
                V(scaled[f[1]].x * r, scaled[f[1]].y * r, scaled[f[1]].z * r),
                V(scaled[f[2]].x * r, scaled[f[2]].y * r, scaled[f[2]].z * r)
            )
        }
        val mapped = tris.map { it.map { v -> xform?.invoke(v) ?: v }.toTypedArray() }
        return TriPart(mapped, argb(color))
    }

    private fun torus(major: Double, minor: Double, color: String, xform: ((V) -> V)? = null): TriPart {
        val s1 = 32; val s2 = 16
        val tris = ArrayList<Array<V>>()
        fun pt(u: Double, v: Double) =
            V((major + minor * cos(v)) * cos(u), minor * sin(v), (major + minor * cos(v)) * sin(u))
        for (i in 0 until s1) for (j in 0 until s2) {
            val u0 = 2 * PI * i / s1; val u1 = 2 * PI * (i + 1) / s1
            val v0 = 2 * PI * j / s2; val v1 = 2 * PI * (j + 1) / s2
            tris += arrayOf(pt(u0, v0), pt(u1, v0), pt(u1, v1))
            tris += arrayOf(pt(u0, v0), pt(u1, v1), pt(u0, v1))
        }
        val mapped = tris.map { it.map { v -> xform?.invoke(v) ?: v }.toTypedArray() }
        return TriPart(mapped, argb(color))
    }

    private fun norm(v: V): V {
        val l = sqrt(v.x * v.x + v.y * v.y + v.z * v.z)
        return V(v.x / l, v.y / l, v.z / l)
    }

    // ---------- recipes (trimesh se port) ----------

    val PALETTE = mapOf(
        "red" to "#ff5252", "blue" to "#4fc3f7", "green" to "#66bb6a", "orange" to "#ffa726",
        "yellow" to "#ffee58", "purple" to "#b388ff", "grey" to "#9e9e9e", "gray" to "#9e9e9e",
        "black" to "#424242", "white" to "#fafafa", "cyan" to "#4dd0e1", "pink" to "#f48fb1",
        "brown" to "#8d6e63"
    )

    private fun rocket(color: String): List<TriPart> {
        val parts = mutableListOf<TriPart>()
        parts += cylinder(0.30, 1.1, color = color) { v -> v.t(0.0, -0.1, 0.0) }
        parts += cone(0.30, 0.45, color = "#ffa726") { v -> v.t(0.0, 0.775, 0.0) }
        parts += cylinder(0.16, 0.12, color = "#424242") { v -> v.t(0.0, -0.71, 0.0) }
        for (ang in listOf(0.0, 90.0, 180.0, 270.0)) {
            parts += box(0.05, 0.35, 0.30, "#ff7043") { v ->
                v.t(0.0, -0.55, 0.35).rotY(ang)
            }
        }
        return parts
    }

    private fun car(color: String): List<TriPart> {
        val parts = mutableListOf<TriPart>()
        parts += box(1.5, 0.35, 0.7, color) { v -> v.t(0.0, 0.3, 0.0) }
        parts += box(0.85, 0.32, 0.62, "#b3e5fc") { v -> v.t(-0.1, 0.62, 0.0) }
        for (x in listOf(0.5, -0.5)) for (z in listOf(0.28, -0.28)) {
            parts += cylinder(0.16, 0.09, color = "#212121") { v -> v.rotX(90.0).t(x, 0.16, z) }
        }
        return parts
    }

    private fun house(color: String): List<TriPart> {
        val parts = mutableListOf<TriPart>()
        parts += box(1.2, 0.8, 1.0, color) { v -> v.t(0.0, 0.4, 0.0) }
        parts += prism(1.4, 0.5, 1.15, "#ff5252") { v -> v.t(0.0, 0.8, 0.0) }
        parts += box(0.22, 0.45, 0.03, "#5d4037") { v -> v.t(0.0, 0.225, 0.51) }
        parts += box(0.25, 0.25, 0.03, "#b3e5fc") { v -> v.t(0.35, 0.5, 0.51) }
        return parts
    }

    private fun chair(color: String): List<TriPart> {
        val parts = mutableListOf<TriPart>()
        parts += box(0.6, 0.06, 0.6, color) { v -> v.t(0.0, 0.32, 0.0) }
        parts += box(0.6, 0.55, 0.06, color) { v -> v.t(0.0, 0.6, -0.27) }
        for (x in listOf(0.25, -0.25)) for (z in listOf(0.25, -0.25)) {
            parts += box(0.05, 0.32, 0.05, "#8d6e63") { v -> v.t(x, 0.16, z) }
        }
        return parts
    }

    val RECIPES: Map<String, (String) -> List<TriPart>> = mapOf(
        "rocket" to { c -> rocket(c) },
        "car" to { c -> car(c) },
        "house" to { c -> house(c) },
        "chair" to { c -> chair(c) },
        "cube" to { c -> listOf(box(0.7, 0.7, 0.7, c)) },
        "sphere" to { c -> listOf(icosphere(0.4, c)) },
        "cylinder" to { c -> listOf(cylinder(0.3, 0.8, color = c)) },
        "cone" to { c -> listOf(cone(0.35, 0.8, color = c)) },
        "torus" to { c -> listOf(torus(0.4, 0.12, c)) },
        "combo" to { c ->
            listOf(
                box(0.4, 0.4, 0.4, PALETTE.getValue("red")) { v -> v.t(-0.55, -0.2, -0.3) },
                icosphere(0.28, PALETTE.getValue("green")) { v -> v.t(0.1, -0.1, 0.0) },
                cylinder(0.3, 0.6, color = PALETTE.getValue("orange")) { v -> v.t(0.55, -0.1, -0.2) },
                cone(0.35, 0.5, color = PALETTE.getValue("purple")) { v -> v.t(-0.2, 0.35, 0.4) },
                torus(0.4, 0.12, PALETTE.getValue("cyan")) { v -> v.t(0.4, 0.15, 0.45) }
            )
        }
    )

    // ---------- exporters ----------

    fun writeStl(mesh: Mesh, out: File) {
        val buf = ByteBuffer.allocate(84 + mesh.triCount * 50).order(ByteOrder.LITTLE_ENDIAN)
        buf.position(80) // header empty
        buf.putInt(mesh.triCount)
        val v = mesh.verts
        for (i in 0 until mesh.triCount) {
            buf.putFloat(0f); buf.putFloat(0f); buf.putFloat(0f) // normal (viewer recomputes)
            for (j in 0 until 3) {
                buf.putFloat(v[i * 9 + j * 3]); buf.putFloat(v[i * 9 + j * 3 + 1]); buf.putFloat(v[i * 9 + j * 3 + 2])
            }
            buf.putShort(0)
        }
        out.writeBytes(buf.array())
    }

    private fun srgbToLinear(c8: Int): Double {
        val c = c8 / 255.0
        return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }

    fun writeGlb(mesh: Mesh, out: File) {
        val triCount = mesh.triCount
        val posBytes = mesh.verts.size * 4
        // normals: flat per-triangle
        val normals = FloatArray(mesh.verts.size)
        val v = mesh.verts
        for (i in 0 until triCount) {
            val ax = v[i * 9]; val ay = v[i * 9 + 1]; val az = v[i * 9 + 2]
            val bx = v[i * 9 + 3]; val by = v[i * 9 + 4]; val bz = v[i * 9 + 5]
            val cx = v[i * 9 + 6]; val cy = v[i * 9 + 7]; val cz = v[i * 9 + 8]
            val ux = by - ay; val uy = bz - az; val uz = bx - ax
            val vx = cy - ay; val vy = cz - az; val vz = cx - ax
            val nx = uy * vz - uz * vy; val ny = uz * vx - ux * vz; val nz = ux * vy - uy * vx
            val l = sqrt(nx * nx + ny * ny + nz * nz).toFloat()
            if (l > 0f) {
                for (j in 0 until 3) {
                    normals[(i * 9 + j * 3)] = nx / l
                    normals[(i * 9 + j * 3 + 1)] = ny / l
                    normals[(i * 9 + j * 3 + 2)] = nz / l
                }
            }
        }
        val binSize = posBytes + normals.size * 4

        // unique colors -> materials
        val colorOrder = ArrayList<Int>()
        val colorToMat = HashMap<Int, Int>()
        for (c in mesh.colors) {
            if (!colorToMat.containsKey(c)) {
                colorToMat[c] = colorOrder.size
                colorOrder.add(c)
            }
        }
        val materials = colorOrder.map { c ->
            val r = ((c shr 16) and 0xFF); val g = ((c shr 8) and 0xFF); val b = (c and 0xFF)
            """{"pbrMetallicRoughness":{"baseColorFactor":[${"%.6f".format(srgbToLinear(r))},${"%.6f".format(srgbToLinear(g))},${"%.6f".format(srgbToLinear(b))},1.0],"metallicFactor":0.0,"roughnessFactor":1.0}}"""
        }

        // meshes: one mesh per consecutive same-color run (part)
        val bvs2 = ArrayList<String>()
        val acc3 = ArrayList<String>()
        val finalMeshes2 = ArrayList<String>()
        var i = 0
        var off = 0
        while (i < triCount) {
            val color = mesh.colors[i * 3]
            var j = i
            while (j < triCount && mesh.colors[j * 3] == color) j++
            val tCount = j - i
            val byteLen = tCount * 27 * 4
            val mn = DoubleArray(3) { Double.MAX_VALUE }; val mx = DoubleArray(3) { -Double.MAX_VALUE }
            for (t in i until j) for (k in 0 until 3) for (d in 0 until 3) {
                val vv = v[t * 9 + k * 3 + d].toDouble()
                mn[d] = minOf(mn[d], vv); mx[d] = maxOf(mx[d], vv)
            }
            bvs2.add("""{"buffer":0,"byteOffset":$off,"byteLength":$byteLen,"target":34962}""")
            bvs2.add("""{"buffer":0,"byteOffset":${posBytes + off},"byteLength":$byteLen,"target":34962}""")
            val posAcc = """{"bufferView":${bvs2.size - 2},"byteOffset":0,"componentType":5126,"count":${tCount * 3},"type":"VEC3","min":[${"%.6f".format(mn[0])},${"%.6f".format(mn[1])},${"%.6f".format(mn[2])}],"max":[${"%.6f".format(mx[0])},${"%.6f".format(mx[1])},${"%.6f".format(mx[2])}]}"""
            val nrmAcc = """{"bufferView":${bvs2.size - 1},"byteOffset":0,"componentType":5126,"count":${tCount * 3},"type":"VEC3"}"""
            acc3.add(posAcc); acc3.add(nrmAcc)
            finalMeshes2.add("""{"primitives":[{"attributes":{"POSITION":${acc3.size - 2},"NORMAL":${acc3.size - 1}},"material":${colorToMat.getValue(color)},"mode":4}]}""")
            i = j
            off += byteLen
        }

        val nodesJson = (finalMeshes2.indices).joinToString(",") { """{"mesh":$it}""" }
        val json = """
            {"asset":{"version":"2.0","generator":"PiyushOS"},
             "scene":0,"scenes":[{"nodes":[${finalMeshes2.indices.joinToString(",")}]}],
             "nodes":[$nodesJson],
             "meshes":[${finalMeshes2.joinToString(",")}],
             "materials":[${materials.joinToString(",")}],
             "buffers":[{"byteLength":$binSize}],
             "bufferViews":[${bvs2.joinToString(",")}],
             "accessors":[${acc3.joinToString(",")}]}
        """.trimIndent()

        val jsonBytes = json.toByteArray(Charsets.UTF_8)
        val paddedJson = if (jsonBytes.size % 4 != 0)
            jsonBytes + ByteArray(4 - jsonBytes.size % 4) { 0x20 }
        else jsonBytes
        val total = 12 + 8 + paddedJson.size + 8 + binSize

        val buf = ByteBuffer.allocate(total).order(ByteOrder.LITTLE_ENDIAN)
        buf.put("glTF".toByteArray())
        buf.putInt(2)
        buf.putInt(total)
        buf.putInt(paddedJson.size); buf.putInt(0x4E4F534A)
        buf.put(paddedJson)
        buf.putInt(binSize); buf.putInt(0x004E4942)
        for (f in mesh.verts) buf.putFloat(f)
        for (f in normals) buf.putFloat(f)
        out.writeBytes(buf.array())
    }

    fun build(name: String, shape: String, color: String, outDir: File): Map<String, Any> {
        val sh = (shape ?: "rocket").lowercase().trim()
        var col = color
        val named = PALETTE[col?.lowercase()]
        if (named != null) col = named
        val fn = RECIPES[sh] ?: throw IllegalArgumentException("Shape '$sh' nahi milta. Available: ${RECIPES.keys.joinToString()}")
        val mesh = tris(fn(col))
        val stamp = Ooxml.stamp()
        val stl = File(outDir, "${Ooxml.safeName(name)}_${sh}_$stamp.stl")
        val glb = File(outDir, "${Ooxml.safeName(name)}_${sh}_$stamp.glb")
        writeStl(mesh, stl)
        val files = mutableListOf(stl)
        var glbOk = true
        try {
            writeGlb(mesh, glb)
            files.add(0, glb)
        } catch (e: Exception) {
            glbOk = false
            try { glb.delete() } catch (_: Exception) {}
        }
        return mapOf(
            "stl" to stl.absolutePath,
            "glb" to (if (glbOk) glb.absolutePath else ""),
            "shape" to sh,
            "name" to (if (glbOk) glb.name else stl.name),
            "files" to files.map { it.absolutePath },
            "summary" to "3D model ban gaya: " + (if (glbOk) glb.name else stl.name) + ". Shape: $sh"
        )
    }
}
