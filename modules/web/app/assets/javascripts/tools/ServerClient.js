/*
 * Copyright 2014 Intelix Pty Ltd
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

define(['jquery', 'lz', 'logging'], function (jquery, lz, logging) {

    var endpoint = "ws://localhost:9000/socket";

    var opSplitCode = String.fromCharCode(1);
    var msgSplitCode = String.fromCharCode(2);

    var reconnectInterval = 3000;
    var connectionEstablishTimeout = 5000;

    var currentState = WebSocket.CLOSED;

    var attemptsSoFar = 0;
    var forcedClose = false;

    var listeners = [];

    var sock;


    var key2alias = {};
    var alias2key = {};
    var aliasCounter = 1;

    var localAddress = false;

    var location2locationAlias = {};
    var locationAlias2location = {};
    var locationAliasCounter = 1;

    var aggregationTimer = false;
    var aggregatedMessage = [];

    var subscriptionMaintenanceTimer = false;

    var globalSubscribers = {};
    var updateCache = {};
    var subscriptionsForHousekeeping = {};


    function resetAll() {
        alias2key = {};
        key2alias = {};
        location2locationAlias = {};
        locationAlias2location = {};
        locationAliasCounter = 1;
        aliasCounter = 1;
        localAddress = false;
        aggregatedMessage = [];
        globalSubscribers = {};
        updateCache = {};
        subscriptionsForHousekeeping = {};
    }

    var uuid = 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function (c) {
        var r = Math.random() * 16 | 0, v = c == 'x' ? r : (r & 0x3 | 0x8);
        return v.toString(16);
    });

    function attemptConnection() {
        attemptsSoFar++;
        currentState = WebSocket.CONNECTING;

        sock = new WebSocket(endpoint);

        if (logging.isInfo()) {
            logging.logInfo("ws", "Attempting to connect to " + endpoint);
        }

        var timeout = setTimeout(function () {
            if (sock) sock.close();
        }, connectionEstablishTimeout);

        sock.onopen = function (x) {
            currentState = WebSocket.OPEN;
            clearTimeout(timeout);
            if (logging.isInfo()) {
                logging.logInfo("ws", "Websocket open at " + endpoint + " after " + attemptsSoFar + " attempts");
            }
            attemptsSoFar = 0;
            connection = sock;

            sock.send("fX" + uuid);

        };

        sock.onclose = function (x) {
            clearTimeout(timeout);
            sock = null;
            resetAll();


            if (currentState == WebSocket.OPEN) {
                listeners.forEach(function (next) {
                    if (next.onClosed) next.onClosed();
                });
            }


            currentState = WebSocket.CLOSED;

            if (!forcedClose) {
                setTimeout(function () {
                    attemptConnection();
                }, reconnectInterval);
            }
        };

        function nextMsg(content) {

            var type = content.substring(0, 1);

            if (type == 'L') {
                this.localAddress = content.substring(1);
                if (logging.isDebug()) {
                    logging.logDebug("ws", "Local address: " + this.localAddress);
                }

                listeners.forEach(function (next) {
                    if (next.onOpen) next.onOpen();
                });
                return;
            }

            var aliasAndData = content.substring(1).split(opSplitCode);

            var path = alias2key[aliasAndData[0]];
            var payload = aliasAndData[1] ? JSON.parse(aliasAndData[1]) : false;

            if (logging.isDebug()) {
                logging.logDebug("ws", "Alias conversion: " + aliasAndData[0] + " -> " + path);
            }

            var segments = path.split(opSplitCode);
            var address = locationAlias2location[segments[0]] ? locationAlias2location[segments[0]] : segments[0];
            var route = segments[1];
            var topic = segments[2];

            if (logging.isDebug()) {
                logging.logDebug("ws", "Payload received: " + type + " : " + address + " : " + route + " : " + topic + " : " + JSON.stringify(payload));
            }


            var eventId = componentsToKey(address, route, topic);

            updateCache[eventId] = {type: type, payload: payload};
            var subscribers = globalSubscribers[eventId];
            if (subscribers && subscribers.length > 0) {
                subscribers.forEach(function (next) {
                    next(type, payload);
                });
            }

        }

        sock.onmessage = function (e) {
            var flag = e.data.substring(0, 1);
            var data = e.data.substring(1);
            var compressed = flag == 'z';
            var content = compressed ? LZString.decompressFromUTF16(data) : data;
            if (compressed && logging.isDebug()) {
                logging.logDebug("ws", "Compressed payload, " + data.length + "/" + content.length);
            }
            var messages = content.split(msgSplitCode);
            messages.forEach(nextMsg);
        };

    }

    function connected() {
        return currentState == WebSocket.OPEN && this.localAddress;
    }

    function getUUID() {
        return uuid;
    }

    function scheduleSend(msg) {
        if ($.inArray(msg, aggregatedMessage) == -1) {
            aggregatedMessage.push(msg);
        }
    }

    function toRealAddress(address) {
        if (address == "local" || address == "@") return this.localAddress;
        return address;
    }


    function componentsToKey(address, route, topic) {
        return address + opSplitCode + route + opSplitCode + topic;
    }

    function subscribeToSharedStream(address, route, topic, callback) {
        var key = componentsToKey(address, route, topic);
        var subscribers = globalSubscribers[key];
        if (!subscribers) {
            subscribers = [];
            if (logging.isDebug()) {
                logging.logDebug("ws", "Server subscription for: {" + route + "}" + topic + "@" + address);
            }
            delete updateCache[key];
            sendToServer("S", address, route, topic, false);
        }
        if ($.inArray(callback, subscribers) < 0) {
            subscribers.push(callback);
            globalSubscribers[key] = subscribers;
            if (logging.isDebug()) {
                logging.logDebug("ws", "New interest for: {" + route + "}" + topic + "@" + address + ", total listeners: " + subscribers.length);
            }
        }
        var lastUpdate = updateCache[key];
        if (lastUpdate) {
            if (logging.isDebug()) {
                logging.logDebug("ws", "Sending cached update for: {" + route + "}" + topic + "@" + address + " -> " + lastUpdate.type + ":" + JSON.stringify(lastUpdate.payload));
            }
            callback(lastUpdate.type, lastUpdate.payload);
        }
    }

    function unsubscribeFromSharedStream(address, route, topic, callback) {
        var key = componentsToKey(address, route, topic);
        var subscribers = globalSubscribers[key];
        if (!subscribers) {
            return;
        }
        subscribers = subscribers.filter(function (el) {
            return el != callback;
        });

        if (logging.isDebug()) {
            logging.logDebug("ws", "Listener gone for: {" + route + "}" + topic + "@" + address + ", remaining " + subscribers.length);
        }

        if (!subscribers || subscribers.length === 0) {

            if (logging.isDebug()) {
                logging.logDebug("ws", "Scheduled for removal: {" + route + "}" + topic + "@" + address + ", remaining " + subscribers.length);
            }

            subscriptionsForHousekeeping[key] = {
                ts: Date.now(),
                address: address,
                route: route,
                topic: topic
            };
            if (!subscriptionMaintenanceTimer) {
                subscriptionMaintenanceTimer = setTimeout(subscriptionMaintenance, 30 * 1000);
            }
        }
        globalSubscribers[key] = subscribers;
    }

    function subscriptionMaintenance() {
        if (logging.isDebug()) {
            logging.logDebug("ws", "Subscription maintenance...");
        }
        var reschedule = false;
        subscriptionMaintenanceTimer = false;
        if (subscriptionsForHousekeeping) {

            var threshold = Date.now() - 15 * 1000;
            var remainder = {};
            for (var key in subscriptionsForHousekeeping) {
                var el = subscriptionsForHousekeeping[key];
                var unsubTime = el.ts;
                if (unsubTime < threshold) {
                    var subscribers = globalSubscribers[key];
                    if (!subscribers || subscribers.length === 0) {
                        if (logging.isDebug()) {
                            logging.logDebug("ws", "Unsubscribing from server: {" + el.route + "}" + el.topic + "@" + el.address);
                        }
                        sendToServer("U", el.route, el.topic, false);
                        delete globalSubscribers[key];
                        delete updateCache[key];
                    } else {
                        if (logging.isDebug()) {
                            logging.logDebug("ws", "Subscription " + key + " is live again and no longer queued for removal");
                        }
                    }


                } else {
                    reschedule = true;
                    remainder[key] = el;
                }
            }
            subscriptionsForHousekeeping = remainder;
        }
        if (reschedule) {
            subscriptionMaintenanceTimer = setTimeout(subscriptionMaintenance, 30 * 1000);
        }
    }

    function sendToServer(type, address, route, topic, payload) {
        if (connected()) {

            var locAlias = false;
            if (!location2locationAlias[address]) {
                locationAliasCounter++;
                locAlias = locationAliasCounter.toString(32);
                location2locationAlias[address] = locAlias;
                locationAlias2location[locAlias] = address;
                scheduleSend('B' + locAlias + opSplitCode + address);
            } else {
                locAlias = location2locationAlias[address];
            }

            var key = componentsToKey(locAlias, route, topic);
            if (!key2alias[key]) {
                aliasCounter++;

                var alias = aliasCounter.toString(32);

                key2alias[key] = alias;
                alias2key[alias] = key;
                scheduleSend('A' + alias + opSplitCode + key);
                key = alias;
            } else {
                key = key2alias[key];
            }

            scheduleSend(type + key + opSplitCode + (payload ? JSON.stringify(payload) : ""));

            if (!aggregationTimer) {
                aggregationTimer = setTimeout(function () {
                    var msg = aggregatedMessage.join(msgSplitCode);
                    aggregationTimer = false;
                    aggregatedMessage = [];
                    if (msg.length > 100) {
                        msg = "z" + LZString.compressToUTF16(msg);
                    } else {
                        msg = "f" + msg;
                    }
                    if (connected()) sock.send(msg);
                }, 100);
            }
            return true;
        } else {
            return false;
        }
    }

    attemptConnection();

    return {
        getHandle: function () {

            var handle = {
                uuid: getUUID,
                stop: stopFunc,
                subscribe: subscribeFunc,
                unsubscribe: unsubscribeFunc,
                command: function (address, route, topic, data) {
                    return sendToServer("C", toRealAddress(address), route, topic, data);
                },
                connected: connected,
                addWsOpenEventListener: function (callback) {
                    this.onOpen = callback;
                },
                addWsClosedEventListener: function (callback) {
                    this.onClosed = callback;
                }
            };

            function stopFunc() {
                listeners = listeners.filter(function (next) {
                    return next != handle;
                });
            }

            function subscribeFunc(address, route, topic, callback) {
                var realAddress = toRealAddress(address);
                subscribeToSharedStream(realAddress, route, topic, callback);
            }

            function unsubscribeFunc(address, route, topic, callback) {
                var realAddress = toRealAddress(address);
                unsubscribeFromSharedStream(realAddress, route, topic, callback);
            }

            listeners.push(handle);

            return handle;

        }
    };


});
