// AI-generated with Claude Code, 2026-11-01
package com.team3.rocky.server

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.File

/**
 * 서버에서 받은 결과를 폰에 저장해 둔 것. 폴더는 filesDir/scores/<작업 id>/.
 * 작업 id(Work.id)로 찾기 때문에 보관함·밴드 공유(같은 폰)에서도 그대로 열린다.
 * 폴더가 없는 작업은 데모 결과(assets/demo)로 보여준다.
 */
class LocalScore(val dir: File, val result: JobResult) {
    /** part가 null이면 밴드 전체 */
    fun pdf(part: String?): File? = file((if (part == null) result.fullBand else result.instruments[part])?.pdf)

    fun midi(part: String?): File? = file((if (part == null) result.fullBand else result.instruments[part])?.midi)

    val generated: Set<String> get() = result.generatedParts.toSet()

    /** 내보내기 목록: (파일, 이름표) */
    fun exportFiles(kor: (String) -> String): List<Pair<File, String>> = buildList {
        pdf(null)?.let { add(it to "밴드 전체 악보") }
        midi(null)?.let { add(it to "밴드 전체 MIDI") }
        result.instruments.keys.sorted().forEach { p -> pdf(p)?.let { add(it to "${kor(p)} 악보") } }
    }

    private fun file(rel: String?): File? = rel?.let { File(dir, it) }?.takeIf { it.isFile }
}

object ScoreStore {
    private const val RESULT = "result.json"

    fun dir(ctx: Context, workId: Long) = File(ctx.filesDir, "scores/$workId")

    fun load(ctx: Context, workId: Long): LocalScore? {
        val d = dir(ctx, workId)
        val json = File(d, RESULT).takeIf { it.isFile } ?: return null
        return runCatching { LocalScore(d, ApiJson.result(json.readText())) }.getOrNull()
    }

    /** 결과 PDF·MIDI를 모두 받아 저장한다. 네트워크를 쓰므로 IO 스레드에서. */
    fun download(ctx: Context, server: ScoreServer, jobId: String, workId: Long): LocalScore {
        val result = server.result(jobId)
        val d = dir(ctx, workId).apply { mkdirs() }
        val paths = buildSet {
            add(result.fullBand.pdf); result.fullBand.midi?.let(::add)
            result.instruments.values.forEach { add(it.pdf); it.midi?.let(::add) }
        }
        for (path in paths) server.download(jobId, path, File(d, path))
        // result.json은 마지막에 써서, 이 파일이 있으면 받기가 끝난 것으로 본다.
        File(d, RESULT).writeText(toJson(result))
        return LocalScore(d, result)
    }

    private fun toJson(r: JobResult): String {
        fun part(p: ScorePart) = org.json.JSONObject().put("pdf", p.pdf).put("midi", p.midi ?: org.json.JSONObject.NULL)
            .put("pages", p.pages ?: org.json.JSONObject.NULL)
        val inst = org.json.JSONObject()
        r.instruments.forEach { (k, v) -> inst.put(k, part(v)) }
        return org.json.JSONObject()
            .put("id", r.id).put("title", r.title)
            .put("full_band", part(r.fullBand)).put("instruments", inst)
            .put("original_parts", org.json.JSONArray(r.originalParts))
            .put("generated_parts", org.json.JSONArray(r.generatedParts))
            .toString()
    }
}

/** PDF 악보를 화면 너비에 맞춘 그림으로 바꾼다. 페이지가 많으면 오래 걸리니 IO 스레드에서. */
object PdfPages {
    fun render(file: File, widthPx: Int, maxPages: Int = 30): List<Bitmap> =
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRenderer(fd).use { pdf ->
                (0 until minOf(pdf.pageCount, maxPages)).map { i ->
                    pdf.openPage(i).use { page ->
                        val h = (widthPx.toFloat() * page.height / page.width).toInt().coerceAtLeast(1)
                        Bitmap.createBitmap(widthPx, h, Bitmap.Config.ARGB_8888).also { bmp ->
                            bmp.eraseColor(Color.WHITE)
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        }
                    }
                }
            }
        }
}
