package haven.demo.webrtc.rtcdemo.activity;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Point;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.MediaStream;
import org.webrtc.VideoRenderer;
import org.webrtc.VideoRendererGui;

import haven.demo.webrtc.rtcdemo.R;
import haven.demo.webrtc.rtcdemo.model.Message;
import haven.demo.webrtc.rtcdemo.rtc.PeerConnectionParameters;
import haven.demo.webrtc.rtcdemo.rtc.WebRtcClient;
import haven.demo.webrtc.rtcdemo.utils.AppConfig;
import haven.demo.webrtc.rtcdemo.utils.Constants;

public class CallActivity extends Activity implements WebRtcClient.RtcListener, View.OnClickListener {
    private final String TAG = CallActivity.class.getSimpleName();

    private static final String VIDEO_CODEC_VP9 = "VP9";
    private static final String AUDIO_CODEC_OPUS = "opus";
    // Local preview screen position before call is connected.
    private static final int LOCAL_X_CONNECTING = 0;
    private static final int LOCAL_Y_CONNECTING = 0;
    private static final int LOCAL_WIDTH_CONNECTING = 100;
    private static final int LOCAL_HEIGHT_CONNECTING = 100;
    // Local preview screen position after call is connected.
    private static final int LOCAL_X_CONNECTED = 72;
    private static final int LOCAL_Y_CONNECTED = 72;
    private static final int LOCAL_WIDTH_CONNECTED = 25;
    private static final int LOCAL_HEIGHT_CONNECTED = 25;
    // Remote video screen position
    private static final int REMOTE_X = 0;
    private static final int REMOTE_Y = 0;
    private static final int REMOTE_WIDTH = 100;
    private static final int REMOTE_HEIGHT = 100;
    private VideoRendererGui.ScalingType scalingType = VideoRendererGui.ScalingType.SCALE_ASPECT_FILL;
    private GLSurfaceView vsv;
    private VideoRenderer.Callbacks localRender;
    private VideoRenderer.Callbacks remoteRender;
    private WebRtcClient client;

    private String mCallerId;
    private String mCalleeId;
    private String myRegId;
    private ImageButton btAnswer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN
                        | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                        | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                        | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                        | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        setContentView(R.layout.activity_call);

        this.mCallerId = getIntent().getStringExtra(Constants.KEY_CALLER_ID);
        this.mCalleeId = getIntent().getStringExtra(Constants.KEY_CALLEE_ID);
        Log.d(TAG, "onCreate mCallerId: " + mCallerId);
        Log.d(TAG, "onCreate mCalleeId: " + mCalleeId);

        SharedPreferences prefs = getApplicationContext().getSharedPreferences(AppConfig.PRE_NAME, Context.MODE_PRIVATE);
        myRegId = prefs.getString(Constants.KEY_REG_ID, null);

        btAnswer = (ImageButton) findViewById(R.id.btAnswer);
        if (mCallerId.equals(myRegId)) {
            btAnswer.setVisibility(View.GONE);
        }

        vsv = (GLSurfaceView) findViewById(R.id.glview_call);
        vsv.setPreserveEGLContextOnPause(true);
        vsv.setKeepScreenOn(true);

        VideoRendererGui.setView(vsv, new Runnable() {
            @Override
            public void run() {
                init();
            }
        });

        // local and remote render
        remoteRender = VideoRendererGui.create(
                REMOTE_X, REMOTE_Y,
                REMOTE_WIDTH, REMOTE_HEIGHT, scalingType, false);
        localRender = VideoRendererGui.create(
                LOCAL_X_CONNECTING, LOCAL_Y_CONNECTING,
                LOCAL_WIDTH_CONNECTING, LOCAL_HEIGHT_CONNECTING, scalingType, true);
    }

    private void init() {
        Point displaySize = new Point();
        Log.d(TAG, "Point: " + displaySize.toString());
        getWindowManager().getDefaultDisplay().getSize(displaySize);
        PeerConnectionParameters params = new PeerConnectionParameters(
                true, false, displaySize.x, displaySize.y, 30, 1, VIDEO_CODEC_VP9, true, 1, AUDIO_CODEC_OPUS, true);

        client = new WebRtcClient(this, params, VideoRendererGui.getEGLContext(), myRegId);
        this.onCallReady();
    }

    @Override
    public void onClick(View v) {
        Log.d(TAG, "onClick");
        switch (v.getId()) {
            case R.id.btAnswer:
                btAnswer.setVisibility(View.GONE);
                answer();
                break;
            case R.id.btEndCall:
                finish();
                break;
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        LocalBroadcastManager.getInstance(this).registerReceiver(gcmMsgReceiver, new IntentFilter(Constants.FILTER_RECEIVE_GCM_MSG));
        vsv.onResume();
        if (client != null) {
            client.onResume();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        vsv.onPause();
        if (client != null) {
            client.onPause();
        }
    }

    @Override
    public void onDestroy() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(gcmMsgReceiver);
        if (client != null) {
            client.onDestroy();
        }
        super.onDestroy();
    }

    private BroadcastReceiver gcmMsgReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String jsonMsg = intent.getStringExtra(Constants.KEY_JSON_MSG);
            try {
                JSONObject obj = new JSONObject(jsonMsg);
                client.callMsgHandlerOnPeer(obj);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    };

    private void answer() {
        Log.d(TAG, "ANSWER mCallerId: " + mCallerId);
        try {
            client.sendMessage(mCallerId, Message.Type.init.toString(), null);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void call() {
        try {
            JSONObject obj = new JSONObject();
            obj.put(Constants.KEY_CALLER_ID, mCallerId);
            client.sendMessage(mCalleeId, Message.Type.request.toString(), obj);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        startCam();
    }

    private void startCam() {
        // Camera settings
        client.start();
    }

    @Override
    public void onCallReady() {
        if (mCallerId.equals(myRegId)) {
            call();
        } else {
            startCam();
        }
    }

    @Override
    public void onStatusChanged(final String newStatus) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(), newStatus, Toast.LENGTH_SHORT).show();
            }
        });
        if (newStatus.equals(WebRtcClient.CONN_STATE_CLOSED)) {
            finish();
        }
    }

    @Override
    public void onLocalStream(MediaStream localStream) {
        Log.d(TAG, "onLocalStream");
        localStream.videoTracks.get(0).addRenderer(new VideoRenderer(localRender));
        VideoRendererGui.update(localRender,
                LOCAL_X_CONNECTING, LOCAL_Y_CONNECTING,
                LOCAL_WIDTH_CONNECTING, LOCAL_HEIGHT_CONNECTING,
                scalingType);
    }

    @Override
    public void onAddRemoteStream(MediaStream remoteStream, int endPoint) {
        Log.d(TAG, "onAddRemoteStream");
        remoteStream.videoTracks.get(0).addRenderer(new VideoRenderer(remoteRender));
        VideoRendererGui.update(remoteRender,
                REMOTE_X, REMOTE_Y,
                REMOTE_WIDTH, REMOTE_HEIGHT, scalingType);
        VideoRendererGui.update(localRender,
                LOCAL_X_CONNECTED, LOCAL_Y_CONNECTED,
                LOCAL_WIDTH_CONNECTED, LOCAL_HEIGHT_CONNECTED,
                scalingType);
    }

    @Override
    public void onRemoveRemoteStream(int endPoint) {
        Log.d(TAG, "onRemoveRemoteStream");
        VideoRendererGui.update(localRender,
                LOCAL_X_CONNECTING, LOCAL_Y_CONNECTING,
                LOCAL_WIDTH_CONNECTING, LOCAL_HEIGHT_CONNECTING,
                scalingType);
    }
}
