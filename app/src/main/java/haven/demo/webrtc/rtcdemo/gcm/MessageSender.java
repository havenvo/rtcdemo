package haven.demo.webrtc.rtcdemo.gcm;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import haven.demo.webrtc.rtcdemo.utils.AppConfig;

/**
 * Created by harold on 11/05/2016.
 */
public class MessageSender {

    public void sendPost(final String to, final JSONObject message) {
        /**
         * registration_ids
         * data
         */
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    JSONObject msg = new JSONObject();
                    msg.put("to", to);
                    msg.put("data", message);
                    Log.d("MessageSender", "Send msg: " + msg.toString());

                    URL gcmAPI = new URL(AppConfig.GCM_API);
                    HttpURLConnection connection = (HttpURLConnection) gcmAPI.openConnection();

                    connection.setRequestMethod("POST");
                    connection.setRequestProperty("Content-Type", "application/json");
                    connection.setRequestProperty("Authorization", "key=" + AppConfig.API_KEY);
                    connection.setDoOutput(true);

                    OutputStream outputStream = connection.getOutputStream();
                    outputStream.write(msg.toString().getBytes());
                    outputStream.close();

                    int responseCode = connection.getResponseCode();
                    if (responseCode == 200) {
                        Log.i("Request Status", "This is success response status from server: " + responseCode);
                    } else {
                        Log.i("Request Status", "This is failure response status from server: " + responseCode);
                    }
                } catch (IOException | JSONException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }
}
