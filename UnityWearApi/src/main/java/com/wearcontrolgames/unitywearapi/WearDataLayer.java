package com.wearcontrolgames.unitywearapi;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import com.google.android.gms.tasks.Tasks;
import com.google.android.gms.wearable.CapabilityClient;
import com.google.android.gms.wearable.CapabilityInfo;
import com.google.android.gms.wearable.DataClient;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.MessageClient;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import androidx.annotation.NonNull;

public class WearDataLayer implements DataClient.OnDataChangedListener, MessageClient.OnMessageReceivedListener {
    private static WearDataLayer instance;

    private static final String TAG = "com.google.unitywear.WearDataLayer";

    public static final String NODE_ID_KEY = "com.wearcontrolgames.unitywearapi.node_id";
    public static final String EVENT_KEY = "com.wearcontrolgames.unitywearapi.event";
    public static final String TIMESTAMP_KEY = "com.wearcontrolgames.unitywearapi.timestamp";

    public static final String WEAR_CONTROL_CAPABILITY = "wear_control_support";

    public interface UnityWearListener {
        void onEvent(WearEvent event);
    }

    private Context context;
    private DataClient dataClient;
    private MessageClient messageClient;
    private List<UnityWearListener> listeners;

    private String nodeId;
    // This keeps track of the node that we are paired to.
    private boolean waitingForPair = false;
    private HashMap<Integer, String> pairedMap = new HashMap<>();

    private HashMap<Integer, String> pendingPairMap = new HashMap<>();
    // This id is stored for when we are attempting to respond to a pair.
    private String pendingPairRequestId;

    private Uri queuedPair;

    public WearDataLayer(Context context) {
        this.context = context;
        dataClient = Wearable.getDataClient(context);
        dataClient.addListener(this);

        messageClient = Wearable.getMessageClient(context);
        messageClient.addListener(this);

        listeners = new ArrayList<>();

        // TODO: Add in handling if we can't get the node id for some reason.
        Wearable.getNodeClient(context).getLocalNode().addOnSuccessListener((node) -> {
            nodeId = node.getId();
        });

        CapabilityClient.OnCapabilityChangedListener capabilityListener =
                capabilityInfo -> { checkCapabilityInfo(capabilityInfo); };
        Wearable.getCapabilityClient(context).addListener(
                capabilityListener,
                WEAR_CONTROL_CAPABILITY);
    }


    public static WearDataLayer with(Context context) {
        if (instance == null) {
            instance = new WearDataLayer(context);
        }
        return instance;
    }

    public void initialize() {
        dataClient.getDataItems().addOnSuccessListener((dataItems) -> {
            dataItems.forEach(item -> dataClient.deleteDataItems(item.getUri()));
        });
    }

    public void addListener(UnityWearListener listener) {
        if (listeners.contains(listener)) {
            return;
        }
        listeners.add(listener);
    }

    public void removeListener(UnityWearListener listener) {
        if (listeners.contains(listener)) {
            listeners.remove(listener);
        }
    }

    public void checkAvailableNodes() {
        try {
            CapabilityInfo info = Tasks.await(
                    Wearable.getCapabilityClient(context).getCapability(
                            WEAR_CONTROL_CAPABILITY, CapabilityClient.FILTER_REACHABLE));
            checkCapabilityInfo(info);
        } catch (Exception e) {
            dispatchUpdate(new WearEvent(WearEvent.EventType.NODES_UNAVAILABLE));
        }
    }

    private void checkCapabilityInfo(CapabilityInfo info) {
        boolean oneAvailable = false;
        if (info.getNodes().size() > 0) {
            for (Node node : info.getNodes()) {
                oneAvailable |= !pairedMap.containsValue(node.getId());
            };
            if (oneAvailable) {
                dispatchUpdate(new WearEvent(WearEvent.EventType.NODES_AVAILABLE));
                return;
            } else {
                dispatchUpdate(new WearEvent(WearEvent.EventType.NODES_UNAVAILABLE));
            }
        } else {
            dispatchUpdate(new WearEvent(WearEvent.EventType.NODES_UNAVAILABLE));
        }
    }

