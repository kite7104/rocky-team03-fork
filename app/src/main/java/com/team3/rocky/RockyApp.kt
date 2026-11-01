@file:OptIn(ExperimentalMaterial3Api::class)

package com.team3.rocky

import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.team3.rocky.auth.AccountScreen
import com.team3.rocky.auth.AuthRepository
import com.team3.rocky.auth.LoginScreen
import com.team3.rocky.auth.SignUpScreen
import com.team3.rocky.auth.User
import com.team3.rocky.band.BandDetailScreen
import com.team3.rocky.band.BandJoinScreen
import com.team3.rocky.band.BandListScreen
import com.team3.rocky.band.BandRepository
import com.team3.rocky.band.LocalBandRepository
import com.team3.rocky.band.PickScoreScreen
import com.team3.rocky.band.ShareToBandScreen
import com.team3.rocky.progress.DemoProgress
import com.team3.rocky.progress.JobProgress
import com.team3.rocky.progress.ProgressSource
import com.team3.rocky.progress.Stage
import com.team3.rocky.progress.formatEta
import com.team3.rocky.server.ApiException
import com.team3.rocky.server.JobFailedException
import com.team3.rocky.server.PdfPages
import com.team3.rocky.server.RockyApi
import com.team3.rocky.server.ScoreStore
import com.team3.rocky.server.ServerProgress
import com.team3.rocky.server.ServerSettings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

// ---------- 화면 목록 ----------
sealed class Screen {
    object Login : Screen()                                       // 0. 로그인
    object SignUp : Screen()                                      // 0-1. 회원가입
    object Account : Screen()                                     // 0-2. 내 정보·로그아웃
    object Home : Screen()                                        // 1. 홈
    object Storage : Screen()                                     // 1-2. 보관함
    object Instruments : Screen()                                 // 2. 악기 선택
    object Upload : Screen()                                      // 3. 음원 업로드
    object Processing : Screen()                                  // 4. 처리 중
    // 5. 결과. 밴드에서 열면 myPart(이 밴드에서 내 악기) 악보를 먼저 보여준다.
    class Result(val work: Work, val fresh: Boolean, val myPart: String? = null) : Screen()
    class Export(val work: Work) : Screen()                       // 6. 내보내기
    object Bands : Screen()                                       // 7. 나의 밴드
    object BandJoin : Screen()                                    // 7-1. 밴드 만들기·참여
    class BandDetail(val bandId: Long) : Screen()                 // 7-2. 밴드 상세
    class ShareToBand(val work: Work) : Screen()                  // 7-3. 결과 → 밴드에 공유
    class PickScore(val bandId: Long) : Screen()                  // 7-4. 밴드 → 보관함 악보 공유
}

