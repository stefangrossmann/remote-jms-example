How to connect to a remote JMS server?
===============
Tested with a plain Wildfly 10.1.0.Final.

# Standalone testing of the example

### Startup the server with the full profile

```
    standalone.bat -c standalone-full.xml
```

### Configure queue and topic

Start the 'jboss-cli', connect and execute the following commands:

```
    jms-queue add --queue-address=HelloWorldQueue --entries=[queue/HelloWorldQueue java:jboss/exported/queue/HelloWorldQueue]
```

```
    jms-topic add --topic-address=HelloWorldTopic --entries=[topic/HelloWorldTopic java:jboss/exported/topic/HelloWorldTopic]
```

(requires reload)

### Testing

* Deploy the demo app e. G. with 'mvn clean wildfly:deploy'
* Send a POST Message to the following REST endpoint: `http://localhost:8080/mdb-qickstart/resources/demo/message/topic?message=Test`

There should be a log message at the server log.

# Possibility to configure a remote JMS server

References:
* https://www.javacodegeeks.com/2016/11/building-horizontal-jms-bridge-two-wildfly-servers-using-activemq-artemis.html
* https://access.redhat.com/documentation/en-us/red_hat_jboss_enterprise_application_platform/7.0/html/configuring_messaging/configuring_jms_bridges
* https://stackoverflow.com/questions/39134984/wildfly-10-amq119032-user-does-not-have-permission-create-non-durable-queue-on/39135131#39135131
* http://www.mastertheboss.com/jboss-server/jboss-jms/connecting-to-an-external-wildfly-jms-server
* https://access.redhat.com/documentation/en-us/red_hat_jboss_enterprise_application_platform/7.0/html/configuring_messaging/resource_adapters#use_provided_amq_adapter
* http://wikihealthcare.agfa.net/x/JGf6Ew

There are multiple possibilities to connect Wildfly JMS destinations to each other:

1. JMS bridge
2. JMS core bridge
3. Configure a remote connector towards a remote JMS Server
4. Configuring the remote connection properties hardly in an MDB (not discussed in here)

## Bridges

Wildfly messaging includes a fully functional JMS message bridge. The function of a JMS bridge is to consume messages 
from a source queue or topic and send them to a target queue or topic, typically on a different server. 

Do not confuse a JMS bridge with a core bridge. A JMS bridge can be used to bridge any two JMS 1.1 compliant 
JMS providers and uses the JMS API. A core bridge is used to bridge any two ActiveMQ/Artemis messaging instances 
and uses the core API.

## Remote connection

In this case the client connects directly to a remote server. This approach also provides resiliency in case of failure,
as connection retries will happen. 

## Scenario

The proposed setup demonstrated by this example is the following:

* There are two Wildfly servers. The first one is the "JMS-Master" which hosts the topics and the second is a "JMS-Slave" 
  which connects to the first one.
* The demonstrated scenario uses a JMS bridge to forward messages from the "JMS-Slave" to the "JMS-Master".
* The message driven beans of the "JMS-Slave" connect remotely to the "JMS-Master" to as well receive messages.
* The JMS-Master connects its message driven beans directly to its own destinations.

The goal is that a message which is send to a JMS topic or a JMS queue can be received by the "JMS-Master" as well 
as multiple "JMS-Slaves". 

## Configure the JMS-Master

### Startup the server

Install an new server and start it with the full profile.

```
    standalone.bat -c standalone-full.xml
```

### Configure queue and topic

```
    jms-queue add --queue-address=HelloWorldQueue --entries=[queue/HelloWorldQueue java:jboss/exported/queue/HelloWorldQueue]
```

```
    jms-topic add --topic-address=HelloWorldTopic --entries=[topic/HelloWorldTopic java:jboss/exported/topic/HelloWorldTopic]
```

### Create an oas-connection factory as in-vm connection factory

```
    /subsystem=messaging-activemq/server=default/pooled-connection-factory=oas-connection:add(connectors=[in-vm], transaction=xa, entries=[java:/JmsOas])
```


### Add a user with permissions to send and consume JMS messages

* Call add-user.bat
* Create an application user user heinz/becker
* Assign the group guest (as already configured at standalone-full.xml). So if the CLI prompts the following, enter `guest`:
`What groups do you want this user to belong to? (Please enter a comma separated list, or leave blank for none)[  ]: guest`

