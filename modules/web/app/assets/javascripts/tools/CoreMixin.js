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

define(['react','logging'], function (React, logging) {

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


        componentWillUpdate: function (newProps, newState) {
            if (this.onComponentUpdate) this.onComponentUpdate(newProps, newState);
        },

        componentWillMount: function () {
            if (this.subscribeToEvents) {
                var eventslist = this.subscribeToEvents();
                eventslist.forEach(function (el) {
                    addEventListener(el[0], el[1]);
                });
            }
        },

        componentDidMount: function () {
            if (this.onMount) {
                this.onMount();
            }
        },
        componentWillUnmount: function () {
            if (this.subscribeToEvents) {
                var eventslist = this.subscribeToEvents();
                eventslist.forEach(function (el) {
                    removeEventListener(el[0], el[1]);
                });
            }
            if (this.onUnmount) {
                this.onUnmount();
            }
        }
    };

});