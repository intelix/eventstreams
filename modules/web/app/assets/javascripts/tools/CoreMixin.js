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

define(['jquery'], function() {

    var evtElement = window;

    return {

        addEventListener: function(evt, func) {
            console.debug("Added event listener: " + evt +" on " + evtElement);
            evtElement.addEventListener(evt, func);
        },
        removeEventListener: function(evt, func) {
            console.debug("Removed event listener: " + evt);
            evtElement.removeEventListener(evt, func);
        },
        raiseEvent: function(evt, data) {
            console.debug("Dispatched event: " + evt +" with "+ data +" into " + evtElement);
            evtElement.dispatchEvent(new CustomEvent(evt, { detail: data }));
        },


        componentWillUpdate: function(newProps, newState) {
            if (this.onComponentUpdate) this.onComponentUpdate(newProps, newState);
        },

        componentWillReceiveProps: function(nextProps) {
            if (this.validateListener) this.validateListener(nextProps);
        },

        componentDidMount: function() {
            if (this.subscribeToEvents) {
                var eventslist = this.subscribeToEvents();
                eventslist.forEach(function(el) {
                   addEventListener(el[0], el[1]);
                });
            }
            if (this.startListener) this.startListener();
            if (this.onMount) {
                this.onMount();
            }
        },
        componentWillUnmount: function() {
            if (this.subscribeToEvents) {
                var eventslist = this.subscribeToEvents();
                eventslist.forEach(function(el) {
                    removeEventListener(el[0], el[1]);
                });
            }
            if (this.stopListener) this.stopListener();
            if (this.onUnmount) {
                this.onUnmount();
            }
        }
    };

});