### Deploy demo application

Deploy the demo app. E. g with 'mvn clean wildfly:deploy'

You can test the configuration while sending a POST message to:

```
    http://localhost:8080/mdb-qickstart/resources/demo/message/topic?message=Test
```

After this you should se an INFO message at the server log.

## Configure the JMS-Slave

### Startup the server

Install an new server and start it with the full profile and a port offset.

```
    standalone.bat -Djboss.socket.binding.port-offset=10000 -c standalone-full.xml
```

### Configure queue and topic

```
    jms-queue add --queue-address=HelloWorldQueue --entries=[queue/HelloWorldQueue java:jboss/exported/queue/HelloWorldQueue]
```

```
    jms-topic add --topic-address=HelloWorldTopic --entries=[topic/HelloWorldTopic java:jboss/exported/topic/HelloWorldTopic]
```

(requires reload)

### Configure the jms bridge

One for the queue:

```
   /subsystem=messaging-activemq/jms-bridge=HelloWorldQueue-jms-bridge:add(quality-of-service=DUPLICATES_OK, failure-retry-interval=5000, max-retries=-1, max-batch-size=10, max-batch-time=100, source-connection-factory=ConnectionFactory, source-destination=queue/HelloWorldQueue, target-user=heinz, target-password=becker, target-connection-factory=jms/RemoteConnectionFactory,target-destination=queue/HelloWorldQueue,target-context={java.naming.factory.initial=org.jboss.naming.remote.client.InitialContextFactory,java.naming.provider.url=http-remoting://localhost:8080})
```

And one for the topic:

```
   /subsystem=messaging-activemq/jms-bridge=orbis-HelloWorldTopic-bridge:add(quality-of-service=DUPLICATES_OK, failure-retry-interval=5000, max-retries=-1, max-batch-size=10, max-batch-time=100, source-connection-factory=ConnectionFactory, source-destination=topic/HelloWorldTopic, target-user=heinz, target-password=becker, target-connection-factory=jms/RemoteConnectionFactory,target-destination=topic/HelloWorldTopic,target-context={java.naming.factory.initial=org.jboss.naming.remote.client.InitialContextFactory,java.naming.provider.url=http-remoting://localhost:8080})
```

After this step, messages which are send to the demo topic or the demo queue are forwarded to the JMS-Master. If the
JMS-Master is down, the messages to a queue are stored and forwarded to the master if it is up again. 

But until now, the MDBs of the JMS-Slave are not able to receive messages from the JMS-Master.

### Create an oas-connection factory as outbound connection

First create an outbound-socket-binding pointing to the remote messaging server:

```
    /socket-binding-group=standard-sockets/remote-destination-outbound-socket-binding=remote-oas:add(host=localhost, port=8080)
```
    
Then create a remote-connector referencing the previously created outbound-socket-binding .

```
    /subsystem=messaging-activemq/server=default/http-connector=remote-http-connector:add(socket-binding=remote-oas,endpoint=http-acceptor)
```

(requires reload)

Create a pooled-connection-factory referencing the previously created remote-connector.

```
    /subsystem=messaging-activemq/server=default/pooled-connection-factory=oas-connection:add(connectors=[remote-http-connector], entries=[java:/jms/remoteCF], user=heinz, password=becker)
```

A connector defines how to connect to an integrated messaging server, and is used by a client to make connections.

### Deploy the demo application

Deploy the demo app to the second server (CLI or web console)

### Your configuration of the slave should now look like this

