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

define(['react', 'core_mixin'], function (React, core_mixin) {

    return {

        getInitialState: function () {
            return {active: this.props.active}
        },

        componentDidMount: function() {
            this.addEventListener("navBarSelection", this.handleSelectionEvent);
        },
        componentWillUnmount: function() {
            this.removeEventListener("navBarSelection", this.handleSelectionEvent);
        },

        handleSelectionEvent: function(evt) {
            this.setState({active: false});
        },

        handleClick: function() {
            if (!this.state.active) {
                this.raiseEvent("navBarSelection", {ckey: this.props.ckey});
                this.setState({active: true});
            }
        },

        asNavbarElement: function (element) {
            var self = this;

            var classes = self.cx({
                'active': self.state.active,
                'disabled': (!self.state.connected)
            });

            return <li className={classes}><a onClick={self.handleClick} href="#">{element}</a></li>;
        }
    };

});