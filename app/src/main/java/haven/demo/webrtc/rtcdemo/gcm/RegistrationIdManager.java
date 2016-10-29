package haven.demo.webrtc.rtcdemo.gcm;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.gcm.GoogleCloudMessaging;
import com.google.android.gms.iid.InstanceID;

import java.io.IOException;

public class RegistrationIdManager {
    // Constants
    private static final String TAG = "RegistrationIdManager";
    private static final String PROPERTY_REG_ID = "registration_id";
    private final static int PLAY_SERVICES_RESOLUTION_REQUEST = 9000;

    private GoogleCloudMessaging gcm;
    private String regId;
    private String projectNumber;
    private Activity activity;

    public RegistrationIdManager(Activity activity, String projectNumber) {
        this.activity = activity;
        this.projectNumber = projectNumber;
        this.gcm = GoogleCloudMessaging.getInstance(activity);
    }

    public void registerIfNeeded(final RegistrationCompletedHandler handler) {
        if(checkPlayServices()) {
            regId = getRegistrationId(getActivity());
            if(regId.isEmpty()) {
                registerInBackground(handler);
            } else {
                handler.onSuccess(regId, false);
            }
        } else {
            Log.i(TAG, "No valid Google Play Services APK found! ");
        }
    }

    private void registerInBackground(final RegistrationCompletedHandler handler) {
        new AsyncTask<Void, Void, String>() {

            @Override
            protected String doInBackground(Void... params) {
                try {
                    if(gcm == null) {
                        gcm = GoogleCloudMessaging.getInstance(getActivity());
                    }
                    InstanceID instanceID = InstanceID.getInstance(getActivity());
                    regId = instanceID.getToken(projectNumber, GoogleCloudMessaging.INSTANCE_ID_SCOPE, null);
                } catch (IOException e) {
                    handler.onFailure("Error :" + e.getMessage());
                }
                return regId;
            }

            @Override
            protected void onPostExecute(String regId) {
                if(regId != null) {
                    handler.onSuccess(regId, true);
                }
            }
        }.execute(null, null, null);
    }

    private String getRegistrationId(Context context) {
        final SharedPreferences prefs = getGCMPreferences(context);
        String registrationId = prefs.getString(PROPERTY_REG_ID,"");
        if(registrationId.isEmpty()){
            Log.i(TAG, "Registration not found");
            return "";
        }
        return registrationId;
    }

    private SharedPreferences getGCMPreferences(Context context) {
        return getActivity().getSharedPreferences(context.getPackageName(), Context.MODE_PRIVATE);
    }

    private Activity getActivity() {
        return activity;
    }

    private boolean checkPlayServices() {
        int resultCode = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(getActivity());
        if(resultCode != ConnectionResult.SUCCESS){
            if(GoogleApiAvailability.getInstance().isUserResolvableError(resultCode)){
                GoogleApiAvailability.getInstance().getErrorDialog(getActivity(), resultCode , PLAY_SERVICES_RESOLUTION_REQUEST).show();
            } else {
                Log.i(TAG, "This device is not supported Google Play Services");
            }
            return false;
        }
        return true;
    }

    public static abstract class RegistrationCompletedHandler {
        public abstract void onSuccess(String registrationId, boolean isNewRegistration);
        public void onFailure(String ex){
            Log.e(TAG, ex);
        }
    }
}
