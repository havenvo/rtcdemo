package haven.demo.webrtc.rtcdemo.model;

public class Message {
    public static final String KEY_ID = "id";
    public static final String KEY_TYPE = "type";
    public static final String KEY_SENT_FROM = "sent_from";
    public static final String KEY_SDP = "sdp";
    public static final String KEY_LABEL = "label";
    public static final String KEY_PAYLOAD = "payload";
    public static final String KEY_CANDIDATE = "candidate";

    public enum Type {
        init,
        offer,
        answer,
        candidate,
        request
    }
}
