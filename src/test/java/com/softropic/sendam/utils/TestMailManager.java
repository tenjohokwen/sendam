package com.softropic.sendam.utils;



import com.softropic.sendam.email.contract.Envelope;
import com.softropic.sendam.email.service.MailManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


public class TestMailManager extends MailManager {
    private static final Logger                log       = LoggerFactory.getLogger(TestMailManager.class);
    private final        Map<String, Envelope> sentMails = new ConcurrentHashMap<>();

    public TestMailManager() {
        super(null, null, null, null, null);
    }

    @Override
    public void sendEmailSync(final Envelope envelope) {
        log.debug("EMAIL***:       " + envelope.data());
        log.debug("EMAIL DATA***:       " + envelope.data().toString());
        sentMails.put(envelope.sendId(), envelope);
    }

    /**
     * Captures envelope events synchronously regardless of transaction state.
     * Uses a distinct method name so Spring's TransactionalEventListenerFactory
     * cannot claim it via the parent's @TransactionalEventListener annotation.
     */
    @EventListener
    public void onEnvelope(final Envelope envelope) {
        sendEmailSync(envelope);
    }

    @Override
    public void sendEmailFromTemplate(final Envelope envelope) {
        sendEmailSync(envelope);
    }

    public Envelope getEnvelope(String referenceId) {
        return sentMails.get(referenceId);
    }

    public void clear() {
        sentMails.clear();
    }
}
