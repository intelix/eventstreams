/*
 * Copyright 2014-15 Intelix Pty Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

EventCommWSConnecting = "EventCommWSConnecting";
EventCommWSConnected = "EventCommWSConnected";
EventCommWSDisconnected = "EventCommWSDisconnected";
EventCommWSDataFrame = "EventCommWSDataFrame";

define(['logging', 'eventing'], function (logging, eventing) {


    var componentId = "comm/Connectivity";

    var endpoint = "ws://" + window.location.host + "/socket";

    var reconnectInterval = 3000;
    var connectionEstablishTimeout = 5000;

    var currentState = WebSocket.CLOSED;

    var attemptsSoFar = 0;
    var forcedClose = false;

    var sock;

    function connected() {
        return currentState == WebSocket.OPEN;
    }

    function attemptConnection() {
        eventing.raiseEvent(EventCommWSConnecting);
        attemptsSoFar++;
        currentState = WebSocket.CONNECTING;

        sock = new WebSocket(endpoint);

        if (logging.isInfo()) {
            logging.logInfo(componentId, "Connection attempt to " + endpoint);
        }

        var timeout = setTimeout(function () {
            if (sock) sock.close();
        }, connectionEstablishTimeout);

        sock.onopen = function (x) {
            clearTimeout(timeout);
            currentState = WebSocket.OPEN;
            attemptsSoFar = 0;
            connection = sock;
            eventing.raiseEvent(EventCommWSConnected);
            if (logging.isInfo()) {
                logging.logInfo(componentId, "WebSocket connected to " + endpoint + " after " + attemptsSoFar + " attempt(s)");
            }
        };

        sock.onclose = function (x) {
            clearTimeout(timeout);
            sock = null;
            eventing.raiseEvent(EventCommWSDisconnected, { wasOpen: (currentState == WebSocket.OPEN )} );
            currentState = WebSocket.CLOSED;
            if (!forcedClose) {
                setTimeout(function () {
                    attemptConnection();
                }, reconnectInterval);
            }
        };

        sock.onmessage = function (e) {
            eventing.raiseEvent(EventCommWSDataFrame, e.data);
        };
    }

    attemptConnection();


    return {
        send: function (msg) {
            if (connected()) {
                sock.send(msg);
            } else {
                if (logging.isDebug()) {
                    logging.logDebug(componentId, "Connection is not active, message dropped: " + msg);

                }

            }

        },

        isConnected: connected

    };

});