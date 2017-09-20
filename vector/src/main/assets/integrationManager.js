var android_scalar_events = {};

var sendObjectMessageToRiotAndroid = function(parameters) {
    Android.onScalarEvent(JSON.stringify(parameters));
};

var onScalarMessageToRiotAndroid = function(event) {

    console.log("onScalarMessageToRiotAndroid " + event.data._id);

    if (android_scalar_events[event.data._id]) {
        console.log("onScalarMessageToRiotAndroid : already managed");
        return;
    }

    if (!event.origin) {
        event.origin = event.originalEvent.origin;
    }

    android_scalar_events[event.data._id] = event;

    console.log("onScalarMessageToRiotAndroid : manage " + event.data);
    sendObjectMessageToRiotAndroid({'event.data': event.data,});
};

var sendResponseFromRiotAndroid = function(eventId, res) {
    var event = android_scalar_events[eventId];

    console.log("sendResponseFromRiotAndroid to " + event.data.action + " for "+ eventId + ": " + JSON.stringify(res));

    var data = JSON.parse(JSON.stringify(event.data));

    data.response = res;

     console.log("sendResponseFromRiotAndroid  ---> " + data);

    event.source.postMessage(data, event.origin);
    android_scalar_events[eventId] = true;

      console.log("sendResponseFromRiotAndroid to done");
};

window.addEventListener('message', onScalarMessageToRiotAndroid, false);
