package com.antonio_asaro.www.android_antonio_analog;

import android.util.Log;

import com.google.android.gms.wearable.CapabilityInfo;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.WearableListenerService;

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
    public void onPeerDisconnected(com.google.android.gms.wearable.Node peer) {
        Log.d(TAG, "onPeerDisconnected()");
    }

    @Override
    public void onPeerConnected(com.google.android.gms.wearable.Node peer) {
        Log.d(TAG, "onPeerConnected()");
    }

}
