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

define(['wsclient'], function (client) {

    return {

        subId2String: function (id) {
            return id ? id.address + "/" + id.route + "[" + id.topic + "]" : "Nil";
        },

        componentDidMount: function () {
            this.startListenerWithParams(this.props);
        },

        unsubscribeFor: function (config) {
            var self = this;
            self.handle.unsubscribe(config.address, config.route, config.topic);
            if (self.isDebug()) {
                self.logDebug("Closed subscription for " + self.subId2String(config));
            }
        },
        subscribeFor: function (config) {
            var self = this;

            var onStateChange = config.onStateChange;
            var onData = config.onData;
            var dataKey = config.dataKey;
            var dataStateKey = dataKey ? dataKey + "_stale" : false;

            var subIdAsStr = self.subId2String(config);
            var internalStateTrackingKey = subIdAsStr + "_state";

            function updateStaleFlag(flag) {
                if (self.state[internalStateTrackingKey] != flag) {
                    var partialStateUpdate = {};
                    if (dataStateKey) partialStateUpdate[dataStateKey] = flag;
                    partialStateUpdate[internalStateTrackingKey] = flag;
                    self.setState(partialStateUpdate);
                    if (onStateChange) onStateChange(flag);
                }
            }

            function updateData(data) {
                if (dataKey) {
                    var partialStateUpdate = {};
                    partialStateUpdate[dataKey] = data;
                    self.setState(partialStateUpdate);
                }
                if (onData) onData(data);
            }

            self.handle.subscribe(config.address, config.route, config.topic, function (type, data) {
                if (self.isDebug()) {
                    self.logDebug("Data IN: " + type + " for " + subIdAsStr);
                }

                if (type == "D") {
                    updateStaleFlag(true);
                } else {
                    updateStaleFlag(false);
                    updateData(data);
                }

            });
            if (self.isDebug()) {
                self.logDebug("Opened subscription for " + subIdAsStr);
            }

        },

        startListenerWithParams: function (props) {
            var self = this;
            var configs = self.subscriptionConfig ? self.subscriptionConfig(props) : [];
            this.handle = client.getHandle();
            this.sendCommand = this.handle.command;

            function subscribe() {
                if (configs && configs.constructor == Array && configs.length > 0) {
                    configs.forEach(self.subscribeFor);
                } else {
                    if (self.isDebug()) {
                        self.logDebug("Initialised without subscription");
                    }
                }
            }

            function wsOpenHandler() {
                if (!self.state || !self.state.connected) {
                    self.setState({connected: true});
                    if (self.onConnected) self.onConnected();
                }
                if (self.isDebug()) {
                    self.logDebug("Received ws connected event, re-subscribing");
                }
                subscribe();
            }

            function wsClosedHandler() {
                if (self.isDebug()) {
                    self.logDebug("Received ws disconnected event");
                }
                if (self.state && self.state.connected) {
                    self.setState({connected: false});
                    if (self.onDisconnected) self.onDisconnected();
                }
            }

            this.handle.addWsOpenEventListener(wsOpenHandler);
            this.handle.addWsClosedEventListener(wsClosedHandler);


            if (this.handle.connected) {
                wsOpenHandler();
            } else {
                wsClosedHandler();
            }

        },

        componentWillUnmount: function () {
            var self = this;

            if (self.handle) {
                var configs = self.subscriptionConfig ? self.subscriptionConfig(self.props) : [];

                if (configs && configs.constructor == Array && configs.length > 0) {
                    configs.forEach(self.unsubscribeFor);
                }

                self.handle.stop();
                self.handle = null;
            }
        },

        componentWillReceiveProps: function (nextProps) {
            var self = this;

            function different(id1, id2) {
                return id1.address != id2.address ||
                    id1.route != id2.route ||
                    id1.topic != id2.topic;
            }

            if (self.handle) {
                var configs = self.subscriptionConfig ? self.subscriptionConfig(self.props) : [];
                var newConfigs = self.subscriptionConfig ? self.subscriptionConfig(nextProps) : [];

                var toClose = configs.filter(function (config) {
                    return !newConfigs.some(function (newConfig) {
                            return !different(config, newConfig);
                        });
                });
                var toOpen = newConfigs.filter(function (config) {
                    return !configs.some(function (newConfig) {
                            return !different(config, newConfig);
                        });
                });

                toClose.forEach(self.unsubscribeFor);
                toOpen.forEach(self.subscribeFor);

            }
        }
    };

});