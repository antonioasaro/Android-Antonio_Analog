package com.antonio_asaro.www.android_antonio_analog;

import android.os.Vibrator;
import android.util.Log;

import com.google.android.gms.wearable.CapabilityInfo;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.WearableListenerService;

import java.util.Calendar;
import java.util.List;

public class ComplicationListenerService extends WearableListenerService {
    private static final String TAG = "ComplicationListenerService";
    private static final int FORGOT_PHONE_NOTIFICATION_ID = 1;

    @Override
    public void onCreate() {
        Log.d(TAG, "onCreate()");
        super.onCreate();
    }

    @Override
    public void onCapabilityChanged(CapabilityInfo capabilityInfo) {
        Log.d(TAG, "onCapabilityChanged()");
    }

    @Override
    public void onConnectedNodes(List<Node> connectedNodes) {
        Log.d(TAG, "onConnectedNodes()");
        for (Node node : connectedNodes){
            if (node.isNearby()){
                Log.d(TAG, "Nearby: " + node.getDisplayName());
            }
        }
    }

    @Override
    public void onPeerConnected(com.google.android.gms.wearable.Node peer) {
        Log.d(TAG, "onPeerConnected()");
        ComplicationWatchFaceService.mWearableConnected = true;
    }

    @Override
    public void onPeerDisconnected(com.google.android.gms.wearable.Node peer) {
        Log.d(TAG, "onPeerDisconnected()");
        Calendar now = Calendar.getInstance();
        int hour = now.get(Calendar.HOUR_OF_DAY);

        ComplicationWatchFaceService.mWearableConnected = false;
        if ((hour >= 7) && (hour <= 22)) {
            Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            long[] vibrationPattern = {0, 500, 250, 500};
            vibrator.vibrate(vibrationPattern, -1);
        }
    }

}
