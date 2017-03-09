import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.ZMQ.Context;
import org.zeromq.ZMQ.Socket;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Poller;

import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SimpleKernel
{


    public static int CONTROL = 0;
    public static int STDIN = 1;
    public static int SHELL = 2;

    Socket heartbeatChannel;
    Socket controlChannel;
    Socket stdinChannel;
    Socket shellChannel;
    Socket iopubChannel;

    byte[] key;
    String sessionId;
    Poller items;
    Context context;
    private static final Logger LOG = LoggerFactory.getLogger(SimpleKernel.class);
    boolean exiting;


    /**
     * config = {
     'control_port'      : 0,
     'hb_port'           : 0,
     'iopub_port'        : 0,
     'ip'                : '127.0.0.1',
     'key'               : str(uuid.uuid4()),
     'shell_port'        : 0,
     'signature_scheme'  : 'hmac-sha256',
     'stdin_port'        : 0,
     'transport'         : 'tcp'
     }
     * @param config
     */

    public SimpleKernel(Config config)
    {
        key = config.key.getBytes();
        sessionId = UUID.randomUUID().toString();
        //  Prepare our context and sockets
        context = ZMQ.context(1);
        //  Initialize poll set
        items = new Poller(3);
        heartbeatChannel = Utils.createSocket(context, ZMQ.REP, config.ip, config.hb_port);
        controlChannel = Utils.createPolledSocket(context, items, ZMQ.ROUTER, config.ip, config.control_port);
        stdinChannel = Utils.createPolledSocket(context, items, ZMQ.ROUTER, config.ip, config.stdin_port);
        shellChannel = Utils.createPolledSocket(context, items, ZMQ.ROUTER, config.ip, config.shell_port);
        iopubChannel = Utils.createSocket(context, ZMQ.PUB, config.ip, config.iopub_port);
    }

    void dumpHeader(String where, Message msg)
    {
        LOG.info("=========================================");
        LOG.info("Channel:  " + where);
        LOG.info("=========================================");
        LOG.info("msg_id:   " + msg.header.msg_id);
        LOG.info("msg_type: " + msg.header.msg_type);
        LOG.info("username: " + msg.header.username);
        LOG.info("version:  " + msg.header.version);
        LOG.info("date:     " + msg.header.date);
        LOG.info("-----------------------------------------");
    }

    class HeartbeatThread extends Thread
    {
        public HeartbeatThread()
        {

        }
        @Override
        public void run()
        {
            LOG.info("Starting heartbeat");
            while (!exiting)
            {
                ZMQ.proxy(heartbeatChannel, heartbeatChannel, null);
            }
        }
    }
    void start() throws Exception
    {

        HeartbeatThread thread = new HeartbeatThread();
        thread.start();
        LOG.info("Starting Heartbeat thread");
        exiting = false;
        //  Switch messages between sockets
        while (!Thread.currentThread().isInterrupted() && !exiting)
        {


            items.poll();

            if (items.pollin(CONTROL))
            {
                Message msg = Message.recv(key, controlChannel);
                dumpHeader("CONTROL", msg);
                if (msg.header.msg_type.equals("shutdown_request"))
                {
                    exiting = true;
                }
            }
            if (items.pollin(STDIN))
            {

                Message msg = Message.recv(key, stdinChannel);
                dumpHeader("STDIN", msg);
                System.out.println("STDIN message recv'd");
            }
            if (items.pollin(SHELL))
            {
                Message msg = Message.recv(key, shellChannel);
                dumpHeader("SHELL", msg);
                shellHandler(msg);
            }

        }
        LOG.info("DONE");
        //  We never get here but clean up anyhow
        heartbeatChannel.close();
        controlChannel.close();
        stdinChannel.close();
        shellChannel.close();
        iopubChannel.close();

        context.term();
    }

    public static void main(String[] args)
    {

        try
        {
            LOG.debug("Config file: " + args[0]);
            FileInputStream fis = new FileInputStream(args[0]);
            ObjectMapper om = new ObjectMapper();
            Config config = om.readValue(fis, Config.class);
            SimpleKernel sk = new SimpleKernel(config);
            LOG.info("Launched kernel");
            sk.start();
            LOG.info("Kernel finished");

        }
        catch (Exception ex)
        {
            ex.printStackTrace();
        }
    }

    int globalExecutionCount = 1;

    private void sendStatus(Message msgParent, String status) throws Exception
    {
        Message busyStatus = new Message(msgParent.header, "status");
        busyStatus.content.put("execution_state", status);
        busyStatus.send(iopubChannel, key);
    }
    private void executeRequest(Message msg) throws Exception
    {
        LOG.debug("simple-kernel-java Executing");
        sendStatus(msg, "busy");

        Message executeInput = new Message(msg.header, "execute_input");
        executeInput.content.put("execution_count", this.globalExecutionCount);
        executeInput.content.put("code", msg.content.get("code"));
        executeInput.send(iopubChannel, key);

        Message stream = new Message(msg.header, "stream");
        stream.content.put("name", "stdout");
        stream.content.put("text", "hello, world\n");
        stream.send(iopubChannel, key);

        Message executeResult = new Message(msg.header, "execute_result");
        executeResult.content.put("execution_count", this.globalExecutionCount);
        Map<String, Object> data = new HashMap<>();
        data.put("text/plain", "result!");
        executeResult.content.put("data", data);
        executeResult.content.put("metadata", new HashMap<>());
        executeResult.send(iopubChannel, key);

        sendStatus(msg, "idle");

        Message executeReply = new Message(msg.header, "execute_reply");
        executeReply.content.put("status", "ok");
        executeReply.content.put("execution_count", globalExecutionCount);
        executeReply.content.put("user_variables", new HashMap<>());
        executeReply.content.put("payload", new ArrayList<>());
        executeReply.content.put("user_expressions", new HashMap<>());
        executeReply.identities = msg.identities;
        executeReply.send(shellChannel, key);
        globalExecutionCount++;
    }
    private void shellHandler(Message msg) throws Exception
    {

        if (msg.header.msg_type.equals("execute_request"))
        {
            executeRequest(msg);
        }
        else if (msg.header.msg_type.equals("kernel_info_request"))
        {

            Message kernelInfoReply = new Message(msg.header, "kernel_info_reply");
            kernelInfoReply.identities = msg.identities;
            kernelInfoReply.content.put("protocol_version", "5.0");
            kernelInfoReply.content.put("implementation", "simple-kernel-java");
            kernelInfoReply.content.put("implementation_version", "0.0.1");

            Map<String, Object> languageInfo = new HashMap<>();
            languageInfo.put("name", "simple-kernel-java");
            languageInfo.put("version", "0.0.1");
            languageInfo.put("mimetype", "");
            languageInfo.put("file_extension", ".java");
            languageInfo.put("pygments_lexer", "");
            languageInfo.put("codemirror_code", "");
            languageInfo.put("nbconvert_exporter", "");

            kernelInfoReply.content.put("language_info", languageInfo);
            kernelInfoReply.content.put("banner", "Simple Kernel Java");
            kernelInfoReply.send(shellChannel, key);
            sendStatus(msg, "idle");
        }
        else if (msg.header.msg_type.equals("history_request"))
        {
            LOG.info("unhandled history request");
        }
        else
        {
            LOG.warn("unknown msg_type " + msg.header.msg_type);
        }
    }

}