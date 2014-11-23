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

define(['jquery', 'lz'], function (jquery, xxhash, lz) {

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

    var uuid = 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function (c) {
        var r = Math.random() * 16 | 0, v = c == 'x' ? r : (r & 0x3 | 0x8);
        return v.toString(16);
    });

    function attemptConnection() {
        attemptsSoFar++;
        currentState = WebSocket.CONNECTING;

        sock = new WebSocket(endpoint);

        console.debug("Attempting to connect to " + endpoint);

        var timeout = setTimeout(function () {
            if (sock) sock.close();
        }, connectionEstablishTimeout);

        sock.onopen = function (x) {
            currentState = WebSocket.OPEN;
            clearTimeout(timeout);
            console.debug("Websocket open at " + endpoint + " after " + attemptsSoFar + " attempts");
            attemptsSoFar = 0;
            connection = sock;

            sock.send("fX" + uuid);

        };

        sock.onclose = function (x) {
            clearTimeout(timeout);
            sock = null;
            alias2key = {};
            key2alias = {};

            if (currentState == WebSocket.OPEN) {
                listeners.forEach(function (next) {
                    if (next.onClosed) next.onClosed();
                });
            }

            this.localAddress = false;

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
                console.debug("Local address: " + this.localAddress);
                listeners.forEach(function (next) {
                    if (next.onOpen) next.onOpen();
                });
                return;
            }

            var aliasAndData = content.substring(1).split(opSplitCode);

            var path = alias2key[aliasAndData[0]];
            var payload = aliasAndData[1] ? JSON.parse(aliasAndData[1]) : false;

            console.debug("Alias conversion: " + aliasAndData[0] + " -> " + path);

            var segments = path.split(opSplitCode);
            var address = locationAlias2location[segments[0]] ? locationAlias2location[segments[0]] : segments[0];
            var route = segments[1];
            var topic = segments[2];

            console.debug("From Websocket: " + type + " : " + address + " : " + route + " : " + topic + " : " + payload);

            var eventId = address + opSplitCode + route + opSplitCode + topic;

            listeners.forEach(function (next) {
                next.onMessage(eventId, type, payload);
            });
        }

        sock.onmessage = function (e) {
            var flag = e.data.substring(0, 1);
            var data = e.data.substring(1);
            var content = flag == 'z' ? LZString.decompressFromUTF16(data) : data;
            console.debug("From Websocket (" + data.length + "/" + content.length + "): " + content);
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

            var key = locAlias + opSplitCode + route + opSplitCode + topic;
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

            var messageHandlers = {};

            var handle = {
                //uid: Math.random().toString(36).substr(2, 10),
                onMessage: function (eventId, type, payload) {
                    if (messageHandlers[eventId]) messageHandlers[eventId](type, payload);
                },
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
                if (sendToServer("S", realAddress, route, topic, false)) {
                    console.log("Registered interest: {" + route + "}" + topic);
                    messageHandlers[realAddress + opSplitCode + route + opSplitCode + topic] = callback;
                }
            }

            function unsubscribeFunc(address, route, topic, callback) {
                var realAddress = toRealAddress(address);
                if (sendToServer("U", realAddress, route, topic, false)) {
                    console.log("Unregistered interest: {" + route + "}" + topic);
                    messageHandlers[realAddress + opSplitCode + route + opSplitCode + topic] = null;
                }
            }

            listeners.push(handle);

            return handle;

        }
    };


});
