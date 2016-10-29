package haven.demo.webrtc.rtcdemo.gcm;

import android.app.IntentService;
import android.content.Intent;

/**
 * Created by harold on 10/05/2016.
 */
public class MessageService  extends IntentService {


    public MessageService() {
        super("MessageService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        MessageReceiver.completeWakefulIntent(intent);
    }



}
