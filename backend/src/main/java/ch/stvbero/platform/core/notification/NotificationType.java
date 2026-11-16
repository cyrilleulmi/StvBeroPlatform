package ch.stvbero.platform.core.notification;

/** Trigger of a notification, matching BEN-01 to BEN-07. */
public enum NotificationType {
    /** BEN-01: the response deadline is getting close and there is no response yet. */
    REMINDER,
    /** BEN-02: change away from «dabei» after the response deadline. */
    LATE_CANCELLATION,
    /** BEN-03: call-up published. */
    CALL_UP_PUBLISHED,
    /** BEN-05: published call-up changed. */
    CALL_UP_CHANGED,
    /** BEN-04: a called-up player declines. */
    CALLED_UP_PLAYER_DECLINED,
    /** BEN-06: imported match changed (comes with F-07). */
    MATCH_CHANGED,
    /** BEN-07: event cancelled. */
    EVENT_CANCELLED,
    /** BEN-07: event rescheduled. */
    EVENT_RESCHEDULED
}
