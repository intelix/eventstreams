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

define(['react', 'core_mixin'], function (React, core_mixin) {

    // use this.sendCommand(subject, data) to talk to server
    if (!Notification) {
        // TODO proper alert
        alert('Notifications are supported in modern versions of Chrome, Firefox, Opera and Firefox.');
        return;
    }

    if (Notification.permission !== "granted")
        Notification.requestPermission();

    return React.createClass({

        mixins: [core_mixin],

        componentName: function () {
            return "plugin/desktopnotifications/config/Subscriber/" + this.props.ckey;
        },

        subscriptionConfig: function (props) {
            var self = this;
            if (props.subscribed)
                return [
                    {address: props.addr, route: props.ckey, topic: 'signal', onData: self.onSignal},
                    {address: props.addr, route: props.ckey, topic: 'info', dataKey: 'info'}
                ]
            else
                return [{address: props.addr, route: props.ckey, topic: 'info', dataKey: 'info'}];
        },

        getInitialState: function () {
            return {connected: false, info: false}
        },

        onSignal: function(data) {
            var self = this;

            if (Notification) {
                if (Notification.permission !== "granted")
                    Notification.requestPermission();
                console.log("!>>> ICON: " + data.icon);

                var meta = {
                    icon: data.icon,
                    body: data.body
                };

                if (self.state.info && self.state.info.conflate && data.conflationKey) {
                    meta.tag = data.conflationKey;
                }

                var notification = new Notification(data.title, meta);

                if (self.state.info && self.state.info.autoClose) {
                    notification.addEventListener("show", function () {
                        var closeFunc = function () {
                            if (notification) {
                                if (notification.close) {
                                    notification.close();
                                }
                                else if (notification.cancel) {
                                    notification.cancel();
                                } else if (window.external && window.external.msIsSiteMode()) {
                                    if (notification.ieVerification === ieVerification) {
                                        window.external.msSiteModeClearIconOverlay();
                                    }
                                }
                            }
                        };
                        setTimeout(function () {
                            closeFunc();
                        }, self.state.info.autoClose * 1000);
                    });
                }
            }
        },



        render: function () {
            var self = this;
            return <span />;
        }
    });

});