@Composable
fun RockyApp() {
    val ctx = LocalContext.current
    val auth = remember { AuthRepository(ctx) }
    val bands: BandRepository = remember { LocalBandRepository(ctx) }
    // 앱을 켤 때 로그인 상태가 남아 있으면 바로 홈, 아니면 로그인 화면에서 시작한다.
    var user by remember { mutableStateOf(auth.currentUser()) }
    val stack = remember { mutableStateListOf(if (user != null) Screen.Home else Screen.Login) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var songTitle by remember { mutableStateOf("") }
    // 사용자가 고른 음원. null이면 데모 곡(서버 없이 미리 준비한 결과).
    var request by remember { mutableStateOf<ScoreRequest?>(null) }

    fun newWork(id: Long, instruments: List<String>) = Work(
        id = id,
        title = songTitle,
        instruments = instruments,
        date = SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.KOREA).format(Date()),
    )

    fun go(s: Screen) { stack.add(s) }
    fun back() { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
    // 로그인·로그아웃 뒤에는 이전 화면으로 돌아갈 수 없게 쌓인 화면을 비운다.
    fun restart(s: Screen) { stack.clear(); stack.add(s) }
    fun signedIn(u: User) { user = u; restart(Screen.Home) }

    BackHandler(enabled = stack.size > 1) { back() }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (val s = stack.last()) {
            Screen.Login -> LoginScreen(auth, onLoggedIn = ::signedIn, onSignUp = { go(Screen.SignUp) })
            Screen.SignUp -> SignUpScreen(auth, onBack = { back() }, onSignedUp = ::signedIn)
            Screen.Account -> user?.let { u ->
                AccountScreen(u, onBack = { back() }, onLogout = {
                    auth.logout(); user = null; restart(Screen.Login)
                })
            }
            Screen.Home -> {
                val bandCount by produceState<Int?>(null, user) { value = user?.let { bands.myBands(it).size } }
                HomeScreen(
                    nickname = user?.nickname.orEmpty(),
                    bandCount = bandCount,
                    // 회원가입 때 고른 악기를 새 악보의 기본 선택으로 쓴다.
                    onNew = { selected = user?.instruments.orEmpty().toSet(); songTitle = ""; go(Screen.Instruments) },
                    onBands = { go(Screen.Bands) },
                    onStorage = { go(Screen.Storage) },
                    onAccount = { go(Screen.Account) },
                )
            }
            Screen.Storage -> StorageScreen(
                onBack = { back() },
                onOpen = { go(Screen.Result(it, fresh = false)) },
            )
            Screen.Instruments -> InstrumentScreen(
                selected = selected,
                onToggle = { i -> selected = if (i in selected) selected - i else selected + i },
                onBack = { back() },
                onNext = { go(Screen.Upload) },
            )
            Screen.Upload -> UploadScreen(
                onBack = { back() },
                onStart = { title, audio ->
                    songTitle = title
                    request = audio?.let { (uri, fileName) ->
                        ScoreRequest(uri, fileName, title, Demo.INSTRUMENTS.filter { it in selected })
                    }
                    go(Screen.Processing)
                },
            )
            Screen.Processing -> ProcessingScreen(
                title = songTitle,
                request = request,
                onDone = { serverWork ->
                    // serverWork가 null이면 데모 결과로 보여준다.
                    val work = serverWork?.copy(title = songTitle)
                        ?: newWork(System.currentTimeMillis(), Demo.INSTRUMENTS.filter { it in selected })
                    stack.removeAt(stack.lastIndex)          // 처리 중 화면은 뒤로가기에서 제외
                    go(Screen.Result(work, fresh = true))
                },
                onExit = { back() },
            )
            is Screen.Result -> ResultScreen(
                work = s.work,
                fresh = s.fresh,
                myPart = s.myPart,
                onBack = { back() },
                onExport = { go(Screen.Export(s.work)) },
                onShareToBand = { go(Screen.ShareToBand(s.work)) },
            )
            is Screen.Export -> ExportScreen(work = s.work, onBack = { back() })

            // ---------- 나의 밴드 ----------
            Screen.Bands -> user?.let { u ->
                BandListScreen(
                    u, bands,
                    onBack = { back() },
                    onOpen = { go(Screen.BandDetail(it)) },
                    onAdd = { go(Screen.BandJoin) },
                )
            }
            Screen.BandJoin -> user?.let { u ->
                BandJoinScreen(u, bands, onBack = { back() }, onDone = { id ->
                    // 만들기·참여 화면은 뒤로가기에서 빼고 바로 밴드 상세로 간다.
                    stack.removeAt(stack.lastIndex)
                    go(Screen.BandDetail(id))
                })
            }
            is Screen.BandDetail -> user?.let { u ->
                BandDetailScreen(
                    u, bands, s.bandId,
                    onBack = { back() },
                    onOpenScore = { work, part -> go(Screen.Result(work, fresh = false, myPart = part)) },
                    onShareScore = { go(Screen.PickScore(s.bandId)) },
                    // 밴드 멤버들이 맡은 악기를 새 악보의 기본 선택으로 쓴다.
                    onNewScore = { insts -> selected = insts; songTitle = ""; go(Screen.Instruments) },
                    onLeft = { back() },
                )
            }
            is Screen.ShareToBand -> user?.let { u ->
                ShareToBandScreen(
                    u, bands, s.work,
                    onBack = { back() },
                    onShared = { back() },
                    onAddBand = { go(Screen.BandJoin) },
                )
            }
            is Screen.PickScore -> user?.let { u ->
                PickScoreScreen(u, bands, s.bandId, onBack = { back() })
            }
        }
    }
}

