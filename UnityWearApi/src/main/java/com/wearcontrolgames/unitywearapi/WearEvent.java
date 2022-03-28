package com.wearcontrolgames.unitywearapi;

import android.net.Uri;

import org.json.*;

import androidx.annotation.Nullable;

/**
 * This class handles the various types of updates and converting them into a JSON object to be
 * sent to Unity. Unity will have a C# implementation of this same class to convert the various
 * enums and data back to an object to be used.
 */
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
    public final EventType eventType;
    // This field is necessary for C# to try parsing out the enum from this value. This is more
    // scalable for adding new event types versus manually mapping them on Unity's side.
    public final String eventTypeString;

    public int playerNum = -1;

    public Uri uri;

    public enum ControllerType {
        UNKNOWN,
        SLIDER,
        ANALOG,
        DPAD
    }
    public ControllerType controllerType;
    public String controllerTypeString;

    public String targetController;

    // Required fields for a position update event. The Unity side should handle which it cares
    // about.
    public double posX;
    public double posY;

    // Required for gestures.
    // TODO: Create an enum for the gestures we care about.

    public WearEvent(EventType type) {
        this.eventType = type;
        this.eventTypeString = type.name();
    }

    public WearEvent setUri(Uri uri) {
        this.uri = uri;
        return this;
    }

    public WearEvent setTargetController(String t) {
        targetController = t;
        return this;
    }

    public WearEvent setControllerType(String type) {
        this.controllerType = ControllerType.valueOf(type);
        this.controllerTypeString = type;
        return this;
    }

    public WearEvent setPosition(double posX, double posY) {
        this.posX = posX;
        this.posY = posY;
        return this;
    }

    public WearEvent setPlayerNum(int num) {
        this.playerNum = num;
        return this;
    }

    @Nullable
    public static WearEvent deserialize(String json) {
        try {
            JSONObject obj = new JSONObject(json);
            EventType t = EventType.valueOf(obj.getString("eventType"));
            WearEvent e = new WearEvent(t);
            if (obj.has("uri")) {
                e.setUri(Uri.parse(obj.getString("uri")));
            }
            if (obj.has("playerNum")) {
                e.setPlayerNum(obj.getInt("playerNum"));
            }
            switch(t) {
                case POSITION_UPDATE:
                    e.setPosition(
                            obj.getDouble("posX"), obj.getDouble("posY"));
                    break;
                case PAIR_ACCEPTED:
                    e.controllerType =
                            ControllerType.valueOf(obj.getString("controllerType"));
                    e.controllerTypeString = obj.getString("controllerType");
                    break;
                case PAIR_REQUEST:
                    e.targetController = obj.getString("targetController");
                    break;
            }
            return e;
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return null;
    }

    public String serialize() {
        JSONObject obj = new JSONObject();
        try {
            obj.put("eventType", eventType.name());
            if (uri != null) {
                obj.put("uri", uri.toString());
            }
            if (playerNum >= 0) {
                obj.put("playerNum", playerNum);
            }
            switch(eventType) {
                case POSITION_UPDATE:
                    obj.put("posX", posX);
                    obj.put("posY", posY);
                    break;
                case PAIR_ACCEPTED:
                    obj.put("controllerType", controllerType.name());
                    break;
                case PAIR_REQUEST:
                    obj.put("targetController", targetController);
                    break;
            }
            return obj.toString();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return "";
    }
}
