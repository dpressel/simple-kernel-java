import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.ZMQ;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

public class Utils
{

    private static final Logger LOG = LoggerFactory.getLogger(Message.class);

    public static String isoDate(Date date)
    {
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.S'Z'");
        df.setTimeZone(TimeZone.getTimeZone("UTC"));
        return df.format(date);
    }

    public static String isoDateNow()
    {
        return isoDate(new Date());
    }

    public static byte[] sign(byte[] key,
                              byte[] header,
                              byte[] parent,
                              byte[] meta,
                              byte[] content) throws Exception
    {
        byte[][] data = {header, parent, meta, content};
        SecretKeySpec keySpec = new SecretKeySpec(key, "HmacSHA256");
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(keySpec);
        for (int i = 0; i < 4; i++)
        {
            mac.update(data[i]);
        }
        return mac.doFinal();
    }

    // https://codahale.com/a-lesson-in-timing-attacks/
    public static boolean digestEqual(byte digesta[], byte digestb[])
    {
        if (digesta.length != digestb.length)
            return false;

        for (int i = 0; i < digesta.length; i++)
        {
            if (digesta[i] != digestb[i])
            {
                return false;
            }
        }
        return true;
    }

    public static ZMQ.Socket createPolledSocket(ZMQ.Context context, ZMQ.Poller poller, int type, String host, int port)
    {
        ZMQ.Socket socket = createSocket(context, type, host, port);
        poller.register(socket, ZMQ.Poller.POLLIN);
        return socket;
    }

    public static ZMQ.Socket createSocket(ZMQ.Context context, int type, String host, int port)
    {
        String hostProto = (host == null) ? "127.0.0.1" : host;
        ZMQ.Socket socket = context.socket(type);
        // For jeromq, we are stuck with tcp...
        hostProto = "tcp://" + hostProto + ":" + port;
        socket.bind(hostProto);
        LOG.debug("BOUND " + hostProto);
        return socket;
    }
}
