package haven.demo.webrtc.rtcdemo.rtc;

import android.opengl.EGLContext;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.AudioSource;
import org.webrtc.DataChannel;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoCapturerAndroid;
import org.webrtc.VideoSource;

import java.util.HashMap;
import java.util.LinkedList;

import haven.demo.webrtc.rtcdemo.gcm.MessageSender;
import haven.demo.webrtc.rtcdemo.model.Message;

public class WebRtcClient {
    private final static String TAG = WebRtcClient.class.getSimpleName();
    private final static int MAX_PEER = 2;
    private boolean[] endPoints = new boolean[MAX_PEER];
    private PeerConnectionFactory factory;
    private HashMap<String, Peer> peers = new HashMap<>();
    private LinkedList<PeerConnection.IceServer> iceServers = new LinkedList<>();
    private PeerConnectionParameters pcParams;
    private MediaConstraints pcConstraints = new MediaConstraints();
    private MediaStream localMS;
    private VideoSource videoSource;
    private RtcListener mListener;
    //    private Socket client;
    private MessageSender mSender;
    private MessageHandler msgHandler;
    private String myRegId;

    public static final String CONN_STATE_CONNECTING = "CONNECTING";
    public static final String CONN_STATE_CLOSED = "CLOSED";

    /**
     * Implement this interface to be notified of events.
     */
    public interface RtcListener {
        void onCallReady();

        void onStatusChanged(String newStatus);

        void onLocalStream(MediaStream localStream);

        void onAddRemoteStream(MediaStream remoteStream, int endPoint);

        void onRemoveRemoteStream(int endPoint);
    }

    private interface Command {
        void execute(String peerId, JSONObject payload) throws JSONException;
    }

    private class CreateOfferCommand implements Command {
        public void execute(String peerId, JSONObject payload) throws JSONException {
            Peer peer = peers.get(peerId);
            peer.pc.createOffer(peer, pcConstraints);
        }
    }

    private class CreateAnswerCommand implements Command {
        public void execute(String peerId, JSONObject payload) throws JSONException {
            Peer peer = peers.get(peerId);
            SessionDescription sdp = new SessionDescription(
                    SessionDescription.Type.fromCanonicalForm(payload.getString(Message.KEY_TYPE)),
                    payload.getString(Message.KEY_SDP)
            );
            peer.pc.setRemoteDescription(peer, sdp);
            peer.pc.createAnswer(peer, pcConstraints);
        }
    }

    private class SetRemoteSDPCommand implements Command {
        public void execute(String peerId, JSONObject payload) throws JSONException {
            Peer peer = peers.get(peerId);
            SessionDescription sdp = new SessionDescription(
                    SessionDescription.Type.fromCanonicalForm(payload.getString(Message.KEY_TYPE)),
                    payload.getString(Message.KEY_SDP)
            );
            peer.pc.setRemoteDescription(peer, sdp);
        }
    }

    private class AddIceCandidateCommand implements Command {
        public void execute(String peerId, JSONObject payload) throws JSONException {
            PeerConnection pc = peers.get(peerId).pc;
            if (pc.getRemoteDescription() != null) {
                IceCandidate candidate = new IceCandidate(
                        payload.getString(Message.KEY_ID),
                        payload.getInt(Message.KEY_LABEL),
                        payload.getString(Message.KEY_CANDIDATE)
                );
                pc.addIceCandidate(candidate);
            }
        }
    }

    /**
     * Send a message through the signaling server
     *
     * @param to      id of recipient
     * @param type    type of message
     * @param payload payload of message
     * @throws JSONException
     */
    public void sendMessage(String to, String type, JSONObject payload) throws JSONException {
        JSONObject message = new JSONObject();
        message.put(Message.KEY_SENT_FROM, myRegId);
        message.put(Message.KEY_TYPE, type);
        message.put(Message.KEY_PAYLOAD, payload);
        mSender.sendPost(to, message);
    }

    public void callMsgHandlerOnPeer(JSONObject data) {
        msgHandler.onPeer(data);
    }

    public class MessageHandler {
        private HashMap<String, Command> commandMap;