    public void requestPair(String targetController, int playerNum) {
        Log.v(TAG, "Requesting pair");
        if (nodeId == null) {
            Log.e(TAG, "No node ID for pair request");
            return;
        }

        if (pairedMap.get(playerNum) != null) {
            disconnect(playerNum);
        }

        waitingForPair = true;
        pendingPairMap.put(playerNum, null);
        queuedPair = sendDataMapUpdate(
                nodeId,
                new WearEvent(WearEvent.EventType.PAIR_REQUEST)
                        .setTargetController(targetController)
                        .setPlayerNum(playerNum),
                "/pair_request");
    }

    /**
     * The pending pair response happens on the watch. We don't need to deal with the pending pair
     * map, but we do need to respond to which player we want to pair to. The current system doesn't
     * expect/need pairs to be synchronous.
     * @param playerNum
     */
    public void respondToPair(int playerNum) {
        Log.v(TAG, "Respond to pair");
        if (nodeId == null || pendingPairRequestId == null || pendingPairRequestId.isEmpty()) {
            Log.e(TAG,
                    String.format(
                            "Trying to pair when we shouldn't be.\n" +
                                    "Node: %s\n" +
                                    "pendingPairRequestId: %s",
                            nodeId,
                            pendingPairRequestId));
            return;
        }

        // We we respond to a pair, we're officially waiting for a response. If we get a response
        // and we're in this state, and the ID matches our pending request ID, we are paired.
        waitingForPair = true;
        sendDataMapUpdate(
                nodeId,
                new WearEvent(WearEvent.EventType.PAIR_RESPONSE)
                    .setPlayerNum(playerNum),
                "/pair_response");
    }

    public void acceptPair(String controllerType, int playerNum) {
        Log.v(TAG, "Accept pair");
        assert(waitingForPair);
        assert(nodeId != null);
        String pendingId = pendingPairMap.get(playerNum);
        if (nodeId == null ||
                pendingId == null ||
                pendingId.isEmpty()) {
            Log.e(TAG,
                    String.format(
                            "Trying to accept pair when we shouldn't be.\n" +
                                    "Node: %s\n" +
                                    "pendingPair: %s",
                            nodeId,
                            pendingId));
            return;
        }

        waitingForPair = false;
        pairedMap.put(playerNum, pendingId);
        pendingPairMap.put(playerNum, null);
        sendDataMapUpdate(
                nodeId,
                new WearEvent(WearEvent.EventType.PAIR_ACCEPTED)
                        .setPlayerNum(playerNum)
                        .setControllerType(controllerType),
                "/pair_accept");
        dataClient.deleteDataItems(queuedPair);
        checkAvailableNodes();
    }

    public void rejectPair(int playerNum) {
        if (pendingPairRequestId != null) {
            sendMessage(pendingPairRequestId,
                    new WearEvent(WearEvent.EventType.PAIR_REJECTED)
                            .setPlayerNum(playerNum),
                    "/pair_reject");
            pendingPairRequestId = null;
        }
    }

    public void pauseGame() {
        sendDataMapUpdate(nodeId, new WearEvent(WearEvent.EventType.PAUSE_GAME), "/resume");
    }

    public void unpauseGame() {
        sendDataMapUpdate(nodeId, new WearEvent(WearEvent.EventType.UNPAUSE_GAME), "/resume");
    }

    public void restartGame() {
        sendDataMapUpdate(nodeId, new WearEvent(WearEvent.EventType.RESTART_GAME), "/restart");
    }

    public void disconnect(int playerNum) {
        pairedMap.put(playerNum, null);
        waitingForPair = false;
        pendingPairRequestId = null;
        pendingPairMap.put(playerNum, null);

        sendDataMapUpdate(
                nodeId,
                new WearEvent(WearEvent.EventType.DISCONNECT)
                        .setPlayerNum(playerNum),
                "/disconnect");

        checkAvailableNodes();
    }

