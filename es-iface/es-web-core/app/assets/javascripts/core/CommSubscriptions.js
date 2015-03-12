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

define(['logging', 'eventing', 'comm_protocol', 'comm_auth'], function (logging, eventing, protocol, auth) {

    var componentId = "comm/Subscriptions";

    var listeners = [];

    var subscriptionMaintenanceTimer = false;

    var globalSubscribers = {};
    var updateCache = {};
    var subscriptionsForHousekeeping = {};

    function resetAll() {
        globalSubscribers = {};
        updateCache = {};
        subscriptionsForHousekeeping = {};
    }
    function onDisconnected(e) {
        resetAll();
        if (e.detail.wasOpen) {
            listeners.forEach(function (next) {
                if (next.onClosed) next.onClosed();
            });
        }

    }
    eventing.addEventListener(EventCommWSDisconnected, onDisconnected);

    function eventKey(address, route, topic) {
        return address + "|" + route + "|" + topic;
    }

    function subscribeToSharedStream(address, route, topic, callback) {
        var key = eventKey(address, route, topic);
        var subscribers = globalSubscribers[key];
        if (!subscribers) {
            subscribers = [];
            if (logging.isDebug()) {
                logging.logDebug(componentId, "Server subscription for: " + route + "#" + topic + "@" + address);
            }
            delete updateCache[key];
            protocol.send("S", address, route, topic, false);
        }
        if ($.inArray(callback, subscribers) < 0) {
            subscribers.push(callback);
            globalSubscribers[key] = subscribers;
            if (logging.isDebug()) {
                logging.logDebug(componentId, "New interest for: " + route + "#" + topic + "@" + address + ", total listeners: " + subscribers.length);
            }
        }
        var lastUpdate = updateCache[key];
        if (lastUpdate) {
            if (logging.isDebug()) {
                logging.logDebug(componentId, "Sending cached update for: " + route + "#" + topic + "@" + address + " -> " + lastUpdate.type + ":" + JSON.stringify(lastUpdate.payload));
            }
            callback(lastUpdate.type, lastUpdate.payload);
        }
    }

    function unsubscribeFromSharedStream(address, route, topic, callback) {
        var key = eventKey(address, route, topic);
        var subscribers = globalSubscribers[key];
        if (!subscribers) {
            return;
        }
        subscribers = subscribers.filter(function (el) {
            return el != callback;
        });

        if (logging.isDebug()) {
            logging.logDebug(componentId, "Listener gone for: " + route + "#" + topic + "@" + address + ", remaining " + subscribers.length);
        }

        if (!subscribers || subscribers.length === 0) {

            if (logging.isDebug()) {
                logging.logDebug(componentId, "Scheduled for removal: " + route + "#" + topic + "@" + address + ", remaining " + subscribers.length);
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
    
    
    function onInboundMessage(e) {
        var data = e.detail;
        var eventId = eventKey(data.addr, data.component, data.topic);
        var messageType = data.type;
        updateCache[eventId] = {type: messageType, payload: data.payload};
        var subscribers = globalSubscribers[eventId];
        if (subscribers && subscribers.length > 0) {
            subscribers.forEach(function (next) {
                next(messageType, data.payload);
            });
        }
    }
    eventing.addEventListener(EventCommProtoInbound, onInboundMessage);


    function onAccessGranted() {
        listeners.forEach(function (next) {
            if (next.onOpen) next.onOpen();
        });
    }
    eventing.addEventListener(EventCommAuthAccessAllowed, onAccessGranted);


    function subscriptionMaintenance() {
        if (logging.isDebug()) {
            logging.logDebug(componentId, "Subscription maintenance...");
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
                            logging.logDebug(componentId, "Unsubscribing from server: " + el.route + "#" + el.topic + "@" + el.address);
                        }
                        protocol.send("U", el.route, el.topic, false);
                        delete globalSubscribers[key];
                        delete updateCache[key];
                    } else {
                        if (logging.isDebug()) {
                            logging.logDebug(componentId, "Subscription " + key + " is live again and no longer queued for removal");
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


    function connectedHandshakedAndAuthenticated() {
        return protocol.isHandshaked() && auth.isAuthenticated();
    }



    return {
        
        // TODO refactor this mess
        
        getHandle: function () {

            var handle = {
                stop: stopFunc,
                subscribe: subscribeFunc,
                unsubscribe: unsubscribeFunc,
                command: function (address, route, topic, data) {
                    return protocol.send("C", protocol.convertAddress(address), route, topic, data);
                },
                connected: connectedHandshakedAndAuthenticated,
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
                var realAddress = protocol.convertAddress(address);
                subscribeToSharedStream(realAddress, route, topic, callback);
            }

            function unsubscribeFunc(address, route, topic, callback) {
                var realAddress = protocol.convertAddress(address);
                unsubscribeFromSharedStream(realAddress, route, topic, callback);
            }

            listeners.push(handle);

            return handle;

        }
    };

});