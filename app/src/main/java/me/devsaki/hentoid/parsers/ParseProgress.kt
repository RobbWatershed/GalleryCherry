package me.devsaki.hentoid.parsers

import me.devsaki.hentoid.events.DownloadPreparationEvent
import me.devsaki.hentoid.util.ProgressManager
import org.greenrobot.eventbus.EventBus
import java.util.concurrent.atomic.AtomicBoolean

class ParseProgress {
    private var contentId: Long = 0
    private var storedId: Long = 0
    private var hasStarted = false
    private var currentStep = 0
    private var indefinite = false
    private var progressMgr: ProgressManager? = null
    private val processHalted = AtomicBoolean(false)

    fun start(contentId: Long, storedId: Long = -1, maxSteps: Int = 1, indefinite : Boolean = false) {
        this.contentId = contentId
        this.storedId = storedId
        this.indefinite = indefinite
        progressMgr = ProgressManager(maxSteps)
        signalProgress(contentId, storedId, 0f, indefinite)
        hasStarted = true
    }

    fun hasStarted(): Boolean {
        return hasStarted
    }

    fun isProcessHalted(): Boolean {
        return processHalted.get()
    }

    fun haltProcess() {
        processHalted.set(true)
    }

    fun nextStep() {
        progressMgr?.setProgress(currentStep.toString(), 1f)
        currentStep++
    }

    fun advance(progress: Float) {
        progressMgr?.setProgress(currentStep.toString(), progress)
        signalProgress(contentId, storedId, progressMgr?.getGlobalProgress() ?: 1f)
    }

    fun complete() {
        signalProgress(contentId, storedId, 1f)
        progressMgr = null
    }

    /**
     * Signal download preparation event for the given processed elements
     *
     * @param contentId Online content ID being processed
     * @param storedId  Stored content ID being processed
     * @param progress  Progress (0.0 -> 1.0)
     */
    fun signalProgress(contentId: Long, storedId: Long, progress: Float, indefinite: Boolean = false) {
        EventBus.getDefault()
            .post(DownloadPreparationEvent(contentId, storedId, progress, indefinite))
    }
}