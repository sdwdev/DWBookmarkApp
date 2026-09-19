package com.gameitstudio.dwbookmarkapp.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * 화면 수명과 무관하게 살아 있어야 하는 작업용 코루틴 스코프
 *
 * 공유 인텐트로 저장할 때는 액티비티가 곧바로 종료되므로
 * lifecycleScope 를 쓰면 저장 도중 코루틴이 취소된다.
 * SupervisorJob: 한 작업이 실패해도 다른 작업이 취소되지 않는다.
 */
object AppScope {
    val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
}
