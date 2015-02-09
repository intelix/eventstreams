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

EventCommProtoHandshakeCompleted = "EventCommProtoHandshakeCompleted";
EventCommProtoInbound = "EventCommProtoInbound";

EventCommProtoInboundAuthorization = "EventCommProtoInboundAuthorization";

define(['lz', 'logging', 'eventing', 'comm_connectivity'], function (lz, logging, eventing, connectivity) {


    var componentId = "comm/Protocol";

    var EncodingFlagFlat = "f";
    var EncodingFlagCompressed = "z";

    var OutboundMsgTypeHandshake = "H";
    var OutboundMsgTypeOperationAlias = "A";
    var OutboundMsgTypeLocationAlias = "B";
    var OutboundMsgTypeTokenAuth = "T";
    var OutboundMsgTypeCredentialsAuth = "X";

    var InboundMsgTypeLocalAddress = "L";
    var InboundMsgTypeAuthorization = "A";

    var opSplitCode = String.fromCharCode(1);
    var msgSplitCode = String.fromCharCode(2);

    var key2alias = {};
    var alias2key = {};
    var aliasCounter = 1;

    var location2locationAlias = {};
    var locationAlias2location = {};
    var locationAliasCounter = 1;

    var aggregationTimer = false;
    var aggregatedMessage = [];

    var localAddress = false;


    function resetAll() {
        alias2key = {};
        key2alias = {};
        location2locationAlias = {};
        locationAlias2location = {};
        locationAliasCounter = 1;
        aliasCounter = 1;
        aggregatedMessage = [];
        localAddress = false;
    }

    function onConnected() {
        connectivity.send(flat(OutboundMsgTypeHandshake));
    }
    eventing.addEventListener(EventCommWSConnected, onConnected);

    function onDisconnected() {
        resetAll();
    }
    eventing.addEventListener(EventCommWSDisconnected, onDisconnected);

    function flat(msg) {
        return EncodingFlagFlat + msg;
    }
    function zip(msg) {
        return EncodingFlagCompressed + LZString.compressToUTF16(msg);
    }
    
    function encode(msg) {
        if (msg.length > 100) {
            return zip(msg);
        } else {
            return flat(msg);
        }
    }

    function decompress(flag, data) {
        var compressed = flag == EncodingFlagCompressed;
        var payload = compressed ? LZString.decompressFromUTF16(data) : data;
        if (compressed && logging.isDebug()) {
            logging.logDebug(componentId, "Compressed payload, " + data.length + "/" + payload.length);
        }
        return payload;
    }

    function componentsToKey(address, route, topic) {
        return address + opSplitCode + route + opSplitCode + topic;
    }

    function nextMsg(content) {

        var type = content.substring(0, 1);
        var rawPayload = content.substring(1);

        if (type == InboundMsgTypeLocalAddress) {
            localAddress = rawPayload;
            if (logging.isDebug()) {
                logging.logDebug(componentId, "Local address: " + localAddress);
            }
            eventing.raiseEvent(EventCommProtoHandshakeCompleted);
            return;
        }

        if (type == InboundMsgTypeAuthorization) {
            eventing.raiseEvent(EventCommProtoInboundAuthorization, rawPayload);
            return;
        }


        var aliasAndData = rawPayload.split(opSplitCode);

        var path = alias2key[aliasAndData[0]];
        var payload = aliasAndData[1] ? JSON.parse(aliasAndData[1]) : false;

        if (logging.isDebug()) {
            logging.logDebug(componentId, "Alias conversion: " + aliasAndData[0] + " -> " + path);
        }

        var segments = path.split(opSplitCode);
        var address = locationAlias2location[segments[0]] ? locationAlias2location[segments[0]] : segments[0];
        var route = segments[1];
        var topic = segments[2];

        if (logging.isDebug()) {
            logging.logDebug(componentId, 
                "Payload received: " + type + " : " + route + "#" + topic + "@" + address + " : " + JSON.stringify(payload));
        }
        
        var eventPayload = {
            type: type,
            addr: address,
            component: route,
            topic: topic,
            payload: payload
        };
        
        eventing.raiseEvent(EventCommProtoInbound, eventPayload);

    }

    function onDataFrame(e) {
        var flag = e.detail.substring(0, 1);
        var data = e.detail.substring(1);
        var payload = decompress(flag, data);
        var messages = payload.split(msgSplitCode);
        messages.forEach(nextMsg);
    }
    eventing.addEventListener(EventCommWSDataFrame, onDataFrame);

    
    function handshaked() {
        return connectivity.isConnected() && localAddress;
    }

    
    function sendAggregatedMessages() {
        if (handshaked()) {
            var msg = encode(aggregatedMessage.join(msgSplitCode));
            aggregationTimer = false;
            aggregatedMessage = [];
            connectivity.send(msg);
        } else {
            aggregationTimer = setTimeout(sendAggregatedMessages, 1000);
        }
    }
    
    function scheduleSend(type, key, payload) {
        var msg = type + key + opSplitCode + payload;
        if ($.inArray(msg, aggregatedMessage) == -1) {
            aggregatedMessage.push(msg);
        }
        if (!aggregationTimer) {
            aggregationTimer = setTimeout(sendAggregatedMessages, 100);
        }
    }

    function toRealAddress(address) {
        if (address == "local") return localAddress;
        return address;
    }


    function sendToServer(type, address, route, topic, payload) {
        if (handshaked()) {

            var locAlias = false;
            if (!location2locationAlias[address]) {
                locationAliasCounter++;
                locAlias = locationAliasCounter.toString(32);
                location2locationAlias[address] = locAlias;
                locationAlias2location[locAlias] = address;
                scheduleSend(OutboundMsgTypeLocationAlias, locAlias, address);
            } else {
                locAlias = location2locationAlias[address];
            }

            var key = componentsToKey(locAlias, route, topic);
            if (!key2alias[key]) {
                aliasCounter++;

                var alias = aliasCounter.toString(32);

                key2alias[key] = alias;
                alias2key[alias] = key;
                scheduleSend(OutboundMsgTypeOperationAlias, alias, key);
                key = alias;
            } else {
                key = key2alias[key];
            }

            scheduleSend(type, key, (payload ? JSON.stringify(payload) : ""));

            return true;
        } else {
            return false;
        }
    }

    function sendTokenAuth(token) {
        scheduleSend(OutboundMsgTypeTokenAuth, token, "");
    }
    function sendCredentialsAuth(user, passwordHash) {
        scheduleSend(OutboundMsgTypeCredentialsAuth, user, passwordHash);
    }


    return {
        convertAddress: toRealAddress,
        isHandshaked: handshaked,

        sendTokenAuth: sendTokenAuth,
        sendCredentialsAuth: sendCredentialsAuth,
        
        send: sendToServer
    };

});