```
        <subsystem xmlns="urn:jboss:domain:messaging-activemq:1.0">
            <server name="default">
                <security-setting name="#">
                    <role name="guest" send="true" consume="true" create-non-durable-queue="true" delete-non-durable-queue="true"/>
                </security-setting>
                <address-setting name="#" dead-letter-address="jms.queue.DLQ" expiry-address="jms.queue.ExpiryQueue" max-size-bytes="10485760" page-size-bytes="2097152" message-counter-history-day-limit="10"/>
                <http-connector name="http-connector" socket-binding="http" endpoint="http-acceptor"/>
                <http-connector name="http-connector-throughput" socket-binding="http" endpoint="http-acceptor-throughput">
                    <param name="batch-delay" value="50"/>
                </http-connector>
                <http-connector name="remote-http-connector" socket-binding="remote-oas" endpoint="http-acceptor"/>
                <in-vm-connector name="in-vm" server-id="0"/>
                <http-acceptor name="http-acceptor" http-listener="default"/>
                <http-acceptor name="http-acceptor-throughput" http-listener="default">
                    <param name="batch-delay" value="50"/>
                    <param name="direct-deliver" value="false"/>
                </http-acceptor>
                <in-vm-acceptor name="in-vm" server-id="0"/>
                <jms-queue name="ExpiryQueue" entries="java:/jms/queue/ExpiryQueue"/>
                <jms-queue name="DLQ" entries="java:/jms/queue/DLQ"/>
                <jms-queue name="HelloWorldQueue" entries="queue/HelloWorldQueue java:jboss/exported/queue/HelloWorldQueue"/>
                <jms-topic name="HelloWorldTopic" entries="topic/HelloWorldTopic java:jboss/exported/topic/HelloWorldTopic"/>
                <connection-factory name="InVmConnectionFactory" entries="java:/ConnectionFactory" connectors="in-vm"/>
                <connection-factory name="RemoteConnectionFactory" entries="java:jboss/exported/jms/RemoteConnectionFactory" connectors="http-connector"/>
                <pooled-connection-factory name="activemq-ra" entries="java:/JmsXA java:jboss/DefaultJMSConnectionFactory" connectors="in-vm" transaction="xa"/>
                <pooled-connection-factory name="oas-connection" entries="java:/jms/remoteCF" connectors="remote-http-connector" user="heinz" password="becker"/>
            </server>
            <jms-bridge name="HelloWorldQueue-jms-bridge" quality-of-service="DUPLICATES_OK" failure-retry-interval="5000" max-retries="-1" max-batch-size="10" max-batch-time="100">
                <source connection-factory="ConnectionFactory" destination="queue/HelloWorldQueue"/>
                <target connection-factory="jms/RemoteConnectionFactory" destination="queue/HelloWorldQueue" user="heinz" password="becker">
                    <target-context>
                        <property name="java.naming.factory.initial" value="org.jboss.naming.remote.client.InitialContextFactory"/>
                        <property name="java.naming.provider.url" value="http-remoting://localhost:8080"/>
                    </target-context>
                </target>
            </jms-bridge>
            <jms-bridge name="orbis-HelloWorldTopic-bridge" quality-of-service="DUPLICATES_OK" failure-retry-interval="5000" max-retries="-1" max-batch-size="10" max-batch-time="100">
                <source connection-factory="ConnectionFactory" destination="topic/HelloWorldTopic"/>
                <target connection-factory="jms/RemoteConnectionFactory" destination="topic/HelloWorldTopic" user="heinz" password="becker">
                    <target-context>
                        <property name="java.naming.factory.initial" value="org.jboss.naming.remote.client.InitialContextFactory"/>
                        <property name="java.naming.provider.url" value="http-remoting://localhost:8080"/>
                    </target-context>
                </target>
            </jms-bridge>
        </subsystem>
```

## Code example

To send a message you should have a look at the DemoService. It connects to the local destinations. The message is
forwarded to the remote server by the JMS-Bridges. 

```
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
```

The message driven bean at the slave needs to be annotated with import `org.jboss.ejb3.annotation.ResourceAdapter`.
It remotely listens to the topic at the remote server. Otherwise it would not receive messages, if they are posted on 
another server (g. G. a second slave). 

```
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
```

## Test endpoints

```
    http://localhost:8080/mdb-qickstart/resources/demo/message/topic?message=Test
```

and:

```
    http://localhost:18080/mdb-qickstart/resources/demo/message/queue?message=Test
```

### Expected behaviour

* Messages send to a topic are processed at any connected server. So there should be a log message at any server.
* Messages send to a queue are processed at one of the connected servers. But it is undefined at which one. 
  So there should be exactly one log message.
* If the master is down, a message can not be received or send, if it is configured like in this example.
* If a slave is down, all other servers should not be impacted.
* A message to a queue will be redelivered it the master is back again (after it was down).
* A message to a topic is lost, if the master is down.