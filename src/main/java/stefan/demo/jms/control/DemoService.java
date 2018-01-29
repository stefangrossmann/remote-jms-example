package stefan.demo.jms.control;

import javax.annotation.Resource;
import javax.enterprise.context.Dependent;
import javax.inject.Inject;
import javax.jms.JMSContext;
import javax.jms.Queue;
import javax.jms.Topic;
import java.util.UUID;

@Dependent
public class DemoService {
    @Inject
    private JMSContext context;

    @Resource(mappedName = "java:/topic/HelloWorldTopic")
    private Topic topic;

    @Resource(mappedName = "java:/queue/HelloWorldQueue")
    private Queue queue;

    public void sendTextMessageToAll(final String message) {
        final String text = buildMessage(message);
        context.createProducer().send(topic, text);
    }

    public void sendTextMessageToOne(final String message) {
        final String text = buildMessage(message);
        context.createProducer().send(queue, text);
    }

    private String buildMessage(final String message) {
        return String.format("%s [%s]", message, UUID.randomUUID().toString());
    }
}