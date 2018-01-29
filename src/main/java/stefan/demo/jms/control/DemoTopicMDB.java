package stefan.demo.jms.control;

import org.jboss.ejb3.annotation.ResourceAdapter;

import javax.ejb.ActivationConfigProperty;
import javax.ejb.MessageDriven;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.TextMessage;
import java.util.logging.Logger;


@ResourceAdapter("oas-connection")
@MessageDriven(
        name = "HelloWorldTopicMDB",
        activationConfig = {
            @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "javax.jms.Topic"),
            @ActivationConfigProperty(propertyName = "destination", propertyValue = "topic/HelloWorldTopic"),
            @ActivationConfigProperty(propertyName = "acknowledgeMode", propertyValue = "Auto-acknowledge")
        })
public class DemoTopicMDB implements MessageListener {
    private final static Logger LOGGER = Logger.getLogger(DemoTopicMDB.class.toString());

    public void onMessage(Message rcvMessage) {
        try {
            if (rcvMessage instanceof TextMessage) {
                final TextMessage msg = (TextMessage) rcvMessage;
                LOGGER.info("Received Message from topic: " + msg.getText());
            } else {
                LOGGER.warning("Message of wrong type: " + rcvMessage.getClass().getName());
            }
        } catch (JMSException e) {
            throw new RuntimeException(e);
        }
    }
}