        private MessageHandler() {
            this.commandMap = new HashMap<>();
            commandMap.put(Message.Type.init.toString(), new CreateOfferCommand());
            commandMap.put(Message.Type.offer.toString(), new CreateAnswerCommand());
            commandMap.put(Message.Type.answer.toString(), new SetRemoteSDPCommand());
            commandMap.put(Message.Type.candidate.toString(), new AddIceCandidateCommand());
            Log.i("Command", "Init successfully");
        }

        public void onPeer(JSONObject data) {
            Log.d(TAG, "MessageHandler onPeer: " + data.toString());
            try {
                String from = data.getString(Message.KEY_SENT_FROM);
                String type = data.getString(Message.KEY_TYPE);
                JSONObject payload = null;
                if (!type.equals(Message.Type.init.toString())) {
                    String str = data.getString(Message.KEY_PAYLOAD);
                    payload = new JSONObject(str);
                }
                // if peer is unknown, try to add him
                if (!peers.containsKey(from)) {
                    // if MAX_PEER is reach, ignore the call
                    int endPoint = findEndPoint();
                    if (endPoint != MAX_PEER) {
                        Peer peer = addPeer(from, endPoint);
                        peer.pc.addStream(localMS);
                        commandMap.get(type).execute(from, payload);
                    }
                } else {
                    commandMap.get(type).execute(from, payload);
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    private class Peer implements SdpObserver, PeerConnection.Observer {
        private PeerConnection pc;
        private String id;
        private int endPoint;

        @Override
        public void onCreateSuccess(final SessionDescription sdp) {
            // TODO: modify sdp to use pcParams prefered codecs
            try {
                JSONObject payload = new JSONObject();
                payload.put(Message.KEY_TYPE, sdp.type.canonicalForm());
                payload.put(Message.KEY_SDP, sdp.description);
                sendMessage(id, sdp.type.canonicalForm(), payload);
                pc.setLocalDescription(Peer.this, sdp);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onSetSuccess() {
        }

        @Override
        public void onCreateFailure(String s) {
        }

        @Override
        public void onSetFailure(String s) {
        }

        @Override
        public void onSignalingChange(PeerConnection.SignalingState signalingState) {
        }

        @Override
        public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {
            Log.d(TAG, "IceConnectionState: " + iceConnectionState.toString());
            if (iceConnectionState == PeerConnection.IceConnectionState.CONNECTED) {
                mListener.onStatusChanged("CONNECTED");
                return;
            }
            if (iceConnectionState == PeerConnection.IceConnectionState.DISCONNECTED) {
                removePeer(id);
                mListener.onStatusChanged("DISCONNECTED");
                return;
            }
            if (iceConnectionState == PeerConnection.IceConnectionState.CLOSED) {
                mListener.onStatusChanged(CONN_STATE_CLOSED);
            }
        }

        @Override
        public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {
        }

        @Override
        public void onIceCandidate(final IceCandidate candidate) {
            try {
                JSONObject payload = new JSONObject();
                payload.put(Message.KEY_LABEL, candidate.sdpMLineIndex);
                payload.put(Message.KEY_ID, candidate.sdpMid);
                payload.put(Message.KEY_CANDIDATE, candidate.sdp);
                sendMessage(id, Message.Type.candidate.toString(), payload);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onAddStream(MediaStream mediaStream) {
            Log.d(TAG, "onAddStream " + mediaStream.label());
            // remote streams are displayed from 1 to MAX_PEER (0 is localStream)
            mListener.onAddRemoteStream(mediaStream, endPoint + 1);
        }

        @Override
        public void onRemoveStream(MediaStream mediaStream) {
            Log.d(TAG, "onRemoveStream " + mediaStream.label());
            removePeer(id);
        }

        @Override
        public void onDataChannel(DataChannel dataChannel) {
        }

        @Override
        public void onRenegotiationNeeded() {

        }

        public Peer(String id, int endPoint) {
            Log.d(TAG, "new Peer: " + id + " " + endPoint);
            this.pc = factory.createPeerConnection(iceServers, pcConstraints, this);
            this.id = id;
            this.endPoint = endPoint;

            if (localMS == null) {
                setCamera();
            }
            pc.addStream(localMS); //, new MediaConstraints()
        }
    }

    private Peer addPeer(String id, int endPoint) {
        Peer peer = new Peer(id, endPoint);
        peers.put(id, peer);

        endPoints[endPoint] = true;
        return peer;
    }

    private void removePeer(String id) {
        Peer peer = peers.get(id);
        mListener.onRemoveRemoteStream(peer.endPoint);
        peer.pc.close();
        peers.remove(peer.id);
        endPoints[peer.endPoint] = false;
    }

    public WebRtcClient(RtcListener listener, PeerConnectionParameters params, EGLContext mEGLcontext, String myRegId) {
        this.myRegId = myRegId;
        mListener = listener;
        pcParams = params;
        mSender = new MessageSender();
        PeerConnectionFactory.initializeAndroidGlobals(listener, true, true,
                params.videoCodecHwAcceleration, mEGLcontext);
        factory = new PeerConnectionFactory();
        msgHandler = new MessageHandler();

        iceServers.add(new PeerConnection.IceServer("stun:23.21.150.121"));
        iceServers.add(new PeerConnection.IceServer("stun:stun.l.google.com:19302"));
        iceServers.add(new PeerConnection.IceServer("turn:numb.viagenie.ca", "river@enclave.vn", "enclaveit@123"));

        pcConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
        pcConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"));
        pcConstraints.optional.add(new MediaConstraints.KeyValuePair("DtlsSrtpKeyAgreement", "true"));
    }

    /**
     * Call this method in Activity.onPause()
     */
    public void onPause() {
        if (videoSource != null) videoSource.stop();
    }

    /**
     * Call this method in Activity.onResume()
     */
    public void onResume() {
        if (videoSource != null) videoSource.restart();
    }

    /**
     * Call this method in Activity.onDestroy()
     */
    public void onDestroy() {
        Log.d(TAG, "onDestroy is called");
        for (Peer peer : peers.values()) {
            Log.d(TAG, "onDestroy is disposing peer");
            peer.pc.dispose();
        }
        if (videoSource != null) {
            videoSource.dispose();
            videoSource = null;
        }
        factory.dispose();
        localMS = null;
    }

    private int findEndPoint() {
        for (int i = 0; i < MAX_PEER; i++) if (!endPoints[i]) return i;
        return MAX_PEER;
    }

    /**
     * Start the client.
     * <p/>
     * Set up the local stream and notify the signaling server.
     * Call this method after onCallReady.
     */
    public void start() {
        setCamera();
    }

    private void setCamera() {
        localMS = factory.createLocalMediaStream("ARDAMS");
        if (pcParams.videoCallEnabled) {
            MediaConstraints videoConstraints = new MediaConstraints();
            videoConstraints.mandatory.add(new MediaConstraints.KeyValuePair("maxHeight", Integer.toString(pcParams.videoHeight)));
            videoConstraints.mandatory.add(new MediaConstraints.KeyValuePair("maxWidth", Integer.toString(pcParams.videoWidth)));
            videoConstraints.mandatory.add(new MediaConstraints.KeyValuePair("maxFrameRate", Integer.toString(pcParams.videoFps)));
            videoConstraints.mandatory.add(new MediaConstraints.KeyValuePair("minFrameRate", Integer.toString(pcParams.videoFps)));

            videoSource = factory.createVideoSource(getVideoCapturer(), videoConstraints);
            localMS.addTrack(factory.createVideoTrack("ARDAMSv0", videoSource));
        }

        AudioSource audioSource = factory.createAudioSource(new MediaConstraints());
        localMS.addTrack(factory.createAudioTrack("ARDAMSa0", audioSource));

        mListener.onLocalStream(localMS);
    }

    private VideoCapturer getVideoCapturer() {
        String frontCameraDeviceName = VideoCapturerAndroid.getNameOfFrontFacingDevice();
        return VideoCapturerAndroid.create(frontCameraDeviceName);
    }
}
