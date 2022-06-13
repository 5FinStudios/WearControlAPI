using System;
using System.Collections;
using System.Collections.Generic;
using UnityEngine;

/**
 * This class handles the various types of updates and converting them into a JSON object to be
 * sent to Unity. Unity will have a C# implementation of this same class to convert the various
 * enums and data back to an object to be used.
 */
 [Serializable]
public class WearEvent {
    public enum EventType {
        UNKNOWN,
        PAIR_REQUEST,
        PAIR_RESPONSE,
        PAIR_ACCEPTED,
        PAIR_REJECTED,
        DISCONNECT,
        POSITION_UPDATE,
        GESTURE,
        PAUSE_GAME,
        UNPAUSE_GAME,
        RESTART_GAME,
        NODES_AVAILABLE,
        NODES_UNAVAILABLE
    }
    public EventType eventType;

    public enum ControllerType {
        UNKNOWN,
        SLIDER,
        ANALOG,
        DPAD
    }
    public ControllerType controllerType;

    public int playerNum;
    public string targetController;

    // Required fields for a position update event. The Unity side should handle which it cares
    // about.
    public float posX;
    public float posY;

    // Required for gestures.
    // TODO: Create an enum for the gestures we care about.

    public WearEvent(AndroidJavaObject obj) {
        Enum.TryParse<EventType>(obj.Get<string>("eventTypeString"), true, out eventType);
        targetController = obj.Get<string>("targetController");
        playerNum = obj.Get<int>("playerNum");
        Enum.TryParse<ControllerType>(obj.Get<string>("controllerTypeString"), true, out controllerType);
        posX = (float) obj.Get<double>("posX");
        posY = (float) obj.Get<double>("posY");
    }

    public WearEvent(EventType type) {
        this.eventType = type;
    }

    public WearEvent setPlayerNum(int playerNum) {
        this.playerNum = playerNum;
        return this;
    }

    public WearEvent setTargetController(string targetController) {
        this.targetController = targetController;
        return this;
    }

    public WearEvent setControllerType(ControllerType controllerType) {
        this.controllerType = controllerType;
        return this;
    }

    public WearEvent setPosition(double posX, double posY) {
        this.posX = (float) posX;
        this.posY = (float) posY;
        return this;
    }

    public static WearEvent deserialize(string json) {
        return JsonUtility.FromJson<WearEvent>(json);
    }

    public string serialize() {
        return JsonUtility.ToJson(this);
    }
}
