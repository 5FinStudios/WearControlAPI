using System.Collections;
using System.Collections.Generic;
using UnityEngine;

public class WearDataLayer {
    private AndroidJavaObject wearLayer;
    public WearDataLayer(AndroidJavaObject obj) {
        this.wearLayer = obj;
    }

    public void CheckAvailableNodes() {
        wearLayer.Call("checkAvailableNodes");
    }

    public void AddListener(WearDataListener listener) {
        wearLayer.Call("addListener", listener);
    }

    public void RequestPair(string targetController, int playerNum) {
        wearLayer.Call("requestPair", targetController, playerNum);
    }

    public void AcceptPair(WearEvent.ControllerType controllerType, int playerNum) {
        wearLayer.Call("acceptPair", controllerType.ToString(), playerNum);
    }

    public void Initialize() {
        wearLayer.Call("initialize");
    }

    public void Disconnect(int playerNum) {
        wearLayer.Call("disconnect", playerNum);
    }
}
