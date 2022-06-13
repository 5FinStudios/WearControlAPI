# WearControlAPI

Welcome to the WearControl API Github.

We have uploaded the entire source code for the Android WearOS library that WearControl Games uses to connect a smartwatch to our Unity game.

## Overview

This Android Library can be compiled into an AAR and imported into either your Android or Unity project. Below is a brief overview of the way the library is structured.

### Android Library
To connect the watch and phone we use the Wear OS Data Layer API. To establish a connection, we follow a simple handshake model. First the phone broadcasts that it wants a watch to control a player by adding the details to the data map. Any linked watches will get the update and display to the user that a connection can be made. When the user clicks on the accept button we broadcast back to the phone a desire to connect, and a handshake is made.

After a pairing is completed to a specific player, we default to sending messages instead of data map updates. Messages resulted in a more consistent delivery of data and didn’t risk other updates overriding data in the map. We pushed as much of the state management to the library to track which player was paired to which node (i.e., device), when a disconnect signal arrived, when we were in a pairing state, and so on. This alleviates the Unity application and watch having to track this data independently and makes the library more generally usable in other projects.

### Unity
Since this all had to be written in Android to access the Data Layer API, an API layer needed to be added to our Unity project to process and use the Android library. We used Unity’s ability to wrap and call Android code directly (e.g., AndroidJavaClass) to create a mirrored version of our library and API in Unity. This allowed us to get updates from the library and send messages back to the connected devices from within our game’s code.

### Wear OS
Writing the watch app was the easiest part to integrate with the library since it is Android all the way down. We created activities for each controller and step of the pairing process, imported our library, and hooked up the listeners to react when the phone requested a pair for a specific game.


## Technical Details

### WearDataLayer

This file is the core of the library API. It provides functions for storing which nodes are wanting to connect, connecting, and connected. It toggles between using the DataMap to broadcast connection requests or availability and the MessageClient when two nodes are connected for better data syncing (and reliability).

### WearEvent

This is a quick-and-dirty serializable event that gets used by the Unity project to better handle different types of updates. A recommended improvement here would be to use a library like Google Proto language to create the event so it's easier to update/compile/serialize across Android and Unity. In the current state any updates to the WearEvent need to be reflected in the Unity's version of the same file.

### WearDataLayer-Unity

This folder contains the files to handle Unity's side of the WearDataLayer events. It's a simple wrapper around the library's APIs and WearEvent, but makes classes that depend on the data layer easier to use. Here is an example if it's usage:

```C#
public void Initialize() {
  var listener = new WearDataListener();
  listener.OnEvent += OnWearEvent;
  WearManager.instance.wearDataLayer.AddListener(listener);

  PairingDialog.instance.ShowFor(this);
  RequestPair();
}

private void OnWearEvent(WearEvent wearEvent) {
  // We queue events to handle them on Update instead of here to make sure we're on the main
  // thread. Otherwise we run into all sorts of funkyness of having to save states to process them
  // later anyways.
  eventQueue.Enqueue(wearEvent);
}

void Update() {
  while (eventQueue.Count > 0) {
    HandleEvent(eventQueue.Dequeue());
  }
}

private void HandleEvent(WearEvent wearEvent) {
  switch(wearEvent.EventType) {
    case WearEvent.EventType.PAIR_RESPONSE:
      ...
    case WearEvent.EventType.PAIR_RESPONSE:
      ...
    default:
      ...
  }
}
```
