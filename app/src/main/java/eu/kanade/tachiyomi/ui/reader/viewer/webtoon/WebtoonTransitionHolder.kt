package eu.kanade.tachiyomi.ui.reader.viewer.webtoon

import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatTextView
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.view.isNotEmpty
import androidx.core.view.isVisible
import eu.kanade.tachiyomi.ui.reader.model.ChapterTransition
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.viewer.ReaderTransitionView
import eu.kanade.tachiyomi.util.system.dpToPx
import eu.kanade.tachiyomi.util.view.setText
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import yokai.i18n.MR
import yokai.presentation.theme.YokaiTheme
import yokai.util.lang.getString

/**
 * Holder of the webtoon viewer that contains a chapter transition.
 */
class WebtoonTransitionHolder(
    val layout: LinearLayout,
    viewer: WebtoonViewer,
) : WebtoonBaseHolder(layout, viewer) {

    private val scope = MainScope()
    private var stateJob: Job? = null

    private val transitionView = ReaderTransitionView(context)

    /**
     * View container of the current status of the transition page. Child views will be added
     * dynamically.
     */
    private var pagesContainer = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
    }

    init {
        layout.layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        layout.orientation = LinearLayout.VERTICAL
        layout.gravity = Gravity.CENTER

        val paddingVertical = 128.dpToPx
        val paddingHorizontal = 32.dpToPx
        layout.setPadding(paddingHorizontal, paddingVertical, paddingHorizontal, paddingVertical)

        val childMargins = 16.dpToPx
        val childParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
            setMargins(0, childMargins, 0, childMargins)
        }

        layout.addView(transitionView)
        layout.addView(pagesContainer, childParams)
    }

    /**
     * Binds the given [transition] with this view holder, subscribing to its state.
     */
    fun bind(transition: ChapterTransition) {
        transitionView.bind(viewer.config.readerTheme, transition, viewer.downloadManager, viewer.activity.viewModel.manga)

        transition.to?.let { observeStatus(it, transition) }
    }

    /**
     * Called when the view is recycled and being added to the view pool.
     */
    override fun recycle() {
        stateJob?.cancel()
    }

    /**
     * Observes the status of the page list of the next/previous chapter. Whenever there's a new
     * state, the pages container is cleaned up before setting the new state.
     */
    private fun observeStatus(chapter: ReaderChapter, transition: ChapterTransition) {
        stateJob?.cancel()
        stateJob = scope.launch {
            chapter.stateFlow
                .collectLatest { state ->
                    pagesContainer.removeAllViews()
                    when (state) {
                        is ReaderChapter.State.Loading -> setLoading()
                        is ReaderChapter.State.Error -> setError(state.error, transition)
                        is ReaderChapter.State.Wait, is ReaderChapter.State.Loaded -> {
                            // No additional view is added
                        }
                    }
                    pagesContainer.isVisible = pagesContainer.isNotEmpty()
                }
        }
    }

    /**
     * Sets the loading state on the pages container.
     */
    private fun setLoading() {
        val progress = ComposeView(context).apply {
            layoutParams = FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.CENTER)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
            setContent {
                YokaiTheme { CircularProgressIndicator() }
            }
        }

        val textView = AppCompatTextView(context).apply {
            wrapContent()
            setText(MR.strings.loading_pages)
        }

        pagesContainer.addView(progress)
        pagesContainer.addView(textView)
    }

    /**
     * Sets the loaded state on the pages container.
     */
    private fun setLoaded() {
        // No additional view is added
    }

    /**
     * Sets the error state on the pages container.
     */
    private fun setError(error: Throwable, transition: ChapterTransition) {
        val textView = AppCompatTextView(context).apply {
            wrapContent()
            text = context.getString(MR.strings.failed_to_load_pages_, error.message ?: "")
        }

        val retryBtn = AppCompatButton(context).apply {
            wrapContent()
            setText(MR.strings.retry)
            setOnClickListener {
                val toChapter = transition.to
                if (toChapter != null) {
                    viewer.activity.requestPreloadChapter(toChapter)
                }
            }
        }

        pagesContainer.addView(textView)
        pagesContainer.addView(retryBtn)
    }
}