// ---------- 공통 화면 틀 ----------
@Composable
fun Page(
    title: String,
    onBack: (() -> Unit)?,
    bottom: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.Bold, maxLines = 1) },
                navigationIcon = {
                    if (onBack != null) TextButton(onClick = onBack) { Text("←", fontSize = 22.sp) }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        bottomBar = {
            if (bottom != null) Box(Modifier.padding(20.dp)) { bottom() }
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().padding(horizontal = 20.dp),
            content = content,
        )
    }
}

// ---------- 1. 홈 ----------
@Composable
fun HomeScreen(
    nickname: String,
    bandCount: Int?,
    onNew: () -> Unit,
    onBands: () -> Unit,
    onStorage: () -> Unit,
    onAccount: () -> Unit,
) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val count = remember { WorkStore.load(ctx).size }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("${nickname}님, 반가워요", color = cs.onSurfaceVariant, modifier = Modifier.weight(1f), maxLines = 1)
            TextButton(onClick = onAccount) { Text("내 정보") }
        }
        Spacer(Modifier.height(16.dp))
        Text("ROCKY", fontSize = 48.sp, fontWeight = FontWeight.Black, color = cs.primary)
        Text("어떤 곡이든, 우리 밴드 악보로", fontSize = 16.sp, color = cs.onSurfaceVariant)
        Spacer(Modifier.height(48.dp))
        BigCard("NEW", "새 악보 만들기", "음원을 올리면 밴드 악보로 바꿔드려요", cs.primary, cs.onPrimary, onNew)
        Spacer(Modifier.height(16.dp))
        BigCard(
            "BAND", "나의 밴드",
            when (bandCount) {
                null -> " "
                0 -> "멤버들과 악보를 함께 봐요"
                else -> "참여 중인 밴드 ${bandCount}개"
            },
            cs.secondary, cs.onSecondary, onBands,
        )
        Spacer(Modifier.height(16.dp))
        BigCard("STORAGE", "보관함", "저장한 악보 ${count}개", cs.surfaceVariant, cs.onSurface, onStorage)
    }
}

@Composable
fun BigCard(tag: String, title: String, sub: String, bg: Color, fg: Color, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(24.dp)
    ) {
        Text(tag, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = fg.copy(alpha = 0.7f))
        Spacer(Modifier.height(8.dp))
        Text(title, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = fg)
        Spacer(Modifier.height(4.dp))
        Text(sub, fontSize = 14.sp, color = fg.copy(alpha = 0.8f))
    }
}