    public void sendUpdate(WearEvent event) {
        sendEvent(event, "/update");
    }

    /**
     * Send an event across the Wear Data Layer. We default to the message client if we are paired,
     * otherwise we'll use the data map. This simplifies the client's API without having to worry
     * about the paired state.
     * @param event
     * @param path
     */
    private void sendEvent(WearEvent event, String path) {
        Log.v(TAG, "sendEvent: " + event.serialize());
        String pairedTo = pairedMap.get(event.playerNum);
        if (pairedTo != null && !pairedTo.isEmpty()) {
            sendMessage(event, path);
        } else {
            sendDataMapUpdate(nodeId, event, path);
        }
    }

    private void sendMessage(WearEvent event, String path) {
        sendMessage(pairedMap.get(event.playerNum), event, path);
    }

    private void sendMessage(String receiver, WearEvent event, String path) {
        Log.v(TAG, "Sending message: " + event.serialize());
        messageClient.sendMessage(receiver, path, event.serialize().getBytes());
    }

    private Uri sendDataMapUpdate(String nodeId, WearEvent event, String path) {
        PutDataMapRequest putDataMapReq = PutDataMapRequest.create(path);
        event.setUri(putDataMapReq.getUri());
        putDataMapReq.getDataMap().putLong(TIMESTAMP_KEY, System.currentTimeMillis());
        putDataMapReq.getDataMap().putString(EVENT_KEY, event.serialize());
        putDataMapReq.getDataMap().putString(NODE_ID_KEY, nodeId);
        PutDataRequest putDataReq = putDataMapReq.asPutDataRequest();
        putDataMapReq.setUrgent();
        Log.v(TAG, "Sending data map update as " + nodeId + ": " + event.serialize());
        dataClient.putDataItem(putDataReq);
        return putDataMapReq.getUri();
    }

    @Override
    public void onDataChanged(@NonNull DataEventBuffer dataEvents) {
        Log.v(TAG, "onDataChanged");
        for (DataEvent event : dataEvents) {
            if (event.getType() == DataEvent.TYPE_CHANGED) {
                // DataItem changed
                DataItem item = event.getDataItem();
                // We don't care about the path that the update game on, only that there's a WearEvent. The
                // WearEvent will have the type of update this is.
                DataMap map = DataMapItem
                        .fromDataItem(item)
                        .getDataMap();
                String rawEvent = map
                        .getString(WearDataLayer.EVENT_KEY);
                String requesterId = map.getString(WearDataLayer.NODE_ID_KEY);

                if (rawEvent == null || rawEvent.isEmpty()) {
                    // Nothing to do.
                    return;
                }
                WearEvent wearEvent = WearEvent.deserialize(rawEvent);
                wearEvent.setUri(item.getUri());
                handleWearEvent(requesterId, wearEvent);
            } else if (event.getType() == DataEvent.TYPE_DELETED) {
                Log.i(TAG, "Change was delete");
                // DataItem deleted
            }
        }
    }

    @Override
    public void onMessageReceived(@NonNull MessageEvent messageEvent) {
        Log.v(TAG,
                String.format(
                        "Message received from %s as %s",
                        messageEvent.getSourceNodeId(),
                        nodeId));
        String message = new String(messageEvent.getData());
        if (message.isEmpty()) {
            Log.e(TAG, "Message was empty");
            return;
        }
        String requesterId = messageEvent.getSourceNodeId();
        WearEvent wearEvent = WearEvent.deserialize(message);
        String pairedTo = pairedMap.get(wearEvent.playerNum);
        // Don't dispatch events to the client if the ID doesn't match the one we're paired to,
        // unless it's a rejection, that we want to send directly to the requesting client.
        if (!requesterId.equals(pairedTo) && wearEvent.eventType != WearEvent.EventType.PAIR_REJECTED) {
            Log.e(TAG, String.format("Message shouldn't be handled.\n" +
                    "eventType: %s\n" +
                    "requesterId: %s\n" +
                    "pairedTo: %s",
                    wearEvent.eventType,
                    requesterId,
                    pairedTo));
            return;
        }

        handleWearEvent(requesterId, wearEvent);
    }

