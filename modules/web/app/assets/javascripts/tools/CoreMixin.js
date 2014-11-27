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

define(['jquery'], function () {

    var evtElement = window;

    var DEBUG = 1;
    var INFO = 2;
    var WARN = 3;
    var ERROR = 4;

    var defaultLogLevel = DEBUG;

    return {

        componentNamePrefix: function() {
            return this.componentName ? this.componentName() + ": " : "";
        },

        isDebug: function () {
            return (this.logLevel && this.logLevel() <= DEBUG) || (!this.logLevel && defaultLogLevel <= DEBUG);
        },

        isInfo: function () {
            return (this.logLevel && this.logLevel() <= INFO) || (!this.logLevel && defaultLogLevel <= INFO);
        },

        logDebug: function (msg) {
            if (this.isDebug()) {
                console.debug(this.componentNamePrefix() + msg);
            }
        },
        logInfo: function (msg) {
            if (this.isInfo()) {
                console.info(this.componentNamePrefix() + msg);
            }
        },
        logWarn: function (msg) {
            console.warn(this.componentNamePrefix() + msg);
        },
        logError: function (msg) {
            console.error(this.componentNamePrefix() + msg);
        },

        addEventListener: function (evt, func) {
            if (this.isDebug()) {
                this.logDebug("Added event listener: " + evt );
            }
            evtElement.addEventListener(evt, func);
        },
        removeEventListener: function (evt, func) {
            if (this.isDebug()) {
                this.logDebug("Removed event listener: " + evt );
            }
            evtElement.removeEventListener(evt, func);
        },
        raiseEvent: function (evt, data) {
            if (this.isDebug()) {
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