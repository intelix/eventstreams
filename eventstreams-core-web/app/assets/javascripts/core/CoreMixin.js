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

define(['react','logging','wsclient'], function (React, logging, client) {

    var evtElement = window;
    window.onscroll = function (event) {
        evtElement.dispatchEvent(new CustomEvent("onscroll"));
    };


    return {

        componentNamePrefix: function() {
            return this.componentName ? this.componentName() : "";
        },

        cx: function(v) {
            var cx = React.addons.classSet;
            return cx(v);
        },

        isDebug: function() { return logging.isDebug(); },
        isInfo: function() { return logging.isInfo(); },
        isWarn: function() { return logging.isWarn(); },

        logDebug: function (msg) {
            logging.logDebug(this.componentNamePrefix(), msg);
        },
        logInfo: function (msg) {
            logging.logInfo(this.componentNamePrefix(), msg);
        },
        logWarn: function (msg) {
            logging.logWarn(this.componentNamePrefix(), msg);
        },
        logError: function (msg) {
            logging.logError(this.componentNamePrefix(), msg);
        },

        addEventListener: function (evt, func) {
            if (logging.isDebug()) {
                this.logDebug("Added event listener: " + evt );
            }
            evtElement.addEventListener(evt, func);
        },
        removeEventListener: function (evt, func) {
            if (logging.isDebug()) {
                this.logDebug("Removed event listener: " + evt );
            }
            evtElement.removeEventListener(evt, func);
        },
        raiseEvent: function (evt, data) {
            if (logging.isDebug()) {
                this.logDebug("Dispatched event: " + evt +" with data: " + JSON.stringify(data));
            }
            evtElement.dispatchEvent(new CustomEvent(evt, {detail: data}));
        },


        subId2String: function (id) {
            return id ? id.address + "/" + id.route + "[" + id.topic + "]" : "Nil";
        },


        unsubscribeFor: function (config) {
            var self = this;
            self.handle.unsubscribe(config.address, config.route, config.topic, self.subscriptionCallback);
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
                    if (self.componentAvailable) self.setState(partialStateUpdate);
                    if (onStateChange) onStateChange(flag);
                }
            }

            function updateData(data) {
                if (dataKey) {
                    var partialStateUpdate = {};
                    partialStateUpdate[dataKey] = data;
                    if (self.componentAvailable) self.setState(partialStateUpdate);
                }
                if (onData) onData(data);
            }

            self.subscriptionCallback = function (type, data) {
                if (self.isDebug()) {
                    self.logDebug("Data IN: " + type + " for " + subIdAsStr);
                }

                if (type == "D") {
                    updateStaleFlag(true);
                } else {
                    updateStaleFlag(false);
                    updateData(data);
                }

            };

            self.handle.subscribe(config.address, config.route, config.topic, self.subscriptionCallback);
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
                    if (self.componentAvailable) self.setState({connected: true});
                    if (self.onConnected) self.onConnected();
                }
                if (self.state && (self.state.visibility === true || self.state.visibility === undefined)) {
                    if (self.isDebug()) {
                        self.logDebug("Received ws connected event, re-subscribing");
                    }
                    subscribe();
                }
            }

            function wsClosedHandler() {
                if (self.isDebug()) {
                    self.logDebug("Received ws disconnected event");
                }
                if (self.state && self.state.connected) {
                    if (self.componentAvailable) self.setState({connected: false});
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

        closeAllSubscriptions: function () {
            var self = this;
            if (self.isDebug()) {
                self.logDebug("Closing subscriptions on request...");
            }
            var configs = self.subscriptionConfig ? self.subscriptionConfig(self.props) : [];
            configs.forEach(this.unsubscribeFor);
        },
        reopenAllSubscriptions: function () {
            var self = this;
            if (self.isDebug()) {
                self.logDebug("Reopening subscriptions on request...");
            }
            var configs = self.subscriptionConfig ? self.subscriptionConfig(self.props) : [];
            configs.forEach(this.subscribeFor);
        },



        visibilityMonitorEnabled: function() {
            return this.refs.monitorVisibility;
        },

        checkVisibility: function () {
            var self = this;
            var element = self.refs.monitorVisibility;
            if (this.visibilityMonitorEnabled()) {
                var dom = element.getDOMNode();
                var lastState = self.state.visibility;
                if (lastState === undefined) lastState = true;
                var currentlyVisible = $(dom).visible(true);
                if (lastState != currentlyVisible) {
                    this.setState({visibility: currentlyVisible});
                    if (currentlyVisible) {
                        self.reopenAllSubscriptions();
                    } else {
                        self.closeAllSubscriptions();
                    }
                }
            }
        },


        componentDidUpdate: function() {
            var self = this;
            self.checkVisibility();
        },

        componentWillMount: function () {
            var self = this;
            if (self.subscribeToEvents) {
                var eventslist = self.subscribeToEvents();
                eventslist.forEach(function (el) {
                    addEventListener(el[0], el[1]);
                });
            }
        },

        componentDidMount: function () {
            var self = this;
            self.componentAvailable = true;
            self.startListenerWithParams(self.props);

            if (self.visibilityMonitorEnabled()) {
                self.addEventListener("resize", self.checkVisibility);
                self.addEventListener("onscroll", self.checkVisibility);
                self.checkVisibility();
            }


        },


        componentWillUnmount: function () {
            var self = this;
            self.componentAvailable = false;

            if (self.handle) {
                var configs = self.subscriptionConfig ? self.subscriptionConfig(self.props) : [];

                if (configs && configs.constructor == Array && configs.length > 0) {
                    configs.forEach(self.unsubscribeFor);
                }

                self.handle.stop();
                self.handle = null;
            }
            if (self.subscribeToEvents) {
                var eventslist = self.subscribeToEvents();
                eventslist.forEach(function (el) {
                    removeEventListener(el[0], el[1]);
                });
            }

            if (self.visibilityMonitorEnabled()) {
                self.removeEventListener("resize", self.checkVisibility);
                self.removeEventListener("onscroll", self.checkVisibility);
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