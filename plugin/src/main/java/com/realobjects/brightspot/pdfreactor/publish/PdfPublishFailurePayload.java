package com.realobjects.brightspot.pdfreactor.publish;

import java.util.Date;
import java.util.UUID;

import com.psddev.cms.notification.ToolPayload;
import com.psddev.dari.db.Record;

/**
 * Payload for a {@link PdfPublishFailureTopic} notification: which content
 * failed, on which site, why, and when. A plain Dari {@link Record}
 * implementing {@link ToolPayload}, following the platform's payload pattern
 * (e.g. {@code NotificationReminderTopicPayload}).
 */
public class PdfPublishFailurePayload extends Record implements ToolPayload {

    private UUID contentId;
    private String contentLabel;
    private String siteName;
    private String errorDetail;
    private Date failedAt;

    public UUID getContentId() {
        return contentId;
    }

    public void setContentId(UUID contentId) {
        this.contentId = contentId;
    }

    public String getContentLabel() {
        return contentLabel;
    }

    public void setContentLabel(String contentLabel) {
        this.contentLabel = contentLabel;
    }

    public String getSiteName() {
        return siteName;
    }

    public void setSiteName(String siteName) {
        this.siteName = siteName;
    }

    public String getErrorDetail() {
        return errorDetail;
    }

    public void setErrorDetail(String errorDetail) {
        this.errorDetail = errorDetail;
    }

    public Date getFailedAt() {
        return failedAt;
    }

    public void setFailedAt(Date failedAt) {
        this.failedAt = failedAt;
    }

    /**
     * Stable identifier for this payload (content + failure time), used by the
     * notification framework to key the payload.
     */
    @Override
    public String getNotificationPayloadKey() {
        return "pdfreactor-publish-failure:" + contentId
                + ":" + (failedAt != null ? failedAt.getTime() : 0L);
    }
}

