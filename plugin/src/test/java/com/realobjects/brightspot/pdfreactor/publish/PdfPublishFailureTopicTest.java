package com.realobjects.brightspot.pdfreactor.publish;

import java.util.Date;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PdfPublishFailureTopicTest {

    private static PdfPublishFailurePayload payload(String label, String site, String detail) {
        PdfPublishFailurePayload payload = new PdfPublishFailurePayload();
        payload.setContentId(UUID.randomUUID());
        payload.setContentLabel(label);
        payload.setSiteName(site);
        payload.setErrorDetail(detail);
        payload.setFailedAt(new Date());
        return payload;
    }

    @Test
    void plainTextIncludesLabelSiteAndDetail() {
        String text = PdfPublishFailureTopic.plain(payload("My Article", "Brand A", "img 403"));
        assertThat(text).isEqualTo("PDF generation failed for My Article (Brand A): img 403");
    }

    @Test
    void htmlEscapesContent() {
        String text = PdfPublishFailureTopic.html(payload("A & B <x>", null, "host <down>"));
        assertThat(text).contains("A &amp; B &lt;x&gt;");
        assertThat(text).contains("host &lt;down&gt;");
        assertThat(text).doesNotContain("<x>");
    }

    @Test
    void fallsBackToContentIdAndUnknownError() {
        PdfPublishFailurePayload payload = payload(null, null, null);
        String text = PdfPublishFailureTopic.plain(payload);
        assertThat(text).contains(payload.getContentId().toString());
        assertThat(text).endsWith(": unknown error");
    }

    @Test
    void topicDescribesItselfForTheSubscriptionUi() {
        // The topic is user-subscribable; it carries a description so the
        // Notifications subscription UI explains what subscribing delivers.
        assertThat(new PdfPublishFailureTopic().getNoteHtml()).isNotBlank();
    }

    @Test
    void payloadKeyIsStablePerContentAndTime() {
        PdfPublishFailurePayload payload = payload("L", "S", "d");
        assertThat(payload.getNotificationPayloadKey())
                .isEqualTo(payload.getNotificationPayloadKey())
                .startsWith("pdfreactor-publish-failure:" + payload.getContentId());
    }
}
