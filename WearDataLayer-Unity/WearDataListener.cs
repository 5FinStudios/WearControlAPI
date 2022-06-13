using System.Collections;
using System.Collections.Generic;
using UnityEngine;

public class WearDataListener : AndroidJavaProxy {
    public delegate void WearEventCallback(WearEvent wearEvent);
    public WearEventCallback OnEvent;

    public WearEvent wearEvent;
    public WearDataListener() : base("com.wearcontrolgames.unitywearapi.WearDataLayer$UnityWearListener") {
    }

    public void onEvent(AndroidJavaObject e) {
        wearEvent = new WearEvent(e);
        if (OnEvent != null) {
            OnEvent(wearEvent);
        }
    }
}