// ---------- 1-2. 보관함 ----------
@Composable
fun StorageScreen(onBack: () -> Unit, onOpen: (Work) -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val works = remember { WorkStore.load(ctx) }
    Page("보관함", onBack) {
        if (works.isEmpty()) {
            Spacer(Modifier.height(80.dp))
            Text(
                "아직 저장한 악보가 없어요.\n홈에서 NEW로 첫 악보를 만들어 보세요.",
                color = cs.onSurfaceVariant, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(works) { w ->
                    Column(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(cs.surface)
                            .clickable { onOpen(w) }
                            .padding(18.dp)
                    ) {
                        Text(w.title, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            w.instruments.joinToString(" · ") { Demo.kor(it) } + "   |   " + w.date,
                            fontSize = 13.sp, color = cs.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

// ---------- 2. 악기 선택 ----------
@Composable
fun InstrumentScreen(
    selected: Set<String>,
    onToggle: (String) -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Page("새 악보", onBack, bottom = {
        Button(
            onClick = onNext, enabled = selected.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) { Text("다음  (${selected.size}개 선택)", fontSize = 17.sp) }
    }) {
        Text("당신을 위한 악보를\n만드세요", fontSize = 28.sp, fontWeight = FontWeight.Bold, lineHeight = 36.sp)
        Spacer(Modifier.height(8.dp))
        Text("연주할 악기를 골라주세요 · 여러 개 선택 가능", color = cs.onSurfaceVariant)
        Spacer(Modifier.height(28.dp))
        Demo.INSTRUMENTS.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { inst ->
                    InstrumentTile(inst, inst in selected, Modifier.weight(1f)) { onToggle(inst) }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
fun InstrumentTile(inst: String, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier.height(140.dp)
            .clip(shape)
            .background(if (on) cs.primary else cs.surface)
            .border(BorderStroke(2.dp, if (on) cs.primary else cs.surfaceVariant), shape)
            .clickable(onClick = onClick)
            .padding(18.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(Demo.emoji(inst), fontSize = 36.sp)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                Demo.kor(inst), fontSize = 19.sp, fontWeight = FontWeight.Bold,
                color = if (on) cs.onPrimary else cs.onSurface,
            )
            Spacer(Modifier.weight(1f))
            if (on) Text("✓", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = cs.onPrimary)
        }
    }
}

// ---------- 3. 음원 업로드 ----------
@Composable
fun UploadScreen(onBack: () -> Unit, onStart: (title: String, audio: Pair<Uri, String>?) -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    var picked by remember { mutableStateOf<String?>(null) }
    // 고른 파일과 원래 파일 이름(확장자 포함). 데모 곡이면 null.
    var audio by remember { mutableStateOf<Pair<Uri, String>?>(null) }
    var editServer by remember { mutableStateOf(false) }
    var serverUrl by remember { mutableStateOf(ServerSettings.url(ctx)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            picked = displayName(ctx, uri)
            audio = uri to fileName(ctx, uri)
        }
    }
    val shape = RoundedCornerShape(24.dp)
    if (editServer) {
        ServerDialog(onDismiss = { editServer = false; serverUrl = ServerSettings.url(ctx) })
    }
    Page("음원 업로드", onBack, bottom = {
        Button(
            onClick = { onStart(picked ?: Demo.SONG_TITLE, audio) }, enabled = picked != null,
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) { Text("악보 만들기 시작", fontSize = 17.sp) }
    }) {
        Text("어떤 곡으로\n만들까요?", fontSize = 28.sp, fontWeight = FontWeight.Bold, lineHeight = 36.sp)
        Spacer(Modifier.height(8.dp))
        Text("MP3 · WAV 음원 파일을 올려주세요", color = cs.onSurfaceVariant)
        Spacer(Modifier.height(28.dp))
        Column(
            Modifier.fillMaxWidth().height(190.dp)
                .clip(shape)
                .background(cs.surface)
                .border(BorderStroke(2.dp, if (picked == null) cs.surfaceVariant else cs.primary), shape)
                .clickable { launcher.launch("audio/*") }
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(if (picked == null) "＋" else "♪", fontSize = 44.sp, color = cs.primary)
            Spacer(Modifier.height(8.dp))
            Text(
                picked ?: "눌러서 음원 파일 선택",
                fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                color = if (picked == null) cs.onSurfaceVariant else cs.onSurface,
            )
        }
        Spacer(Modifier.height(16.dp))
        OutlinedButton(
            onClick = { picked = Demo.SONG_TITLE; audio = null },
            modifier = Modifier.fillMaxWidth().height(50.dp),
        ) {
            Text("데모 곡으로 체험하기")
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "올린 음원은 ROCKY 서버에서 악보로 바뀌어요. 곡 길이에 따라 몇 분 걸려요.\n" +
                "데모 곡은 서버 없이 미리 준비한 결과를 바로 보여드려요.",
            fontSize = 12.sp, color = cs.onSurfaceVariant,
        )
        TextButton(onClick = { editServer = true }, modifier = Modifier.padding(top = 4.dp)) {
            Text(
                "서버: " + serverUrl.ifBlank { "설정 안 됨" },
                fontSize = 12.sp, color = cs.onSurfaceVariant, maxLines = 1,
            )
        }
    }
}

/** 서버 주소·키 바꾸기. 기본값은 빌드 때 local.properties에서 넣은 값. */
@Composable
fun ServerDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var url by remember { mutableStateOf(ServerSettings.url(ctx)) }
    var key by remember { mutableStateOf(ServerSettings.key(ctx)) }
    var checking by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(checking) {
        if (checking) {
            val ok = withContext(Dispatchers.IO) { RockyApi(url, key).health() }
            result = if (ok) "연결됨 ✓" else "연결할 수 없어요"
            checking = false
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("서버 설정") },
        text = {
            Column {
                OutlinedTextField(url, { url = it; result = null }, label = { Text("주소 (https://…)") }, singleLine = true)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(key, { key = it; result = null }, label = { Text("키") }, singleLine = true)
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { checking = true }, enabled = !checking && url.isNotBlank()) {
                    Text(if (checking) "확인 중…" else result ?: "연결 확인")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { ServerSettings.save(ctx, url, key); onDismiss() }) { Text("저장") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

// ---------- 4. 처리 중 (진행률 · 남은 시간) ----------
/** 서버로 보낼 악보 만들기 요청. requested는 사용자가 고른 악기(없는 악기는 서버가 새로 만든다). */
data class ScoreRequest(val uri: Uri, val fileName: String, val title: String, val requested: List<String>)

/**
 * request가 null이면 데모 진행을 보여주고 onDone(null).
 * 아니면 서버에 올리고 → 진행 상태를 받고 → 결과를 폰에 받아 onDone(Work).
 * 실패하면 다시 시도 / 데모 결과 보기 / 돌아가기를 고르게 한다.
 */
@Composable
fun ProcessingScreen(
    title: String,
    request: ScoreRequest?,
    onDone: (Work?) -> Unit,
    onExit: () -> Unit,
    demoSource: ProgressSource = DemoProgress,
) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    var state by remember { mutableStateOf<JobProgress?>(null) }
    var phase by remember { mutableStateOf<String?>(null) }      // 진행률이 없는 단계 안내
    var failure by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(attempt) {
        failure = null
        state = null
        if (request == null) {
            demoSource.track("demo").collect { state = it }
            delay(500)
            onDone(null)
            return@LaunchedEffect
        }
        try {
            if (!ServerSettings.isConfigured(ctx)) throw IOException("서버 주소가 설정되지 않았어요")
            val api = RockyApi.from(ctx)
            phase = "음원을 서버에 올리는 중이에요"
            val jobId = withContext(Dispatchers.IO) {
                val input = ctx.contentResolver.openInputStream(request.uri)
                    ?: throw IOException("음원 파일을 열 수 없어요")
                input.use { api.upload(it, request.fileName, request.title, request.requested) }
            }
            phase = null
            ServerProgress(api).track(jobId).collect { state = it }
            phase = "완성된 악보를 받는 중이에요"
            val workId = System.currentTimeMillis()
            val local = withContext(Dispatchers.IO) { ScoreStore.download(ctx, api, jobId, workId) }
            onDone(
                Work(
                    id = workId,
                    title = request.title,
                    instruments = Demo.INSTRUMENTS.filter { it in local.result.instruments.keys },
                    date = SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.KOREA).format(Date()),
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            phase = null
            failure = failureMessage(e)
        }
    }

    val msg = failure
    if (msg != null) {
        Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.Center) {
            Text(title, fontSize = 15.sp, color = cs.onSurfaceVariant, maxLines = 1)
            Spacer(Modifier.height(6.dp))
            Text("악보를 만들지 못했어요", fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Text(msg, color = cs.onSurfaceVariant)
            Spacer(Modifier.height(28.dp))
            Button(onClick = { attempt++ }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("다시 시도") }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = { onDone(null) }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text("데모 결과로 보기")
            }
            TextButton(onClick = onExit, modifier = Modifier.fillMaxWidth()) { Text("돌아가기") }
        }
        return
    }

    val p = state
    // 서버는 5초 구간이 끝날 때마다 한 번씩 알려주므로, 그 사이를 부드럽게 이어 준다.
    val shown by animateFloatAsState(
        targetValue = p?.overall ?: 0f,
        animationSpec = tween(durationMillis = 400, easing = LinearEasing),
        label = "overall",
    )
    val currentIndex = when {
        p == null -> 0
        p.done -> Stage.entries.size
        else -> p.stage.ordinal
    }

    Column(
        Modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, fontSize = 15.sp, color = cs.onSurfaceVariant, maxLines = 1)
        Spacer(Modifier.height(6.dp))
        Text("악보를 만드는 중이에요", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        phase?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, fontSize = 14.sp, color = cs.primary)
        }
        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                "${(shown * 100).roundToInt()}", fontSize = 64.sp, fontWeight = FontWeight.Black,
                color = cs.primary, lineHeight = 64.sp,
            )
            Text("%", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = cs.primary,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
            Spacer(Modifier.weight(1f))
            Text(
                if (p?.done == true) "완료!" else formatEta(p?.etaSec),
                fontSize = 15.sp, color = cs.onSurfaceVariant, modifier = Modifier.padding(bottom = 12.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(cs.surfaceVariant)) {
            Box(Modifier.fillMaxWidth(shown).height(8.dp).background(cs.primary))
        }
        Spacer(Modifier.height(28.dp))
        Stage.entries.forEachIndexed { i, stage ->
            val active = i == currentIndex
            Row(Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                    when {
                        i < currentIndex -> Text("✓", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = cs.primary)
                        active -> CircularProgressIndicator(Modifier.size(30.dp), strokeWidth = 3.dp)
                        else -> Text("${i + 1}", fontSize = 18.sp, color = cs.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        stage.title, fontSize = 19.sp, fontWeight = FontWeight.Bold,
                        color = if (i <= currentIndex) cs.onSurface else cs.onSurfaceVariant,
                    )
                    Text(
                        // 채보 중에는 MuScriptor가 처리한 5초 구간 수를 그대로 보여준다.
                        if (active && p != null && stage == Stage.TRANSCRIBE && p.total > 1)
                            "5초 구간 ${p.completed} / ${p.total} 처리"
                        else stage.desc,
                        fontSize = 13.sp, color = cs.onSurfaceVariant,
                    )
                }
                if (active && p != null) {
                    Text(
                        "${(p.stageFraction * 100).roundToInt()}%",
                        fontSize = 14.sp, fontWeight = FontWeight.Bold, color = cs.primary,
                    )
                }
            }
        }
    }
}

// ---------- 5. 결과 (밴드 전체 악보 + 전체 재생) ----------
@Composable
fun ResultScreen(
    work: Work,
    fresh: Boolean,
    onBack: () -> Unit,
    onExport: () -> Unit,
    onShareToBand: () -> Unit,
    myPart: String? = null,
) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    // 밴드에서 연 악보는 보관함에 없을 수 있으니 실제로 저장돼 있는지 확인한다.
    var saved by remember { mutableStateOf(!fresh && WorkStore.load(ctx).any { it.id == work.id }) }
    var playing by remember { mutableStateOf(false) }
    val bandPart = myPart?.takeIf { it in work.instruments }
    var selectedPart by remember { mutableStateOf(bandPart) }
    val player = remember { MediaPlayer() }
    DisposableEffect(Unit) { onDispose { player.release() } }
    // 서버에서 받은 결과가 폰에 있으면 그것을, 없으면 데모 결과를 보여준다.
    val local = remember(work.id) { ScoreStore.load(ctx, work.id) }
    val aiParts = local?.generated ?: Demo.AI_GENERATED

    fun selectPart(inst: String) {
        if (playing) {
            player.stop()
            player.reset()
            playing = false
        }
        selectedPart = if (selectedPart == inst) null else inst
    }

    fun togglePlay() {
        if (playing) {
            player.stop(); player.reset(); playing = false
            return
        }
        val base = selectedPart ?: Demo.BAND
        // 서버 결과는 MIDI로 들려준다 (안드로이드 기본 MIDI 소리).
        val path = if (local != null) local.midi(selectedPart)?.absolutePath
        else Assets.find(ctx, base, AUDIO_EXT)?.let { Assets.toCache(ctx, it).absolutePath }
        if (path == null) {
            Toast.makeText(ctx, "${Demo.kor(base)} 음원 파일이 없어요", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            player.reset()
            player.setDataSource(path)
            player.prepare()
            player.setOnCompletionListener { playing = false }
            player.start()
            playing = true
        } catch (e: Exception) {
            Toast.makeText(ctx, "재생 실패: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // 선택한 악기의 악보, 또는 아무 악기도 선택하지 않았을 때 밴드 전체 악보
    val scoreBase = selectedPart ?: Demo.BAND
    val widthPx = with(LocalDensity.current) { LocalConfiguration.current.screenWidthDp.dp.roundToPx() }
    val pages by produceState<List<android.graphics.Bitmap>?>(null, scoreBase) {
        value = null
        value = withContext(Dispatchers.IO) {
            if (local != null) {
                local.pdf(selectedPart)?.let { runCatching { PdfPages.render(it, widthPx) }.getOrNull() }.orEmpty()
            } else {
                val pageNo = Regex("^${Regex.escape(scoreBase)}(-(\\d+))?\\.(png|jpg|jpeg|webp)$")
                Assets.list(ctx)
                    .mapNotNull { n -> pageNo.find(n)?.let { m -> (m.groupValues[2].toIntOrNull() ?: 0) to n } }
                    .sortedBy { it.first }
                    .mapNotNull { (_, n) -> ctx.assets.open("${Demo.DIR}/$n").use { BitmapFactory.decodeStream(it) } }
            }
        }
    }

    Page(work.title, onBack, bottom = {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { WorkStore.add(ctx, work); saved = true }, enabled = !saved,
                modifier = Modifier.weight(1f).height(52.dp),
            ) { Text(if (saved) "저장됨 ✓" else "저장", maxLines = 1) }
            OutlinedButton(onClick = onShareToBand, modifier = Modifier.weight(1f).height(52.dp)) {
                Text("밴드 공유", maxLines = 1)
            }
            OutlinedButton(onClick = onExport, modifier = Modifier.weight(1f).height(52.dp)) {
                Text("내보내기", maxLines = 1)
            }
        }
    }) {
        // 악기를 다시 누르면 밴드 전체 보기로 돌아간다.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            work.instruments.forEach { inst ->
                val ai = inst in aiParts
                val selected = selectedPart == inst
                Box(
                    Modifier.weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            when {
                                selected -> cs.primary.copy(alpha = 0.24f)
                                ai -> cs.secondary.copy(alpha = 0.18f)
                                else -> cs.surface
                            },
                        )
                        .border(
                            BorderStroke(1.dp, if (selected) cs.primary else Color.Transparent),
                            RoundedCornerShape(12.dp),
                        )
                        .clickable { selectPart(inst) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        (if (ai) "✨ " else "") + Demo.kor(inst), fontWeight = FontWeight.Bold,
                        color = if (selected) cs.primary else if (ai) cs.secondary else cs.onSurface,
                    )
                }
            }
        }
        if (bandPart != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                "밴드에서 내 파트(${Demo.kor(bandPart)})를 먼저 보여드려요",
                fontSize = 12.sp, color = cs.primary,
            )
        }
        if (work.instruments.any { it in aiParts }) {
            Spacer(Modifier.height(6.dp))
            Text("✨ AI 생성: 원곡에 없던 파트를 ROCKY가 새로 만들었어요", fontSize = 12.sp, color = cs.onSurfaceVariant)
        }
        Spacer(Modifier.height(12.dp))

        // 선택한 악기의 음원 또는 밴드 전체 음원 재생
        Button(onClick = { togglePlay() }, modifier = Modifier.fillMaxWidth().height(50.dp)) {
            Text(
                if (playing) "■ 정지" else "▶ ${selectedPart?.let(Demo::kor) ?: "밴드 전체"} 듣기",
                fontSize = 16.sp,
            )
        }
        Spacer(Modifier.height(12.dp))

        // 선택한 악기 또는 밴드 전체 악보
        Box(
            Modifier.fillMaxWidth().weight(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White),
            contentAlignment = Alignment.TopCenter,
        ) {
            val shownPages = pages
            if (shownPages == null) {
                CircularProgressIndicator(Modifier.padding(40.dp))
            } else if (shownPages.isNotEmpty()) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    shownPages.forEach { bmp ->
                        Image(
                            bitmap = bmp.asImageBitmap(), contentDescription = "밴드 악보",
                            modifier = Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth,
                        )
                    }
                }
            } else {
                Text(
                    if (local != null) "이 파트의 악보 파일이 없어요" else "악보 이미지가 없어요\n(assets/demo/$scoreBase.png)",
                    color = Color.Gray, textAlign = TextAlign.Center, modifier = Modifier.padding(40.dp),
                )
            }
        }
    }
}

// ---------- 6. 내보내기 ----------
@Composable
fun ExportScreen(work: Work, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    // (파일, 이름표). 서버 결과가 폰에 있으면 그 파일들, 없으면 데모 파일.
    val files: List<Pair<File, String>> = remember {
        ScoreStore.load(ctx, work.id)?.exportFiles(Demo::kor) ?: run {
            val all = Assets.list(ctx)
            listOf("${Demo.BAND}.pdf" to "밴드 악보", "${Demo.BAND}.mid" to "밴드 MIDI")
                .filter { it.first in all }
                .map { (name, label) -> Assets.toCache(ctx, name) to label }
        }
    }
    val checked = remember { mutableStateMapOf<String, Boolean>().apply { files.forEach { put(it.first.path, true) } } }

    Page("내보내기", onBack, bottom = {
        Button(
            onClick = { shareLocalFiles(ctx, files.map { it.first }.filter { checked[it.path] == true }, work.title) },
            enabled = checked.values.any { it },
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) { Text("공유하기", fontSize = 17.sp) }
    }) {
        Text("어떤 파일을 보낼까요?", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(work.title + " · " + work.instruments.joinToString(" · ") { Demo.kor(it) }, color = cs.onSurfaceVariant)
        Spacer(Modifier.height(20.dp))
        if (files.isEmpty()) {
            Text("보낼 수 있는 악보 파일이 없어요", color = cs.onSurfaceVariant)
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(files) { (file, label) ->
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(cs.surface)
                        .clickable { checked[file.path] = checked[file.path] != true }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = checked[file.path] == true, onCheckedChange = { checked[file.path] = it })
                    Text(label, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    Text(
                        file.extension.uppercase(), fontSize = 12.sp,
                        color = cs.primary, modifier = Modifier.padding(end = 12.dp),
                    )
                }
            }
        }
    }
}

/** 실패 이유를 사용자가 알아들을 말로. */
fun failureMessage(e: Exception): String = when (e) {
    is JobFailedException -> "서버에서 처리하다 멈췄어요.\n(${e.message})"
    is ApiException -> when (e.code) {
        401 -> "서버 키가 맞지 않아요. 업로드 화면 아래 '서버'에서 키를 확인해 주세요."
        413 -> "음원 파일이 너무 커요. 더 짧은 곡이나 MP3로 올려 주세요."
        415 -> "MP3, WAV, FLAC, M4A 음원만 올릴 수 있어요."
        404 -> "서버에서 작업을 찾을 수 없어요. 처음부터 다시 해 주세요."
        else -> "서버가 요청을 처리하지 못했어요. (HTTP ${e.code})"
    }
    is IOException -> "서버에 연결할 수 없어요. 인터넷 연결이나 서버 주소를 확인해 주세요.\n(${e.message})"
    else -> "알 수 없는 오류가 났어요. (${e.message})"
}
