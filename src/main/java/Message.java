import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.ZFrame;
import org.zeromq.ZMQ;
import org.zeromq.ZMsg;

import javax.xml.bind.DatatypeConverter;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class Message
{

    private static final Logger LOG = LoggerFactory.getLogger(Message.class);
    public Header header;
    public Header parentHeader;
    public Map<String, Object> metadata;
    public Map<String, Object> content;
    public List<String> identities;

    public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public Message()
    {
        identities = new ArrayList<>();
    }
    public Message(String sessionUUID, String messageType)
    {

        header = new Header(sessionUUID, messageType);
        identities = new ArrayList<>();
        content = new HashMap<>();
        metadata = new HashMap<>();
    }
    public Message(Header parent, String messageType)
    {
        header = new Header(parent.session, messageType);
        parentHeader = parent;
        identities = new ArrayList<>();
        content = new HashMap<>();
        metadata = new HashMap<>();
    }

    public static Message recv(byte[] key, ZMQ.Socket socket) throws Exception
    {
        ZMsg zmsg = ZMsg.recvMsg(socket);
        try
        {

            Message message = new Message();
            ZFrame[] zframes = new ZFrame[zmsg.size()];
            zmsg.toArray(zframes);
            boolean found = false;
            int current = 0;
            for (; current < zframes.length; ++current)
            {
                byte[] next = zframes[current].getData();
                String delim = new String(next, StandardCharsets.UTF_8);
                if (delim.equals("<IDS|MSG>"))
                {
                    found = true;
                    break;
                }
                else
                {
                    message.identities.add(delim);
                }
            }

            assert(found);
            byte[] hmac = zframes[++current].getData();
            // hmac is an UTF-8 string and has to be converted into a byte array first
            hmac = DatatypeConverter.parseHexBinary(new String(hmac));
            byte[] headerBytes = zframes[++current].getData();
            LOG.debug(new String(headerBytes));
            message.header = OBJECT_MAPPER.readValue(headerBytes, Header.class);
            byte[] parentBytes = zframes[++current].getData();
            LOG.debug(new String(parentBytes));
            message.parentHeader = OBJECT_MAPPER.readValue(parentBytes, Header.class);
            byte[] metaBytes = zframes[++current].getData();
            LOG.debug(new String(metaBytes));
            byte[] contentBytes = zframes[++current].getData();
            LOG.debug(new String(contentBytes));
            byte[] digest = Utils.sign(key, headerBytes, parentBytes, metaBytes, contentBytes);

            assert(Utils.digestEqual(digest, hmac));
            message.metadata = OBJECT_MAPPER.readValue(parentBytes, Map.class);
            message.content = OBJECT_MAPPER.readValue(contentBytes, Map.class);
            return message;
        }
        finally
        {
            zmsg.destroy();
        }

    }

    public void send(ZMQ.Socket socket, byte[] key) throws Exception
    {

        ZMsg zmsg = new ZMsg();
        for (String identity : identities)
        {
            zmsg.add(identity);
        }
        zmsg.add("<IDS|MSG>");
        byte[] headerBytes  = OBJECT_MAPPER.writeValueAsBytes(header);

        byte[] parentBytes = OBJECT_MAPPER.writeValueAsBytes(parentHeader);
        byte[] metaBytes = OBJECT_MAPPER.writeValueAsBytes(metadata);
        LOG.debug("[SEND] metadata: " + new String(metaBytes));
        byte[] contentBytes = OBJECT_MAPPER.writeValueAsBytes(content);
        LOG.debug("[SEND] content: " + new String(contentBytes));
        byte[] signature  = Utils.sign(key, headerBytes, parentBytes, metaBytes, contentBytes);
        signature = DatatypeConverter.printHexBinary(signature).toLowerCase().getBytes();
        zmsg.add(signature);
        zmsg.add(headerBytes);
        zmsg.add(parentBytes);
        zmsg.add(metaBytes);
        zmsg.add(contentBytes);
        zmsg.send(socket);
    }
}
