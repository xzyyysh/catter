package f3l1x.catter.service

import android.content.Context
import f3l1x.catter.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ResolveState {
    data object Idle : ResolveState
    data class Loading(val target: String) : ResolveState
    data class Video(val details: VideoDetails) : ResolveState
    data class Post(val post: MediaPost) : ResolveState
    data class Queued(val message: String) : ResolveState
    data class Failed(val message: String) : ResolveState
}

object Resolver {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow<ResolveState>(ResolveState.Idle)
    val state: StateFlow<ResolveState> = _state.asStateFlow()

    private var job: Job? = null
    private var token = 0

    fun resolve(context: Context, link: String, treeUri: String?) {
        val target = Links.parse(link)
        if (target == null) {
            Logger.w("unsupported link")
            _state.value = ResolveState.Failed("Unsupported link")
            return
        }

        val appContext = context.applicationContext
        val current = ++token

        job?.cancel()
        _state.value = ResolveState.Loading(target.id)
        Logger.d("resolve link: ${target.source.label} ${target.id}")

        job = scope.launch {
            when (target.source) {
                Source.YouTube -> loadVideo(current, target.id)
                Source.Instagram -> loadPost(appContext, current, target.id, treeUri) { Instagram.post(it) }
                Source.TikTok -> loadPost(appContext, current, target.id, treeUri) { TikTok.post(it) }
                Source.Pinterest -> loadPost(appContext, current, target.id, treeUri) { Pinterest.post(it) }
                Source.Reddit -> loadPost(appContext, current, target.id, treeUri) { Reddit.post(it) }
            }
        }
    }

    fun markQueued(message: String) {
        _state.value = ResolveState.Queued(message)
    }

    fun clear() {
        token++
        job?.cancel()
        job = null
        _state.value = ResolveState.Idle
    }

    private fun active(current: Int): Boolean = current == token

    private suspend fun loadVideo(current: Int, id: String) {
        runCatching { YouTube.details(id) }
            .onSuccess { details ->
                if (!active(current)) return@onSuccess
                _state.value = ResolveState.Video(details)
                scope.launch { YouTube.prefetchKey(id) }
            }
            .onFailure {
                if (!active(current)) return@onFailure
                Logger.w("video resolve failed: $id", it)
                _state.value = ResolveState.Failed("Could not load this video")
            }
    }

    private suspend fun loadPost(
        context: Context,
        current: Int,
        target: String,
        treeUri: String?,
        fetch: suspend (String) -> MediaPost,
    ) {
        runCatching { fetch(target) }
            .onSuccess { post ->
                if (!active(current)) return@onSuccess

                if (post.items.size == 1) {
                    val item = post.items.first()
                    DownloadManager.start(
                        context = context,
                        request = Requests.post(post, item, 0),
                        destination = SaveLocation.destination(treeUri, item.kind),
                    )
                    _state.value = ResolveState.Queued("Added to queue")
                } else {
                    _state.value = ResolveState.Post(post)
                }
            }
            .onFailure {
                if (!active(current)) return@onFailure
                Logger.w("post resolve failed: $target", it)
                _state.value = ResolveState.Failed("Could not load this post")
            }
    }
}
