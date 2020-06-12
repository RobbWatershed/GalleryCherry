package me.devsaki.hentoid.events;

import androidx.annotation.IntDef;
import androidx.documentfile.provider.DocumentFile;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Created by Robb on 17/10/2018.
 * Tracks processing events (e.g. import, migration) for interested subscribers.
 */
public class ProcessEvent {

    @IntDef({EventType.PROGRESS, EventType.COMPLETE})
    @Retention(RetentionPolicy.SOURCE)
    public @interface EventType {
        int PROGRESS = 0; // Processing in progress (1 element done)
        int COMPLETE = 1; // Processing complete
    }

    public final @EventType
    int eventType;                              // Event type
    public final int step;                      // Step of the process
    public final int elementsOK;                // Number of elements that have been correctly processed
    public final int elementsKO;                // Number of elements whose processing has failed
    public final int elementsTotal;             // Number of elements to process
    public final DocumentFile logFile;          // Log file, if exists (for EventType.COMPLETE)

    /**
     * Use for EventType.PROGRESS events
     *
     * @param eventType  event type code
     * @param step       step of the  process
     * @param elementsOK elements processed successfully so far
     * @param elementsKO elements whose processing has failed so far
     * @param elementsTotal total elements to process
     */
    public ProcessEvent(@EventType int eventType, int step, int elementsOK, int elementsKO, int elementsTotal) {
        this.eventType = eventType;
        this.step = step;
        this.elementsOK = elementsOK;
        this.elementsKO = elementsKO;
        this.elementsTotal = elementsTotal;
        this.logFile = null;
    }

    /**
     * Use for EventType.COMPLETE events
     *
     * @param eventType  event type code
     * @param step       step of the process
     * @param elementsOK elements processed successfully so far
     * @param elementsKO elements whose processing has failed so far
     * @param elementsTotal total elements to process
     */
    public ProcessEvent(@EventType int eventType, int step, int elementsOK, int elementsKO, int elementsTotal, DocumentFile logFile) {
        this.eventType = eventType;
        this.step = step;
        this.elementsOK = elementsOK;
        this.elementsKO = elementsKO;
        this.elementsTotal = elementsTotal;
        this.logFile = logFile;
    }

}
