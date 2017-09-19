"use strict";

window.riotAndroid = {};
window.riotAndroid.events = {};


// debug tools
function showToast(toast) {
     Android.showToast(toast);
}

function sendObjectMessageToAndroid(parameters) {
    var iframe = document.createElement('iframe');
    iframe.setAttribute('src', 'js:' + JSON.stringify(parameters));

    document.documentElement.appendChild(iframe);
    iframe.parentNode.removeChild(iframe);
    iframe = null;
};

// Listen to messages posted by Modular
function onMessage(event) {
	
	showToast("hello")

    // Do not SPAM ObjC with event already managed
    if (riotAndroid.events[event.data._id]) {
        return;
    }

    if (!event.origin) { // stupid chrome
        event.origin = event.originalEvent.origin;
    }

    // Keep this event for future usage
    riotAndroid.events[event.data._id] = event;

    sendObjectMessageToAndroid({'event.data': event.data,});
};


window.addEventListener('message', onMessage, false);


// android -> Modular JS bridge
function sendResponse(eventId, res) {

    // Retrieve the correspong JS event
    var event = riotAndroid.events[eventId];

    console.log("sendResponse to " + event.data.action + " for "+ eventId + ": " + JSON.stringify(res));

    var data = JSON.parse(JSON.stringify(event.data));
    data.response = res;
    event.source.postMessage(data, event.origin);

    // Mark this event as handled
    riotAndroid.events[eventId] = true;
}

