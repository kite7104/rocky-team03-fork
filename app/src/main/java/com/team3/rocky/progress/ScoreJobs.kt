package com.team3.rocky.progress

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.team3.rocky.Work
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 백그라운드에서 만들고 있는 악보 한 곡. */
class ScoreJob(
    val id: Long,
    val title: String,
    val lineup: List<String>,
    val original: List<String>,
) {
    var progress by mutableStateOf<JobProgress?>(null)
        internal set
    /** 결과 화면에서 보관함에 저장했는지 */
    var saved by mutableStateOf(false)
    internal var runner: Job? = null

    val done get() = progress?.done == true

    /** 완성되면 결과 화면에서 쓰는 작업 정보 (id가 같아서 저장 여부를 맞출 수 있다). */
    fun toWork() = Work(
        id = id, title = title, instruments = lineup,
        date = SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.KOREA).format(Date(id)),
        original = original,
    )
}

/**
 * 악보 만들기 작업 목록. 화면을 옮겨 다녀도 계속 돌아가도록 화면 밖(앱 전체)에서 관리한다.
 * 지금은 [DemoProgress]로 흉내 내고, 서버가 생기면 [source]를 서버 구현으로 바꾼다.
 * 앱을 완전히 끄면 목록은 사라진다.
 */
object ScoreJobs {
    val jobs = mutableStateListOf<ScoreJob>()
    var source: ProgressSource = DemoProgress
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    val runningCount get() = jobs.count { !it.done }

    fun start(ctx: Context, title: String, lineup: List<String>, original: List<String>): ScoreJob {
        val app = ctx.applicationContext
        val job = ScoreJob(System.currentTimeMillis(), title, lineup, original)
        jobs.add(0, job)
        job.runner = scope.launch {
            source.track(job.id.toString()).collect { job.progress = it }
            Toast.makeText(app, "'${job.title}' 악보가 완성됐어요. 진행상황 탭에서 볼 수 있어요", Toast.LENGTH_LONG).show()
        }
        return job
    }

    fun find(id: Long) = jobs.firstOrNull { it.id == id }

    /** 목록에서 지운다. 아직 만드는 중이면 취소한다. */
    fun remove(job: ScoreJob) {
        job.runner?.cancel()
        jobs.remove(job)
    }

    /** 로그아웃하면 다른 회원에게 보이지 않게 비운다. */
    fun clear() {
        jobs.forEach { it.runner?.cancel() }
        jobs.clear()
    }
}
