package org.wordpress.android.ui.reader.repository

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.wordpress.android.models.ReaderPostList
import org.wordpress.android.models.ReaderTag
import org.wordpress.android.modules.BG_THREAD
import org.wordpress.android.ui.reader.ReaderEvents.UpdatePostsEnded
import org.wordpress.android.ui.reader.repository.ReaderRepositoryCommunication.Error.RemoteRequestFailure
import org.wordpress.android.ui.reader.repository.ReaderRepositoryUseCaseType.FETCH_NUM_POSTS_BY_TAG
import org.wordpress.android.ui.reader.repository.ReaderRepositoryUseCaseType.FETCH_POSTS_BY_TAG
import org.wordpress.android.ui.reader.repository.ReaderRepositoryUseCaseType.FETCH_POSTS_BY_TAG_WITH_COUNT
import org.wordpress.android.ui.reader.repository.ReaderRepositoryUseCaseType.SHOULD_AUTO_UDPATE_TAG
import org.wordpress.android.util.EventBusWrapper
import org.wordpress.android.util.NetworkUtilsWrapper
import org.wordpress.android.viewmodel.ContextProvider
import org.wordpress.android.viewmodel.Event
import org.wordpress.android.viewmodel.ReactiveMutableLiveData
import javax.inject.Inject
import javax.inject.Named

class ReaderPostRepository(
    private val bgDispatcher: CoroutineDispatcher,
    private val eventBusWrapper: EventBusWrapper,
    private val networkUtilsWrapper: NetworkUtilsWrapper,
    private val contextProvider: ContextProvider,
    private val readerTag: ReaderTag,
    private val fetchPostsForTagUseCase: FetchPostsForTagUseCase,
    private val fetchNumPostsForTagUseCase: FetchNumPostsForTagUseCase,
    private val shouldAutoUpdateTagUseCase: ShouldAutoUpdateTagUseCase,
    private val fetchPostsForTagWithCountUseCase: FetchPostsForTagWithCountUseCase
) : BaseReaderRepository(eventBusWrapper,
        networkUtilsWrapper,
        contextProvider) {
    private val tagPostsMap: HashMap<ReaderTag, ReaderPostList> = hashMapOf()
    private val readerRepositoryUseCases:
            HashMap<ReaderRepositoryUseCaseType, ReaderRepositoryDispatchingUseCase> = hashMapOf()

    private val _postsForTag = ReactiveMutableLiveData<ReaderPostList>(
            onActive = { onActivePostsForTag() }, onInactive = { onInactivePostsForTag() })
    val postsForTag: ReactiveMutableLiveData<ReaderPostList> = _postsForTag

    private var isStarted = false

    init {
        readerRepositoryUseCases[FETCH_POSTS_BY_TAG] = fetchPostsForTagUseCase
        readerRepositoryUseCases[FETCH_NUM_POSTS_BY_TAG] = fetchNumPostsForTagUseCase
        readerRepositoryUseCases[SHOULD_AUTO_UDPATE_TAG] = shouldAutoUpdateTagUseCase
        readerRepositoryUseCases[FETCH_POSTS_BY_TAG_WITH_COUNT] = fetchPostsForTagWithCountUseCase
    }

    override fun start() {
        if (isStarted) return

        isStarted = true
        super.start()
    }

    override fun stop() {
        readerRepositoryUseCases.values.forEach { it.stop() }
    }

    override fun getTag(): ReaderTag {
        return readerTag
    }

    override fun onNewPosts(event: UpdatePostsEnded) {
        reloadPosts()
    }

    override fun onChangedPosts(event: UpdatePostsEnded) {
        reloadPosts()
    }

    override fun onUnchanged(event: UpdatePostsEnded) {
        // todo: annmarie Handle the refresh situation but nothing changed
    }

    override fun onFailed(event: UpdatePostsEnded) {
        _communicationChannel.postValue(
                Event(RemoteRequestFailure)
        )
    }

    private fun onActivePostsForTag() {
        loadPosts()
    }

    private fun onInactivePostsForTag() {
        // todo: annmarie this may not be used
    }

    private fun loadPosts() {
        GlobalScope.launch(bgDispatcher) {
            val existsInMemory = tagPostsMap[readerTag]?.let {
                !it.isEmpty()
            } ?: false
            val refresh = shouldAutoUpdateTagUseCase.fetch(readerTag)

            if (existsInMemory) {
                _postsForTag.postValue(tagPostsMap[readerTag])
            } else {
                val result = fetchPostsForTagUseCase.fetch(readerTag)
                tagPostsMap[readerTag] = result
                _postsForTag.postValue(tagPostsMap[readerTag])
            }

            if (refresh) {
                requestPostsFromRemoteStorage(readerTag)
            }
        }
    }

    private fun reloadPosts() {
        GlobalScope.launch(bgDispatcher) {
            val result = fetchPostsForTagUseCase.fetch(readerTag)
            tagPostsMap[readerTag] = result
            _postsForTag.postValue(tagPostsMap[readerTag])
        }
    }

    class Factory
    @Inject constructor(
        @Named(BG_THREAD) private val bgDispatcher: CoroutineDispatcher,
        private val eventBusWrapper: EventBusWrapper,
        private val networkUtilsWrapper: NetworkUtilsWrapper,
        private val contextProvider: ContextProvider,
        private val fetchPostsForTagUseCase: FetchPostsForTagUseCase,
        private val fetchNumPostsForTagUseCase: FetchNumPostsForTagUseCase,
        private val shouldAutoUpdateTagUseCase: ShouldAutoUpdateTagUseCase,
        private val fetchPostsForTagWithCountUseCase: FetchPostsForTagWithCountUseCase
    ) {
        fun create(readerTag: ReaderTag): ReaderPostRepository {

            return ReaderPostRepository(
                    bgDispatcher,
                    eventBusWrapper,
                    networkUtilsWrapper,
                    contextProvider,
                    readerTag,
                    fetchPostsForTagUseCase,
                    fetchNumPostsForTagUseCase,
                    shouldAutoUpdateTagUseCase,
                    fetchPostsForTagWithCountUseCase
            )
        }
    }
}
