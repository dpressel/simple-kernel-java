import java.util.UUID;

public class Header
{
    public String msg_id;
    public String username;
    public String session;
    public String date;
    public String msg_type;
    public String version = "5.0";

    public Header()
    {

    }
    public Header(String sessionId, String messageType)
    {
        msg_id = UUID.randomUUID().toString();
        username = "kernel";
        date = Utils.isoDateNow();
        username = "kernel";
        session = sessionId;
        msg_type = messageType;
        version = "5.0";
    }
}