    private void handleWearEvent(String requesterId, WearEvent wearEvent) {
        Log.v(TAG, "Handling event from " + requesterId + " as " + nodeId + ": " + wearEvent.eventType);
        // Ignore any events from ourselves for handling.
        if (requesterId.equals(nodeId)) {
            return;
        }
        switch (wearEvent.eventType) {
            case PAIR_REQUEST:
                handlePairRequest(requesterId, wearEvent);
                break;
            case PAIR_ACCEPTED:
                handlePairAccepted(requesterId, wearEvent);
                break;
            case PAIR_RESPONSE:
                handlePairResponse(requesterId, wearEvent);
                break;
            case PAIR_REJECTED:
                handlePairRejected(requesterId, wearEvent);
                break;
            default:
                // Several updates will just be immediately forwarded to the listeners
                // without the WDL needing to do anything.
                dispatchUpdate(wearEvent);
                break;
        }
    }

    private void handlePairRequest(String requesterId, WearEvent event) {
        Log.v(TAG, "Handling pair request");
        pendingPairRequestId = requesterId;
        dispatchUpdate(event);
    }

    /**
     * Handles when a pair response comes through. If we are in the waiting for pair state, then
     * hold onto the ID until we get a confirmation from the client that a pair is accepted. On
     * pair accept, we will store the pending ID as our official paired node for future messages.
     * @param requesterId The node ID of the client that sent the pair response.
     * @param event
     */
    private void handlePairResponse(String requesterId, WearEvent event) {
        Log.v(TAG, "Handling pair response");
        if (waitingForPair) {
            String pendingPairId = pendingPairMap.get(event.playerNum);
            if (pendingPairId != null && !pendingPairId.isEmpty()) {
                Log.e(TAG, String.format("Rejecting pair.\n" +
                                "requesterId: %s\n" +
                                "pendingPairId: %s",
                        requesterId,
                        pendingPairId));
                // We are already waiting on a pair, so reject this pair.
                sendMessage(requesterId,
                        new WearEvent(WearEvent.EventType.PAIR_REJECTED)
                            .setPlayerNum(event.playerNum),
                        "/pair_reject");
                return;
            }
            pendingPairMap.put(event.playerNum, requesterId);
        }
        dispatchUpdate(event);
    }

    private void handlePairRejected(String requeterId, WearEvent event) {
        waitingForPair = false;
        pendingPairRequestId = null;
        dataClient.deleteDataItems(queuedPair);
        dispatchUpdate(event);
    }

    /**
     * Handles when a pair accept message is received. If we are in the state of waiting for a pair
     * and the ID of the accepted message matches our pending ID, we are paired to that node. The
     * client will determine if they want to show a message or transition to a new screen.
     * @param event
     */
    private void handlePairAccepted(String requesterId, WearEvent event) {
        Log.v(TAG, "Handling pair accepted");
        if (waitingForPair && pendingPairRequestId.equals(requesterId)) {
            pairedMap.put(event.playerNum, pendingPairRequestId);
            pendingPairRequestId = null;
            pendingPairMap.put(event.playerNum, null);
            waitingForPair = false;
            dispatchUpdate(event);
            checkAvailableNodes();
        } else {
            pendingPairRequestId = null;
            dispatchUpdate(new WearEvent(WearEvent.EventType.PAIR_REJECTED)
                    .setPlayerNum(event.playerNum));
        }
    }

    private void handleDisconnect(String requesterId, WearEvent event) {
        String pairedTo = pairedMap.get(event.playerNum);
        if (requesterId.equals(pairedTo)) {
            // We are being disconnected from.
            pairedMap.put(event.playerNum, null);
            dispatchUpdate(event);
            checkAvailableNodes();
        }
    }

    private void dispatchUpdate(WearEvent event) {
        Log.v(TAG, "Dispatching event: " + event.serialize());
        listeners.forEach((listener) -> {
            listener.onEvent(event);
        });
    }
}
