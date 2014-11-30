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

    return {

        checkVisibility: function () {
            var element = this.refs.monitorVisibility;
            if (element) {
                var dom = element.getDOMNode();
                var lastState = this.state.visibility;
                if (lastState === undefined) lastState = true;
                var currentlyVisible = $(dom).visible(true);
                if (lastState != currentlyVisible) {
                    this.setState({visibility: currentlyVisible});
                    if (currentlyVisible) {
                        this.reopenAllSubscriptions();
                    } else {
                        this.closeAllSubscriptions();
                    }
                }
            }
        },

        componentDidMount: function () {
            this.addEventListener("resize", this.checkVisibility);
            this.addEventListener("onscroll", this.checkVisibility);
            this.checkVisibility();
        },

        componentDidUpdate: function () {
            this.checkVisibility();
        },

        componentWillUnmount: function () {
            this.removeEventListener("resize", this.checkVisibility);
            this.removeEventListener("onscroll", this.checkVisibility);
        }
    };

});