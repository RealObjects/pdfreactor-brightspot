package com.realobjects.brightspot.pdfreactor.publish;

import com.psddev.cms.notification.ToolTopic;
import com.psddev.cms.notification.ToolTopicContext;
import com.psddev.dari.db.Recordable;
import com.realobjects.brightspot.pdfreactor.Html;

/**
 * Notification topic raised when publish automation fails to generate a PDF.
 * {@link PdfPublishAutomation} publishes a {@link PdfPublishFailurePayload} via
 * {@code ToolNotification.publish(PdfPublishFailureTopic.class, payload)} in
 * its failure branch.
 *
 * <p><b>Delivery is opt-in.</b> {@code ToolNotification.publish}
 * broadcasts only to the topic's <em>subscribers</em>
 * ({@code ToolNotification.getSubscribers()} →
 * {@code ToolSubscriber.findSubscribersForTopic}), so an editor/admin who has
 * not subscribed sees nothing in the Notifications panel — by design, mirroring
 * the platform's own user-subscribable topics (e.g. {@code ContentEditTopic}).
 * This topic is therefore made cleanly subscribable: it is <em>not</em>
 * {@code @ToolUi.Hidden}, carries a {@link Recordable.DisplayName} so it
 * presents with a friendly name in the subscription UI, and overrides
 * {@link #getNoteHtml()} to describe what subscribing delivers. To receive
 * these notifications, subscribe to "PDF Publish Failure" in the Tool's
 * Notifications settings. (The right-rail PDFreactor widget always surfaces the
 * last publish failure on the edit form regardless of subscription.)</p>
 */
@Recordable.DisplayName("PDF Publish Failure")
public class PdfPublishFailureTopic extends ToolTopic<PdfPublishFailurePayload> {

    @Override
    public String getNoteHtml() {
        return "Notifies you when publishing content fails to generate its PDF"
                + " (PDFreactor on-publish automation).";
    }

    @Override
    protected boolean shouldDeliver(ToolTopicContext<PdfPublishFailurePayload> context) {
        // Every failure is worth delivering to a subscriber.
        return true;
    }

    @Override
    protected String toStringFormat(ToolTopicContext<PdfPublishFailurePayload> context) {
        return plain(context.getPayload());
    }

    @Override
    protected String toHtmlFormat(ToolTopicContext<PdfPublishFailurePayload> context) {
        return html(context.getPayload());
    }

    /** Plain-text rendering of a failure (package-private for testing). */
    static String plain(PdfPublishFailurePayload payload) {
        return "PDF generation failed for " + label(payload)
                + site(payload) + ": " + detail(payload);
    }

    /** HTML rendering of a failure (package-private for testing). */
    static String html(PdfPublishFailurePayload payload) {
        return "<strong>PDF generation failed</strong> for "
                + Html.escape(label(payload)) + Html.escape(site(payload))
                + ": " + Html.escape(detail(payload));
    }

    private static String label(PdfPublishFailurePayload payload) {
        String contentLabel = payload.getContentLabel();
        return contentLabel != null && !contentLabel.isEmpty()
                ? contentLabel
                : String.valueOf(payload.getContentId());
    }

    private static String site(PdfPublishFailurePayload payload) {
        String siteName = payload.getSiteName();
        return siteName != null && !siteName.isEmpty() ? " (" + siteName + ")" : "";
    }

    private static String detail(PdfPublishFailurePayload payload) {
        String errorDetail = payload.getErrorDetail();
        return errorDetail != null && !errorDetail.isEmpty() ? errorDetail : "unknown error";
    }

}
