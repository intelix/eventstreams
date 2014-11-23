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

define(['wsclient'], function(client) {

    return {
        startListener: function () {
            var self = this;

            this.handle = client.getHandle();

            console.debug("!>>>> created");

            function wsOpenHandler() {
                if (!self.state.connected) {
                    if (self.onConnected) self.onConnected();
                    self.setState({connected: true});
                    console.debug("onConnected()" );
                }
            }

            function wsClosedHandler() {
                if (self.state.connected) {
                    self.setState({connected: false});
                    if (self.onDisconnected) self.onDisconnected();
                    console.debug("onDisconnected()");
                }
            }

            this.handle.addWsOpenEventListener(wsOpenHandler);
            this.handle.addWsClosedEventListener(wsClosedHandler);

            this.sendCommand = this.handle.command;

            if (this.handle.connected) {
                wsOpenHandler();
            } else {
                wsClosedHandler();
            }

        },

        stopListener: function () {
            if (this.handle) {
                this.handle.stop();
            }
        }
    };

});